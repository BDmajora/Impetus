package com.bdmajora.fulgor;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.async.AsyncLightStats;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.WorldLightManager;
import com.bdmajora.fulgor.async.engine.FaceOcclusion;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

// Entry points for the lighting subsystem, a Phosphor backport with Alfheim's corrections; not an FML entry point, mixins do the work and each World owns a LightingEngine
public final class Fulgor {
    public static final Logger LOGGER = LogManager.getLogger("Fulgor");

    // 2 light types * 4 directions * 2 halves * (inwards + outwards): width of a chunk's neighbour-light-check table, and its required length when read from NBT
    public static final int BOUNDARY_FLAG_COUNT = 32;

    // Reports luminance for blocks with none of their own (dropped torch, held lantern); when present the engine must ask it instead of the block state
    private static boolean dynamicLights;

    // A fluidlogged block has two states at one position and real opacity/luminance is the max of both, so no block can use the cached fast path while it is installed
    private static boolean fluidloggedApi;

    // Whether the per-block light-info cache is usable; see useCachedBlockLightInfo()
    private static boolean cachedBlockLightInfo;

    // LongAdder rather than plain fields: single-player runs two engines (client + integrated server) on two threads, and a lost update would make dedup look better than it is
    private static final LongAdder SCHEDULED = new LongAdder();
    private static final LongAdder DEDUPLICATED = new LongAdder();
    private static final LongAdder PROCESSED = new LongAdder();

    private Fulgor() {
    }

    // Called from FMLConstructionEvent, the earliest point Loader can answer and still before the first World and its LightingEngine exist
    public static void detectCompatibility() {
        dynamicLights = Loader.isModLoaded("dynamiclights");
        fluidloggedApi = Loader.isModLoaded("fluidlogged_api");

        // The config switch also gates the Block mixin, so without it the fast path's cast would fail rather than merely mislead
        cachedBlockLightInfo = FulgorConfig.get().cacheBlockLightInfo && !dynamicLights && !fluidloggedApi;

        if (isAsync()) {
            MinecraftForge.EVENT_BUS.register(new FulgorEvents());
        }
    }

    // Post-init, once every mod has finished shaping its blocks: the per-face transparency scan the async engine reads through LightInfo
    public static void onPostInit() {
        if (isAsync()) {
            FaceOcclusion.registerDefaults();
        }
    }

    // Whether the Starlight-style async engine is the one in use rather than the Phosphor-style deferred one
    public static boolean isAsync() {
        FulgorConfig config = FulgorConfig.get();
        return config.enabled && config.asyncLightUpdates;
    }

    // One call per client tick that serves both engines: the deferred engine flushes its queues, the async one drains its lanes on this thread
    public static void processClientLightUpdates(World world) {
        if (world instanceof LightingEngineProvider) {
            ((LightingEngineProvider) world).fulgor$getLightingEngine().processLightUpdates();
        } else if (world instanceof AsyncLitWorld) {
            WorldLightManager manager = ((AsyncLitWorld) world).fulgor$getLightManager();
            if (manager != null) {
                manager.processClientUpdates();
            }
        }
    }

    // Whether AtomicStryker's Dynamic Lights was detected at startup
    public static boolean hasDynamicLights() {
        return dynamicLights;
    }

    // Whether Fluidlogged API was detected at startup
    public static boolean hasFluidloggedApi() {
        return fluidloggedApi;
    }

    // Resolved once in detectCompatibility() since this is read for six neighbours of every position per batch; Dynamic Lights and Fluidlogged API answer per position rather than per state, so the cache is bypassed rather than second-guessed
    public static boolean useCachedBlockLightInfo() {
        return cachedBlockLightInfo;
    }

    // Counts a position handed to the engine, for /fulgor
    public static void recordScheduled() {
        SCHEDULED.increment();
    }

    // Counts a scheduled position the queue collapsed as a repeat
    public static void recordDeduplicated() {
        DEDUPLICATED.increment();
    }

    // Recorded once per pass rather than per position; sits on the engine's innermost loop
    public static void recordProcessed(long count) {
        PROCESSED.add(count);
    }

    // Human-readable engine statistics, one entry per line; shared by the log and /fulgor
    public static List<String> statistics() {
        List<String> lines = new ArrayList<>();
        if (isAsync()) {
            lines.add("Fulgor lighting statistics (async engine)");
            lines.add("  Block changes queued:  " + AsyncLightStats.BLOCK_CHANGES.sum());
            lines.add("  Chunk batches queued:  " + AsyncLightStats.CHUNKS_QUEUED.sum());
            lines.add("  Initial chunk passes:  " + AsyncLightStats.INITIAL_LIGHTS.sum());
            lines.add("  Sky tasks:             " + AsyncLightStats.SKY_TASKS.sum() + " (" + AsyncLightStats.SKY_WORKER_NANOS.sum() / 1_000_000L + " ms)");
            lines.add("  Block tasks:           " + AsyncLightStats.BLOCK_TASKS.sum() + " (" + AsyncLightStats.BLOCK_WORKER_NANOS.sum() / 1_000_000L + " ms)");
            lines.add("  Positions evaluated:   " + AsyncLightStats.POSITIONS_PROCESSED.sum());
            lines.add("  Queue overflows:       " + AsyncLightStats.QUEUE_OVERFLOWS.sum());
            return lines;
        }

        long scheduled = SCHEDULED.sum();
        lines.add("Fulgor lighting statistics");
        lines.add("  Updates scheduled:   " + scheduled);
        lines.add("  Collapsed as dupes:  " + DEDUPLICATED.sum() + " (" + percentOfScheduled(DEDUPLICATED.sum()) + ")");
        lines.add("  Positions evaluated: " + PROCESSED.sum());
        return lines;
    }

    // Single line added to F3; the deduplication rate is the share of lighting work that never happened, since vanilla would have evaluated all of it
    public static String debugOverlayLine() {
        if (isAsync()) {
            return String.format("Fulgor: %s changes, %s chunk passes, %s/%s ms sky/block (/fulgor for detail)",
                    compact(AsyncLightStats.BLOCK_CHANGES.sum()), compact(AsyncLightStats.INITIAL_LIGHTS.sum()),
                    AsyncLightStats.SKY_WORKER_NANOS.sum() / 1_000_000L, AsyncLightStats.BLOCK_WORKER_NANOS.sum() / 1_000_000L);
        }
        return String.format("Fulgor: %s updates, %s deduped (/fulgor for detail)",
                compact(SCHEDULED.sum()), percentOfScheduled(DEDUPLICATED.sum()));
    }

    // Ratio against the scheduled count, guarding the divide-by-zero on a fresh session
    private static String percentOfScheduled(long value) {
        long scheduled = SCHEDULED.sum();

        return scheduled == 0 ? "0%" : String.format("%.0f%%", (value * 100.0D) / scheduled);
    }

    // Human-readable magnitude suffix so the report fits in chat
    private static String compact(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return String.format("%.1fk", value / 1_000.0D);
        }
        return String.format("%.1fM", value / 1_000_000.0D);
    }
}
