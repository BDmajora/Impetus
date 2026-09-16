package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// The model registry ModelManager consults: bakes on request from the unbaked provider, keeps the result in an expiring soft cache, and holds permanently only what mods put or the item prebake baked (VintageFix's DynamicBakedModelProvider)
public final class BakedModelProvider extends RegistrySimple<ModelResourceLocation, IBakedModel> {
    private final UnbakedModelProvider models;
    private final Map<ModelResourceLocation, IBakedModel> permanent = Collections.synchronizedMap(new Object2ObjectOpenHashMap<>());
    private final Cache<ModelResourceLocation, Optional<IBakedModel>> baked = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .maximumSize(1000)
            .concurrencyLevel(8)
            .softValues()
            .build();
    private final Map<IBlockState, IBakedModel> stateStore = new StateStore();
    // Set by the reload once builtin/missing has baked; answered for known variants that fail so a lookup never returns null for them
    IBakedModel missingModel;

    public BakedModelProvider(UnbakedModelProvider models) {
        this.models = models;
    }

    // RegistrySimple's own map is never used; getObject and putObject are answered from the caches
    @Override
    protected Map<ModelResourceLocation, IBakedModel> createUnderlyingMap() {
        return ImmutableMap.of();
    }

    // What BlockModelShapes.bakedModelStore becomes: mods that reach into that field to read or replace a state's model keep working through the registry
    public Map<IBlockState, IBakedModel> stateStore() {
        return this.stateStore;
    }

    @Nullable
    @Override
    public IBakedModel getObject(ModelResourceLocation location) {
        Optional<IBakedModel> model = this.baked.getIfPresent(location);
        if (model == null) {
            synchronized (this) {
                model = this.baked.getIfPresent(location);
                if (model == null) {
                    model = Optional.ofNullable(this.bake(location));
                    this.baked.put(location, model);
                }
            }
        }
        if (model.isPresent()) {
            return model.get();
        }
        return ModelLocations.ITEM_VARIANTS.contains(location) ? this.missingModel : null;
    }

    @Nullable
    public IBakedModel getIfBaked(ModelResourceLocation location) {
        Optional<IBakedModel> model = this.baked.getIfPresent(location);
        return model != null ? model.orElse(null) : null;
    }

    private IBakedModel bake(ModelResourceLocation location) {
        IBakedModel model = this.permanent.get(location);
        if (model != null) {
            return model;
        }
        IModel unbaked = null;
        Throwable failure = null;
        try {
            unbaked = this.models.getObject(location);
        } catch (Throwable e) {
            failure = e;
        }
        if (unbaked == null) {
            // A listener may still supply a model for a location no file backs
            DynamicModelBakeEvent event = new DynamicModelBakeEvent(location, null, this.missingModel);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.bakedModel != this.missingModel) {
                return event.bakedModel;
            }
            if (ModelLocations.canLogError(location.getNamespace())) {
                Coarctatio.LOGGER.error("Failed to load model {}", location, failure);
            }
        } else {
            try {
                return bakeChecked(location, unbaked);
            } catch (Throwable e) {
                if (ModelLocations.canLogError(location.getNamespace())) {
                    Coarctatio.LOGGER.error("Error baking model {}", location, e);
                }
            }
        }
        // A variant the state mapper produced answers the missing model the way vanilla's registry would after onPostBakeEvent filled it in
        Collection<ModelResourceLocation> known = ModelLocations.VARIANTS_BY_BLOCKSTATE.get(new ResourceLocation(location.getNamespace(), location.getPath()));
        return known != null && known.contains(location) ? this.missingModel : null;
    }

    // Warns once per sprite the scan did not find, which is the signal the folder list in TextureDiscovery is missing a mod's layout
    private static final Function<ResourceLocation, TextureAtlasSprite> TEXTURE_GETTER = location -> {
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        String name = location.toString();
        TextureAtlasSprite sprite = map.getAtlasSprite(name);
        if (sprite == map.getMissingSprite() && !sprite.getIconName().equals(name)
                && !(location.getNamespace().equals("minecraft") && sprite.getIconName().equals(location.getPath()))
                && !name.equals("minecraft:builtin/white")) {
            Coarctatio.LOGGER.warn("Texture {} was not discovered during the texture scan", name);
        }
        return sprite;
    };

    private static IBakedModel bakeChecked(ModelResourceLocation location, IModel model) {
        IBakedModel baked = model.bake(model.getDefaultState(), DefaultVertexFormats.ITEM, TEXTURE_GETTER);
        if (UnbakedModelProvider.MISSING.equals(location)) {
            return baked;
        }
        DynamicModelBakeEvent event = new DynamicModelBakeEvent(location, model, baked);
        MinecraftForge.EVENT_BUS.post(event);
        return event.bakedModel;
    }

    @Override
    public void putObject(ModelResourceLocation key, IBakedModel value) {
        this.permanent.put(key, value);
        this.baked.invalidate(key);
    }

    // Drops both the baked and unbaked entry, for a mod that replaced a model after it may already have been baked
    public void invalidate(ModelResourceLocation key) {
        this.baked.invalidate(key);
        this.models.invalidate(key);
    }

    public long cachedCount() {
        return this.baked.size();
    }

    public int permanentCount() {
        return this.permanent.size();
    }

    @Override
    public Set<ModelResourceLocation> getKeys() {
        return this.permanent.keySet();
    }

    @Override
    public Iterator<IBakedModel> iterator() {
        return this.permanent.values().iterator();
    }

    // A Map view over the registry keyed by state; reads resolve through BlockModelShapes, writes pin the model for that state's location
    private final class StateStore implements Map<IBlockState, IBakedModel> {
        @Override
        public int size() {
            return BakedModelProvider.this.permanent.size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(Object key) {
            return true;
        }

        @Override
        public boolean containsValue(Object value) {
            return true;
        }

        @Override
        public IBakedModel get(Object key) {
            if (!(key instanceof IBlockState)) {
                return null;
            }
            return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState((IBlockState) key);
        }

        @Override
        public IBakedModel put(IBlockState key, IBakedModel value) {
            LocationAwareBlockModelShapes shapes = (LocationAwareBlockModelShapes) Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
            ModelResourceLocation location = shapes.coarctatio$locationForState(key);
            if (location != null) {
                BakedModelProvider.this.putObject(location, value);
            }
            if (key instanceof ModelHoldingState) {
                ((ModelHoldingState) key).coarctatio$cacheModel(null);
            }
            return null;
        }

        @Override
        public IBakedModel remove(Object key) {
            return null;
        }

        @Override
        public void putAll(Map<? extends IBlockState, ? extends IBakedModel> map) {
            map.forEach(this::put);
        }

        @Override
        public void clear() {
        }

        @Override
        public Set<IBlockState> keySet() {
            return Collections.emptySet();
        }

        @Override
        public Collection<IBakedModel> values() {
            return BakedModelProvider.this.permanent.values();
        }

        @Override
        public Set<Entry<IBlockState, IBakedModel>> entrySet() {
            return Collections.emptySet();
        }
    }
}
