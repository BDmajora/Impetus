package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshRegionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Drives the mesh backend for a section manager.
public class MeshTerrainRenderer {
    // Logger of the backend.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // Query of the free video memory in kilobytes.
    static final int GPU_MEMORY_AVAILABLE_NVX = 0x9049;

    // Size of the upload staging ring.
    static final long UPLOAD_BUFFER_BYTES = 64L << 20;
    // Size of the download staging ring.
    static final long DOWNLOAD_BUFFER_BYTES = 8L << 20;

    // Megabytes left free by an automatic budget.
    static final int AUTOMATIC_RESERVE_MB = 1024;
    // Smallest automatic budget in megabytes.
    static final int AUTOMATIC_MINIMUM_MB = 2048;
    // Largest automatic budget of a dense arena in megabytes.
    static final int DENSE_AUTOMATIC_CAP_MB = 3072;
    // Megabytes below the budget where eviction starts.
    static final int EVICTION_HEADROOM_MB = 100;
    // Time between two reads of the free video memory.
    static final long BUDGET_REFRESH_NANOS = 60_000_000_000L;

    // Chunk radius assumed when nothing is evicted for distance.
    static final int EVERYTHING_RADIUS = 256;
    // Smallest number of region slots.
    static final long MIN_REGIONS = 256L;
    // Largest number of region slots.
    static final long MAX_REGIONS = 65535L;

    // Settings of the live backend or null.
    private static volatile MeshTerrainConfig activeConfig;

    // Settings of the renderer.
    private final MeshTerrainConfig config;
    // Pipeline that draws the terrain.
    private final MeshRenderPipeline pipeline;
    // Whether the arena is sparse.
    private final boolean sparse;
    // Source of the time.
    private final LongSupplier clock;

    // Geometry budget in bytes.
    private long budgetBytes;
    // Time of the last budget read.
    private long lastBudgetRefresh;

    // Whether the opaque draw ran and the frame was not closed.
    private boolean frameOpen;

    // Section of the camera.
    private int cameraSectionX, cameraSectionY, cameraSectionZ;

    // Creates a renderer with the programs of the backend.
    public MeshTerrainRenderer(MeshTerrainConfig config, int verticalRegions) {
        this(config, verticalRegions, null, System::nanoTime);
    }

    // Creates a renderer with the programs of a shader pack or the default programs for null.
    public MeshTerrainRenderer(MeshTerrainConfig config, int verticalRegions, @Nullable MeshTerrainShading shading) {
        this(config, verticalRegions, shading, System::nanoTime);
    }

    // Creates a renderer with a clock and rejects null parts.
    MeshTerrainRenderer(MeshTerrainConfig config, int verticalRegions, @Nullable MeshTerrainShading shading, LongSupplier clock) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.sparse = MeshShaderSupport.supportsSparseGeometry();
        this.budgetBytes = computeBudgetMB(config, this.sparse, 0L) << 20;
        this.lastBudgetRefresh = clock.getAsLong();

        int maxRegions = computeMaxRegions(config, verticalRegions);
        this.pipeline = new MeshRenderPipeline(config, maxRegions, UPLOAD_BUFFER_BYTES, DOWNLOAD_BUFFER_BYTES, this.budgetBytes, shading);

