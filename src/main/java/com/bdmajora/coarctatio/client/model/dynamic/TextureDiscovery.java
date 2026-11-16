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

// Finds the textures that models use before the atlas is built.
public final class TextureDiscovery {
    // Pattern of texture paths that are collected.
    private static final Pattern TEXTURE_PATH = Pattern.compile("^/?assets/(.+?(?=/))/textures/((?:attachment|aspect.?|bettergrass|block.?|cape|customoverlay|decors|item.?|entity/(armor|bed|chest)|fluid.?|model.?|part.?|pipe|rendering|ropebridge|slot.?|solid_block|tile.?|tinkers|tconstruct|valuetype.?)/.*)\\.png$");
    // Pattern of quoted locations inside model files.
    private static final Pattern JSON_LOCATION = Pattern.compile("\"(?:([A-Za-z0-9_\\-.]+):|)([A-Za-z0-9_\\-./]+)\"");
    // Textures that mods need and that no file mentions.
    private static final ImmutableListMultimap<String, ResourceLocation> EXTRA_BY_MOD = ImmutableListMultimap.<String, ResourceLocation>builder()
            .put("mekanism", new ResourceLocation("mekanism", "entities/robit"))
            .put("gbook", new ResourceLocation("gbook", "cover"))
            .put("gbook", new ResourceLocation("gbook", "cover_gray"))
            .put("gbook", new ResourceLocation("gbook", "paper"))
            .put("gbook", new ResourceLocation("gbook", "transparent"))
            .put("industrialwires", new ResourceLocation("minecraft", "font/ascii"))
            .build();
    // Folders of the game directory that hold extra resources.
    private static final String[] GAME_FOLDERS = {"resources", "oresources"};

    // Worker pool of the scan.
    private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1), pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("Coarctatio texture scan " + thread.getPoolIndex());
        thread.setDaemon(true);
        return thread;
    }, null, false);

    // Scan that is running or null.
    private static volatile ForkJoinTask<Set<ResourceLocation>> pending;
    // Scan of the current reload or null.
    private static volatile TextureDiscovery current;
    // Packs to scan.
    private final List<IResourcePack> packs;
    // Manager that reads the model files.
    private final IResourceManager manager;
    // Model files already read.
    private final Set<ResourceLocation> visitedJson = Collections.synchronizedSet(new ObjectOpenHashSet<>());
    // Known results of resource existence checks.
    private final Map<ResourceLocation, Boolean> exists = new ConcurrentHashMap<>();
    // Future completed when the location tables are filled.
    private final CompletableFuture<Void> tablesReady = new CompletableFuture<>();

    // Creates a scan.
    private TextureDiscovery(IResourceManager manager, List<IResourcePack> packs) {
        this.manager = manager;
        this.packs = packs;
    }

    // Starts a scan in the background.
    public static void start(IResourceManager manager, List<IResourcePack> packs) {
        TextureDiscovery discovery = new TextureDiscovery(manager, packs);
        current = discovery;
        pending = POOL.submit(discovery::discover);
    }

    // Signals that the location tables are filled.
    public static void tablesReady() {
        TextureDiscovery discovery = current;
        if (discovery != null) {
            discovery.tablesReady.complete(null);
        }
    }

    // Cancels the scan and passes the cause to its waiting tasks.
    public static void abandon(Throwable cause) {
        TextureDiscovery discovery = current;
        current = null;
        pending = null;
        if (discovery != null) {
            discovery.tablesReady.completeExceptionally(cause);
        }
    }

    // Waits for the scan and returns the textures found.
    public static Set<ResourceLocation> take() {
        ForkJoinTask<Set<ResourceLocation>> task = pending;
        if (task == null) {
            return Collections.emptySet();
        }
        pending = null;
        current = null;
        return task.join();
    }

    // Returns the textures found in packs and in model files.
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

    // Adds the texture of a path and returns one when the path matched.
    private static int match(String path, Set<ResourceLocation> into) {
        Matcher matcher = TEXTURE_PATH.matcher(path);
        if (!matcher.matches()) {
            return 0;
        }
        into.add(new ResourceLocation(matcher.group(1), matcher.group(2)));
        return 1;
    }

    // Returns the textures named by block state and item model files.
    private List<ResourceLocation> crawlJson() {
        this.tablesReady.join();
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

    // Returns whether the resource exists.
    private boolean resourceExists(ResourceLocation location) {
        return this.exists.computeIfAbsent(location, key -> {
            try (IResource ignored = this.manager.getResource(key)) {
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    // Returns the textures named by a model file and its parents.
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
