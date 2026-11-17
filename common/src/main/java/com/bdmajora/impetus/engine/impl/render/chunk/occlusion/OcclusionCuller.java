package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import grondag.bitraster.AbstractRasterizer;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3ic;

import static com.bdmajora.impetus.engine.impl.common.util.MathUtil.nearestToZero;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.DIR_MASK;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.XYZ_STEP;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.XYZ_SHIFT;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.XYZ_LOCAL_MASK;

// Searches the visible sections of a lattice from the camera.
public class OcclusionCuller {
    // Lattice of the sections.
    private final SectionLattice lattice;
    // Lowest and highest section of the world.
    private final int minSectionY, maxSectionY;

    // Incoming face set of each step direction.
    static final int[] INCOMING = new int[GraphDirection.COUNT];
    // Face pairs that cross the section along x.
    private static final long X_OPPOSITE_FACE_PAIRS = (1L << VisibilityEncoding.bit(GraphDirection.WEST, GraphDirection.EAST))
            | (1L << VisibilityEncoding.bit(GraphDirection.EAST, GraphDirection.WEST));
    // Face pairs that cross the section along y.
    private static final long Y_OPPOSITE_FACE_PAIRS = (1L << VisibilityEncoding.bit(GraphDirection.DOWN, GraphDirection.UP))
            | (1L << VisibilityEncoding.bit(GraphDirection.UP, GraphDirection.DOWN));
    // Face pairs that cross the section along z.
    private static final long Z_OPPOSITE_FACE_PAIRS = (1L << VisibilityEncoding.bit(GraphDirection.NORTH, GraphDirection.SOUTH))
            | (1L << VisibilityEncoding.bit(GraphDirection.SOUTH, GraphDirection.NORTH));

    // Visibility masks without the face pairs of the axes that are not dominant.
    static final long[] ANGLE_REFINEMENT_MASKS = new long[8];

    // Fixed point shift of the angle comparisons.
    private static final int ANGLE_SHIFT = 8;

    // Bit of each direction.
    private static final int DOWN_BIT = 1 << GraphDirection.DOWN;
    private static final int UP_BIT = 1 << GraphDirection.UP;
    private static final int NORTH_BIT = 1 << GraphDirection.NORTH;
    private static final int SOUTH_BIT = 1 << GraphDirection.SOUTH;
    private static final int WEST_BIT = 1 << GraphDirection.WEST;
    private static final int EAST_BIT = 1 << GraphDirection.EAST;

    // Fills the incoming and refinement tables.
    static {
        for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
            INCOMING[dir] = GraphDirectionSet.of(GraphDirection.opposite(dir));
        }

