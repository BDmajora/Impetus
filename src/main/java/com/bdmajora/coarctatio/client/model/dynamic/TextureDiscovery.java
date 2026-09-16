package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Finds the sprites to stitch without loading a model: every png under the texture folders models draw from, plus whatever the blockstate and model json files name, the way 1.19.3+ lists atlas sources (VintageFix's TextureCollector); runs on its own pool while the main thread builds the location tables
public final class TextureDiscovery {
    // The texture subfolders block and item models are known to use, across vanilla and the mods that keep their own layout; a sprite outside them is found through the json crawl or not at all
    private static final Pattern TEXTURE_PATH = Pattern.compile("^/?assets/(.+?(?=/))/textures/((?:attachment|aspect.?|bettergrass|block.?|cape|customoverlay|decors|item.?|entity/(armor|bed|chest)|fluid.?|model.?|part.?|pipe|rendering|ropebridge|slot.?|solid_block|tile.?|tinkers|tconstruct|valuetype.?)/.*)\\.png$");
    // Any quoted string shaped like a resource location, inside a json file
    private static final Pattern JSON_LOCATION = Pattern.compile("\"(?:([A-Za-z0-9_\\-.]+):|)([A-za-z0-9_\\-./]+)\"");
    // Sprites mods register from code, in folders the pattern above does not cover
    private static final ImmutableListMultimap<String, ResourceLocation> EXTRA_BY_MOD = ImmutableListMultimap.<String, ResourceLocation>builder()
            .put("mekanism", new ResourceLocation("mekanism", "entities/robit"))
            .put("gbook", new ResourceLocation("gbook", "cover"))
            .put("gbook", new ResourceLocation("gbook", "cover_gray"))
            .put("gbook", new ResourceLocation("gbook", "paper"))
            .put("gbook", new ResourceLocation("gbook", "transparent"))
            .put("industrialwires", new ResourceLocation("minecraft", "font/ascii"))
            .build();
    // Loose resource folders some packs ship next to the jar
    private static final String[] GAME_FOLDERS = {"resources", "oresources"};

    private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1), pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("Coarctatio texture scan " + thread.getPoolIndex());
        thread.setDaemon(true);
        return thread;
    }, null, false);

    // The scan in flight for the reload under way, joined when the atlas registers its sprites
    private static volatile ForkJoinTask<Set<ResourceLocation>> pending;
    private final List<IResourcePack> packs;
    private final IResourceManager manager;
    private final Set<ResourceLocation> visitedJson = Collections.synchronizedSet(new ObjectOpenHashSet<>());
    private final Map<ResourceLocation, Boolean> exists = new ConcurrentHashMap<>();

    private TextureDiscovery(IResourceManager manager, List<IResourcePack> packs) {
        this.manager = manager;
        this.packs = packs;
    }

    // Started at the top of the model reload, before the location tables exist; the json crawl waits for them
    public static void start(IResourceManager manager, List<IResourcePack> packs) {
        TextureDiscovery discovery = new TextureDiscovery(manager, packs);
        pending = POOL.submit(discovery::discover);
    }

    // Blocks until the scan is done and hands over its result once
    public static Set<ResourceLocation> take() {
        ForkJoinTask<Set<ResourceLocation>> task = pending;
        if (task == null) {
            return Collections.emptySet();
        }
        pending = null;
        return task.join();
    }

    private Set<ResourceLocation> discover() {
        long start = System.nanoTime();
        ForkJoinTask<List<ResourceLocation>> fromJson = POOL.submit(this::crawlJson);
        Set<ResourceLocation> textures = new ObjectOpenHashSet<>();
        int seen = 0;
        for (IResourcePack pack : this.packs) {
            for (String path : PackPathLister.paths(pack)) {
                seen += match(path, textures);
            }
        }
        Path gameDir = Minecraft.getMinecraft().gameDir.toPath();
        for (String folder : GAME_FOLDERS) {
            Path base = gameDir.resolve(folder);
            try (Stream<Path> stream = Files.walk(base)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    seen += match("assets/" + base.relativize(path).toString().replace(java.io.File.separatorChar, '/'), textures);
                }
            } catch (FileNotFoundException | NoSuchFileException ignored) {
                // Not every install has the loose folders
            } catch (IOException e) {
                Coarctatio.LOGGER.error("Error listing {}", base, e);
            }
        }
        for (Map.Entry<String, ResourceLocation> entry : EXTRA_BY_MOD.entries()) {
            textures.add(entry.getValue());
        }
        textures.addAll(fromJson.join());
        Coarctatio.LOGGER.info("Texture scan found {} sprites ({} matching paths) in {} ms", textures.size(), seen, (System.nanoTime() - start) / 1_000_000);
        return textures;
    }

    private static int match(String path, Set<ResourceLocation> into) {
        Matcher matcher = TEXTURE_PATH.matcher(path);
        if (!matcher.matches()) {
            return 0;
        }
        into.add(new ResourceLocation(matcher.group(1), matcher.group(2)));
        return 1;
    }

    // Reads every blockstate and item model json the location tables name and follows the model references inside, so a texture in an unlisted folder is still found if a model uses it
    private List<ResourceLocation> crawlJson() {
        ModelLocations.READY.join();
        Set<ResourceLocation> blockstates = new ObjectOpenHashSet<>();
        Consumer<ModelResourceLocation> collect = location -> blockstates.add(new ResourceLocation(location.getNamespace(), location.getPath()));
        ModelLocations.ITEM_VARIANTS.forEach(collect);
        for (Collection<ModelResourceLocation> variants : ModelLocations.VARIANTS_BY_BLOCKSTATE.values()) {
            variants.forEach(collect);
        }
        List<CompletableFuture<List<ResourceLocation>>> results = new ArrayList<>();
        for (ResourceLocation location : blockstates) {
            ResourceLocation file = new ResourceLocation(location.getNamespace(), "blockstates/" + location.getPath() + ".json");
            results.add(CompletableFuture.supplyAsync(() -> this.texturesIn(file), POOL));
        }
        // Snapshot: early loading on the main thread adds override files to this map while the crawl runs
        List<ResourceLocation> itemFiles;
        synchronized (ModelLocations.ITEM_VARIANT_FILES) {
            itemFiles = new ArrayList<>(ModelLocations.ITEM_VARIANT_FILES.values());
        }
        for (ResourceLocation location : itemFiles) {
            ResourceLocation file = new ResourceLocation(location.getNamespace(), "models/" + location.getPath() + ".json");
            results.add(CompletableFuture.supplyAsync(() -> this.texturesIn(file), POOL));
        }
        Set<ResourceLocation> textures = new ObjectOpenHashSet<>();
        for (CompletableFuture<List<ResourceLocation>> result : results) {
            textures.addAll(result.join());
        }
        this.visitedJson.clear();
        this.exists.clear();
        return new ArrayList<>(textures);
    }

    private boolean resourceExists(ResourceLocation location) {
        return this.exists.computeIfAbsent(location, key -> {
            try (IResource ignored = this.manager.getResource(key)) {
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    // Every location-shaped string that is a texture, or the textures of the model it names; the scan is textual since parsing every model as json is what made vanilla's load slow
    private List<ResourceLocation> texturesIn(ResourceLocation json) {
        if (!this.visitedJson.add(json)) {
            return ImmutableList.of();
        }
        try (IResource resource = this.manager.getResource(json)) {
            String text = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            Matcher matcher = JSON_LOCATION.matcher(text);
            List<ResourceLocation> found = new ArrayList<>();
            while (matcher.find()) {
                String namespace = matcher.group(1) != null ? matcher.group(1) : "minecraft";
                String path = matcher.group(2);
                if (this.resourceExists(new ResourceLocation(namespace, "textures/" + path + ".png"))) {
                    found.add(new ResourceLocation(namespace, path));
                    continue;
                }
                ResourceLocation model = new ResourceLocation(namespace, "models/block/" + path + ".json");
                if (this.resourceExists(model)) {
                    found.addAll(this.texturesIn(model));
                    continue;
                }
                model = new ResourceLocation(namespace, "models/" + path + ".json");
                if (this.resourceExists(model)) {
                    found.addAll(this.texturesIn(model));
                }
            }
            return found;
        } catch (FileNotFoundException ignored) {
            return ImmutableList.of();
        } catch (Throwable e) {
            Coarctatio.LOGGER.error("Exception reading JSON for {}", json, e);
            return ImmutableList.of();
        }
    }
}
