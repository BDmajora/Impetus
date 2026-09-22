package com.bdmajora.impetus.engine.impl.render.terrain;

import lombok.Getter;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSectionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTrackerHolder;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// Version-independent half of the world renderer; each game version subclasses it with its own world, layer and block-entity types so the per-frame ordering is written once
public abstract class SimpleWorldRenderer<WORLD, SECTIONMANAGER extends RenderSectionManager, LAYER, BLOCKENTITY, BLOCKENTITY_RENDER_CONTEXT> {
    // Null until a world is loaded; used as the "is a world loaded" flag as well as the world itself
    protected WORLD world;
    // The distance the section manager was last built for, so a settings change can be noticed and force a reload
    protected int renderDistance;

    // Everything about the camera that invalidates the visibility graph when changed, as a record so the dirty check is one equals(); fogDistance is in since fog clips the traversal
    public record CameraState(double x, double y, double z, double pitch, double yaw, float fogDistance) {}

    // The camera state the graph was last built for; null before the first frame
    protected CameraState lastCameraState;

    // The viewport of the pass currently being set up, read back by drawChunkLayer for its occlusion camera
    protected Viewport currentViewport;

    // The real camera the last layer drew with, reused while the position holds
    private CameraTransform realCamera;

    @Getter
    protected SECTIONMANAGER renderSectionManager;

    // Swaps to a different world, tearing down and rebuilding the section manager; called on join, on leave with null, and on dimension change
    public void setWorld(WORLD world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    // Brings up the section manager for a new world; the command list wraps initRenderer because GPU buffer allocation needs one, and try-with-resources frees it even if init throws
    protected void loadWorld(WORLD world) {
        this.world = world;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    // Tears down the manager then forgets the world, in that order so nothing observes a live manager pointing at a dead world
    protected void unloadWorld() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.world = null;
    }

    // Number of chunk sections the last graph update found visible in the camera frustum; the F3 "C:" figure
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    // Marks the visibility graph stale for the next setupTerrain; for changes other than camera movement, e.g. a block update opening a sightline
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    // True once nothing is queued for rebuild, i.e. the world is fully meshed; the loading screen waits on this
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    public abstract int getEffectiveRenderDistance();

    // Per-pass entry point before any chunk drawing: reclaims retired native buffers, finishes any in-flight graph update (everything below assumes no mid-traversal), then updates the manager; `frame` is deprecated
    public void setupTerrain(Viewport viewport,
                             CameraState cameraState,
                             @Deprecated(forRemoval = true) int frame,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        if (this.renderSectionManager != null) {
            this.renderSectionManager.finishAllGraphUpdates();
        }

        if (this.renderSectionManager.isInShadowPass()) {
            // Umbra parity: the shadow pass is culling-only and runs first, so lastCameraState (it would win the dirty check), updateChunks (it would spend the shared build budget on the shadow list) and tickVisibleRenders (double-ticked sprites) are all skipped; only currentViewport is assigned for drawChunkLayer's occlusion camera
            this.currentViewport = viewport;
            this.renderSectionManager.update(viewport, frame, spectator);
            return;
        }

        this.processChunkEvents();

        this.renderSectionManager.runAsyncTasks();

        if (getEffectiveRenderDistance() != this.renderDistance) {
            this.reload();
        }

        boolean dirty = this.lastCameraState == null || !this.lastCameraState.equals(cameraState);

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
            this.lastCameraState = cameraState;
        }

        this.currentViewport = viewport;

        this.renderSectionManager.runAsyncTasks();

        this.renderSectionManager.updateChunks(updateChunksImmediately);

        this.renderSectionManager.uploadChunks();

        if (this.renderSectionManager.needsUpdate()) {
            this.renderSectionManager.update(viewport, frame, spectator);
        }

        if (updateChunksImmediately) {
            this.renderSectionManager.uploadChunks();
        }

        this.renderSectionManager.tickVisibleRenders();
    }

    // Drains the chunk tracker's add/remove events into the section manager once per frame on the render thread, so the section table reshapes once
    private void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    protected abstract ChunkRenderMatrices createChunkRenderMatrices();

    // The viewport the most recent setupTerrain ran with; drawChunkLayer needs it for the occlusion camera
    public Viewport getLastViewport() {
        return this.currentViewport;
    }

    // Draws every visible section for one vanilla layer (a layer may map to several passes); the occlusion camera is the one the graph was built against and the real camera is the caller's current position, and mixing them pops geometry or breaks sorting
    public void drawChunkLayer(LAYER renderLayer, double x, double y, double z) {
        ChunkRenderMatrices matrices = createChunkRenderMatrices();

        Collection<TerrainRenderPass> passes = this.renderSectionManager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);

