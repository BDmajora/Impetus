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
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

// Registry that bakes each model when it is first requested.
public final class BakedModelProvider extends RegistrySimple<ModelResourceLocation, IBakedModel> {
    // Source of the unbaked models.
    private final UnbakedModelProvider models;
    // Models stored explicitly that never expire.
    private final Map<ModelResourceLocation, IBakedModel> permanent = Collections.synchronizedMap(new Object2ObjectOpenHashMap<>());
    // Models baked on demand that may expire.
    private final Cache<ModelResourceLocation, Optional<IBakedModel>> baked = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(3))
            .maximumSize(1000)
            .concurrencyLevel(8)
            .softValues()
            .build();
    // View that maps block states to models.
    private final Map<IBlockState, IBakedModel> stateStore = new StateStore();
    // Model returned for known locations that fail to bake.
    IBakedModel missingModel;

    // Creates a provider and rejects null models.
    public BakedModelProvider(UnbakedModelProvider models) {
        this.models = java.util.Objects.requireNonNull(models, "models");
    }

    // Returns an empty map.
    @Override
    protected Map<ModelResourceLocation, IBakedModel> createUnderlyingMap() {
        return ImmutableMap.of();
    }

    // Returns the view that maps block states to models.
    public Map<IBlockState, IBakedModel> stateStore() {
        return this.stateStore;
    }

    // Returns the stored or baked model of the location.
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

    // Returns the cached model without baking.
    @Nullable
    public IBakedModel getIfBaked(ModelResourceLocation location) {
        Optional<IBakedModel> model = this.baked.getIfPresent(location);
        return model != null ? model.orElse(null) : null;
    }

    // Returns the stored model or bakes the location and returns the missing model for known failures.
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
        Collection<ModelResourceLocation> known = ModelLocations.VARIANTS_BY_BLOCKSTATE.get(new ResourceLocation(location.getNamespace(), location.getPath()));
        return known != null && known.contains(location) ? this.missingModel : null;
    }

    // Looks up the atlas sprite of a texture and warns about undiscovered textures.
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

    // Returns the model baked with the item format and passed through the bake event.
    private static IBakedModel bakeChecked(ModelResourceLocation location, IModel model) {
        IBakedModel baked = model.bake(model.getDefaultState(), DefaultVertexFormats.ITEM, TEXTURE_GETTER);
        if (UnbakedModelProvider.MISSING.equals(location)) {
            return baked;
        }
        DynamicModelBakeEvent event = new DynamicModelBakeEvent(location, model, baked);
        MinecraftForge.EVENT_BUS.post(event);
        return event.bakedModel;
    }

    // Stores a model that never expires.
    @Override
    public void putObject(ModelResourceLocation key, IBakedModel value) {
        this.permanent.put(key, value);
        this.baked.invalidate(key);
    }

    // Drops the cached model and the cached unbaked model.
    public void invalidate(ModelResourceLocation key) {
        this.baked.invalidate(key);
        this.models.invalidate(key);
    }

    // Returns the number of cached models.
    public long cachedCount() {
        return this.baked.size();
    }

    // Returns the number of stored models.
    public int permanentCount() {
        return this.permanent.size();
    }

    // Returns the locations of the stored models.
    @Override
    public Set<ModelResourceLocation> getKeys() {
        return this.permanent.keySet();
    }

    // Returns an iterator over the stored models.
    @Override
    public Iterator<IBakedModel> iterator() {
        return this.permanent.values().iterator();
    }

    // Map view that reads models of block states from the model shapes.
    private final class StateStore implements Map<IBlockState, IBakedModel> {
        // Returns the number of stored models.
        @Override
        public int size() {
            return BakedModelProvider.this.permanent.size();
        }

        // Returns false.
        @Override
        public boolean isEmpty() {
            return false;
        }

        // Returns true.
        @Override
        public boolean containsKey(Object key) {
            return true;
        }

        // Returns true.
        @Override
        public boolean containsValue(Object value) {
            return true;
        }

        // Returns the model of the block state.
        @Override
        public IBakedModel get(Object key) {
            if (!(key instanceof IBlockState)) {
                return null;
            }
            return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState((IBlockState) key);
        }

        // Stores the model under the location of the state and clears the model kept on the state.
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

        // Ignores the removal and returns null.
        @Override
        public IBakedModel remove(Object key) {
            return null;
        }

        // Stores every entry through put.
        @Override
        public void putAll(Map<? extends IBlockState, ? extends IBakedModel> map) {
            map.forEach(this::put);
        }

        // Ignores the request.
        @Override
        public void clear() {
        }

        // Returns an empty set.
        @Override
        public Set<IBlockState> keySet() {
            return Collections.emptySet();
        }

        // Returns the stored models.
        @Override
        public Collection<IBakedModel> values() {
            return BakedModelProvider.this.permanent.values();
        }

        // Returns an empty set.
        @Override
        public Set<Entry<IBlockState, IBakedModel>> entrySet() {
            return Collections.emptySet();
        }
    }
}
