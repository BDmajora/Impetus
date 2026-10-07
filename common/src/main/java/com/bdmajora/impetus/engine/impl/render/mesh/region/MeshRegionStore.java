package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.util.IdAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.IntConsumer;
import org.lwjgl.system.MemoryUtil;


// Region and section header buffers the GPU walks to find geometry (section id = (regionId << 8) | slot); headers are staged per region and uploaded as one 8 KB block
public class MeshRegionStore {
    // Bytes per region header: two packed uint64s
    public static final int REGION_HEADER_BYTES = 16;
    // Bytes per section header: ivec4 header + ivec4 face ranges
    public static final int SECTION_HEADER_BYTES = 32;
    // Sections per region, and therefore the shift between a section id and its region id
    public static final int SECTIONS_PER_REGION = RenderRegion.REGION_SIZE;
    private static final int SECTION_ID_SHIFT = 8;

    private static final int REGION_SECTION_BLOCK_BYTES = SECTIONS_PER_REGION * SECTION_HEADER_BYTES;

    private final BindlessBuffer regionBuffer;
    private final BindlessBuffer sectionBuffer;
    private final UploadStream uploadStream;

    private final Long2IntOpenHashMap regionIdByKey = new Long2IntOpenHashMap();
    private final IdAllocator regionIds = new IdAllocator();
    private final Region[] regions;

    private final Deque<Region> dirtyRegions = new ArrayDeque<>();

    // Told the id of every region whose headers were re-uploaded, since that resets the GPU-side translucent draw order the section sorter wrote
    private IntConsumer uploadListener = regionId -> {};

    public MeshRegionStore(int maxRegions, UploadStream uploadStream) {
        this.regionIdByKey.defaultReturnValue(-1);
        this.uploadStream = uploadStream;
        this.regions = new Region[maxRegions];
        this.regionBuffer = new BindlessBuffer((long) maxRegions * REGION_HEADER_BYTES);
        this.sectionBuffer = new BindlessBuffer((long) maxRegions * REGION_SECTION_BLOCK_BYTES);
    }

    // GPU address of the region table
    public long getRegionBufferAddress() {
        return this.regionBuffer.getDeviceAddress();
    }

    // GPU address of the section table
    public long getSectionBufferAddress() {
        return this.sectionBuffer.getDeviceAddress();
    }

    // Capacity
    public int getMaxRegions() {
        return this.regions.length;
    }

    // Called with each region id commit() uploads
    public void setUploadListener(IntConsumer listener) {
        this.uploadListener = listener;
    }

    // Whether the region a section belongs to is already allocated, i.e. placing it needs no new region id
    public boolean hasRegionFor(int sectionX, int sectionY, int sectionZ) {
        return this.regionIdByKey.containsKey(regionKey(sectionX, sectionY, sectionZ));
    }

    // Live regions
    public int getRegionCount() {
        return this.regionIdByKey.size();
    }

    // One past the highest live region id, i.e. how far the per-frame region scan has to go
    public int getMaxRegionIndex() {
        return this.regionIds.maxIndex();
    }

    // Whether an id is allocated
    public boolean regionExists(int regionId) {
        return this.regions[regionId] != null;
    }

    // Packed region coordinates
    public long getRegionKey(int regionId) {
        return this.regions[regionId].key;
    }

    // Claims a slot for a section, creating its region if needed; returns region id in the high bits and slot in the low 8
    public int allocateSection(int sectionX, int sectionY, int sectionZ) {
        long key = regionKey(sectionX, sectionY, sectionZ);
        int regionId = this.regionIdByKey.computeIfAbsent(key, k -> this.regionIds.allocate());

        Region region = this.regions[regionId];

        if (region == null) {
            region = new Region(regionId, sectionX >> 3, sectionY >> 2, sectionZ >> 3);
            this.regions[regionId] = region;
        }

        int slot = slotWithinRegion(sectionX, sectionY, sectionZ);
        int index = region.count;

        // Validate before claiming the index, or a rejected duplicate leaves a phantom entry the next removal trips over
        if (region.slotToIndex[slot] != -1 || region.indexToSlot[index] != -1) {
            throw new IllegalStateException("Section slot " + slot + " in region " + regionId + " is already taken");
        }

        region.count++;

        region.slotToIndex[slot] = index;
        region.indexToSlot[index] = slot;

        markDirty(region);

        return slot | (regionId << SECTION_ID_SHIFT);
    }

    // Pointer to a section's 32 header bytes in the staging copy, valid until the next call; marks the region dirty so the write goes up on the next commit
    public long beginSectionUpdate(int sectionId) {
        Region region = this.regions[sectionId >>> SECTION_ID_SHIFT];
        int index = region.slotToIndex[sectionId & 0xFF];

        if (index < 0) {
            throw new IllegalStateException("Section " + sectionId + " is not allocated");
        }

        markDirty(region);

        return region.sectionHeaders + (long) index * SECTION_HEADER_BYTES;
    }

