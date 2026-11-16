package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;

import java.util.Arrays;

import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller.ANGLE_REFINEMENT_MASKS;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller.isVisibleInPartialRegion;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.OcclusionCuller.regionOrigin;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.XYZ_LOCAL_MASK;
import static com.bdmajora.impetus.engine.impl.render.chunk.occlusion.SectionLattice.XYZ_SHIFT;

// Searches the sections that can cast a shadow that the camera sees.
final class ShadowOcclusionCuller {
    // Light component below which both directions of an axis stay open.
    private static final float AXIS_EPSILON = 1e-4f;

    // State bit of a root cell.
    private static final int ROOT_BIT = 1 << GraphDirection.COUNT;

    // State bit of a cell that was reported.
    private static final int REPORTED_BIT = 1 << (GraphDirection.COUNT + 1);

    // Mask of the incoming faces in a state.
    private static final int INCOMING_MASK = GraphDirectionSet.ALL;

    // Bit of each direction.
    private static final int DOWN_BIT = 1 << GraphDirection.DOWN;
    private static final int UP_BIT = 1 << GraphDirection.UP;
    private static final int NORTH_BIT = 1 << GraphDirection.NORTH;
    private static final int SOUTH_BIT = 1 << GraphDirection.SOUTH;
    private static final int WEST_BIT = 1 << GraphDirection.WEST;
    private static final int EAST_BIT = 1 << GraphDirection.EAST;

    // Lattice of the sections.
    private final SectionLattice lattice;
    // Region classes of the search.
    private final RegionCullCache regionCullCache = new RegionCullCache();

    // Queue of entries made of the local position and the lattice index.
    private long[] queue = new long[256];

    // Creates a culler for a lattice and rejects null.
    ShadowOcclusionCuller(SectionLattice lattice) {
        this.lattice = java.util.Objects.requireNonNull(lattice, "lattice");
    }

    // Searches from the visible cells toward the light and reports every reached cell.
    void findVisible(OcclusionCuller.Visitor visitor,
                     Viewport viewport,
                     float searchDistance,
                     int numRegions,
                     float lightX, float lightY, float lightZ,
                     int frame)
    {
        // Sizes the queue for the installed cells
        int installed = this.lattice.installedCount;
        if (this.queue.length < installed) {
            this.queue = new long[Math.max(installed, this.queue.length * 2)];
        }

        this.regionCullCache.begin(viewport, searchDistance, numRegions);

        final int towardLight = towardLightDirections(lightX, lightY, lightZ);
        final long refinementMask = refinementMask(lightX, lightY, lightZ);
        final long frameStamp = SectionLattice.frameStamp(frame);

        int tail = this.seed(frameStamp);
        this.process(visitor, viewport, searchDistance, towardLight, refinementMask, frameStamp, tail);
    }

    // Returns the directions toward the light.
    static int towardLightDirections(float lightX, float lightY, float lightZ) {
        int directions = 0;

        if (lightX <= AXIS_EPSILON) directions |= WEST_BIT;
        if (lightX >= -AXIS_EPSILON) directions |= EAST_BIT;

        if (lightY <= AXIS_EPSILON) directions |= DOWN_BIT;
        if (lightY >= -AXIS_EPSILON) directions |= UP_BIT;

        if (lightZ <= AXIS_EPSILON) directions |= NORTH_BIT;
        if (lightZ >= -AXIS_EPSILON) directions |= SOUTH_BIT;

        return directions;
    }

    // Returns the mask of the face pairs that stay for a light direction.
    static long refinementMask(float lightX, float lightY, float lightZ) {
        float ax = Math.abs(lightX), ay = Math.abs(lightY), az = Math.abs(lightZ);
        float max = Math.max(ax, Math.max(ay, az));

        int sel = 0;
        if (ax < max - AXIS_EPSILON) sel |= 1;
        if (ay < max - AXIS_EPSILON) sel |= 2;
        if (az < max - AXIS_EPSILON) sel |= 4;

        return ANGLE_REFINEMENT_MASKS[sel];
    }

