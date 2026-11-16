package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelLoaderRegistryAccessor;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;

import javax.annotation.Nullable;
import java.time.Duration;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Registry that loads each unbaked model when it is first requested.
public final class UnbakedModelProvider implements IRegistry<ResourceLocation, IModel> {
    // Location of the missing model.
    public static final ModelResourceLocation MISSING = new ModelResourceLocation("builtin/missing", "missing");

    // Loader of vanilla model files.
    private static final ICustomModelLoader VANILLA_LOADER = loaderInstance("net.minecraftforge.client.model.ModelLoader$VanillaLoader");
    // Loader of block state variants.
    private static final ICustomModelLoader VARIANT_LOADER = loaderInstance("net.minecraftforge.client.model.ModelLoader$VariantLoader");
    // Class that wraps vanilla models.
    private static final Class<?> VANILLA_MODEL_WRAPPER = classNamed("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper");

    // Set that collects textures of loaded models or null.
    public static volatile Set<ResourceLocation> textureCapture;

    // Loaders registered by mods.
    private final Set<ICustomModelLoader> loaders;
    // Models stored explicitly that never expire.
    private final Map<ResourceLocation, IModel> permanent = new Object2ObjectOpenHashMap<>();
    // Models loaded on demand that may expire.
    private final Cache<ResourceLocation, Optional<IModel>> loaded = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(3))
            .maximumSize(1000)
            .concurrencyLevel(8)
            .softValues()
            .build();
    // Locations that redirect to another location.
    private final Map<ResourceLocation, ResourceLocation> aliases = new Object2ObjectOpenHashMap<>();

    // Creates a provider and rejects null loaders.
    public UnbakedModelProvider(Set<ICustomModelLoader> loaders) {
        this.loaders = java.util.Objects.requireNonNull(loaders, "loaders");
        this.aliases.put(new ResourceLocation("block/builtin/entity"), new ResourceLocation("builtin/entity"));
    }

    // Returns the stored or loaded model of the location.
    @Nullable
    @Override
    public IModel getObject(ResourceLocation location) {
        Optional<IModel> model = this.loaded.getIfPresent(location);
        if (model == null) {
            synchronized (this) {
                model = this.loaded.getIfPresent(location);
                if (model == null) {
                    try {
                        model = Optional.ofNullable(this.loadFromBlockstateOrItem(location));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Set<ResourceLocation> capture = textureCapture;
                    if (capture != null && model.isPresent()) {
                        capture.addAll(model.get().getTextures());
                    }
                    this.loaded.put(location, model);
                }
            }
        }
        return model.orElse(null);
    }

    // Returns the model of the location or the missing model when loading fails.
    public IModel getModelOrMissing(ResourceLocation location) {
        try {
            return this.getObject(location);
        } catch (RuntimeException e) {
            return this.getObject(MISSING);
        }
    }

    // Returns the model loaded as a block state or as an item and throws when both fail.
    private IModel loadFromBlockstateOrItem(ResourceLocation location) throws ModelLoaderRegistry.LoaderException {
        ResourceLocation itemFile = null;
        Throwable blockstateFailure = null;
        Throwable itemFailure = null;
        IModel model = null;
        try {
            model = this.load(location, new LinkedHashSet<>());
        } catch (Throwable e) {
            blockstateFailure = e;
            if (location instanceof ModelResourceLocation) {
                itemFile = ModelLocations.itemFileFor((ModelResourceLocation) location);
                if (itemFile == null) {
                    itemFile = new ResourceLocation(location.getNamespace(), location.getPath());
                }
                try {
                    model = this.load(itemFile, new LinkedHashSet<>());
                    if (VANILLA_MODEL_WRAPPER.isInstance(model)) {
                        for (ResourceLocation override : model.asVanillaModel().get().getOverrideLocations()) {
                            if (!location.equals(override)) {
                                ModelLocations.addItemVariantFile(ModelLocations.inventoryVariant(override.toString()), override);
                            }
                        }
                    }
                } catch (Throwable e2) {
                    itemFailure = e2;
                }
            }
        }
        if (model != null) {
            return model;
        }
        ModelLoaderRegistry.LoaderException failure = new ModelLoaderRegistry.LoaderException("Model loading failure for " + location);
        if (blockstateFailure != null) {
            failure.addSuppressed(blockstateFailure);
        }
        if (itemFailure != null) {
            failure.addSuppressed(itemFailure);
        }
        if (ModelLocations.canLogError(location.getNamespace())) {
            Coarctatio.LOGGER.error("Failed to load model {}", location, blockstateFailure);
            if (itemFailure != null) {
                Coarctatio.LOGGER.error("Failed to load model {} as item {}", location, itemFile, itemFailure);
            }
        }
        throw failure;
    }

    // Returns the model loaded through the loader that accepts the location.
    private IModel load(ResourceLocation location, Set<ResourceLocation> stack) throws ModelLoaderRegistry.LoaderException {
        if (stack.add(location)) {
            ResourceLocation alias;
            synchronized (this.aliases) {
                alias = this.aliases.get(location);
            }
            if (alias != null) {
                return this.load(alias, stack);
            }
        }
        IModel model = this.permanent.get(location);
        if (model != null) {
            return model;
        }
        model = ModelLoaderRegistryAccessor.coarctatio$cache().get(location);
        if (model != null) {
            return model;
        }
        ResourceLocation actual = ModelLoaderRegistry.getActualLocation(location);
        ICustomModelLoader accepted = null;
        for (ICustomModelLoader loader : this.loaders) {
            try {
                if (loader.accepts(actual)) {
                    if (accepted != null) {
                        throw new ModelLoaderRegistry.LoaderException("Loaders (" + accepted + " and " + loader + ") both accept model " + location);
                    }
                    accepted = loader;
                }
            } catch (Exception e) {
                throw new ModelLoaderRegistry.LoaderException("Exception checking if model " + location + " can be loaded with loader " + loader, e);
            }
        }
        if (accepted == null) {
            String path = actual.getPath();
            boolean builtin = path.startsWith("builtin/") || path.startsWith("block/builtin/") || path.startsWith("item/builtin/");
            if (!builtin && VARIANT_LOADER.accepts(actual)) {
                accepted = VARIANT_LOADER;
            } else if (VANILLA_LOADER.accepts(actual)) {
                accepted = VANILLA_LOADER;
            }
        }
        if (accepted == null) {
            throw new ModelLoaderRegistry.LoaderException("No suitable loader found for the model " + location);
        }
        try {
            model = accepted.loadModel(actual);
        } catch (Exception e) {
            if (e instanceof ModelBlockDefinition.MissingVariantException && accepted == VARIANT_LOADER) {
                // Creates the failure with a message.
                throw new MissingVariantFailure("Variant " + location + " does not exist");
            }
            throw new ModelLoaderRegistry.LoaderException("Exception loading model " + location + " with loader " + accepted, e);
        }
        if (model == null) {
            throw new ModelLoaderRegistry.LoaderException("Loader " + accepted + " provided null model for " + location);
        }
        if (model == ModelLoaderRegistry.getMissingModel() && !location.equals(MISSING)) {
            throw new ModelLoaderRegistry.LoaderException("Loader " + accepted + " provided missing model for " + location);
        }
        try {
            model.getTextures();
        } catch (Exception e) {
            throw new ModelLoaderRegistry.LoaderException("Exception loading model " + location + " with loader " + accepted, e);
        }
        return model;
    }

    // Stores a model that never expires.
    @Override
    public void putObject(ResourceLocation key, IModel value) {
        synchronized (this) {
            this.permanent.put(key, value);
        }
        this.loaded.invalidate(key);
    }

    // Redirects the location to another location.
    public void putAlias(ResourceLocation from, ResourceLocation to) {
        synchronized (this.aliases) {
            this.aliases.put(from, to);
        }
    }

    // Drops the cached model of the location.
    public void invalidate(ResourceLocation key) {
        this.loaded.invalidate(key);
    }

    // Drops every cached model.
    public void clearCache() {
        this.loaded.invalidateAll();
    }

    // Returns the number of cached models.
    public long cachedCount() {
        return this.loaded.size();
    }

    // Returns the number of stored models.
    public int permanentCount() {
        return this.permanent.size();
    }

    // Returns the locations of the stored models.
    @Override
    public Set<ResourceLocation> getKeys() {
        return this.permanent.keySet();
    }

    // Returns an iterator over the stored models.
    @Override
    public Iterator<IModel> iterator() {
        return this.permanent.values().iterator();
    }

    // Returns the singleton loader of the named class.
    private static ICustomModelLoader loaderInstance(String className) {
        try {
            Field instance = Class.forName(className).getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            return (ICustomModelLoader) instance.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // Returns the class of the name.
    private static Class<?> classNamed(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Failure for a variant that does not exist and carries no stack trace.
    private static final class MissingVariantFailure extends ModelLoaderRegistry.LoaderException {
        MissingVariantFailure(String message) {
            super(message);
        }

        // Returns the failure without recording a stack trace.
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
