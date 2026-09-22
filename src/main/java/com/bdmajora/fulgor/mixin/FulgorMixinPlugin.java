package com.bdmajora.fulgor.mixin;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.booter.mixin.SimpleMixinPlugin;

// Gates each Fulgor mixin on its config switch and refuses to load beside world implementations it cannot reason about (Cubic Chunks); off means never loaded
public class FulgorMixinPlugin extends SimpleMixinPlugin {
    private static final String PACKAGE = "com.bdmajora.fulgor.mixin.";

    // Cubic Chunks replaces the column with a cube grid and its own lighting engine, breaking every Fulgor assumption (16 sections, 8-bit y, per-column heightmap) as silent corruption, so this is a hard refusal
    private static final String CUBIC_CHUNKS_MARKER =
            "io.github.opencubicchunks.cubicchunks.core.asm.CubicChunksCoreContainer";

    private FulgorConfig config;

    private boolean enabled;

    // Reads the config once; a disabled Fulgor logs and falls back to vanilla lighting
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

    // Maps the mixin's simple name to its config switch; unknown names are refused
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
                // The deferred engine itself; without all of these some paths would defer and others propagate immediately, worse than either
                return !async && this.config.deferredLightUpdates;
            case "world.AnvilChunkLoaderMixin":
                // Also flushes before saving, so it is needed whenever the deferred engine is.
                return !async && (this.config.deferredLightUpdates || this.config.fixChunkBoundaryLighting);
            case "client.MinecraftMixin":
                // The per-tick drain serves both engines
                return async || this.config.deferredLightUpdates;
            case "async.world.WorldMixin":
            case "async.world.ChunkMixin":
            case "async.world.ChunkLightingMixin":
            case "async.world.ChunkSectionMixin":
            case "async.world.PlayerChunkMapEntryMixin":
            case "async.world.WorldEntitySpawnerMixin":
            case "async.world.AnvilChunkLoaderMixin":
            case "async.block.BlockStateMixin":
                // The async engine, all or nothing for the same reason
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

    // Deliberately does not initialize the class: this runs during coremod setup, where eagerly loading a foreign class can change mod load order
    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, FulgorMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