    // Queues every visible cell of the main pass that is not outside the shadow frustum and returns the tail.
    private int seed(long frameStamp) {
        final long[] roots = this.lattice.visibleCells;
        final int rootCount = this.lattice.visibleCount;
        final long[] visitState = this.lattice.shadowVisitState;
        final int[] regionOfCell = this.lattice.regionOfCell;
        final long[] queue = this.queue;
        final RegionCullCache cache = this.regionCullCache;
        final int baseX = this.lattice.baseX, baseY = this.lattice.baseY, baseZ = this.lattice.baseZ;

        int tail = 0;

        for (int i = 0; i < rootCount; i++) {
            long entry = roots[i];
            int idx = (int) entry;

            // Skips duplicates
            if (visitState[idx] >= frameStamp) {
                continue;
            }

            // Reads the region class and unpacks coordinates only on a miss
            int regionId = regionOfCell[idx];
            int classification = cache.cached(regionId);

            if (classification == RegionCullCache.UNCOMPUTED) {
                int xyz = (int) (entry >>> 32);
                int chunkX = baseX + ((xyz >>> (2 * XYZ_SHIFT)) & XYZ_LOCAL_MASK);
                int chunkY = baseY + ((xyz >>> XYZ_SHIFT) & XYZ_LOCAL_MASK);
                int chunkZ = baseZ + (xyz & XYZ_LOCAL_MASK);

                classification = cache.classify(regionId,
                        regionOrigin(chunkX, RenderRegion.REGION_WIDTH_SH, RenderRegion.REGION_BLOCK_WIDTH),
                        regionOrigin(chunkY, RenderRegion.REGION_HEIGHT_SH, RenderRegion.REGION_BLOCK_HEIGHT),
                        regionOrigin(chunkZ, RenderRegion.REGION_LENGTH_SH, RenderRegion.REGION_BLOCK_LENGTH));
            }

            if (classification == RegionCullCache.OUTSIDE) {
                // Skips the section
                continue;
            }

            visitState[idx] = frameStamp | ROOT_BIT;
            queue[tail++] = entry;
        }

        return tail;
    }

