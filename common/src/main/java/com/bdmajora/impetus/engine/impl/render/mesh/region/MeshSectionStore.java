package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.SegmentedAllocator;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Where a finished build lands: geometry into the quad arena, a 32-byte header into the region store so the section rasteriser decides visibility from one cache line
public class MeshSectionStore {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    private final MeshRegionStore regions;
    private final QuadArena arena;
    private final UploadStream uploadStream;

    // Section key -> region store id, and -> first quad of its geometry; two maps rather than one object because both are hit per build result and neither wants a pointer chase
    private final Long2IntOpenHashMap sectionIds = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap sectionQuads = new Long2IntOpenHashMap();

    public MeshSectionStore(MeshRegionStore regions, QuadArena arena, UploadStream uploadStream) {
        this.regions = regions;
        this.arena = arena;
        this.uploadStream = uploadStream;
        this.sectionIds.defaultReturnValue(-1);
        this.sectionQuads.defaultReturnValue(-1);
    }

    // The region table
    public MeshRegionStore getRegions() {
        return this.regions;
    }

    // Where section geometry lives
    public QuadArena getArena() {
        return this.arena;
    }

    // Uploads one section's geometry and metadata; null geometry means the section built to nothing and should be dropped
    public void upload(int sectionX, int sectionY, int sectionZ, SectionGeometry geometry) {
        long key = PositionUtil.packSection(sectionX, sectionY, sectionZ);

        if (geometry == null || geometry.quadCount() == 0) {
            this.remove(sectionX, sectionY, sectionZ);
            return;
        }

        int quadAddress = this.sectionQuads.get(key);

        // A rebuild with exactly as many quads as before keeps its allocation (the common block-change case), saving an arena round trip and a sparse page recommit
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
        LWJGL.memCopy(LWJGL.memAddress(geometry.geometry().getDirectBuffer()), staging, geometry.geometry().getLength());

        int sectionId = this.sectionIds.get(key);

        if (sectionId == -1) {
            sectionId = this.regions.allocateSection(sectionX, sectionY, sectionZ);
            this.sectionIds.put(key, sectionId);
        }

        writeHeader(sectionId, sectionX, sectionY, sectionZ, quadAddress, geometry);
    }

    // Frees a section's geometry and clears its table entry
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

    // Drops every section in a region, when it is evicted for range or the arena needs its memory back
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

    // Uploads every dirty table entry
    public void commit() {
        this.regions.commit();
    }

    private void writeHeader(int sectionId, int sectionX, int sectionY, int sectionZ, int quadAddress,
                             SectionGeometry geometry) {
        long ptr = this.regions.beginSectionUpdate(sectionId);
        int sectionIndex = this.regions.getSectionIndex(sectionId);

        // Chunk Y is masked to 9 bits and sign-extended in the shader, covering every build height the game has had while leaving the top bits for the section index
        LWJGL.memPutInt(ptr, (sectionX << 8) | (geometry.sizeX() << 4) | geometry.minX());
        LWJGL.memPutInt(ptr + 4, ((sectionY & 0x1FF) << 8) | (geometry.sizeY() << 4) | geometry.minY()
                | (sectionIndex << 18));
        LWJGL.memPutInt(ptr + 8, (sectionZ << 8) | (geometry.sizeZ() << 4) | geometry.minZ());
        LWJGL.memPutInt(ptr + 12, quadAddress);

        // Eight uint16s: six directional quad counts, the unassigned count, then the base quad; the task shader accumulates them into absolute offsets so a section can exceed a 16-bit offset
        short[] counts = geometry.quadsPerFacing();
        long ranges = ptr + 16;

        for (int i = 0; i < 3; i++) {
            int low = Short.toUnsignedInt(counts[i * 2]);
            int high = Short.toUnsignedInt(counts[i * 2 + 1]);
            LWJGL.memPutInt(ranges + i * 4L, low | (high << 16));
        }

        int unassigned = Short.toUnsignedInt(counts[ModelQuadFacing.UNASSIGNED.ordinal()]);
        LWJGL.memPutInt(ranges + 12L, unassigned | (Short.toUnsignedInt(geometry.baseQuad()) << 16));
    }
}