    // The section's index in the region's packed array; the GPU addresses its visibility byte and draw slot by dense index, not by region position
    public int getSectionIndex(int sectionId) {
        Region region = this.regions[sectionId >>> SECTION_ID_SHIFT];
        return region.slotToIndex[sectionId & 0xFF];
    }

    // Removes a section and compacts the region's dense array so live sections stay in 0..count-1, letting the GPU dispatch exactly `count` meshlets
    public void removeSection(int sectionId) {
        Region region = this.regions[sectionId >>> SECTION_ID_SHIFT];

        if (region == null) {
            return;
        }

        int slot = sectionId & 0xFF;
        int index = region.slotToIndex[slot];

        if (index < 0) {
            return;
        }

        MemoryUtil.memSet(region.sectionHeaders + (long) index * SECTION_HEADER_BYTES, 0, SECTION_HEADER_BYTES);
        region.slotToIndex[slot] = -1;
        region.indexToSlot[index] = -1;

        int lastIndex = --region.count;

        if (lastIndex != index) {
            // Move the tail entry into the hole
            int movedSlot = region.indexToSlot[lastIndex];

            MemoryUtil.memCopy(region.sectionHeaders + (long) lastIndex * SECTION_HEADER_BYTES,
                    region.sectionHeaders + (long) index * SECTION_HEADER_BYTES, SECTION_HEADER_BYTES);
            MemoryUtil.memSet(region.sectionHeaders + (long) lastIndex * SECTION_HEADER_BYTES, 0, SECTION_HEADER_BYTES);

            region.indexToSlot[lastIndex] = -1;
            region.indexToSlot[index] = movedSlot;
            region.slotToIndex[movedSlot] = index;

            // The header carries its own index, for the translucency draw order, so patch it after the move
            long headerY = region.sectionHeaders + (long) index * SECTION_HEADER_BYTES + 4L;
            int packed = MemoryUtil.memGetInt(headerY);
            packed = (packed & ~(0xFF << 18)) | (index << 18);
            MemoryUtil.memPutInt(headerY, packed);
        }

        if (region.count == 0) {
            region.removed = true;
            region.free();
            this.regions[region.id] = null;
            this.regionIds.release(region.id);
            this.regionIdByKey.remove(region.key);
        }

        markDirty(region);
    }

    // Pushes every dirty region's header and section block to the GPU
    public void commit() {
        while (!this.dirtyRegions.isEmpty()) {
            Region region = this.dirtyRegions.poll();
            region.dirty = false;

            if (region.removed) {
                // A new region may already have claimed the id; its own upload overwrites this one and clearing here would corrupt it
                if (this.regions[region.id] != null) {
                    continue;
                }

                // Wipe both blocks, or the GPU renders whatever the departed region left behind
                long header = this.uploadStream.upload(this.regionBuffer,
                        (long) region.id * REGION_HEADER_BYTES, REGION_HEADER_BYTES);
                MemoryUtil.memSet(header, -1, REGION_HEADER_BYTES);

                long sections = this.uploadStream.upload(this.sectionBuffer,
                        (long) region.id * REGION_SECTION_BLOCK_BYTES, REGION_SECTION_BLOCK_BYTES);
                MemoryUtil.memSet(sections, 0, REGION_SECTION_BLOCK_BYTES);
                continue;
            }

            long header = this.uploadStream.upload(this.regionBuffer,
                    (long) region.id * REGION_HEADER_BYTES, REGION_HEADER_BYTES);
            writeRegionHeader(header, region);

            long sections = this.uploadStream.upload(this.sectionBuffer,
                    (long) region.id * REGION_SECTION_BLOCK_BYTES, REGION_SECTION_BLOCK_BYTES);
            MemoryUtil.memCopy(region.sectionHeaders, sections, REGION_SECTION_BLOCK_BYTES);

            this.uploadListener.accept(region.id);
        }
    }

    // Frustum test on the region's bounds, for CPU-side stats
    public boolean isRegionVisible(Viewport viewport, int regionId) {
        Region region = this.regions[regionId];

        if (region == null) {
            return false;
        }

        // Half-extents of a region in blocks: 8x4x8 sections of 16 blocks
        return viewport.isBoxVisible((region.x << 7) + (1 << 6), (region.y << 6) + (1 << 5), (region.z << 7) + (1 << 6),
                1 << 6, 1 << 5, 1 << 6);
    }

