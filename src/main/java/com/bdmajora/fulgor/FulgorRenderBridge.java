package com.bdmajora.fulgor;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.api.SectionLightInfo;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Bridge between light updates and chunk rerenders.
public final class FulgorRenderBridge {
    // Prevents instantiation
    private FulgorRenderBridge() {
    }

    // Runs the deferred engine's pending light updates of a world.
    public static void flushPendingLightUpdates(World world) {
        if (world instanceof LightingEngineProvider) {
            ((LightingEngineProvider) world).fulgor$getLightingEngine().processLightUpdates();
        }
    }

    // Returns whether render lighting fixes are on.
    public static boolean fixRenderLighting() {
        FulgorConfig config = FulgorConfig.get();
        return config.enabled && config.fixRenderLighting;
    }

    // Drops a section's cached light emptiness after its light arrays were written in place
    public static void markLightChanged(ExtendedBlockStorage section) {
        if (section instanceof SectionLightInfo) {
            ((SectionLightInfo) section).fulgor$markLightChanged();
        }
    }

    // Returns whether a section holds no blocks.
    public static boolean isEmptyOfBlocks(ExtendedBlockStorage section) {
        if (section instanceof SectionLightInfo) {
            return ((SectionLightInfo) section).fulgor$hasNoBlocks();
        }

        return section.isEmpty();
    }
}
