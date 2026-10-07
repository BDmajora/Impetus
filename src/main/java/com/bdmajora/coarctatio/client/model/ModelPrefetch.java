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

// Every model file the packs hold is read and parsed across all cores before the loader walks blocks and items, which otherwise reads them one at a time on the client thread; a server pack with thousands of item override models spent most of its join there
public final class ModelPrefetch {
    private static final String ASSETS = "assets/";
    private static final String MODELS_DIR = "/models/";
    private static final String JSON = ".json";
    private static final Map<ResourceLocation, ModelBlock> MODEL_BLOCKS = new ConcurrentHashMap<>();
    // Keyed by armature location, the form VanillaLoader asks for it in
    private static final Map<ResourceLocation, ModelBlockAnimation> ANIMATIONS = new ConcurrentHashMap<>();

    private ModelPrefetch() {
    }

    // The bakery's own loadModel, so every other hook on it still runs for a prefetched model
    @FunctionalInterface
    public interface ModelReader {
        ModelBlock read(ResourceLocation location) throws IOException;
    }

    public static void run(IResourceManager manager, ModelReader reader) {
        clear();
        if (!(manager instanceof SimpleReloadableResourceManager)) {
            return;
        }
        long start = System.nanoTime();
        // Listed on this thread so every pack's index is built before the workers probe it
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
        // Summed across workers, so the log says whether reading or the armature probes dominate and how many threads actually ran
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
                    // Left to the loader, which reports a missing or broken model against whatever asked for it
                }
            })).join();
        } finally {
            pool.shutdown();
        }
        Coarctatio.LOGGER.debug("Prefetched {} of {} models in {} ms on {} threads ({} ms reading, {} ms probing armatures, summed over threads)",
                MODEL_BLOCKS.size(), locations.size(), (System.nanoTime() - start) / 1_000_000L, workers.size(),
                readNanos.sum() / 1_000_000L, armatureNanos.sum() / 1_000_000L);
    }

    // A pool of its own, so whatever else holds the common pool's workers cannot leave the read on one thread; the workers carry the client thread's context loader for anything that looks classes up through it
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

    // Zip and folder packs (resource packs and mod jars) list from the existence index; the built-in pack would mean walking the whole classpath, and its small models load quickly the usual way
    private static boolean listable(IResourcePack pack) {
        IResourcePack inner = pack instanceof LegacyV2Adapter ? ((LegacyV2AdapterAccessor) pack).coarctatio$pack() : pack;
        return inner instanceof FileResourcePack || inner instanceof FolderResourcePack;
    }

    // "assets/ns/models/item/x.json" to ns:models/item/x, the location VanillaLoader hands the bakery; null for anything else
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

    // VanillaLoader's armature beside every model: ns:models/x to ns:armatures/x.json
    static ResourceLocation armatureOf(ResourceLocation model) {
        return new ResourceLocation(model.getNamespace(), "armatures/" + model.getPath().substring("models/".length()) + JSON);
    }

    // Handed out once, since the loader goes on to set the model's parent link; null takes the usual path
    @Nullable
    public static ModelBlock takeModel(ResourceLocation location) {
        return MODEL_BLOCKS.isEmpty() ? null : MODEL_BLOCKS.remove(location);
    }

    @Nullable
    public static ModelBlockAnimation takeAnimation(ResourceLocation armature) {
        return ANIMATIONS.isEmpty() ? null : ANIMATIONS.remove(armature);
    }

    public static void clear() {
        MODEL_BLOCKS.clear();
        ANIMATIONS.clear();
    }
}
