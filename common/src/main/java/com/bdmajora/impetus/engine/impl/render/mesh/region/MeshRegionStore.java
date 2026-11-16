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

// Region and section tables that the GPU reads to find geometry.
public class MeshRegionStore {
    // Bytes of one region header.
    public static final int REGION_HEADER_BYTES = 16;
    // Bytes of one section header.
    public static final int SECTION_HEADER_BYTES = 32;
    // Sections in a region.
    public static final int SECTIONS_PER_REGION = RenderRegion.REGION_SIZE;
    // Shift of the region id in a section id.
    private static final int SECTION_ID_SHIFT = 8;

    // Bytes of the section headers of a region.
    private static final int REGION_SECTION_BLOCK_BYTES = SECTIONS_PER_REGION * SECTION_HEADER_BYTES;
    // Bytes of the placement times of a region.
    private static final int REGION_TIME_BLOCK_BYTES = SECTIONS_PER_REGION * 4;

    // Buffer of the region headers.
    private final BindlessBuffer regionBuffer;
    // Buffer of the section headers.
    private final BindlessBuffer sectionBuffer;
    // Buffer of the placement times.
    private final BindlessBuffer timeBuffer;
    // Stream that copies the tables.
    private final UploadStream uploadStream;
    // Time at which the clock started.
    private final long epochNanos = System.nanoTime();

    // Region id of each region key.
    private final Long2IntOpenHashMap regionIdByKey = new Long2IntOpenHashMap();
    // Allocator of the region ids.
    private final IdAllocator regionIds = new IdAllocator();
    // Regions by id.
    private final Region[] regions;

    // Regions that need an upload.
    private final Deque<Region> dirtyRegions = new ArrayDeque<>();

    // Receiver of the id of every uploaded region.
    private IntConsumer uploadListener = regionId -> {};

    // Creates the store and rejects a region count that is not positive or a null stream.
    public MeshRegionStore(int maxRegions, UploadStream uploadStream) {
        if (maxRegions <= 0) {
            throw new IllegalArgumentException("Region count must be positive");
        }
        this.regionIdByKey.defaultReturnValue(-1);
        this.uploadStream = java.util.Objects.requireNonNull(uploadStream, "uploadStream");
        this.regions = new Region[maxRegions];
        this.regionBuffer = new BindlessBuffer((long) maxRegions * REGION_HEADER_BYTES);
        this.sectionBuffer = new BindlessBuffer((long) maxRegions * REGION_SECTION_BLOCK_BYTES);
        this.timeBuffer = new BindlessBuffer((long) maxRegions * REGION_TIME_BLOCK_BYTES);
    }

    // Returns the GPU address of the placement times.
    public long getSectionTimeBufferAddress() {
        return this.timeBuffer.getDeviceAddress();
    }

    // Returns the milliseconds since the store was made.
    public int currentTimeMs() {
        return (int) ((System.nanoTime() - this.epochNanos) / 1_000_000L);
    }

    // Returns the GPU address of the region table.
    public long getRegionBufferAddress() {
        return this.regionBuffer.getDeviceAddress();
    }

    // Returns the GPU address of the section table.
    public long getSectionBufferAddress() {
        return this.sectionBuffer.getDeviceAddress();
    }

    // Returns the capacity.
    public int getMaxRegions() {
        return this.regions.length;
    }

