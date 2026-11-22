package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.SegmentedAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

// Stores the geometry and the header of each section.
public class MeshSectionStore {
    // Header bit set when the translucent draw order precedes the vertices.
    private static final int HAS_TRANSLUCENT_ORDER = 1 << 26;
    // Logger of the backend.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // Table of the regions.
    private final MeshRegionStore regions;
    // Arena of the quads.
    private final QuadArena arena;
    // Stream that copies the geometry.
    private final UploadStream uploadStream;

    // Table id of each section.
    private final Long2IntOpenHashMap sectionIds = new Long2IntOpenHashMap();
    // First quad of each section.
    private final Long2IntOpenHashMap sectionQuads = new Long2IntOpenHashMap();
    // Number of translucent quads with a draw order in the arena.
    private final Long2IntOpenHashMap translucentCounts = new Long2IntOpenHashMap();

    // Creates the store and rejects null parts.
    public MeshSectionStore(MeshRegionStore regions, QuadArena arena, UploadStream uploadStream) {
        this.regions = java.util.Objects.requireNonNull(regions, "regions");
        this.arena = java.util.Objects.requireNonNull(arena, "arena");
        this.uploadStream = java.util.Objects.requireNonNull(uploadStream, "uploadStream");
        this.sectionIds.defaultReturnValue(-1);
        this.sectionQuads.defaultReturnValue(-1);
    }

    // Returns the region table.
    public MeshRegionStore getRegions() {
        return this.regions;
    }

    // Returns the quad arena.
    public QuadArena getArena() {
        return this.arena;
    }

    // Uploads the geometry and header of a section and removes the section for null geometry and rejects geometry larger than its allocation.
    public void upload(int sectionX, int sectionY, int sectionZ, SectionGeometry geometry) {
        this.upload(sectionX, sectionY, sectionZ, geometry, null, null);
    }

    public void upload(int sectionX, int sectionY, int sectionZ, @Nullable SectionGeometry geometry,
                       @Nullable ChunkPrimitiveType primitiveType, @Nullable NativeBuffer indices) {
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);

        if (geometry == null || geometry.quadCount() == 0) {
            this.remove(sectionX, sectionY, sectionZ);
            return;
        }

        if (geometry.geometry().getLength() > (long) geometry.quadCount() * this.arena.bytesPerQuad()) {
            throw new IllegalArgumentException("Geometry of " + geometry.quadCount() + " quads has " + geometry.geometry().getLength() + " bytes which exceeds its allocation");
        }

        int translucentCount = indices == null ? 0 : Short.toUnsignedInt(geometry.baseQuad());
        int orderBytes = translucentCount * Short.BYTES;
        int prefixQuads = Math.ceilDiv(orderBytes, this.arena.bytesPerQuad());
        int prefixBytes = prefixQuads * this.arena.bytesPerQuad();
        int allocationQuads = geometry.quadCount() + prefixQuads;
        int quadAddress = this.sectionQuads.get(key);

        if (quadAddress != -1 && !this.arena.canReuse(quadAddress, allocationQuads)) {
            this.sectionQuads.remove(key);
            this.arena.free(quadAddress);
            quadAddress = -1;
        }

        if (quadAddress == -1) {
            quadAddress = this.arena.alloc(allocationQuads);

            if (quadAddress == (int) SegmentedAllocator.OUT_OF_SPACE) {
                LOGGER.error("Terrain arena is full ({} MB resident); dropping section {} {} {}",
                        this.arena.getResidentBytes() >> 20, sectionX, sectionY, sectionZ);
                this.remove(sectionX, sectionY, sectionZ);
                return;
            }
        }

        this.sectionQuads.put(key, quadAddress);

        if (translucentCount > 0) {
            this.translucentCounts.put(key, translucentCount);
            this.uploadOrder(quadAddress, translucentCount, primitiveType, indices);
        } else {
            this.translucentCounts.remove(key);
        }

        long staging = this.arena.beginUpload(this.uploadStream, quadAddress, prefixBytes, geometry.geometry().getLength());
        MemoryUtil.memCopy(MemoryUtil.memAddress(geometry.geometry().getDirectBuffer()), staging, geometry.geometry().getLength());

        int sectionId = this.sectionIds.get(key);

        if (sectionId == -1) {
            sectionId = this.regions.allocateSection(sectionX, sectionY, sectionZ);
            this.sectionIds.put(key, sectionId);
        }

