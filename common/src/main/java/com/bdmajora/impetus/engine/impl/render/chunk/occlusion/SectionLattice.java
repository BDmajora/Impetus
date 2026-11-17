package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import grondag.bitraster.PackedBox;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.chunk.PackedSectionMetadata;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

import java.util.Arrays;

// Grid of the loaded sections with the state of the visibility searches.
public final class SectionLattice {
    // Extra cells around the search radius.
    private static final int SLACK = 4;

    // Shift of the frame in a visit state.
    static final int FRAME_SHIFT = 8;
    // Mask of the incoming directions in a visit state.
    static final int DIR_MASK = 0xFF;

    // Visit state of an empty cell.
    static final long SENTINEL = Long.MAX_VALUE;
    // Visit state of an installed cell that was not visited.
    static final long INITIAL = -1L;

    // Bits of one packed local coordinate.
    static final int XYZ_SHIFT = 10;
    // Mask of one packed local coordinate.
    static final int XYZ_LOCAL_MASK = (1 << XYZ_SHIFT) - 1;
    // Packed coordinate step of each direction.
    static final int[] XYZ_STEP = new int[GraphDirection.COUNT];

    // Fills the packed coordinate steps.
    static {
        for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
            XYZ_STEP[dir] = GraphDirection.x(dir) * (1 << (2 * XYZ_SHIFT))
                    + GraphDirection.y(dir) * (1 << XYZ_SHIFT)
                    + GraphDirection.z(dir);
        }
    }

    // Largest size of one axis.
    private static final int MAX_DIM = 1 << XYZ_SHIFT;
    // Largest number of cells.
    private static final int MAX_LATTICE_SLOTS = 1 << 22;

    // Attached sections by position.
    private final Long2ReferenceMap<RenderSection> sections = new Long2ReferenceOpenHashMap<>();
    // Culler of the main search.
    private final OcclusionCuller culler;
    // Culler of the shadow search or null.
    @Nullable
    private final ShadowOcclusionCuller shadowCuller;
    // Whether the rasterizer is used.
    private final boolean rasterOcclusion;

    // Lowest and highest section of the world.
    private final int minSectionY, maxSectionY;

    // Visit state of each cell.
    long[] visitState;
    // Visit state of each cell for the shadow search.
    long[] shadowVisitState;
    // Packed metadata of each cell.
    long[] sectionMeta;
    // Region id of each cell.
    int[] regionOfCell;

    // Occluder data of each cell.
    int[] @Nullable [] occluderData;

    // Occluder bounds of each cell.
    int @Nullable [] occluderBounds;

    // Section of each cell.
    RenderSection[] latticeSection;

    // Cells reported visible by the last main search.
    long[] visibleCells = new long[0];
    // Number of visible cells.
    int visibleCount;

    // Size of the y axis.
    final int dimY;
    // Section y of the first cell.
    final int baseY;
    // Size of the x and z axes.
    int dimX, dimZ;
    // Section x and z of the first cell.
    int baseX, baseZ;
    // Index step of each direction.
    final int[] delta = new int[GraphDirection.COUNT];
    // Index step of one x and one z cell.
    private int strideX, strideZ;

    // Whether the window is ready for a search.
    private boolean established;
    // Number of installed sections.
    int installedCount;

    // Two alternating buffers of a snapshot.
    private static final class SnapshotBuffers {
        // The two buffers.
        private final int[][] buffers = new int[2][];
        // Index of the next buffer.
        private int parity;

        // Returns the next buffer with a length.
        int[] acquire(int length) {
            int slot = this.parity;
            this.parity ^= 1;

            int[] buffer = this.buffers[slot];
            if (buffer == null || buffer.length != length) {
                buffer = new int[length];
                this.buffers[slot] = buffer;
            }

            return buffer;
        }
    }

    // Buffers of the main snapshots.
    private final SnapshotBuffers mainSnapshotBuffers = new SnapshotBuffers();
    // Buffers of the shadow snapshots.
    private final SnapshotBuffers shadowSnapshotBuffers = new SnapshotBuffers();

    // Creates a lattice for a range of sections.
    public SectionLattice(int minSectionY, int maxSectionY, boolean hasShadowPass, boolean rasterOcclusion) {
        if (maxSectionY < minSectionY) {
            throw new IllegalArgumentException("Section range " + minSectionY + " to " + maxSectionY);
        }
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
        this.baseY = minSectionY - 1;
        this.dimY = (maxSectionY - minSectionY) + 2;
        this.culler = new OcclusionCuller(this, minSectionY, maxSectionY, rasterOcclusion);
        this.shadowCuller = hasShadowPass ? new ShadowOcclusionCuller(this) : null;
        this.rasterOcclusion = rasterOcclusion;

        if (this.dimY > MAX_DIM) {
            throw new IllegalStateException("World height " + this.dimY + " exceeds occlusion lattice limit " + MAX_DIM);
        }
    }

    // Returns the attached section at a position or null.
    @Nullable
    public RenderSection getRenderSection(int x, int y, int z) {
        return this.sections.get(PositionUtil.packSection(x, y, z));
    }

    // Attaches a section and installs it when it lies in the window.
    public void attach(RenderSection section) {
        var key = section.positionAsLong();

        if (this.sections.get(key) != null) {
            throw new IllegalStateException("Section already attached to lattice: " + section);
        }

        this.sections.put(key, section);

        this.install(section);
    }

    // Detaches a section and removes it from the window.
    public void detach(RenderSection section) {
        var key = section.positionAsLong();

        RenderSection removed = this.sections.remove(key);

        if (removed == null) {
            throw new IllegalStateException("Section not attached to lattice: " + section);
        }

        this.uninstall(section);
    }

    // Stores the metadata of a section and returns whether the search input changed.
    public boolean updateSectionMetadata(int x, int y, int z, long packedMetadata) {
        int idx = this.installedIndex(x, y, z);

        if (idx < 0) {
            return false;
        }

        long previous = this.sectionMeta[idx];
        this.sectionMeta[idx] = packedMetadata;

        boolean occluderChanged = false;

        if (this.rasterOcclusion) {
            int[] data = PackedSectionMetadata.hasOccluderData(packedMetadata) ? occluderDataOf(this.latticeSection[idx]) : null;
            occluderChanged = this.occluderData[idx] != data;
            this.occluderData[idx] = data;
            this.occluderBounds[idx] = boundsOf(packedMetadata, data);
        }

        return occluderChanged || ((previous ^ packedMetadata) & PackedSectionMetadata.GRAPH_INPUT_MASK) != 0;
    }

    // Returns the section of a cell.
    public RenderSection sectionAt(int latticeIndex) {
        return this.latticeSection[latticeIndex];
    }

    // Returns the visit state stamp of a frame.
    static long frameStamp(int frame) {
        return (frame & 0xFFFFFFFFL) << FRAME_SHIFT;
    }

    // Copy of the frame of every cell that can be read from another thread.
    public record VisibilitySnapshot(int[] visibleFrames, int baseX, int baseY, int baseZ,
                                     int dimX, int dimY, int dimZ) {
        // Snapshot that reports every section as not visible.
        public static final VisibilitySnapshot EMPTY =
                new VisibilitySnapshot(new int[0], 0, 0, 0, 0, 0, 0);

        // Returns whether a section was visible in a frame or later.
        public boolean isSectionVisible(int x, int y, int z, int minVisibleFrame) {
            int lx = x - this.baseX;
            int ly = y - this.baseY;
            int lz = z - this.baseZ;

            if (lx < 1 || lx > this.dimX - 2
                    || ly < 1 || ly > this.dimY - 2
                    || lz < 1 || lz > this.dimZ - 2) {
                return false;
            }

            int idx = (lx * this.dimZ + lz) * this.dimY + ly;

            return this.visibleFrames[idx] >= minVisibleFrame;
        }
    }

    // Copies the frames of a visit state into a snapshot.
    private VisibilitySnapshot snapshot(long[] src, SnapshotBuffers buffers) {
        if (src == null) {
            return VisibilitySnapshot.EMPTY;
        }

        int[] dst = buffers.acquire(src.length);

        for (int i = 0; i < src.length; i++) {
            dst[i] = (int) (src[i] >>> FRAME_SHIFT);
        }

        return new VisibilitySnapshot(dst, this.baseX, this.baseY, this.baseZ,
                this.dimX, this.dimY, this.dimZ);
    }

    // Returns the cell of an installed section or minus one.
    private int installedIndex(int x, int y, int z) {
        if (this.visitState == null) {
            return -1;
        }

        int idx = this.indexOf(x, y, z);
        return idx >= 0 && this.latticeSection[idx] != null ? idx : -1;
    }

    // Moves or allocates the window to cover the search radius around the camera.
    public void ensureWindowCovers(Vector3ic cameraSectionPos, float searchDistance) {
        java.util.Objects.requireNonNull(cameraSectionPos, "cameraSectionPos");
        if (!(searchDistance >= 0.0f)) {
            throw new IllegalArgumentException("Invalid search distance " + searchDistance);
        }
        int radius = MathUtil.mojfloor(searchDistance / 16.0f);
        int neededDimXZ = 2 * (radius + SLACK) + 3;

        if (this.visitState == null || neededDimXZ > this.dimX) {
            this.allocate(neededDimXZ);
        }

        int lx = cameraSectionPos.x() - this.baseX;
        int lz = cameraSectionPos.z() - this.baseZ;

        int reach = radius + 1;

        boolean valid = this.established
                && lx - reach >= 1 && lx + reach <= this.dimX - 2
                && lz - reach >= 1 && lz + reach <= this.dimZ - 2;

        if (!valid) {
            int newBaseX = cameraSectionPos.x() - this.dimX / 2;
            int newBaseZ = cameraSectionPos.z() - this.dimZ / 2;

            if (this.established) {
                this.shiftRebase(newBaseX, newBaseZ);
            } else {
                this.rebase(newBaseX, newBaseZ);
            }

            this.established = true;
        }
    }

    // Runs the main search and returns its snapshot.
    public VisibilitySnapshot findVisible(OcclusionCuller.Visitor visitor,
                                          Viewport viewport,
                                          float searchDistance,
                                          int numRegions,
                                          boolean useOcclusionCulling,
                                          boolean allowFrustumClamping,
                                          int frame) {
        this.culler.findVisible(visitor, viewport, this.visitState, searchDistance, numRegions,
                useOcclusionCulling, allowFrustumClamping, this.shadowCuller != null, frame);

        return this.snapshot(this.visitState, this.mainSnapshotBuffers);
    }

    // Returns the size of the occlusion buffer as text or null.
    public String rasterBufferSize() {
        return this.culler.rasterBufferSize();
    }

    // Returns the number of sections tested out of order.
    public int rasterBacktrackCount() {
        return this.culler.rasterBacktrackCount();
    }

    // Returns the raster budget.
    public RasterBudget rasterBudget() {
        return this.culler.rasterBudget();
    }

    // Runs the shadow search and returns its snapshot.
    public VisibilitySnapshot findShadowVisible(OcclusionCuller.Visitor visitor,
                                                Viewport viewport,
                                                float searchDistance,
                                                int numRegions,
                                                @Nullable Vector3fc lightVector,
                                                int frame) {
        if (this.shadowCuller == null) {
            throw new IllegalStateException("Lattice was not created with shadow pass support");
        }

        if (lightVector != null) {
            this.shadowCuller.findVisible(visitor, viewport, searchDistance, numRegions,
                    lightVector.x(), lightVector.y(), lightVector.z(), frame);
        } else {
            this.culler.findVisible(visitor, viewport, this.shadowVisitState, searchDistance, numRegions,
                    false, false, false, frame);
        }

        return this.snapshot(this.shadowVisitState, this.shadowSnapshotBuffers);
    }

    // Returns the visible cell buffer grown to a capacity.
    long[] ensureVisibleCellsCapacity(int requiredCapacity) {
        if (this.visibleCells.length < requiredCapacity) {
            this.visibleCells = new long[Math.max(requiredCapacity, this.visibleCells.length * 2)];
        }
        return this.visibleCells;
    }

    // Allocates the arrays for a window size.
    private void allocate(int newDimXZ) {
        long slotsLong = (long) newDimXZ * newDimXZ * this.dimY;

        if (newDimXZ > MAX_DIM || slotsLong >= MAX_LATTICE_SLOTS) {
            throw new IllegalStateException("Occlusion lattice window too large (dimXZ=" + newDimXZ
                    + ", slots=" + slotsLong + "); this render distance is not supported");
        }

        this.dimX = newDimXZ;
        this.dimZ = newDimXZ;
        this.strideZ = this.dimY;
        this.strideX = this.dimY * this.dimZ;

        for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
            this.delta[dir] = GraphDirection.x(dir) * this.strideX
                    + GraphDirection.z(dir) * this.strideZ
                    + GraphDirection.y(dir);
        }

        int slots = (int) slotsLong;
        this.visitState = new long[slots];
        this.shadowVisitState = this.shadowCuller != null ? new long[slots] : null;
        this.sectionMeta = new long[slots];
        this.regionOfCell = new int[slots];
        this.latticeSection = new RenderSection[slots];
        this.occluderData = this.rasterOcclusion ? new int[slots][] : null;
        this.occluderBounds = this.rasterOcclusion ? new int[slots] : null;

        this.established = false;
    }

    // Moves the window to a new base and resolves every cell.
    private void rebase(int newBaseX, int newBaseZ) {
        Arrays.fill(this.visitState, SENTINEL);
        if (this.shadowVisitState != null) {
            Arrays.fill(this.shadowVisitState, SENTINEL);
        }
        Arrays.fill(this.sectionMeta, VisibilityEncoding.NULL);
        Arrays.fill(this.regionOfCell, -1);
        Arrays.fill(this.latticeSection, null);
        if (this.rasterOcclusion) {
            Arrays.fill(this.occluderData, null);
            Arrays.fill(this.occluderBounds, 0);
        }

        this.baseX = newBaseX;
        this.baseZ = newBaseZ;
        this.installedCount = 0;

        for (RenderSection section : this.sections.values()) {
            this.install(section);
        }
    }

    // Moves the window and keeps the cells that overlap.
    private void shiftRebase(int newBaseX, int newBaseZ) {
        int dX = newBaseX - this.baseX;
        int dZ = newBaseZ - this.baseZ;

        int interiorXHi = this.dimX - 2;
        int interiorZHi = this.dimZ - 2;

        int xLo = Math.max(1, 1 - dX);
        int xHi = Math.min(interiorXHi, interiorXHi - dX);
        int zLo = Math.max(1, 1 - dZ);
        int zHi = Math.min(interiorZHi, interiorZHi - dZ);

        if (xLo > xHi || zLo > zHi) {
            this.rebase(newBaseX, newBaseZ);
            return;
        }

        this.dropLeaving(xLo + dX, xHi + dX, zLo + dZ, zHi + dZ);

        if (dZ == 0) {
            this.shiftContentsXOnly(dX, xLo, xHi);
        } else {
            this.shiftContents(dX, dZ, xLo, xHi, zLo, zHi);
        }

        this.baseX = newBaseX;
        this.baseZ = newBaseZ;

        this.fillNewBand(xLo, xHi, zLo, zHi);
    }

    // Moves the cells along x.
    private void shiftContentsXOnly(int dX, int xLo, int xHi) {
        int destOff = xLo * this.strideX;
        int srcOff = (xLo + dX) * this.strideX;
        int len = (xHi - xLo + 1) * this.strideX;

        System.arraycopy(this.visitState, srcOff, this.visitState, destOff, len);
        if (this.shadowVisitState != null) {
            System.arraycopy(this.shadowVisitState, srcOff, this.shadowVisitState, destOff, len);
        }
        System.arraycopy(this.sectionMeta, srcOff, this.sectionMeta, destOff, len);
        System.arraycopy(this.regionOfCell, srcOff, this.regionOfCell, destOff, len);
        System.arraycopy(this.latticeSection, srcOff, this.latticeSection, destOff, len);
        if (this.rasterOcclusion) {
            System.arraycopy(this.occluderData, srcOff, this.occluderData, destOff, len);
            System.arraycopy(this.occluderBounds, srcOff, this.occluderBounds, destOff, len);
        }
    }

    // Moves the cells along x and z.
    private void shiftContents(int dX, int dZ, int xLo, int xHi, int zLo, int zHi) {
        int runLen = (zHi - zLo + 1) * this.strideZ;

        int first = dX < 0 ? xHi : xLo;
        int last = dX < 0 ? xLo : xHi;
        int step = dX < 0 ? -1 : 1;

        for (int lx = first; lx != last + step; lx += step) {
            int destOff = lx * this.strideX + zLo * this.strideZ;
            int srcOff = (lx + dX) * this.strideX + (zLo + dZ) * this.strideZ;

            System.arraycopy(this.visitState, srcOff, this.visitState, destOff, runLen);
            if (this.shadowVisitState != null) {
                System.arraycopy(this.shadowVisitState, srcOff, this.shadowVisitState, destOff, runLen);
            }
            System.arraycopy(this.sectionMeta, srcOff, this.sectionMeta, destOff, runLen);
            System.arraycopy(this.regionOfCell, srcOff, this.regionOfCell, destOff, runLen);
            System.arraycopy(this.latticeSection, srcOff, this.latticeSection, destOff, runLen);
            if (this.rasterOcclusion) {
                System.arraycopy(this.occluderData, srcOff, this.occluderData, destOff, runLen);
                System.arraycopy(this.occluderBounds, srcOff, this.occluderBounds, destOff, runLen);
            }
        }
    }

    // Removes the sections that leave the window.
    private void dropLeaving(int srcXLo, int srcXHi, int srcZLo, int srcZHi) {
        int interiorXHi = this.dimX - 2;
        int interiorZHi = this.dimZ - 2;
        int interiorYHi = this.dimY - 2;

        int xBandLo = srcXLo == 1 ? srcXHi + 1 : 1;
        int xBandHi = srcXLo == 1 ? interiorXHi : srcXLo - 1;
        int zBandLo = srcZLo == 1 ? srcZHi + 1 : 1;
        int zBandHi = srcZLo == 1 ? interiorZHi : srcZLo - 1;

        this.dropLeavingRectangle(xBandLo, xBandHi, 1, interiorZHi, interiorYHi);
        this.dropLeavingRectangle(srcXLo, srcXHi, zBandLo, zBandHi, interiorYHi);
    }

    // Removes the sections of a rectangle.
    private void dropLeavingRectangle(int xLo, int xHi, int zLo, int zHi, int interiorYHi) {
        for (int lx = xLo; lx <= xHi; lx++) {
            for (int lz = zLo; lz <= zHi; lz++) {
                int col = lx * this.strideX + lz * this.strideZ;
                for (int ly = 1; ly <= interiorYHi; ly++) {
                    if (this.latticeSection[col + ly] != null) {
                        this.installedCount--;
                    }
                }
            }
        }
    }

    // Installs the sections of the new bands.
    private void fillNewBand(int keepXLo, int keepXHi, int keepZLo, int keepZHi) {
        int interiorXHi = this.dimX - 2;
        int interiorZHi = this.dimZ - 2;
        int interiorYHi = this.dimY - 2;

        int xBandLo = keepXLo == 1 ? keepXHi + 1 : 1;
        int xBandHi = keepXLo == 1 ? interiorXHi : keepXLo - 1;
        int zBandLo = keepZLo == 1 ? keepZHi + 1 : 1;
        int zBandHi = keepZLo == 1 ? interiorZHi : keepZLo - 1;

        this.fillBandRectangle(xBandLo, xBandHi, 1, interiorZHi, interiorYHi);
        this.fillBandRectangle(keepXLo, keepXHi, zBandLo, zBandHi, interiorYHi);
    }

    // Installs the sections of a rectangle.
    private void fillBandRectangle(int xLo, int xHi, int zLo, int zHi, int interiorYHi) {
        for (int lx = xLo; lx <= xHi; lx++) {
            int worldX = this.baseX + lx;

            for (int lz = zLo; lz <= zHi; lz++) {
                int worldZ = this.baseZ + lz;
                int col = lx * this.strideX + lz * this.strideZ;

                for (int ly = 1; ly <= interiorYHi; ly++) {
                    int idx = col + ly;

                    this.visitState[idx] = SENTINEL;
                    if (this.shadowVisitState != null) {
                        this.shadowVisitState[idx] = SENTINEL;
                    }
                    this.sectionMeta[idx] = VisibilityEncoding.NULL;
                    this.regionOfCell[idx] = -1;
                    this.latticeSection[idx] = null;
                    if (this.rasterOcclusion) {
                        this.occluderData[idx] = null;
                        this.occluderBounds[idx] = 0;
                    }

                    RenderSection section = this.sections.get(
                            PositionUtil.packSection(worldX, this.baseY + ly, worldZ));

                    if (section != null) {
                        this.install(section);
                    }
                }
            }
        }
    }

    int indexOf(int x, int y, int z) {
        int lx = x - this.baseX;
        int ly = y - this.baseY;
        int lz = z - this.baseZ;

        if (lx < 1 || lx > this.dimX - 2
                || ly < 1 || ly > this.dimY - 2
                || lz < 1 || lz > this.dimZ - 2) {
            return -1;
        }

        return (lx * this.dimZ + lz) * this.dimY + ly;
    }

    int packXyz(int x, int y, int z) {
        return ((x - this.baseX) << (2 * XYZ_SHIFT)) | ((y - this.baseY) << XYZ_SHIFT) | (z - this.baseZ);
    }

    // Returns the occluder data of a section or null.
    private static int @Nullable [] occluderDataOf(@Nullable RenderSection section) {
        var built = section != null ? section.getBuiltContext() : null;
        return built != null ? built.occluderBoxes : null;
    }

    // Returns the occluder bounds of a cell.
    private static int boundsOf(long packedMetadata, int @Nullable [] occluderData) {
        if (occluderData == null) {
            return 0;
        }

        var pendingUpdate = PackedSectionMetadata.getPendingUpdate(packedMetadata);

        return pendingUpdate != null && !pendingUpdate.isSort()
                ? PackedBox.FULL_BOX
                : SectionVisibilityBuilder.bounds(occluderData);
    }

    // Installs a section when it lies in the window.
    private void install(RenderSection section) {
        if (this.visitState == null) {
            return;
        }

        int idx = this.indexOf(section.getChunkX(), section.getChunkY(), section.getChunkZ());

        if (idx < 0) {
            return;
        }

        this.visitState[idx] = INITIAL;
        if (this.shadowVisitState != null) {
            this.shadowVisitState[idx] = INITIAL;
        }
        this.latticeSection[idx] = section;
        this.regionOfCell[idx] = section.getRegion().getId();
        long packedMetadata = section.getPackedMetadata();
        this.sectionMeta[idx] = packedMetadata;

        if (this.rasterOcclusion) {
            int[] data = PackedSectionMetadata.hasOccluderData(packedMetadata) ? occluderDataOf(section) : null;
            this.occluderData[idx] = data;
            this.occluderBounds[idx] = boundsOf(packedMetadata, data);
        }

        this.installedCount++;
    }

    // Removes a section from the window.
    private void uninstall(RenderSection section) {
        int idx = this.installedIndex(section.getChunkX(), section.getChunkY(), section.getChunkZ());

        if (idx < 0) {
            return;
        }

        this.visitState[idx] = SENTINEL;
        if (this.shadowVisitState != null) {
            this.shadowVisitState[idx] = SENTINEL;
        }
        this.sectionMeta[idx] = VisibilityEncoding.NULL;
        this.regionOfCell[idx] = -1;
        this.latticeSection[idx] = null;
        if (this.rasterOcclusion) {
            this.occluderData[idx] = null;
            this.occluderBounds[idx] = 0;
        }
        this.installedCount--;
    }
}