        activeConfig = config;
        LOGGER.info("Mesh-shader terrain on: {} MB {} geometry budget, {} regions{}", this.budgetBytes >> 20,
                this.sparse ? "sparse" : "dense", maxRegions, shading != null ? ", shader pack programs" : "");
    }

    // Returns whether a mesh backend is live.
    public static boolean isActive() {
        return activeConfig != null;
    }

    // Returns the far plane extended to the keep distance.
    public static float extendFarPlane(float vanillaBlocks) {
        MeshTerrainConfig config = activeConfig;

        if (config == null || !config.keepsBeyondRenderDistance()) {
            return vanillaBlocks;
        }

        int chunks = config.keepsEverything() ? EVERYTHING_RADIUS : config.regionKeepDistance();
        return Math.max(vanillaBlocks, chunks * 16.0f);
    }

    // Returns the pipeline.
    public MeshRenderPipeline getPipeline() {
        return this.pipeline;
    }

    // Returns the geometry budget in bytes.
    public long getBudgetBytes() {
        return this.budgetBytes;
    }

    // Uploads a finished build and evicts a region first when the table is full.
    public void upload(int sectionX, int sectionY, int sectionZ, SectionGeometry geometry) {
        this.upload(sectionX, sectionY, sectionZ, geometry, null, null);
    }

    public void upload(int sectionX, int sectionY, int sectionZ, @Nullable SectionGeometry geometry,
                       @Nullable ChunkPrimitiveType primitiveType, @Nullable NativeBuffer indices) {
        MeshRegionStore regions = this.pipeline.getSections().getRegions();

        if (geometry != null && !regions.hasRegionFor(sectionX, sectionY, sectionZ)
                && regions.getRegionCount() >= this.pipeline.getMaxRegions() - 1) {
            evictOne();
        }

        this.pipeline.getSections().upload(sectionX, sectionY, sectionZ, geometry, primitiveType, indices);
    }

    public void uploadTranslucentOrder(int sectionX, int sectionY, int sectionZ, ChunkPrimitiveType primitiveType, NativeBuffer indices) {
        this.pipeline.getSections().uploadTranslucentOrder(sectionX, sectionY, sectionZ, primitiveType, indices);
    }

    // Removes a section unless geometry is kept past the render distance.
    public void remove(int sectionX, int sectionY, int sectionZ) {
        if (!this.config.keepsBeyondRenderDistance()) {
            this.pipeline.getSections().remove(sectionX, sectionY, sectionZ);
        }
    }

    // Draws the opaque frame.
    public void renderOpaque(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog,
                             double cameraX, double cameraY, double cameraZ, int screenWidth, int screenHeight) {
        this.renderOpaque(viewport, matrices, fog, null, cameraX, cameraY, cameraZ, screenWidth, screenHeight);
    }

    // Draws the opaque frame with the state of a pass.
    public void renderOpaque(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog, @Nullable TerrainRenderPass pass,
                             double cameraX, double cameraY, double cameraZ, int screenWidth, int screenHeight) {
        if (this.frameOpen) {
            this.pipeline.endFrame();
        }

        MeshCamera camera = MeshCamera.of(matrices, cameraX, cameraY, cameraZ);
        this.cameraSectionX = camera.sectionX();
        this.cameraSectionY = camera.sectionY();
        this.cameraSectionZ = camera.sectionZ();

        this.pipeline.renderFrame(viewport, matrices, fog, camera, screenWidth, screenHeight, pass);
        this.frameOpen = true;

        enforceBudget();
        refreshBudget();
    }

    // Draws the translucent terrain and ends the frame.
    public void renderTranslucent() {
        this.renderTranslucent(null);
    }

    // Draws the translucent terrain with the state of a pass and ends the frame.
    public void renderTranslucent(@Nullable TerrainRenderPass pass) {
        this.pipeline.renderTranslucent(pass);
        this.pipeline.endFrame();
        this.frameOpen = false;
    }

    // Draws the opaque shadow terrain.
    public void renderShadow(Viewport viewport, ChunkRenderMatrices matrices, @Nullable TerrainRenderPass pass,
                             double cameraX, double cameraY, double cameraZ, int width, int height) {
        this.pipeline.renderShadow(viewport, matrices, MeshCamera.at(cameraX, cameraY, cameraZ), pass, width, height);
    }

    // Draws the translucent shadow terrain.
    public void renderShadowTranslucent(@Nullable TerrainRenderPass pass) {
        this.pipeline.renderShadowTranslucent(pass);
    }

    // Adds the debug lines.
    public void addDebugStrings(List<String> lines) {
        QuadArena arena = this.pipeline.getSections().getArena();
        MeshRegionStore regions = this.pipeline.getSections().getRegions();

        lines.add("Mesh terrain: %d/%d MB %s, %d MB resident".formatted(arena.getUsedBytes() >> 20, this.budgetBytes >> 20,
                this.sparse ? "sparse" : "dense", arena.getResidentBytes() >> 20));
        lines.add("Mesh regions: %d/%d".formatted(regions.getRegionCount(), this.pipeline.getMaxRegions()));
        this.pipeline.addDebugStrings(lines);
    }

    // Frees everything and stops the build workers from packing.
    public void delete() {
        if (activeConfig == this.config) {
            activeConfig = null;
        }
        this.pipeline.delete();
    }

    // Evicts regions while the geometry is above the budget minus the headroom.
    private void enforceBudget() {
        long limit = this.budgetBytes - ((long) EVICTION_HEADROOM_MB << 20);

        while (this.pipeline.getSections().getArena().getUsedBytes() > limit) {
            if (!evictOne()) {
                break;
            }
        }
    }

    // Evicts one region and returns false when none is left.
    private boolean evictOne() {
        MeshRegionStore regions = this.pipeline.getSections().getRegions();
        int maxIndex = regions.getMaxRegionIndex();
        var tracker = this.pipeline.getVisibilityTracker();

        int victim = tracker.leastRecentlySeen(maxIndex, id -> regions.regionExists(id)
                && !regions.isWithinChunks(id, this.config.renderDistance(), this.cameraSectionX, this.cameraSectionY, this.cameraSectionZ));

        if (victim < 0) {
            victim = tracker.leastRecentlySeen(maxIndex, regions::regionExists);
        }

        if (victim < 0) {
            victim = farthestRegion(regions);
        }

        if (victim < 0) {
            return false;
        }

        this.pipeline.evictRegion(victim);
        return true;
    }

    // Returns the region farthest from the camera or minus one.
    private int farthestRegion(MeshRegionStore regions) {
        int best = -1;
        int bestDistance = -1;

        for (int id = 0; id < regions.getMaxRegionIndex(); id++) {
            if (!regions.regionExists(id)) {
                continue;
            }

            int distance = regions.distanceTo(id, this.cameraSectionX, this.cameraSectionY, this.cameraSectionZ);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = id;
            }
        }

        return best;
    }

    // Reads the free video memory again for a sparse arena.
    private void refreshBudget() {
        long now = this.clock.getAsLong();

        if (!this.sparse || !this.config.automaticMemory() || now - this.lastBudgetRefresh < BUDGET_REFRESH_NANOS) {
            return;
        }

        this.lastBudgetRefresh = now;
        this.budgetBytes = computeBudgetMB(this.config, true, this.pipeline.getSections().getArena().getResidentBytes()) << 20;
    }

    // Returns the geometry budget in megabytes.
    static long computeBudgetMB(MeshTerrainConfig config, boolean sparse, long residentBytes) {
        if (!config.automaticMemory() || !LWJGL.isExtensionSupported(GLExtension.NVX_gpu_memory_info)) {
            return config.maxGeometryMemoryMB();
        }

        long availableMB = (LWJGL.glGetInteger(GPU_MEMORY_AVAILABLE_NVX) / 1024L) + (residentBytes >> 20);
        long budget = Math.max(AUTOMATIC_MINIMUM_MB, availableMB - AUTOMATIC_RESERVE_MB);

        return sparse ? budget : Math.min(budget, DENSE_AUTOMATIC_CAP_MB);
    }

    // Returns the number of region slots for the keep distance.
    static int computeMaxRegions(MeshTerrainConfig config, int verticalRegions) {
        long distance = config.keepsEverything() ? EVERYTHING_RADIUS
                : Math.max(config.renderDistance(), config.regionKeepDistance());
        long perAxis = (2 * distance + 1 + 7) / 8 + 1;
        long regions = (long) perAxis * perAxis * Math.max(1, verticalRegions) * 5 / 4;

        return (int) Math.clamp(regions, MIN_REGIONS, MAX_REGIONS);
    }
}