        writeHeader(sectionId, sectionX, sectionY, sectionZ, quadAddress + prefixQuads, geometry, translucentCount != 0);
    }

    public void uploadTranslucentOrder(int sectionX, int sectionY, int sectionZ, ChunkPrimitiveType primitiveType, NativeBuffer indices) {
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);
        int count = this.translucentCounts.get(key);
        if (count != 0) {
            this.uploadOrder(this.sectionQuads.get(key), count, primitiveType, indices);
        }
    }

    // Stores one unsigned short per quad immediately before the geometry.
    private void uploadOrder(int quadAddress, int count, ChunkPrimitiveType primitiveType, NativeBuffer indices) {
        int indexBytes = primitiveType.getIndexBufferSize(1);
        if (indices.getLength() < indexBytes * count || indices.getLength() % indexBytes != 0) {
            throw new IllegalArgumentException("Index buffer does not match the resident translucent geometry");
        }
        int orderBytes = count * Short.BYTES;
        int prefixBytes = Math.ceilDiv(orderBytes, this.arena.bytesPerQuad()) * this.arena.bytesPerQuad();
        long destination = this.arena.beginUpload(this.uploadStream, quadAddress, prefixBytes - orderBytes, orderBytes);
        long source = MemoryUtil.memAddress(indices.getDirectBuffer());
        int written = 0;
        for (int offset = 0; offset < indices.getLength(); offset += indexBytes) {
            int quad = MemoryUtil.memGetInt(source + offset) / primitiveType.getVerticesPerPrimitive();
            // The mesh header can count only the first 65,535 source quads.
            if (quad >= 0 && quad < count && written < count) {
                MemoryUtil.memPutShort(destination + (long) written++ * Short.BYTES, (short) quad);
            }
        }
        if (written != count) {
            throw new IllegalStateException("Translucent draw order contains " + written + " of " + count + " quads");
        }
    }

    // Frees the geometry of a section and clears its table entry.
    public void remove(int sectionX, int sectionY, int sectionZ) {
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);

        int sectionId = this.sectionIds.remove(key);
        int quadAddress = this.sectionQuads.remove(key);
        this.translucentCounts.remove(key);

        if (quadAddress != -1) {
            this.arena.free(quadAddress);
        }

        if (sectionId != -1) {
            this.regions.removeSection(sectionId);
        }
    }

    // Removes every section of a region.
    public void removeRegion(int regionId) {
        if (!this.regions.regionExists(regionId)) {
            return;
        }

        long key = this.regions.getRegionKey(regionId);
        int baseX = PositionUtil.unpackSectionX(key) << 3;
        int baseY = PositionUtil.unpackSectionY(key) << 2;
        int baseZ = PositionUtil.unpackSectionZ(key) << 3;

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 8; z++) {
                    this.remove(baseX + x, baseY + y, baseZ + z);
                }
            }
        }
    }

    // Uploads the changed table entries.
    public void commit() {
        this.regions.commit();
    }

    // Writes the header of a section into the region table.
    private void writeHeader(int sectionId, int sectionX, int sectionY, int sectionZ, int quadAddress,
                             SectionGeometry geometry, boolean hasTranslucentOrder) {
        long ptr = this.regions.beginSectionUpdate(sectionId);
        int sectionIndex = this.regions.getSectionIndex(sectionId);

        MemoryUtil.memPutInt(ptr, (sectionX << 8) | (geometry.sizeX() << 4) | geometry.minX());
        MemoryUtil.memPutInt(ptr + 4, ((sectionY & 0x1FF) << 8) | (geometry.sizeY() << 4) | geometry.minY()
                | (sectionIndex << 18) | (hasTranslucentOrder ? HAS_TRANSLUCENT_ORDER : 0));
        MemoryUtil.memPutInt(ptr + 8, (sectionZ << 8) | (geometry.sizeZ() << 4) | geometry.minZ());
        MemoryUtil.memPutInt(ptr + 12, quadAddress);

        short[] counts = geometry.quadsPerFacing();
        long ranges = ptr + 16;

        for (int i = 0; i < 3; i++) {
            int low = Short.toUnsignedInt(counts[i * 2]);
            int high = Short.toUnsignedInt(counts[i * 2 + 1]);
            MemoryUtil.memPutInt(ranges + i * 4L, low | (high << 16));
        }

        int unassigned = Short.toUnsignedInt(counts[ModelQuadFacing.UNASSIGNED.ordinal()]);
        MemoryUtil.memPutInt(ranges + 12L, unassigned | (Short.toUnsignedInt(geometry.baseQuad()) << 16));
    }
}
