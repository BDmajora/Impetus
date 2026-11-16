package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.Minecraft;
import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.client.model.dynamic.compat.TconTextureExistence;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelBakeryAccessor;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelLoaderRegistryAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ProgressManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Loads and bakes models on demand instead of all at once.
public final class DynamicModels {
    // Listener classes that run after the tables are filled.
    private static final Set<String> DEFERRED_LISTENER_CLASSES = Collections.singleton("slimeknights.tconstruct.library.client.CustomTextureCreator");
    // Pattern of model asset paths.
    private static final Pattern MODEL_PATH = Pattern.compile("^/?assets/(.+?(?=/))/(?:.+?(?=/))/(.*)\\.(?:[A-Za-z]*)$");
    // Listeners that were deferred.
    @SuppressWarnings("deprecation")
    private static final List<IResourceManagerReloadListener> DEFERRED = new ArrayList<>();

    // Provider of unbaked models.
    private static UnbakedModelProvider unbaked;
    // Provider of baked models.
    private static BakedModelProvider baked;
    // Names of sprites registered without a reference.
    private static volatile Set<String> weaklyRegisteredSprites = Collections.emptySet();
    // Atlas that is being reloaded.
    private static volatile TextureMap reloadingAtlas;

    // Prevents instantiation
    private DynamicModels() {
    }

    // Returns the provider of unbaked models.
    public static UnbakedModelProvider unbaked() {
        return unbaked;
    }

    // Returns the provider of baked models.
    public static BakedModelProvider baked() {
        return baked;
    }

    // Returns whether the listener was deferred.
    @SuppressWarnings("deprecation")
    public static boolean deferListener(IResourceManagerReloadListener listener) {
        if (!DEFERRED_LISTENER_CLASSES.contains(listener.getClass().getName())) {
            return false;
        }
        DEFERRED.add(listener);
        return true;
    }

    // Runs every deferred listener.
    @SuppressWarnings("deprecation")
    private static void runDeferredListeners(IResourceManager resourceManager) {
        for (IResourceManagerReloadListener listener : DEFERRED) {
            listener.onResourceManagerReload(resourceManager);
        }
    }

    // Replaces the model registry with on demand providers and loads the atlas.
    public static void reload(ModelManager manager, IResourceManager resourceManager, TextureMap atlas, BlockModelShapes shapes) {
        Coarctatio.onResourceReloadStart();
        reloadingAtlas = atlas;
        List<IResourcePack> packs = resourceManager instanceof SimpleReloadableResourceManager
                ? PackPathLister.packsOf((SimpleReloadableResourceManager) resourceManager)
                : Collections.emptyList();
        TextureDiscovery.start(resourceManager, packs);
        ModelLoader loader;
        ProgressManager.ProgressBar bar;
        try {
            ItemModelPrebake.stopAndJoin();
            TconTextureExistence.clear();
            runDeferredListeners(resourceManager);
            loader = new ModelLoader(resourceManager, atlas, shapes);
            bar = ProgressManager.push("Setting up dynamic models", 5);
            bar.step("Model locations");
            ModelLocations.init(loader, shapes.getBlockStateMapper());
        } catch (RuntimeException | Error e) {
            TextureDiscovery.abandon(e);
            throw e;
        }
        TextureDiscovery.tablesReady();
        UnbakedModelProvider models = new UnbakedModelProvider(ModelLoaderRegistryAccessor.coarctatio$loaders());
        BakedModelProvider bakedModels = new BakedModelProvider(models);
        unbaked = models;
        baked = bakedModels;
        // Replaces the model registry.
        ((ModelManagerAccess) manager).coarctatio$setModelRegistry(bakedModels);
        Set<ResourceLocation> textures = new HashSet<>();
        bar.step("Early model loading");
        loadEarlyModels(packs, textures);
        bar.step("Eager model loading");
        Map<ModelResourceLocation, IModel> eager = loadEagerModels(textures);
        for (ResourceLocation texture : ModelLoaderRegistryAccessor.coarctatio$textures()) {
            textures.add(texture);
        }
        textures.remove(TextureMap.LOCATION_MISSING_TEXTURE);
        textures.addAll(ModelBakeryAccessor.coarctatio$builtinTextures());
        bar.step("Textures");
        atlas.loadSprites(resourceManager, map -> textures.forEach(map::registerSprite));
        reloadingAtlas = null;
        IBakedModel missing = bakedModels.getObject(UnbakedModelProvider.MISSING);
        if (missing == null) {
            throw new IllegalStateException("The missing model is missing");
        }
        bakedModels.missingModel = missing;
        // Replaces the default model.
        ((ModelManagerAccess) manager).coarctatio$setDefaultModel(missing);
        bakeEagerModels(eager);
        if (FluidRegistry.isUniversalBucketEnabled()) {
            ModelLoader.setBucketModelDefinition(ForgeModContainer.getInstance().universalBucket);
        }
        bar.step("Bake events");
        BakeEventDispatcher.post(manager, bakedModels, loader);
        ProgressManager.pop(bar);
        shapes.reloadModels();
    }

