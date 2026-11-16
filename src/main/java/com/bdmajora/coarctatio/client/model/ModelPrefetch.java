package com.bdmajora.coarctatio.client.model;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.model.dynamic.PackPathLister;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.LegacyV2AdapterAccessor;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

// Reads model files on worker threads before the bake needs them.
public final class ModelPrefetch {
    // Prefix of asset paths.
    private static final String ASSETS = "assets/";
    // Folder segment that holds models.
    private static final String MODELS_DIR = "/models/";
    // Suffix of model files.
    private static final String JSON = ".json";
    // Prefetched models by location.
    private static final Map<ResourceLocation, ModelBlock> MODEL_BLOCKS = new ConcurrentHashMap<>();
    // Prefetched armatures by location.
    private static final Map<ResourceLocation, ModelBlockAnimation> ANIMATIONS = new ConcurrentHashMap<>();

    // Prevents instantiation
    private ModelPrefetch() {
    }

    // Reader of one model.
    @FunctionalInterface
    public interface ModelReader {
        // Returns the model of the location.
        ModelBlock read(ResourceLocation location) throws IOException;
    }

    // Reads every model of the manager in parallel and stores the results.
    public static void run(IResourceManager manager, ModelReader reader) {
        clear();
        if (!(manager instanceof SimpleReloadableResourceManager)) {
            return;
        }
        long start = System.nanoTime();
        Set<ResourceLocation> locations = new HashSet<>();
        for (IResourcePack pack : PackPathLister.packsOf((SimpleReloadableResourceManager) manager)) {
            if (!listable(pack)) {
                continue;
            }
            for (String path : PackPathLister.paths(pack)) {
                ResourceLocation location = modelLocation(path);
                if (location != null) {
                    locations.add(location);
                }
            }
        }
        LongAdder readNanos = new LongAdder();
        LongAdder armatureNanos = new LongAdder();
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        ForkJoinPool pool = readerPool();
        try {
            pool.submit(() -> locations.parallelStream().forEach(location -> {
                workers.add(Thread.currentThread());
                try {
                    long readStart = System.nanoTime();
                    ModelBlock model = reader.read(location);
                    long armatureStart = System.nanoTime();
                    ResourceLocation armature = armatureOf(location);
                    ANIMATIONS.put(armature, ModelBlockAnimation.loadVanillaAnimation(manager, armature));
                    readNanos.add(armatureStart - readStart);
                    armatureNanos.add(System.nanoTime() - armatureStart);
                    MODEL_BLOCKS.put(location, model);
                } catch (Throwable t) {
                }
            })).join();
        } finally {
            pool.shutdown();
        }
        Coarctatio.LOGGER.debug("Prefetched {} of {} models in {} ms on {} threads ({} ms reading, {} ms probing armatures, summed over threads)",
                MODEL_BLOCKS.size(), locations.size(), (System.nanoTime() - start) / 1_000_000L, workers.size(),
                readNanos.sum() / 1_000_000L, armatureNanos.sum() / 1_000_000L);
    }

    // Returns a pool of named worker threads.
    private static ForkJoinPool readerPool() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        AtomicInteger counter = new AtomicInteger();
        return new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()), forkJoinPool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(forkJoinPool);
            thread.setName("Coarctatio Model Reader #" + counter.incrementAndGet());
            thread.setContextClassLoader(loader);
            return thread;
        }, null, false);
    }

    // Returns whether the pack is a file or folder pack.
    private static boolean listable(IResourcePack pack) {
        IResourcePack inner = pack instanceof LegacyV2Adapter ? ((LegacyV2AdapterAccessor) pack).coarctatio$pack() : pack;
        return inner instanceof FileResourcePack || inner instanceof FolderResourcePack;
    }

    // Returns the model location of an asset path or null when the path is not a model.
    @Nullable
    static ResourceLocation modelLocation(String path) {
        if (!path.startsWith(ASSETS) || !path.endsWith(JSON)) {
            return null;
        }
        int slash = path.indexOf('/', ASSETS.length());
        if (slash <= ASSETS.length() || !path.startsWith(MODELS_DIR, slash)) {
            return null;
        }
        int end = path.length() - JSON.length();
        int modelStart = slash + MODELS_DIR.length();
        if (end <= modelStart) {
            return null;
        }
        return new ResourceLocation(path.substring(ASSETS.length(), slash), "models/" + path.substring(modelStart, end));
    }

    // Returns the armature location of a model.
    static ResourceLocation armatureOf(ResourceLocation model) {
        return new ResourceLocation(model.getNamespace(), "armatures/" + model.getPath().substring("models/".length()) + JSON);
    }

    // Removes and returns the prefetched model of the location.
    @Nullable
    public static ModelBlock takeModel(ResourceLocation location) {
        return MODEL_BLOCKS.isEmpty() ? null : MODEL_BLOCKS.remove(location);
    }

    // Removes and returns the prefetched armature of the location.
    @Nullable
    public static ModelBlockAnimation takeAnimation(ResourceLocation armature) {
        return ANIMATIONS.isEmpty() ? null : ANIMATIONS.remove(armature);
    }

    // Discards every prefetched result.
    public static void clear() {
        MODEL_BLOCKS.clear();
        ANIMATIONS.clear();
    }
}
