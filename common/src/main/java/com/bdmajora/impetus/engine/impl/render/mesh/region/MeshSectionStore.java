package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.SegmentedAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

// Stores the geometry and the header of each section.
public class MeshSectionStore {
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
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);

        if (geometry == null || geometry.quadCount() == 0) {
            this.remove(sectionX, sectionY, sectionZ);
            return;
        }

        if (geometry.geometry().getLength() > (long) geometry.quadCount() * this.arena.bytesPerQuad()) {
            throw new IllegalArgumentException("Geometry of " + geometry.quadCount() + " quads has " + geometry.geometry().getLength() + " bytes which exceeds its allocation");
        }

        int quadAddress = this.sectionQuads.get(key);

        if (quadAddress != -1 && !this.arena.canReuse(quadAddress, geometry.quadCount())) {
            this.sectionQuads.remove(key);
            this.arena.free(quadAddress);
            quadAddress = -1;
        }

        if (quadAddress == -1) {
            quadAddress = this.arena.alloc(geometry.quadCount());

            if (quadAddress == (int) SegmentedAllocator.OUT_OF_SPACE) {
                LOGGER.error("Terrain arena is full ({} MB resident); dropping section {} {} {}",
                        this.arena.getResidentBytes() >> 20, sectionX, sectionY, sectionZ);
                this.remove(sectionX, sectionY, sectionZ);
                return;
            }
        }

        this.sectionQuads.put(key, quadAddress);

        long staging = this.arena.beginUpload(this.uploadStream, quadAddress);
        MemoryUtil.memCopy(MemoryUtil.memAddress(geometry.geometry().getDirectBuffer()), staging, geometry.geometry().getLength());

        int sectionId = this.sectionIds.get(key);

        if (sectionId == -1) {
            sectionId = this.regions.allocateSection(sectionX, sectionY, sectionZ);
            this.sectionIds.put(key, sectionId);
        }

        writeHeader(sectionId, sectionX, sectionY, sectionZ, quadAddress, geometry);
    }

    // Frees the geometry of a section and clears its table entry.
    public void remove(int sectionX, int sectionY, int sectionZ) {
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);

        int sectionId = this.sectionIds.remove(key);
        int quadAddress = this.sectionQuads.remove(key);

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
                             SectionGeometry geometry) {
        long ptr = this.regions.beginSectionUpdate(sectionId);
        int sectionIndex = this.regions.getSectionIndex(sectionId);

        MemoryUtil.memPutInt(ptr, (sectionX << 8) | (geometry.sizeX() << 4) | geometry.minX());
        MemoryUtil.memPutInt(ptr + 4, ((sectionY & 0x1FF) << 8) | (geometry.sizeY() << 4) | geometry.minY()
                | (sectionIndex << 18));
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