    // Access to the model manager fields.
    public interface ModelManagerAccess {
        void coarctatio$setModelRegistry(BakedModelProvider registry);

        void coarctatio$setDefaultModel(IBakedModel model);
    }

    // Loads the models that must exist before baking and collects their textures.
    private static void loadEarlyModels(List<IResourcePack> packs, Set<ResourceLocation> textures) {
        Set<String> paths = new ObjectOpenHashSet<>();
        for (IResourcePack pack : packs) {
            for (String path : PackPathLister.paths(pack)) {
                if (isEarlyModelPath(path)) {
                    paths.add(path);
                }
            }
        }
        if (paths.isEmpty()) {
            return;
        }
        Coarctatio.LOGGER.info("Early loading {} models", paths.size());
        int pinned = 0;
        for (String path : paths) {
            Matcher matcher = MODEL_PATH.matcher(path);
            if (!matcher.matches()) {
                Coarctatio.LOGGER.warn("Path {} is not a valid model location", path);
                continue;
            }
            ResourceLocation location = new ResourceLocation(matcher.group(1), matcher.group(2));
            try {
                IModel model = unbaked.getObject(location);
                if (model == null) {
                    continue;
                }
                collectTextures(model, textures);
                if (path.endsWith(".tmat.json")) {
                    unbaked.putObject(location, model);
                    pinned++;
                }
            } catch (Exception e) {
                Coarctatio.LOGGER.error("Early load error for {}", location, e);
            }
        }
        Coarctatio.LOGGER.info("Pinned {} early models", pinned);
    }

    // Returns whether the path names an early model.
    private static boolean isEarlyModelPath(String path) {
        if (path.length() < 6 || !path.endsWith(".json")) {
            return false;
        }
        String name = path.substring(0, path.length() - 5);
        return name.endsWith("tmat") || name.endsWith("tcon") || name.endsWith(".mod") || name.endsWith("conarm");
    }

    // Returns the models of the eager namespaces and collects their textures.
    private static Map<ModelResourceLocation, IModel> loadEagerModels(Set<ResourceLocation> textures) {
        Set<String> namespaces = new HashSet<>(Arrays.asList(CoarctatioConfig.get().dynamicModelsEagerNamespaces));
        List<ResourceLocation> locations = new ArrayList<>();
        for (ModelResourceLocation location : ModelLocations.ITEM_VARIANT_FILES.keySet()) {
            if (namespaces.contains(location.getNamespace())) {
                locations.add(location);
            }
        }
        for (Collection<ModelResourceLocation> variants : ModelLocations.VARIANTS_BY_BLOCKSTATE.values()) {
            for (ModelResourceLocation location : variants) {
                if (namespaces.contains(location.getNamespace())) {
                    locations.add(location);
                }
            }
        }
        if (Loader.isModLoaded("gbook")) {
            locations.add(new ResourceLocation("gbook", "block/custom/book"));
        }
        Map<ModelResourceLocation, IModel> loaded = new Object2ObjectOpenHashMap<>();
        if (locations.isEmpty()) {
            return loaded;
        }
        ProgressManager.ProgressBar bar = ProgressManager.push("Eager model loading", locations.size());
        int errors = 0;
        for (ResourceLocation location : locations) {
            bar.step(location.toString());
            try {
                IModel model = unbaked.getObject(location);
                if (model == null) {
                    continue;
                }
                collectTextures(model, textures);
                if (location instanceof ModelResourceLocation) {
                    loaded.put((ModelResourceLocation) location, model);
                }
            } catch (RuntimeException e) {
                Coarctatio.LOGGER.error("Error eagerly loading model {}: {}", location, e.toString());
                errors++;
            }
        }
        Coarctatio.LOGGER.info("{}/{} eager models had errors loading", errors, locations.size());
        ProgressManager.pop(bar);
        return loaded;
    }