        for (int sel = 0; sel < ANGLE_REFINEMENT_MASKS.length; sel++) {
            long mask = VisibilityEncoding.EVERYTHING;

            if ((sel & 1) != 0) mask &= ~X_OPPOSITE_FACE_PAIRS;
            if ((sel & 2) != 0) mask &= ~Y_OPPOSITE_FACE_PAIRS;
            if ((sel & 4) != 0) mask &= ~Z_OPPOSITE_FACE_PAIRS;

            ANGLE_REFINEMENT_MASKS[sel] = mask & PackedSectionMetadata.VISIBILITY_MASK;
        }
    }

    // Queue of the cells to expand.
    private long[] queue = new long[256];
    // Number of queued cells.
    private int tail;
    // Aperture of each cell.
    private long[] apertures = new long[0];

    // Visit state array of the current search.
    private long[] visitState;

    // Visible cells of the search or null.
    private long[] visibleCells;
    // Number of visible cells.
    private int visibleCount;

    // Region classes of the search.
    private final RegionCullCache regionCullCache = new RegionCullCache();

    // Rasterizer or null.
    private final @Nullable RasterOccluder rasterOccluder;
    // Budget of the rasterizer.
    private final RasterBudget rasterBudget = new RasterBudget();
    // Whether the rasterizer is used in the search.
    private boolean rasterActive;

    // Whether the camera is in an unloaded section.
    private boolean isCameraInUnloadedSection;
    // Whether the search has several roots.
    private boolean isMultiRootSearch;

    // Lattice index of the camera section or minus one.
    private int cameraSectionIndex;

    // Creates a culler for a lattice.
    public OcclusionCuller(SectionLattice lattice, int minSectionY, int maxSectionY, boolean rasterOcclusion) {
        this.lattice = lattice;
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
        this.rasterOccluder = rasterOcclusion ? new RasterOccluder() : null;
    }

    // Searches the lattice and reports every reached section to the visitor.
    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            long[] visitState,
                            float searchDistance,
                            int numRegions,
                            boolean useOcclusionCulling,
                            boolean allowFrustumClamping,
                            boolean recordVisible,
                            int frame)
    {
        int installed = this.lattice.installedCount;
        if (this.queue.length < installed) {
            this.queue = new long[Math.max(installed, this.queue.length * 2)];
        }
        this.tail = 0;

        this.visitState = visitState;

        if (visitState != null && this.apertures.length < visitState.length) {
            this.apertures = new long[visitState.length];
        }

        if (recordVisible) {
            this.visibleCells = this.lattice.ensureVisibleCellsCapacity(installed);
        } else {
            this.visibleCells = null;
        }
        this.visibleCount = 0;

        this.regionCullCache.begin(viewport, searchDistance, numRegions);

        this.isCameraInUnloadedSection = false;
        this.isMultiRootSearch = false;
        this.cameraSectionIndex = -1;
        this.init(visitor, viewport, searchDistance, useOcclusionCulling, frame);
        if (this.isCameraInUnloadedSection) {
            useOcclusionCulling = false;
        }
        if (this.isMultiRootSearch) {
            allowFrustumClamping = false;
        }

        this.rasterActive = this.rasterOccluder != null && useOcclusionCulling && viewport.getVpMatrix() != null;

        if (this.rasterActive) {
            this.rasterBudget.beginFrame(this.maxSquaredChunkDistance(searchDistance));
            this.rasterOccluder.prepareScene(frame, viewport, this.rasterTestDistance(searchDistance));
            this.occludeCameraSection(viewport);
        }

        this.process(visitor, viewport, searchDistance, useOcclusionCulling, allowFrustumClamping, frame);

        if (this.rasterActive) {
            this.rasterBudget.endFrame(this.visibleCount);
        }

        if (recordVisible) {
            this.lattice.visibleCount = this.visibleCount;
        }
        this.visitState = null;
    }

    // Expands the queued cells and reports them.
    private void process(Visitor visitor,
                         Viewport viewport,
                         float searchDistance,
                         boolean useOcclusionCulling,
                         boolean allowFrustumClamping,
                         int frame)
    {
        final long[] visitState = this.visitState;
        final long[] sectionMeta = this.lattice.sectionMeta;
        final int[] regionOfCell = this.lattice.regionOfCell;
        final int[] delta = this.lattice.delta;
        final long[] queue = this.queue;
        final long[] apertures = this.apertures;
        final long[] visibleCells = this.visibleCells;
        final int[] occluderBounds = this.lattice.occluderBounds;
        final int[][] occluderData = this.lattice.occluderData;
        int visibleCount = this.visibleCount;
        final int baseX = this.lattice.baseX, baseY = this.lattice.baseY, baseZ = this.lattice.baseZ;

        final RegionCullCache cache = this.regionCullCache;
        final CameraTransform transform = viewport.getTransform();
        final Vector3ic camera = viewport.getChunkCoord();
        final int camX = camera.x(), camY = camera.y(), camZ = camera.z();
        final long frameStamp = SectionLattice.frameStamp(frame);

        final int camAngleX = angleOrigin(transform.x);
        final int camAngleY = angleOrigin(transform.y);
        final int camAngleZ = angleOrigin(transform.z);

        int head = 0;
        int tail = this.tail;

        final int strideX = delta[GraphDirection.EAST];
        final int strideZ = delta[GraphDirection.SOUTH];
        final int xStep = 1 << (2 * XYZ_SHIFT);
        final int yStep = 1 << XYZ_SHIFT;

        while (head < tail) {
            long entry = queue[head++];
            int idx = (int) entry;
            int xyz = (int) (entry >>> 32);

            int chunkX = baseX + ((xyz >>> (2 * XYZ_SHIFT)) & XYZ_LOCAL_MASK);
            int chunkY = baseY + ((xyz >>> XYZ_SHIFT) & XYZ_LOCAL_MASK);
            int chunkZ = baseZ + (xyz & XYZ_LOCAL_MASK);

            int regionId = regionOfCell[idx];
            long sm = sectionMeta[idx];
            int compactMeta = PackedSectionMetadata.toCompactMeta(sm);
            int regionX = regionOrigin(chunkX, RenderRegion.REGION_WIDTH_SH, RenderRegion.REGION_BLOCK_WIDTH);
            int regionY = regionOrigin(chunkY, RenderRegion.REGION_HEIGHT_SH, RenderRegion.REGION_BLOCK_HEIGHT);
            int regionZ = regionOrigin(chunkZ, RenderRegion.REGION_LENGTH_SH, RenderRegion.REGION_BLOCK_LENGTH);
            int classification = cache.classify(regionId, regionX, regionY, regionZ);

            boolean visible;

            if (classification == RegionCullCache.FULLY_INSIDE) {
                visible = true;
            } else if (classification == RegionCullCache.OUTSIDE) {
                visible = false;
            } else {
                visible = isVisibleInPartialRegion(classification, viewport, transform, chunkX, chunkY, chunkZ, searchDistance);
            }

            long aperture = FastFrustumClamping.FULL;

            if (allowFrustumClamping && visible) {
                int relX = Math.abs(chunkX - camX);
                int relY = Math.abs(chunkY - camY);
                int relZ = Math.abs(chunkZ - camZ);
                int shift = FastFrustumClamping.normalizationShift(Math.max(relX, Math.max(relY, relZ)));

                aperture = FastFrustumClamping.clip(apertures[idx],
                        relX >>> shift, relY >>> shift, relZ >>> shift);
                visible = aperture != FastFrustumClamping.EMPTY;
            }

            boolean traverse = visible;

            if (this.rasterActive && visible) {
                RasterOccluder.SectionVisibility result = this.rasterTest(occluderBounds, occluderData,
                        idx, chunkX, chunkY, chunkZ, camX, camY, camZ,
                        compactMeta, PackedSectionMetadata.hasOccluderData(sm));
                visible = result == RasterOccluder.SectionVisibility.VISIBLE;
                traverse = result != RasterOccluder.SectionVisibility.HIDDEN;
            }

            int sectionIndex = LocalSectionIndex.pack(chunkX, chunkY, chunkZ);
            visitor.visit(idx, regionId, sectionIndex, chunkX, chunkY, chunkZ, compactMeta, visible);

            if (!traverse) {
                continue;
            }

            if (visible) {
                if (visibleCells != null) {
                    visibleCells[visibleCount] = entry;
                }

                visibleCount++;
            }

            int connections;

            if (useOcclusionCulling) {
                int incoming = (int) (visitState[idx] & DIR_MASK);
                connections = VisibilityEncoding.getConnections(
                        sm & angleRefinementMask(camAngleX, camAngleY, camAngleZ, chunkX, chunkY, chunkZ), incoming);
            } else {
                connections = GraphDirectionSet.ALL;
            }

            connections &= getOutwardDirections(chunkX, chunkY, chunkZ, camX, camY, camZ);

            if ((connections & DOWN_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx - 1, xyz - yStep, UP_BIT, frameStamp, tail);
            }
            if ((connections & UP_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx + 1, xyz + yStep, DOWN_BIT, frameStamp, tail);
            }
            if ((connections & NORTH_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx - strideZ, xyz - 1, SOUTH_BIT, frameStamp, tail);
            }
            if ((connections & SOUTH_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx + strideZ, xyz + 1, NORTH_BIT, frameStamp, tail);
            }
            if ((connections & WEST_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx - strideX, xyz - xStep, EAST_BIT, frameStamp, tail);
            }
            if ((connections & EAST_BIT) != 0) {
                tail = visit(visitState, apertures, queue, aperture, idx + strideX, xyz + xStep, WEST_BIT, frameStamp, tail);
            }
        }

        this.tail = tail;
        this.visibleCount = visibleCount;
    }

    // Returns the largest squared chunk distance the search can reach.
    private int maxSquaredChunkDistance(float searchDistance) {
        int radius = (int) Math.ceil(searchDistance / 16.0f) + 2;
        return 2 * radius * radius;
    }

    // Returns the farthest distance at which a section is tested.
    private float rasterTestDistance(float searchDistance) {
        int testable = this.rasterBudget.testableLimit();

        if (testable == RasterBudget.UNBOUNDED) {
            return searchDistance;
        }

        return Math.min(searchDistance, ((float) Math.sqrt(testable) + 1.0f) * 16.0f);
    }

    // Draws the camera section into the occlusion buffer.
    private void occludeCameraSection(Viewport viewport) {
        int idx = this.cameraSectionIndex;

        if (idx < 0) {
            return;
        }

        long sm = this.lattice.sectionMeta[idx];

        if (!PackedSectionMetadata.hasOccluderData(sm)
                || (PackedSectionMetadata.getVisualsFlags(sm) & (1 << RenderVisualsService.HAS_BLOCK_GEOMETRY)) == 0) {
            return;
        }

        var origin = viewport.getChunkCoord();
        this.rasterOccluder.occludeSectionAt(origin.x() << 4, origin.y() << 4, origin.z() << 4,
                this.lattice.occluderData[idx]);
    }

    // Tests a section with the rasterizer.
    private RasterOccluder.SectionVisibility rasterTest(int[] occluderBounds, int[][] occluderData,
                                                        int idx, int chunkX, int chunkY, int chunkZ, int camX, int camY, int camZ,
                                                        int meta, boolean hasOccluderData) {
        var occluder = this.rasterOccluder;

        if (!hasOccluderData) {
            if (AbstractRasterizer.STATS) RasterOccluder.STAT_EMPTY_SKIP++;
            return RasterOccluder.SectionVisibility.VISIBLE;
        }

        int dx = chunkX - camX;
        int dy = chunkY - camY;
        int dz = chunkZ - camZ;
        int squaredChunkDist = (dx * dx) + (dy * dy) + (dz * dz);

        boolean near = squaredChunkDist <= RasterOccluder.NEAR_SQUARED_CHUNK_DIST;

        if (!near && !this.rasterBudget.shouldTest(squaredChunkDist)) {
            if (AbstractRasterizer.STATS) RasterOccluder.STAT_BUDGET_SKIP++;
            return RasterOccluder.SectionVisibility.VISIBLE;
        }

        boolean hasGeometry =
                (PackedSectionMetadata.getCompactVisualsFlags(meta) & (1 << RenderVisualsService.HAS_BLOCK_GEOMETRY)) != 0;

        RasterOccluder.SectionVisibility result = occluder.testSection(chunkX << 4, chunkY << 4, chunkZ << 4,
                squaredChunkDist, occluderBounds[idx]);

        if (result == RasterOccluder.SectionVisibility.VISIBLE) {
            if (hasGeometry) {
                occluder.occludeSection(occluderData[idx]);
            }
        } else {
            this.rasterBudget.recordCulled();
        }

        return result;
    }

    // Returns the size of the occlusion buffer as text or null.
    public String rasterBufferSize() {
        return this.rasterOccluder == null ? null
                : this.rasterOccluder.bufferWidth() + "x" + this.rasterOccluder.bufferHeight();
    }

    // Returns the number of sections tested out of order.
    public int rasterBacktrackCount() {
        return this.rasterOccluder == null ? 0 : this.rasterOccluder.backtrackCount();
    }

    // Returns the raster budget.
    public RasterBudget rasterBudget() {
        return this.rasterBudget;
    }

    // Visits the neighbors in the selected directions.
    private void visitNeighbors(long[] visitState, long[] queue, int[] delta, int idx, int xyz, int outgoing, long frameStamp) {
        while (outgoing != 0) {
            int dir = Integer.numberOfTrailingZeros(outgoing);
            outgoing &= outgoing - 1;

            this.tail = visit(visitState, this.apertures, queue, FastFrustumClamping.FULL,
                    idx + delta[dir], xyz + XYZ_STEP[dir], INCOMING[dir], frameStamp, this.tail);
        }
    }

    // Queues a cell the first time it is reached and records further faces.
    private static int visit(long[] visitState, long[] apertures, long[] queue, long aperture, int idx, int xyz, int incoming, long frameStamp, int tail) {
        long state = visitState[idx];

        if (state < frameStamp) {
            state = frameStamp;
            queue[tail++] = ((long) xyz << 32) | (idx & 0xFFFFFFFFL);
        } else {
            aperture = FastFrustumClamping.hull(apertures[idx], aperture);
        }

        visitState[idx] = state | incoming;
        apertures[idx] = aperture;

        return tail;
    }

    // Returns whether a section of a partial region passes the tests left open.
    static boolean isVisibleInPartialRegion(int classification, Viewport viewport, CameraTransform transform,
                                            int chunkX, int chunkY, int chunkZ, float searchDistance) {
        return (classification == RegionCullCache.PARTIAL_DISTANCE_IN
                        || isWithinRenderDistance(transform, chunkX, chunkY, chunkZ, searchDistance))
                && (classification == RegionCullCache.PARTIAL_FRUSTUM_IN
                        || isWithinFrustum(viewport, chunkX, chunkY, chunkZ));
    }

    // Returns the directions that lead away from the camera.
    private static int getOutwardDirections(int chunkX, int chunkY, int chunkZ, int camX, int camY, int camZ) {
        int planes = 0;

        planes |= chunkX <= camX ? 1 << GraphDirection.WEST  : 0;
        planes |= chunkX >= camX ? 1 << GraphDirection.EAST  : 0;

        planes |= chunkY <= camY ? 1 << GraphDirection.DOWN  : 0;
        planes |= chunkY >= camY ? 1 << GraphDirection.UP    : 0;

        planes |= chunkZ <= camZ ? 1 << GraphDirection.NORTH : 0;
        planes |= chunkZ >= camZ ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    // Returns a camera coordinate in the fixed point section space.
    private static int angleOrigin(double blockCoordinate) {
        return (int) Math.round(blockCoordinate * (1 << ANGLE_SHIFT) / 16.0) - (1 << (ANGLE_SHIFT - 1));
    }

    // Returns the visibility mask without the face pairs of the non dominant axes.
    private static long angleRefinementMask(int camAngleX, int camAngleY, int camAngleZ, int chunkX, int chunkY, int chunkZ) {
        int dx = Math.abs((chunkX << ANGLE_SHIFT) - camAngleX);
        int dy = Math.abs((chunkY << ANGLE_SHIFT) - camAngleY);
        int dz = Math.abs((chunkZ << ANGLE_SHIFT) - camAngleZ);

        int sel = ((dx - Math.max(dy, dz)) >>> 31)
                | (((dy - Math.max(dx, dz)) >>> 31) << 1)
                | (((dz - Math.max(dx, dy)) >>> 31) << 2);

        return ANGLE_REFINEMENT_MASKS[sel];
    }

    // Returns the block origin of the region of a section coordinate.
    static int regionOrigin(int chunkCoord, int shift, int blockSize) {
        return (chunkCoord >> shift) * blockSize;
    }

    // Returns whether a section is within the render distance.
    static boolean isWithinRenderDistance(CameraTransform camera, int chunkX, int chunkY, int chunkZ, float maxDistance) {
        int ox = (chunkX << 4) - camera.intX;
        int oy = (chunkY << 4) - camera.intY;
        int oz = (chunkZ << 4) - camera.intZ;

        float dx = nearestToZero(ox, ox + 16) - camera.fracX;
        float dy = nearestToZero(oy, oy + 16) - camera.fracY;
        float dz = nearestToZero(oz, oz + 16) - camera.fracZ;

        return ((((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance));
    }

    // Half size of a section in blocks plus model overhang and epsilon.
    static final float CHUNK_SECTION_SIZE = 8.0f + 1.0f + 0.125f;

    // Returns whether the box of a section intersects the frustum.
    public static boolean isWithinFrustum(Viewport viewport, int chunkX, int chunkY, int chunkZ) {
        return viewport.isBoxVisible((chunkX << 4) + 8, (chunkY << 4) + 8, (chunkZ << 4) + 8, CHUNK_SECTION_SIZE);
    }

    // Chooses the seed of the search.
    private void init(Visitor visitor,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.y() < this.minSectionY) {
            this.initOutsideWorldHeight(viewport, searchDistance, frame,
                    this.minSectionY, GraphDirectionSet.of(GraphDirection.DOWN));
        } else if (origin.y() >= this.maxSectionY) {
            this.initOutsideWorldHeight(viewport, searchDistance, frame,
                    this.maxSectionY - 1, GraphDirectionSet.of(GraphDirection.UP));
        } else if (this.lattice.getRenderSection(origin.x(), origin.y(), origin.z()) == null) {
            this.initOutsideWorldHeight(viewport, searchDistance, frame,
                    origin.y(), GraphDirectionSet.of(GraphDirection.UP) | GraphDirectionSet.of(GraphDirection.DOWN));
            this.isCameraInUnloadedSection = true;
        } else {
            this.initWithinWorld(visitor, viewport, useOcclusionCulling, frame);
        }
    }

    // Seeds the search from the camera section.
    private void initWithinWorld(Visitor visitor, Viewport viewport, boolean useOcclusionCulling, int frame) {
        final long[] visitState = this.visitState;
        final long[] queue = this.queue;
        final int[] delta = this.lattice.delta;

        var origin = viewport.getChunkCoord();
        int idx = this.lattice.indexOf(origin.x(), origin.y(), origin.z());

        this.cameraSectionIndex = idx;

        long frameStamp = SectionLattice.frameStamp(frame);
        visitState[idx] = frameStamp;

        this.apertures[idx] = FastFrustumClamping.FULL;

        int sectionIndex = LocalSectionIndex.pack(origin.x(), origin.y(), origin.z());
        long sm = this.lattice.sectionMeta[idx];
        visitor.visit(idx, this.lattice.regionOfCell[idx], sectionIndex, origin.x(), origin.y(), origin.z(),
                PackedSectionMetadata.toCompactMeta(sm), true);

        int xyz = this.lattice.packXyz(origin.x(), origin.y(), origin.z());

        if (this.visibleCells != null) {
            this.visibleCells[this.visibleCount] = ((long) xyz << 32) | (idx & 0xFFFFFFFFL);
        }

        this.visibleCount++;

        int outgoing;

        if (useOcclusionCulling) {
            outgoing = VisibilityEncoding.getConnections(sm & PackedSectionMetadata.VISIBILITY_MASK);
        } else {
            outgoing = GraphDirectionSet.ALL;
        }

        this.visitNeighbors(visitState, queue, delta, idx, xyz, outgoing, frameStamp);
    }

    // Seeds the search from a plane of loaded sections.
    private void initOutsideWorldHeight(Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        final long[] visitState = this.visitState;
        final long[] queue = this.queue;

        var origin = viewport.getChunkCoord();
        var radius = MathUtil.mojfloor(searchDistance / 16.0f);
        long frameStamp = SectionLattice.frameStamp(frame);

        this.isMultiRootSearch = true;

        this.tryVisitNode(visitState, queue, origin.x(), height, origin.z(), direction, frameStamp, viewport);

        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }
        }

        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(visitState, queue, origin.x() + x, height, origin.z() + z, direction, frameStamp, viewport);
            }
        }
    }

    // Visits a seed cell when it is loaded and visible.
    private void tryVisitNode(long[] visitState, long[] queue, int x, int y, int z, int direction, long frameStamp, Viewport viewport) {
        int idx = this.lattice.indexOf(x, y, z);

        if (idx < 0 || visitState[idx] == SectionLattice.SENTINEL) {
            return;
        }

        if (!isWithinFrustum(viewport, x, y, z)) {
            return;
        }

        this.tail = visit(visitState, this.apertures, queue, FastFrustumClamping.FULL,
                idx, this.lattice.packXyz(x, y, z), direction, frameStamp, this.tail);
    }

    // Receiver of the sections reached by a search.
    public interface Visitor {
        // Receives one reached section.
        void visit(int latticeIndex, int regionId, int sectionIndex, int chunkX, int chunkY, int chunkZ, int meta, boolean visible);
    }
}