    // Manhattan distance from camera to region, averaged over two opposite corners; only used for front-to-back sorting so cheapness beats exactness
    public int distanceTo(int regionId, int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        Region region = this.regions[regionId];

        return (Math.abs((region.x << 3) + 4 - cameraSectionX)
                + Math.abs((region.y << 2) + 2 - cameraSectionY)
                + Math.abs((region.z << 3) + 4 - cameraSectionZ)
                + Math.abs((region.x << 3) + 3 - cameraSectionX)
                + Math.abs((region.y << 2) + 1 - cameraSectionY)
                + Math.abs((region.z << 3) + 3 - cameraSectionZ)) >> 1;
    }

    // Whether the camera sits inside the region's slab on any axis, where moving even slightly can change which of its sections is farther
    public boolean isRegionInCameraAxis(int regionId, double cameraX, double cameraY, double cameraZ) {
        Region region = this.regions[regionId];

        return ((region.x << 7) <= cameraX && cameraX <= ((region.x + 1) << 7))
                || ((region.y << 6) <= cameraY && cameraY <= ((region.y + 1) << 6))
                || ((region.z << 7) <= cameraZ && cameraZ <= ((region.z + 1) << 7));
    }

    // Whether the region's centre lies within a square of the given chunk radius around the camera's section, on every axis
    public boolean isWithinChunks(int regionId, int distance, int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        Region region = this.regions[regionId];

        return Math.abs((region.x << 3) + 4 - cameraSectionX) <= distance
                && Math.abs((region.y << 2) + 2 - cameraSectionY) <= distance
                && Math.abs((region.z << 3) + 4 - cameraSectionZ) <= distance;
    }

    // Frees the GPU tables
    public void delete() {
        for (Region region : this.regions) {
            if (region != null) {
                region.free();
            }
        }
        Arrays.fill(this.regions, null);
        this.dirtyRegions.clear();

        this.regionBuffer.delete();
        this.sectionBuffer.delete();
    }

    // Queues the region's table entry for re-upload
    private void markDirty(Region region) {
        if (region.dirty) {
            return;
        }
        region.dirty = true;
        this.dirtyRegions.add(region);
    }

    // Two packed uint64s for the region rasteriser: a = [0..23] y, [24..47] x, [48..55] highest live index, [56..58] size z, [59..61] size x, [62..63] size y; b = [40..63] z; extents cover only the occupied part
    private static void writeRegionHeader(long ptr, Region region) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int lastIndex = 0;

        for (int slot = 0; slot < SECTIONS_PER_REGION; slot++) {
            if (region.slotToIndex[slot] == -1) {
                continue;
            }

            int x = slot & 7;
            int y = slot >>> 6;
            int z = (slot >>> 3) & 7;

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            lastIndex = Math.max(lastIndex, region.slotToIndex[slot]);
        }

        long size = (long) (maxY - minY) << 62 | (long) (maxX - minX) << 59 | (long) (maxZ - minZ) << 56;
        long count = (long) lastIndex << 48;
        long x = ((((long) region.x << 3) + minX) & 0xFFFFFF) << 24;
        long y = (((long) region.y << 2) + minY) & 0xFFFFFF;
        long z = ((((long) region.z << 3) + minZ) & 0xFFFFFF) << 40;

        MemoryUtil.memPutLong(ptr, size | count | x | y);
        MemoryUtil.memPutLong(ptr + 8, z);
    }

    // Section coordinates to the containing region's key
    private static long regionKey(int sectionX, int sectionY, int sectionZ) {
        return PositionUtil.packSection(sectionX >> 3, sectionY >> 2, sectionZ >> 3);
    }

    // Slot layout within a region: y in the top two bits, then z, then x, matching what the section rasteriser's mesh shader unpacks
    private static int slotWithinRegion(int sectionX, int sectionY, int sectionZ) {
        return ((sectionY & 3) << 6) | ((sectionZ & 7) << 3) | (sectionX & 7);
    }

    private static final class Region {
        private final int id;
        private final int x, y, z;
        private final long key;

        // Slot within the 8x4x8 grid <-> dense index in the uploaded array; -1 when unused
        private final int[] slotToIndex = new int[SECTIONS_PER_REGION];
        private final int[] indexToSlot = new int[SECTIONS_PER_REGION];
        private int count;

        // The whole region's section headers, staged off-heap so a dirty region uploads as one contiguous copy
        private long sectionHeaders;

        private boolean dirty;
        private boolean removed;

        private Region(int id, int x, int y, int z) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = PositionUtil.packSection(x, y, z);

            Arrays.fill(this.slotToIndex, -1);
            Arrays.fill(this.indexToSlot, -1);

            this.sectionHeaders = MemoryUtil.nmemAlloc(REGION_SECTION_BLOCK_BYTES);
            MemoryUtil.memSet(this.sectionHeaders, 0, REGION_SECTION_BLOCK_BYTES);
        }

        // Releases the native mirrors
        private void free() {
            if (this.sectionHeaders != 0L) {
                MemoryUtil.nmemFree(this.sectionHeaders);
                this.sectionHeaders = 0L;
            }
        }
    }
}