    // Bakes the eagerly loaded models.
    private static void bakeEagerModels(Map<ModelResourceLocation, IModel> eager) {
        if (eager.isEmpty()) {
            return;
        }
        ProgressManager.ProgressBar bar = ProgressManager.push("Eager model baking", eager.size());
        for (ModelResourceLocation location : eager.keySet()) {
            bar.step(location.toString());
            try {
                baked.getObject(location);
            } catch (RuntimeException e) {
                Coarctatio.LOGGER.error("Error eagerly baking model {}: {}", location, e.toString());
            }
        }
        ProgressManager.pop(bar);
    }

    // Adds the textures of the model.
    private static void collectTextures(IModel model, Set<ResourceLocation> into) {
        try {
            into.addAll(model.getTextures());
        } catch (RuntimeException ignored) {
        }
    }

    // Registers the discovered textures and fluid sprites for the atlas being reloaded.
    public static void registerDiscoveredSprites(TextureMap atlas) {
        if (atlas != reloadingAtlas) {
            return;
        }
        WeakSpriteTextureMap weak = (WeakSpriteTextureMap) atlas;
        for (ResourceLocation texture : TextureDiscovery.take()) {
            registerWeak(weak, texture);
        }
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            registerFluidSprite(weak, fluid.getStill());
            registerFluidSprite(weak, fluid.getFlowing());
        }
    }

    // Registers the fluid sprite when its file exists.
    private static void registerFluidSprite(WeakSpriteTextureMap atlas, ResourceLocation sprite) {
        if (sprite == null) {
            return;
        }
        ResourceLocation file = new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(file)) {
            registerWeak(atlas, sprite);
        } catch (Exception ignored) {
        }
    }

    // Registers the sprite without keeping a reference.
    private static void registerWeak(WeakSpriteTextureMap atlas, ResourceLocation sprite) {
        try {
            atlas.coarctatio$registerSpriteWeak(sprite);
        } catch (RuntimeException ignored) {
        }
    }

    // Stores the names of sprites registered without a reference.
    public static void rememberWeakSprites(Set<String> names) {
        weaklyRegisteredSprites = new ObjectOpenHashSet<>(names);
    }

    // Stitches the atlas and falls back to fewer sprites when it does not fit.
    public static void stitchWithFallback(Stitcher stitcher) {
        boolean triedReferencedOnly = false;
        while (true) {
            try {
                stitcher.doStitch();
                weaklyRegisteredSprites = Collections.emptySet();
                return;
            } catch (StitcherException e) {
                if (!triedReferencedOnly) {
                    Coarctatio.LOGGER.warn("The scanned sprites do not fit the atlas; loading every model to find which are referenced");
                    ((DroppingStitcher) stitcher).coarctatio$retainSprites(referencedSpriteNames());
                    triedReferencedOnly = true;
                } else if (!((DroppingStitcher) stitcher).coarctatio$dropLargestSprite()) {
                    throw new StitcherException(null, "Could not stitch the atlas even with every droppable sprite removed");
                }
            }
        }
    }

    // Returns the sprites that loaded models reference.
    private static Set<String> referencedSpriteNames() {
        Set<ResourceLocation> captured = Collections.synchronizedSet(new HashSet<>());
        UnbakedModelProvider.textureCapture = captured;
        unbaked.clearCache();
        ProgressManager.ProgressBar bar = ProgressManager.push("Fallback texture gathering", 1);
        Set<ResourceLocation> visited = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>(ModelLocations.ALL_KNOWN);
        ResourceLocation next;
        while ((next = queue.poll()) != null) {
            if (!visited.add(next)) {
                continue;
            }
            try {
                IModel model = unbaked.getObject(next);
                if (model != null) {
                    for (ResourceLocation dependency : model.getDependencies()) {
                        if (!visited.contains(dependency)) {
                            queue.add(dependency);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        bar.step("");
        ProgressManager.pop(bar);
        UnbakedModelProvider.textureCapture = null;
        Set<String> names = new HashSet<>();
        for (ResourceLocation location : captured) {
            names.add(location.toString());
        }
        return names;
    }

    // Returns whether the sprite may be dropped.
    public static boolean isDroppable(String spriteName, Set<String> referenced) {
        return weaklyRegisteredSprites.contains(spriteName) && !referenced.contains(spriteName);
    }

    // Returns the cache sizes as text lines.
    public static List<String> statistics() {
        if (baked == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(
                "  Dynamic models:   " + baked.cachedCount() + " baked cached, " + baked.permanentCount() + " pinned",
                "  Unbaked models:   " + unbaked.cachedCount() + " cached, " + unbaked.permanentCount() + " pinned");
    }
}