    // Stores the receiver of uploaded region ids and rejects null.
    public void setUploadListener(IntConsumer listener) {
        this.uploadListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    // Returns whether the region of a section is allocated.
    public boolean hasRegionFor(int sectionX, int sectionY, int sectionZ) {
        return this.regionIdByKey.containsKey(regionKey(sectionX, sectionY, sectionZ));
    }

    // Returns the number of regions.
    public int getRegionCount() {
        return this.regionIdByKey.size();
    }

    // Returns one past the highest region id.
    public int getMaxRegionIndex() {
        return this.regionIds.maxIndex();
    }

    // Returns whether a region id is allocated.
    public boolean regionExists(int regionId) {
        return regionId >= 0 && regionId < this.regions.length && this.regions[regionId] != null;
    }

    // Returns the number of sections of a region.
    public int getSectionCount(int regionId) {
        return this.regions[regionId].count;
    }

    // Returns the key of a region.
    public long getRegionKey(int regionId) {
        return this.regions[regionId].key;
    }

    // Claims a slot for a section and creates its region when needed and rejects a taken slot.
    public int allocateSection(int sectionX, int sectionY, int sectionZ) {
        long key = regionKey(sectionX, sectionY, sectionZ);
        if (!this.regionIdByKey.containsKey(key) && this.regionIdByKey.size() >= this.regions.length) {
            throw new IllegalStateException("No free region ids");
        }
        int regionId = this.regionIdByKey.computeIfAbsent(key, k -> this.regionIds.allocate());

        Region region = this.regions[regionId];

        if (region == null) {
            try {
                region = new Region(regionId, sectionX >> 3, sectionY >> 2, sectionZ >> 3);
            } catch (Throwable failure) {
                // Releases the id of a region that could not be created
                this.regionIdByKey.remove(key);
                this.regionIds.release(regionId);
                throw failure;
            }
            this.regions[regionId] = region;
        }

        int slot = slotWithinRegion(sectionX, sectionY, sectionZ);
        int index = region.count;

        if (region.slotToIndex[slot] != -1 || region.indexToSlot[index] != -1) {
            throw new IllegalStateException("Section slot " + slot + " in region " + regionId + " is already taken");
        }

        region.count++;

        region.slotToIndex[slot] = index;
        region.indexToSlot[index] = slot;
        MemoryUtil.memPutInt(region.sectionTimes + (long) index * 4, currentTimeMs());

        markDirty(region);

        return slot | (regionId << SECTION_ID_SHIFT);
    }

    // Returns the address of the header of a section and marks its region for upload.
    public long beginSectionUpdate(int sectionId) {
        Region region = this.regionOf(sectionId);
        int index = region.slotToIndex[sectionId & 0xFF];

        if (index < 0) {
            throw new IllegalStateException("Section " + sectionId + " is not allocated");
        }

        markDirty(region);

        return region.sectionHeaders + (long) index * SECTION_HEADER_BYTES;
    }

    // Returns the dense index of a section.
    public int getSectionIndex(int sectionId) {
        Region region = this.regionOf(sectionId);
        return region.slotToIndex[sectionId & 0xFF];
    }

    // Removes a section and moves the last section into the gap.
    public void removeSection(int sectionId) {
        int regionId = sectionId >>> SECTION_ID_SHIFT;
        Region region = regionId < this.regions.length ? this.regions[regionId] : null;

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
            int movedSlot = region.indexToSlot[lastIndex];

            MemoryUtil.memCopy(region.sectionHeaders + (long) lastIndex * SECTION_HEADER_BYTES,
                    region.sectionHeaders + (long) index * SECTION_HEADER_BYTES, SECTION_HEADER_BYTES);
            MemoryUtil.memSet(region.sectionHeaders + (long) lastIndex * SECTION_HEADER_BYTES, 0, SECTION_HEADER_BYTES);
            MemoryUtil.memPutInt(region.sectionTimes + (long) index * 4, MemoryUtil.memGetInt(region.sectionTimes + (long) lastIndex * 4));

            region.indexToSlot[lastIndex] = -1;
            region.indexToSlot[index] = movedSlot;
            region.slotToIndex[movedSlot] = index;

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

    // Uploads every region that changed.
    public void commit() {
        while (!this.dirtyRegions.isEmpty()) {
            Region region = this.dirtyRegions.poll();
            region.dirty = false;

            if (region.removed) {
                if (this.regions[region.id] != null) {
                    continue;
                }

                long header = this.uploadStream.upload(this.regionBuffer,
                        (long) region.id * REGION_HEADER_BYTES, REGION_HEADER_BYTES);
                MemoryUtil.memSet(header, -1, REGION_HEADER_BYTES);

                long sections = this.uploadStream.upload(this.sectionBuffer,
                        (long) region.id * REGION_SECTION_BLOCK_BYTES, REGION_SECTION_BLOCK_BYTES);
                MemoryUtil.memSet(sections, 0, REGION_SECTION_BLOCK_BYTES);
                continue;
            }

            try {
                long header = this.uploadStream.upload(this.regionBuffer,
                        (long) region.id * REGION_HEADER_BYTES, REGION_HEADER_BYTES);
                writeRegionHeader(header, region);

                long sections = this.uploadStream.upload(this.sectionBuffer,
                        (long) region.id * REGION_SECTION_BLOCK_BYTES, REGION_SECTION_BLOCK_BYTES);
                MemoryUtil.memCopy(region.sectionHeaders, sections, REGION_SECTION_BLOCK_BYTES);

                long times = this.uploadStream.upload(this.timeBuffer, (long) region.id * REGION_TIME_BLOCK_BYTES, REGION_TIME_BLOCK_BYTES);
                MemoryUtil.memCopy(region.sectionTimes, times, REGION_TIME_BLOCK_BYTES);
            } catch (Throwable failure) {
                this.markDirty(region);
                throw failure;
            }

            this.uploadListener.accept(region.id);
        }
    }

    // Returns whether the bounds of a region are inside the frustum.
    public boolean isRegionVisible(Viewport viewport, int regionId) {
        Region region = this.regions[regionId];

        if (region == null) {
            return false;
        }

        return viewport.isBoxVisible((region.x << 7) + (1 << 6), (region.y << 6) + (1 << 5), (region.z << 7) + (1 << 6),
                1 << 6, 1 << 5, 1 << 6);
    }

    // Returns the approximate distance from the camera to a region.
    public int distanceTo(int regionId, int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        Region region = this.regions[regionId];

        return (Math.abs((region.x << 3) + 4 - cameraSectionX)
                + Math.abs((region.y << 2) + 2 - cameraSectionY)
                + Math.abs((region.z << 3) + 4 - cameraSectionZ)
                + Math.abs((region.x << 3) + 3 - cameraSectionX)
                + Math.abs((region.y << 2) + 1 - cameraSectionY)
                + Math.abs((region.z << 3) + 3 - cameraSectionZ)) >> 1;
    }

    // Returns whether the camera is inside the slab of a region on any axis.
    public boolean isRegionInCameraAxis(int regionId, double cameraX, double cameraY, double cameraZ) {
        Region region = this.regions[regionId];

        return ((region.x << 7) <= cameraX && cameraX <= ((region.x + 1) << 7))
                || ((region.y << 6) <= cameraY && cameraY <= ((region.y + 1) << 6))
                || ((region.z << 7) <= cameraZ && cameraZ <= ((region.z + 1) << 7));
    }

    // Returns whether the center of a region is within a chunk distance on every axis.
    public boolean isWithinChunks(int regionId, int distance, int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        Region region = this.regions[regionId];

        return Math.abs((region.x << 3) + 4 - cameraSectionX) <= distance
                && Math.abs((region.y << 2) + 2 - cameraSectionY) <= distance
                && Math.abs((region.z << 3) + 4 - cameraSectionZ) <= distance;
    }

    // Frees the tables.
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
        this.timeBuffer.delete();
    }

    // Returns the region of a section id and rejects a missing region.
    private Region regionOf(int sectionId) {
        int regionId = sectionId >>> SECTION_ID_SHIFT;
        Region region = regionId < this.regions.length ? this.regions[regionId] : null;
        if (region == null) {
            throw new IllegalStateException("Region of section " + sectionId + " is not allocated");
        }
        return region;
    }

    // Queues a region for upload.
    private void markDirty(Region region) {
        if (region.dirty) {
            return;
        }
        region.dirty = true;
        this.dirtyRegions.add(region);
    }

    // Writes the header of a region.
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

    // Returns the key of the region of a section.
    private static long regionKey(int sectionX, int sectionY, int sectionZ) {
        return PositionUtil.packSection(sectionX >> 3, sectionY >> 2, sectionZ >> 3);
    }

    // Returns the slot of a section inside its region.
    private static int slotWithinRegion(int sectionX, int sectionY, int sectionZ) {
        return ((sectionY & 3) << 6) | ((sectionZ & 7) << 3) | (sectionX & 7);
    }

    // State of one region.
    private static final class Region {
        // Id of the region.
        private final int id;
        // Position of the region.
        private final int x, y, z;
        // Key of the region.
        private final long key;

        // Dense index of each slot.
        private final int[] slotToIndex = new int[SECTIONS_PER_REGION];
        // Slot of each dense index.
        private final int[] indexToSlot = new int[SECTIONS_PER_REGION];
        // Number of sections.
        private int count;

        // Address of the section headers.
        private long sectionHeaders;
        // Address of the placement times.
        private long sectionTimes;

        // Upload queue state of the region.
        private boolean dirty;
        // Removed state of the region.
        private boolean removed;

        // Creates a region with zeroed native blocks and rejects a failed allocation.
        private Region(int id, int x, int y, int z) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.key = PositionUtil.packSection(x, y, z);

            Arrays.fill(this.slotToIndex, -1);
            Arrays.fill(this.indexToSlot, -1);

            long headers = MemoryUtil.nmemCalloc(1, REGION_SECTION_BLOCK_BYTES);
            long times = MemoryUtil.nmemCalloc(1, REGION_TIME_BLOCK_BYTES);
            // Frees a partial allocation and rejects a failed one
            if (headers == 0L || times == 0L) {
                if (headers != 0L) {
                    MemoryUtil.nmemFree(headers);
                }
                if (times != 0L) {
                    MemoryUtil.nmemFree(times);
                }
                throw new OutOfMemoryError("Failed to allocate the native blocks of region " + id);
            }
            this.sectionHeaders = headers;
            this.sectionTimes = times;
        }

        // Frees the native memory once.
        private void free() {
            long headers = this.sectionHeaders;
            long times = this.sectionTimes;
            this.sectionHeaders = 0L;
            this.sectionTimes = 0L;
            if (headers != 0L) {
                MemoryUtil.nmemFree(headers);
            }
            if (times != 0L) {
                MemoryUtil.nmemFree(times);
            }
        }
    }
}
