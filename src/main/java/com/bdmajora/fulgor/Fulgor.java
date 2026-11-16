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

// Entry point of the Fulgor module.
public final class Fulgor {
    // Logger of the lighting module.
    public static final Logger LOGGER = LogManager.getLogger("Fulgor");

    // Number of chunk boundary flags.
    public static final int BOUNDARY_FLAG_COUNT = 32;

    // Flag set when a dynamic lights mod is present.
    private static boolean dynamicLights;

    // Flag set when Fluidlogged API is present.
    private static boolean fluidloggedApi;

    // Flag set when block light info is cached.
    private static boolean cachedBlockLightInfo;

    // Count of scheduled light updates.
    private static final LongAdder SCHEDULED = new LongAdder();
    // Count of updates dropped as repeats.
    private static final LongAdder DEDUPLICATED = new LongAdder();
    // Count of processed positions.
    private static final LongAdder PROCESSED = new LongAdder();

    // Prevents instantiation
    private Fulgor() {
    }

    // Records which compatible mods are present.
    public static void detectCompatibility() {
        dynamicLights = Loader.isModLoaded("dynamiclights");
        fluidloggedApi = Loader.isModLoaded("fluidlogged_api");

        cachedBlockLightInfo = FulgorConfig.get().cacheBlockLightInfo && !dynamicLights && !fluidloggedApi;

        if (isAsync()) {
            MinecraftForge.EVENT_BUS.register(new FulgorEvents());
        }
    }

    // Handles the post init.
    public static void onPostInit() {
        if (isAsync()) {
            FaceOcclusion.registerDefaults();
        }
    }

    // Returns whether async.
    public static boolean isAsync() {
        FulgorConfig config = FulgorConfig.get();
        return config.enabled && config.asyncLightUpdates;
    }

    // Processes the client light updates.
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

    // Returns whether dynamic lights.
    public static boolean hasDynamicLights() {
        return dynamicLights;
    }

    // Returns whether fluidlogged api.
    public static boolean hasFluidloggedApi() {
        return fluidloggedApi;
    }

    // Returns whether block light info is cached.
    public static boolean useCachedBlockLightInfo() {
        return cachedBlockLightInfo;
    }

    // Counts a scheduled update.
    public static void recordScheduled() {
        SCHEDULED.increment();
    }

    // Counts a deduplicated update.
    public static void recordDeduplicated() {
        DEDUPLICATED.increment();
    }

    // Adds to the processed update count.
    public static void recordProcessed(long count) {
        PROCESSED.add(count);
    }

    // Returns the counter lines.
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

    // Logs the overlay line.
    public static String debugOverlayLine() {
        if (isAsync()) {
            return String.format("Fulgor: %s changes, %s chunk passes, %s/%s ms sky/block (/fulgor for detail)",
                    compact(AsyncLightStats.BLOCK_CHANGES.sum()), compact(AsyncLightStats.INITIAL_LIGHTS.sum()),
                    AsyncLightStats.SKY_WORKER_NANOS.sum() / 1_000_000L, AsyncLightStats.BLOCK_WORKER_NANOS.sum() / 1_000_000L);
        }
        return String.format("Fulgor: %s updates, %s deduped (/fulgor for detail)",
                compact(SCHEDULED.sum()), percentOfScheduled(DEDUPLICATED.sum()));
    }

    // Returns a value as a percent of the scheduled count.
    private static String percentOfScheduled(long value) {
        long scheduled = SCHEDULED.sum();

        return scheduled == 0 ? "0%" : String.format("%.0f%%", (value * 100.0D) / scheduled);
    }

    // Returns a count with a thousand or million suffix.
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