        if (passes != null && !passes.isEmpty()) {
            var occlusionCamera = this.getLastViewport().getTransform();
            var realCamera = this.realCamera;

            // Every layer of a frame draws from the same position, so the split transform is built once per move
            if (realCamera == null || realCamera.x != x || realCamera.y != y || realCamera.z != z) {
                this.realCamera = realCamera = new CameraTransform(x, y, z);
            }
            for (var pass : passes) {
                this.renderSectionManager.renderLayer(matrices, pass, occlusionCamera, realCamera);
            }
        }
    }

    // Tears down and recreates the section manager, e.g. after a render distance change
    public void reload() {
        if (this.world == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    protected abstract SECTIONMANAGER createRenderSectionManager(CommandList commandList);

    // Creates the section manager for the current world
    protected void initRenderer(CommandList commandList) {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.renderDistance = getEffectiveRenderDistance();

        this.renderSectionManager = this.createRenderSectionManager(commandList);

        var tracker = ChunkTrackerHolder.get(this.world);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
    }

    protected abstract void renderBlockEntityList(List<BLOCKENTITY> list, BLOCKENTITY_RENDER_CONTEXT context);

    // Block entities drawn by the current renderBlockEntities call
    private int renderedBlockEntities;

    // Both passes; returns the count for the debug screen
    public int renderBlockEntities(BLOCKENTITY_RENDER_CONTEXT renderContext) {
        this.renderedBlockEntities = 0;
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(this.renderSectionManager.getRenderLists(),
                data -> this.drawBlockEntityList(data.culledBlockEntities, renderContext));
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(this.renderSectionManager.getSectionsWithGlobalEntities(),
                data -> this.drawBlockEntityList(data.globalBlockEntities, renderContext));
        return this.renderedBlockEntities;
    }

    // Skips empty lists and keeps the per-frame tally the caller reports
    @SuppressWarnings("unchecked")
    private void drawBlockEntityList(List<?> list, BLOCKENTITY_RENDER_CONTEXT renderContext) {
        if (!list.isEmpty()) {
            this.renderedBlockEntities += list.size();
            this.renderBlockEntityList((List<BLOCKENTITY>) list, renderContext);
        }
    }

    // the volume of a section multiplied by the number of sections to be checked at most
    public static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    public abstract int getMinimumBuildHeight();
    public abstract int getMaximumBuildHeight();

    // Whether the section containing a point was drawn
    public boolean isPointVisible(double x, double y, double z) {
        if (y < getMinimumBuildHeight() + 0.5D || y > getMaximumBuildHeight() - 0.5D) {
            return true;
        }

        return this.renderSectionManager.isSectionVisible(
                PositionUtil.posToSectionCoord(x),
                PositionUtil.posToSectionCoord(y),
                PositionUtil.posToSectionCoord(z)
        );
    }

    // Whether any section a box overlaps was drawn
    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid world height never map to a rendered chunk, so always render them or they are culled incorrectly
        if (y2 < getMinimumBuildHeight() + 0.5D || y1 > getMaximumBuildHeight() - 0.5D) {
            return true;
        }

        double entityVolume = (x2 - x1) * (y2 - y1) * (z2 - z1);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            return true;
        }

        int minX = PositionUtil.posToSectionCoord(x1 - 0.5D);
        int minY = PositionUtil.posToSectionCoord(y1 - 0.5D);
        int minZ = PositionUtil.posToSectionCoord(z1 - 0.5D);

        int maxX = PositionUtil.posToSectionCoord(x2 + 0.5D);
        int maxY = PositionUtil.posToSectionCoord(y2 + 0.5D);
        int maxZ = PositionUtil.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.renderSectionManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // The C: line for the debug screen
    public String getChunksDebugString() {
        // C: visible/total D: distance
        return String.format("C: %d/%d D: %d %s", this.renderSectionManager.getVisibleChunkCount(),
                this.renderSectionManager.getTotalSections(), this.renderDistance,
                this.renderSectionManager.getTickerDebugString());
    }

    // The pass set in use
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderSectionManager.getRenderPassConfiguration();
    }

    // Rebuilds every section overlapping a block-coordinate region; >> 4 is an arithmetic shift so negative coordinates floor correctly, unlike division
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    // Rebuilds every section in an inclusive section-coordinate box (hence <=), since callers pass the min and max section actually touched
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    // Queues one section for remeshing; `important` uses the blocking queue drained before the frame draws, which a just-placed block needs
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    // Lines for the F3 overlay; the viewport block is skipped before the first setupTerrain, when it is still null
    public Collection<String> getDebugStrings() {
        var debugStrings = new ArrayList<String>();
        if (this.currentViewport != null) {
            var transform = this.currentViewport.getTransform();
            debugStrings.add("Viewport: %.02f %.02f %.02f".formatted(transform.x, transform.y, transform.z));
        }
        if (this.renderSectionManager != null) {
            debugStrings.addAll(this.renderSectionManager.getDebugStrings());
        }
        return debugStrings;
    }

    // Whether a section has been built at least once
    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }

    // Implemented by mixin on the game's own WorldRenderer so anything holding one can reach ours; the impetus$ prefix avoids name collisions
    public interface Provider<T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> {
        T impetus$getWorldRenderer();

        @SuppressWarnings("unchecked")
        static <T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> @Nullable T getWorldRendererNullable(Object o) {
            return ((Provider<T>)o).impetus$getWorldRenderer();
        }

        static <T extends SimpleWorldRenderer<?, ?, ?, ?, ?>> @NotNull T getWorldRenderer(Object o) {
            T result = getWorldRendererNullable(o);
            if (result == null) {
                throw new IllegalStateException("No renderer attached to active world");
            }
            return result;
        }
    }
}
