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
import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

// Extension of a game's regular world renderer.
public abstract class SimpleWorldRenderer<WORLD, SECTIONMANAGER extends RenderSectionManager, LAYER, BLOCKENTITY, BLOCKENTITY_RENDER_CONTEXT> {
    protected WORLD world;
    protected int renderDistance;

    public record CameraState(double x, double y, double z, double pitch, double yaw, float fogDistance) {}

    protected CameraState lastCameraState;

    protected Viewport currentViewport;

    @Getter
    protected SECTIONMANAGER renderSectionManager;

    public void setWorld(WORLD world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // Unloads the renderer of the current world.
        if (this.world != null) {
            this.unloadWorld();
        }

        // Loads the renderer for the new world.
        if (world != null) {
            this.loadWorld(world);
        }
    }

    protected void loadWorld(WORLD world) {
        this.world = world;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    protected void unloadWorld() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }
        // Deletes the moving light buffer.
        TerrainDynamicLights.delete();

        this.world = null;
    }

    // Returns the number of visible sections in the current camera's frustum.
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    // Marks the section graph as changed.
    public void scheduleTerrainUpdate() {
        // Skips the call before the section manager exists.
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    // Returns whether no section is waiting for a rebuild.
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    public abstract int getEffectiveRenderDistance();

    // Returns whether the section manager has to be rebuilt this frame.
    protected boolean needsRebuild() {
        return false;
    }

    // Returns whether terrain is being set up or drawn for a shadow pass.
    public boolean isInShadowPass() {
        return this.renderSectionManager.isInShadowPass();
    }

    // Updates the terrain state before any section is drawn.
    public void setupTerrain(Viewport viewport,
                             CameraState cameraState,
                             int frame,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        // Joins the searches and applies chunk events when no shadow pass ran this frame.
        if (!this.renderSectionManager.didShadowPassRunThisFrame()) {
            this.renderSectionManager.finishAllGraphUpdates();
            this.processChunkEvents();
        }

        this.renderSectionManager.runAsyncTasks();

        if (getEffectiveRenderDistance() != this.renderDistance || this.needsRebuild()) {
            this.reload();
        }

        this.prepareFrame(viewport, cameraState, updateChunksImmediately);

        // Tracks the player viewport for the mesh backend.
        this.renderSectionManager.trackViewport(viewport);

        this.renderSectionManager.uploadChunks();

        if (this.renderSectionManager.needsUpdate()) {
            this.renderSectionManager.update(viewport, frame, spectator);
        } else {
            this.renderSectionManager.updateTranslucency(viewport);
        }

        if (updateChunksImmediately) {
            this.renderSectionManager.uploadChunks();
        }

        this.renderSectionManager.tickVisibleRenders();
    }

    // Joins last frame's searches and applies chunk events and runs a due terrain search for the shadow pass.
    public void setupShadowTerrain(Viewport playerViewport,
                                   Viewport shadowViewport,
                                   CameraState cameraState,
                                   int frame,
                                   boolean spectator) {
        NativeBuffer.reclaim(false);

        this.renderSectionManager.finishAllGraphUpdates();

        this.processChunkEvents();

        this.prepareFrame(shadowViewport, cameraState, false);

        this.renderSectionManager.updateForShadowPass(playerViewport, shadowViewport, frame, spectator);

        this.renderSectionManager.tickVisibleRenders();
    }

    // Detects camera changes and drains async tasks and schedules chunk builds.
    private void prepareFrame(Viewport viewport, CameraState cameraState, boolean updateChunksImmediately) {
        boolean dirty = this.lastCameraState == null || !this.lastCameraState.equals(cameraState);

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
            this.lastCameraState = cameraState;
        }

        this.currentViewport = viewport;

        this.renderSectionManager.runAsyncTasks();

        this.renderSectionManager.updateChunks(updateChunksImmediately);
    }

    private void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    protected abstract ChunkRenderMatrices createChunkRenderMatrices();

    public Viewport getLastViewport() {
        return this.currentViewport;
    }

    // Draws every visible section of one layer.
    public void drawChunkLayer(LAYER renderLayer, double x, double y, double z) {
        ChunkRenderMatrices matrices = createChunkRenderMatrices();
        // Uploads the moving lights relative to the draw origin.
        TerrainDynamicLights.prepare(x, y, z);

        Collection<TerrainRenderPass> passes = this.renderSectionManager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);

        if (passes != null && !passes.isEmpty()) {
            var occlusionCamera = this.getLastViewport().getTransform();
            var realCamera = new CameraTransform(x, y, z);
            for (var pass : passes) {
                this.renderSectionManager.renderLayer(matrices, pass, occlusionCamera, realCamera);
            }
        }
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    protected abstract SECTIONMANAGER createRenderSectionManager(CommandList commandList);

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

    private int renderedBlockEntities;

    // Draws the culled block entities of visible sections and every global one and returns the count.
    public int renderBlockEntities(BLOCKENTITY_RENDER_CONTEXT renderContext) {
        this.renderedBlockEntities = 0;
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(this.renderSectionManager.getRenderLists(),
                data -> this.drawBlockEntityList(data.culledBlockEntities, renderContext));
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(this.renderSectionManager.getSectionsWithGlobalEntities(),
                data -> this.drawBlockEntityList(data.globalBlockEntities, renderContext));
        return this.renderedBlockEntities;
    }

    // Visits the culled block entities of visible sections and every global one.
    @SuppressWarnings("unchecked")
    public void forEachVisibleBlockEntity(Consumer<? super BLOCKENTITY> visitor) {
        MinecraftBuiltRenderSectionData.forEachVisibleSectionData(this.renderSectionManager.getRenderLists(),
                data -> ((List<BLOCKENTITY>) data.culledBlockEntities).forEach(visitor));
        MinecraftBuiltRenderSectionData.forEachGlobalSectionData(this.renderSectionManager.getSectionsWithGlobalEntities(),
                data -> ((List<BLOCKENTITY>) data.globalBlockEntities).forEach(visitor));
    }

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

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Draws boxes outside the world height.
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

    public String getChunksDebugString() {
        // Formats the visible and total section counts and the render distance.
        return String.format("C: %d/%d D: %d %s", this.renderSectionManager.getVisibleChunkCount(),
                this.renderSectionManager.getTotalSections(), this.renderDistance,
                this.renderSectionManager.getTickerDebugString());
    }

    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderSectionManager.getRenderPassConfiguration();
    }

    // Schedules rebuilds for every section in a block region.
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    // Schedules rebuilds for every section in a section region.
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    // Schedules a rebuild of one section.
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

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

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }

    // Implemented by the game's world renderer to expose its SimpleWorldRenderer.
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