    // Expands the queued cells toward the light and reports them.
    private void process(OcclusionCuller.Visitor visitor,
                         Viewport viewport,
                         float searchDistance,
                         int towardLight,
                         long refinementMask,
                         long frameStamp,
                         int tail)
    {
        final long[] visitState = this.lattice.shadowVisitState;
        final long[] sectionMeta = this.lattice.sectionMeta;
        final int[] regionOfCell = this.lattice.regionOfCell;
        final int[] delta = this.lattice.delta;
        long[] queue = this.queue;
        final int baseX = this.lattice.baseX, baseY = this.lattice.baseY, baseZ = this.lattice.baseZ;

        final RegionCullCache cache = this.regionCullCache;
        final CameraTransform transform = viewport.getTransform();

        final int strideX = delta[GraphDirection.EAST];
        final int strideZ = delta[GraphDirection.SOUTH];
        final int xStep = 1 << (2 * XYZ_SHIFT);
        final int yStep = 1 << XYZ_SHIFT;

        int head = 0;

        while (head < tail) {
            // Makes room for one entry per direction
            if (tail + GraphDirection.COUNT > queue.length) {
                queue = this.grow(tail + GraphDirection.COUNT);
            }

            long entry = queue[head++];
            int idx = (int) entry;
            int xyz = (int) (entry >>> 32);

            int chunkX = baseX + ((xyz >>> (2 * XYZ_SHIFT)) & XYZ_LOCAL_MASK);
            int chunkY = baseY + ((xyz >>> XYZ_SHIFT) & XYZ_LOCAL_MASK);
            int chunkZ = baseZ + (xyz & XYZ_LOCAL_MASK);

            // Reads the state of the cell
            long state = visitState[idx];
            boolean first = (state & REPORTED_BIT) == 0;

            int regionId = regionOfCell[idx];
            long sm = sectionMeta[idx];
            int classification = cache.classify(regionId,
                    regionOrigin(chunkX, RenderRegion.REGION_WIDTH_SH, RenderRegion.REGION_BLOCK_WIDTH),
                    regionOrigin(chunkY, RenderRegion.REGION_HEIGHT_SH, RenderRegion.REGION_BLOCK_HEIGHT),
                    regionOrigin(chunkZ, RenderRegion.REGION_LENGTH_SH, RenderRegion.REGION_BLOCK_LENGTH));

            boolean visible;

            if (classification == RegionCullCache.FULLY_INSIDE) {
                visible = true;
            } else if (classification == RegionCullCache.OUTSIDE) {
                visible = false;
            } else {
                visible = isVisibleInPartialRegion(classification, viewport, transform, chunkX, chunkY, chunkZ, searchDistance);
            }

            // Reports the cell once
            if (first) {
                visitState[idx] = state | REPORTED_BIT;

                int sectionIndex = LocalSectionIndex.pack(chunkX, chunkY, chunkZ);
                visitor.visit(idx, regionId, sectionIndex, chunkX, chunkY, chunkZ, PackedSectionMetadata.toCompactMeta(sm), visible);
            }

            if (!visible) {
                continue;
            }

            int connections;

            if ((state & ROOT_BIT) != 0) {
                // Opens every face of a root
                connections = GraphDirectionSet.ALL;
            } else {
                // Follows the paths that the visibility data allows
                connections = VisibilityEncoding.getConnections(sm & refinementMask, (int) (state & INCOMING_MASK));
            }

            // Steps only toward the light
            connections &= towardLight;

            if ((connections & DOWN_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx - 1, xyz - yStep, UP_BIT, refinementMask, towardLight, frameStamp, tail);
            }
            if ((connections & UP_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx + 1, xyz + yStep, DOWN_BIT, refinementMask, towardLight, frameStamp, tail);
            }
            if ((connections & NORTH_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx - strideZ, xyz - 1, SOUTH_BIT, refinementMask, towardLight, frameStamp, tail);
            }
            if ((connections & SOUTH_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx + strideZ, xyz + 1, NORTH_BIT, refinementMask, towardLight, frameStamp, tail);
            }
            if ((connections & WEST_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx - strideX, xyz - xStep, EAST_BIT, refinementMask, towardLight, frameStamp, tail);
            }
            if ((connections & EAST_BIT) != 0) {
                tail = visit(visitState, sectionMeta, queue, idx + strideX, xyz + xStep, WEST_BIT, refinementMask, towardLight, frameStamp, tail);
            }
        }
    }

    // Replaces the queue with a larger one and keeps its entries.
    private long[] grow(int minimumCapacity) {
        this.queue = Arrays.copyOf(this.queue, Math.max(minimumCapacity, this.queue.length * 2));
        return this.queue;
    }

    // Queues a neighbor the first time it is reached or again when a new face widens its exits and returns the tail.
    private static int visit(long[] visitState, long[] sectionMeta, long[] queue, int idx, int xyz, int incoming,
                             long refinementMask, int towardLight, long frameStamp, int tail) {
        long state = visitState[idx];

        if (state < frameStamp) {
            state = frameStamp;
            queue[tail++] = ((long) xyz << 32) | (idx & 0xFFFFFFFFL);
        } else if ((state & (incoming | ROOT_BIT | REPORTED_BIT)) == REPORTED_BIT
                && widensExits(sectionMeta[idx], refinementMask, towardLight, (int) state, incoming)) {
            queue[tail++] = ((long) xyz << 32) | (idx & 0xFFFFFFFFL);
        }

        visitState[idx] = state | incoming;

        return tail;
    }

    // Returns whether an additional incoming face lets a cell reach more directions.
    private static boolean widensExits(long sm, long refinementMask, int towardLight, int state, int incoming) {
        long visibility = sm & refinementMask;

        // Returns false when every pair of faces is open
        if (visibility == refinementMask) {
            return false;
        }

        int before = VisibilityEncoding.getConnections(visibility, state & INCOMING_MASK) & towardLight;
        int after = VisibilityEncoding.getConnections(visibility, (state | incoming) & INCOMING_MASK) & towardLight;

        return after != before;
    }
}
