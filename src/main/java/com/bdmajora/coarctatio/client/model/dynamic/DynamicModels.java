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

// The model reload with dynamic loading on: collect locations, scan for textures, stitch, and bake only what mods ask for at ModelBakeEvent; everything else bakes on first use (VintageFix's dynamic resources, laid over Coarctatio's model pipeline)
public final class DynamicModels {
    // Reload listeners that must run before the models rather than in registration order; TConstruct's texture creator generates the sprites its models reference
    private static final Set<String> DEFERRED_LISTENER_CLASSES = Collections.singleton("slimeknights.tconstruct.library.client.CustomTextureCreator");
    // "assets/ns/models/item/x.tmat.json" to "ns:item/x.tmat", the form the vanilla loader takes
    private static final Pattern MODEL_PATH = Pattern.compile("^/?assets/(.+?(?=/))/(?:.+?(?=/))/(.*)\\.(?:[A-Za-z]*)$");
    private static final List<IResourceManagerReloadListener> DEFERRED = new ArrayList<>();

    private static UnbakedModelProvider unbaked;
    private static BakedModelProvider baked;
    // Sprite names the scan registered weakly and no mod replaced, kept until the stitch so an unreferenced one can be dropped if the atlas does not fit
    private static volatile Set<String> weaklyRegisteredSprites = Collections.emptySet();
    // The atlas the reload in progress is filling, so the weak registration hook only fires for it
    private static volatile TextureMap reloadingAtlas;

    private DynamicModels() {
    }

    public static UnbakedModelProvider unbaked() {
        return unbaked;
    }

    public static BakedModelProvider baked() {
        return baked;
    }

    // True for the one listener class deferred; the caller cancels its registration
    public static boolean deferListener(IResourceManagerReloadListener listener) {
        if (!DEFERRED_LISTENER_CLASSES.contains(listener.getClass().getName())) {
            return false;
        }
        DEFERRED.add(listener);
        return true;
    }

    // ModelManager.onResourceManagerReload, dynamic edition
    public static void reload(ModelManager manager, IResourceManager resourceManager, TextureMap atlas, BlockModelShapes shapes) {
        // The pools stay open for the session: baking happens whenever a model is first drawn, and open() on the next reload starts them fresh
        Coarctatio.onResourceReloadStart();
        reloadingAtlas = atlas;
        List<IResourcePack> packs = resourceManager instanceof SimpleReloadableResourceManager
                ? PackPathLister.packsOf((SimpleReloadableResourceManager) resourceManager)
                : Collections.emptyList();
        TextureDiscovery.start(resourceManager, packs);
        ItemModelPrebake.stopAndJoin();
        TconTextureExistence.clear();
        for (IResourceManagerReloadListener listener : DEFERRED) {
            listener.onResourceManagerReload(resourceManager);
        }
        ModelLoader loader = new ModelLoader(resourceManager, atlas, shapes);
        ProgressManager.ProgressBar bar = ProgressManager.push("Setting up dynamic models", 5);
        bar.step("Model locations");
        ModelLocations.init(loader, shapes.getBlockStateMapper());
        UnbakedModelProvider models = new UnbakedModelProvider(ModelLoaderRegistryAccessor.coarctatio$loaders());
        BakedModelProvider bakedModels = new BakedModelProvider(models);
        unbaked = models;
        baked = bakedModels;
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

    // Implemented on ModelManager by mixin; the two private fields the reload writes
    public interface ModelManagerAccess {
        void coarctatio$setModelRegistry(BakedModelProvider registry);

        void coarctatio$setDefaultModel(IBakedModel model);
    }

    // TConstruct's material, tool and armor models are read by its own loaders before any bake and must already be resolvable; the material ones are pinned since they are asked for constantly
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

    private static boolean isEarlyModelPath(String path) {
        if (path.length() < 6 || !path.endsWith(".json")) {
            return false;
        }
        String name = path.substring(0, path.length() - 5);
        return name.endsWith("tmat") || name.endsWith("tcon") || name.endsWith(".mod") || name.endsWith("conarm");
    }

    // Namespaces whose models are loaded up front the way vanilla did, for mods that expect every model of theirs to exist by ModelBakeEvent
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
        // Guidebook's book model is drawn by a renderer that never registers it, so its textures would otherwise miss the atlas
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

    private static void collectTextures(IModel model, Set<ResourceLocation> into) {
        try {
            into.addAll(model.getTextures());
        } catch (RuntimeException ignored) {
            // A model whose parent chain is broken still loads; its textures are simply not known yet
        }
    }

    // Called from the atlas right before TextureStitchEvent.Pre: the scanned sprites and every fluid's, registered weakly so a mod's own registration for the same name wins
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

    private static void registerFluidSprite(WeakSpriteTextureMap atlas, ResourceLocation sprite) {
        if (sprite == null) {
            return;
        }
        ResourceLocation file = new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(file)) {
            registerWeak(atlas, sprite);
        } catch (Exception ignored) {
            // Fluids registered without textures are drawn by their own renderers
        }
    }

    private static void registerWeak(WeakSpriteTextureMap atlas, ResourceLocation sprite) {
        try {
            atlas.coarctatio$registerSpriteWeak(sprite);
        } catch (RuntimeException ignored) {
            // A malformed location the scan matched is not a sprite
        }
    }

    // The atlas hands over its weak set once stitching starts, when weak and strong no longer differ for registration purposes
    public static void rememberWeakSprites(Set<String> names) {
        weaklyRegisteredSprites = new ObjectOpenHashSet<>(names);
    }

    // Stitches, and if the scan over-collected past what fits, first drops every weakly registered sprite no model references, then the largest sprites one at a time
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

    // Loads every known model and its dependencies with texture capture on, the slow path vanilla always took
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
                // A model that fails to load references nothing
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

    // Names the stitcher may drop: weakly registered and not in the referenced set
    public static boolean isDroppable(String spriteName, Set<String> referenced) {
        return weaklyRegisteredSprites.contains(spriteName) && !referenced.contains(spriteName);
    }

    public static List<String> statistics() {
        if (baked == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(
                "  Dynamic models:   " + baked.cachedCount() + " baked cached, " + baked.permanentCount() + " pinned",
                "  Unbaked models:   " + unbaked.cachedCount() + " cached, " + unbaked.permanentCount() + " pinned");
    }
}
