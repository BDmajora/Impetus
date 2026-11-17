package com.bdmajora.fulgor.mixin;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.core.SimpleMixinPlugin;

// Class fulgor mixin plugin.
public class FulgorMixinPlugin extends SimpleMixinPlugin {
    // Package of the Fulgor mixins.
    private static final String PACKAGE = "com.bdmajora.fulgor.mixin.";

    // Class whose presence marks Cubic Chunks.
    private static final String CUBIC_CHUNKS_MARKER =
            "io.github.opencubicchunks.cubicchunks.core.asm.CubicChunksCoreContainer";

    // Loaded config.
    private FulgorConfig config;

    // Flag set when the module is on.
    private boolean enabled;

    // Reads the Fulgor config and refuses to load beside Cubic Chunks.
    @Override
    public void onLoad(String mixinPackage) {
        this.config = FulgorConfig.get();
        this.enabled = this.config.enabled;

        if (!this.enabled) {
            Fulgor.LOGGER.warn("Fulgor is disabled in configuration; vanilla lighting will be used");
            return;
        }

        if (isClassPresent(CUBIC_CHUNKS_MARKER)) {
            Fulgor.LOGGER.warn("Cubic Chunks was detected. It uses its own lighting engine and is "
                    + "fundamentally incompatible with Fulgor, which will not load.");
            this.enabled = false;
            return;
        }

    }

    // Maps the mixin's simple name to the switch of its engine or fix and applies unknown names with a warning.
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!this.enabled) {
            return false;
        }

        String name = mixinClassName.startsWith(PACKAGE)
                ? mixinClassName.substring(PACKAGE.length())
                : mixinClassName;

        boolean async = this.config.asyncLightUpdates;

        switch (name) {
            case "world.WorldMixin":
            case "world.ChunkMixin":
            case "world.ChunkSkylightMixin":
            case "world.ChunkProviderServerMixin":
            case "network.SPacketChunkDataMixin":
                return !async && this.config.deferredLightUpdates;
            case "world.AnvilChunkLoaderMixin":
                return !async && (this.config.deferredLightUpdates || this.config.fixChunkBoundaryLighting);
            case "client.MinecraftMixin":
                return async || this.config.deferredLightUpdates;
            case "async.world.WorldMixin":
            case "async.world.ChunkMixin":
            case "async.world.ChunkLightingMixin":
            case "async.world.ChunkSectionMixin":
            case "async.world.PlayerChunkMapEntryMixin":
            case "async.world.WorldEntitySpawnerMixin":
            case "async.world.AnvilChunkLoaderMixin":
            case "async.block.BlockStateMixin":
            case "async.world.BlockStateContainerPaletteAccessor":
                return async;
            case "world.ExtendedBlockStorageMixin":
                return this.config.sendNonTrivialSectionLight;
            case "block.BlockMixin":
                return this.config.cacheBlockLightInfo;
            case "client.RenderGlobalMixin":
                return this.config.optimizeRenderLightUpdates;
            case "client.WorldNeighborLightMixin":
            case "client.ChunkCacheNeighborLightMixin":
            case "client.BlockLightmapMixin":
                return this.config.fixRenderLighting;
            default:
                Fulgor.LOGGER.warn("No config switch is wired up for {}, applying it", mixinClassName);
                return true;
        }
    }

    // Returns whether a class can be found without loading it.
    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, FulgorMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
