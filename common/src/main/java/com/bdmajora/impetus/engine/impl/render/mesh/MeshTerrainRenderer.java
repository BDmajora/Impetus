package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshRegionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.SectionGeometry;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.LongSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// What a section manager drives when the mesh backend is on (Nvidium's world renderer): routes build results into the GPU stores, draws through the pipeline, and keeps the geometry inside its VRAM budget
public class MeshTerrainRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, in KB
    static final int GPU_MEMORY_AVAILABLE_NVX = 0x9049;

    static final long UPLOAD_BUFFER_BYTES = 64L << 20;
    static final long DOWNLOAD_BUFFER_BYTES = 8L << 20;

    // Automatic budgets leave a gigabyte for everything else and never start below two, like Nvidium
    static final int AUTOMATIC_RESERVE_MB = 1024;
    static final int AUTOMATIC_MINIMUM_MB = 2048;
    // A dense arena is allocated whole up front, so an automatic budget is capped there; a far keep distance needs the manual setting
    static final int DENSE_AUTOMATIC_CAP_MB = 3072;
    // Eviction starts this far below the budget, so a burst of uploads does not hit the arena's hard limit
    static final int EVICTION_HEADROOM_MB = 100;
    // How often a sparse arena re-reads free VRAM, which other programs change under it
    static final long BUDGET_REFRESH_NANOS = 60_000_000_000L;

    // Chunks of radius assumed when nothing is ever evicted for distance
    static final int EVERYTHING_RADIUS = 256;
    static final long MIN_REGIONS = 256L;
    // Region ids travel as uint16 in the visible list
    static final long MAX_REGIONS = 65535L;

    // The live backend's settings, null when none; read on build workers to decide whether to pack, and by the far plane hook
    private static volatile MeshTerrainConfig activeConfig;

    private final MeshTerrainConfig config;
    private final MeshRenderPipeline pipeline;
    private final boolean sparse;
    private final LongSupplier clock;

    private long budgetBytes;
    private long lastBudgetRefresh;

    // Set by the opaque draw and cleared once translucency closed the frame; a frame whose translucent pass never came is closed before the next one starts
    private boolean frameOpen;

    private int cameraSectionX, cameraSectionY, cameraSectionZ;

    public MeshTerrainRenderer(MeshTerrainConfig config, int verticalRegions) {
        this(config, verticalRegions, System::nanoTime);
    }

    MeshTerrainRenderer(MeshTerrainConfig config, int verticalRegions, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
        this.sparse = MeshShaderSupport.supportsSparseGeometry();
        this.budgetBytes = computeBudgetMB(config, this.sparse, 0L) << 20;
        this.lastBudgetRefresh = clock.getAsLong();

        int maxRegions = computeMaxRegions(config, verticalRegions);
        this.pipeline = new MeshRenderPipeline(config, maxRegions, UPLOAD_BUFFER_BYTES, DOWNLOAD_BUFFER_BYTES, this.budgetBytes);

        activeConfig = config;
        LOGGER.info("Mesh-shader terrain on: {} MB {} geometry budget, {} regions", this.budgetBytes >> 20,
                this.sparse ? "sparse" : "dense", maxRegions);
    }

    // Whether a mesh backend is live, so build workers pack their output for it
    public static boolean isActive() {
        return activeConfig != null;
    }

    // Vanilla's far plane, pushed out to the keep distance when geometry is kept past the render distance, so the projection and fog reach it
    public static float extendFarPlane(float vanillaBlocks) {
        MeshTerrainConfig config = activeConfig;

        if (config == null || !config.keepsBeyondRenderDistance()) {
            return vanillaBlocks;
        }

        int chunks = config.keepsEverything() ? EVERYTHING_RADIUS : config.regionKeepDistance();
        return Math.max(vanillaBlocks, chunks * 16.0f);
    }

    // The pipeline, for tests and the debug screen
    public MeshRenderPipeline getPipeline() {
        return this.pipeline;
    }

    // The geometry budget in bytes
    public long getBudgetBytes() {
        return this.budgetBytes;
    }

    // Puts a finished build on the GPU, or drops the section when it built to nothing
    public void upload(int sectionX, int sectionY, int sectionZ, SectionGeometry geometry) {
        MeshRegionStore regions = this.pipeline.getSections().getRegions();

        // A full region table makes room before a new region needs an id
        if (geometry != null && !regions.hasRegionFor(sectionX, sectionY, sectionZ)
                && regions.getRegionCount() >= this.pipeline.getMaxRegions() - 1) {
            evictOne();
        }

        this.pipeline.getSections().upload(sectionX, sectionY, sectionZ, geometry);
    }

    // The CPU dropped a section; it stays on the GPU when the keep distance outreaches the render distance
    public void remove(int sectionX, int sectionY, int sectionZ) {
        if (!this.config.keepsBeyondRenderDistance()) {
            this.pipeline.getSections().remove(sectionX, sectionY, sectionZ);
        }
    }

    // The whole opaque frame; later opaque passes of the same frame have nothing left to draw
    public void renderOpaque(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog,
                             double cameraX, double cameraY, double cameraZ, int screenWidth, int screenHeight) {
        if (this.frameOpen) {
            this.pipeline.endFrame();
        }

        this.cameraSectionX = (int) Math.floor(cameraX) >> 4;
        this.cameraSectionY = (int) Math.floor(cameraY) >> 4;
        this.cameraSectionZ = (int) Math.floor(cameraZ) >> 4;

        this.pipeline.renderFrame(viewport, matrices, fog, cameraX, cameraY, cameraZ, screenWidth, screenHeight);
        this.frameOpen = true;

        enforceBudget();
        refreshBudget();
    }

    // Translucent terrain, then the frame's readbacks and uploads are retired
    public void renderTranslucent() {
        this.pipeline.renderTranslucent();
        this.pipeline.endFrame();
        this.frameOpen = false;
    }

    // F3 lines
    public void addDebugStrings(List<String> lines) {
        QuadArena arena = this.pipeline.getSections().getArena();
        MeshRegionStore regions = this.pipeline.getSections().getRegions();

        lines.add("Mesh terrain: %d/%d MB %s, %d MB resident".formatted(arena.getUsedBytes() >> 20, this.budgetBytes >> 20,
                this.sparse ? "sparse" : "dense", arena.getResidentBytes() >> 20));
        lines.add("Mesh regions: %d/%d".formatted(regions.getRegionCount(), this.pipeline.getMaxRegions()));
        this.pipeline.addDebugStrings(lines);
    }

    // Frees everything and lets build workers stop packing
    public void delete() {
        activeConfig = null;
        this.pipeline.delete();
    }

    // Evicts the least recently seen regions while the geometry sits within the headroom of its budget
    private void enforceBudget() {
        long limit = this.budgetBytes - ((long) EVICTION_HEADROOM_MB << 20);

        while (this.pipeline.getSections().getArena().getUsedBytes() > limit) {
            if (!evictOne()) {
                break;
            }
        }
    }

    // Drops one region: the least recently seen beyond the render distance, else the least recently seen anywhere, else the farthest; false when none is left
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

    // The live region farthest from the camera, or -1 when there are none
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

    // A sparse arena only pays for what it commits, so its budget follows free VRAM as other programs come and go
    private void refreshBudget() {
        long now = this.clock.getAsLong();

        if (!this.sparse || !this.config.automaticMemory() || now - this.lastBudgetRefresh < BUDGET_REFRESH_NANOS) {
            return;
        }

        this.lastBudgetRefresh = now;
        this.budgetBytes = computeBudgetMB(this.config, true, this.pipeline.getSections().getArena().getResidentBytes()) << 20;
    }

    // Free VRAM plus what this arena already holds, minus a reserve; the manual setting when automatic memory is off or the driver cannot say
    static long computeBudgetMB(MeshTerrainConfig config, boolean sparse, long residentBytes) {
        if (!config.automaticMemory() || !LWJGL.isExtensionSupported(GLExtension.NVX_gpu_memory_info)) {
            return config.maxGeometryMemoryMB();
        }

        long availableMB = (LWJGL.glGetInteger(GPU_MEMORY_AVAILABLE_NVX) / 1024L) + (residentBytes >> 20);
        long budget = Math.max(AUTOMATIC_MINIMUM_MB, availableMB - AUTOMATIC_RESERVE_MB);

        return sparse ? budget : Math.min(budget, DENSE_AUTOMATIC_CAP_MB);
    }

    // Enough region slots for the farthest distance geometry is kept at, with a quarter spare; region ids travel as uint16
    static int computeMaxRegions(MeshTerrainConfig config, int verticalRegions) {
        int distance = config.keepsEverything() ? EVERYTHING_RADIUS
                : Math.max(config.renderDistance(), config.regionKeepDistance());
        int perAxis = (2 * distance + 1 + 7) / 8 + 1;
        long regions = (long) perAxis * perAxis * Math.max(1, verticalRegions) * 5 / 4;

        return (int) Math.clamp(regions, MIN_REGIONS, MAX_REGIONS);
    }
}
