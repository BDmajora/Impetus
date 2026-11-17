package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteIterator;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteArrayIterator;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

// Visible sections of one region grouped by what they contain.
public class ChunkRenderList {
    // Region of the sections.
    private final RenderRegion region;

    // Sections with geometry and their count.
    private final byte[] sectionsWithGeometry = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithGeometryCount = 0;

    // Sections with sprites and their count.
    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    // Sections with block entities and their count.
    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    // Sections that need sorting and their count.
    private final byte[] sectionsNeedingDynamicSort = new byte[RenderRegion.REGION_SIZE];
    private int sectionsNeedingDynamicSortCount = 0;

    // Number of sections listed.
    private int size;

    // Creates a list for a region and rejects null.
    public ChunkRenderList(RenderRegion region) {
        this.region = java.util.Objects.requireNonNull(region, "region");
    }

    // Adds a section with its content flags and rejects an index outside a byte or a full list.
    public void add(int index, int flags) {
        if ((index & ~0xFF) != 0) {
            throw new IllegalArgumentException("Section index out of range " + index);
        }

        if (this.size >= RenderRegion.REGION_SIZE) {
            throw new ArrayIndexOutOfBoundsException("Render list is full");
        }

        this.size++;

        this.sectionsWithGeometry[this.sectionsWithGeometryCount] = (byte) index;
        this.sectionsWithGeometryCount += (flags >>> RenderVisualsService.HAS_BLOCK_GEOMETRY) & 1;

        this.sectionsWithSprites[this.sectionsWithSpritesCount] = (byte) index;
        this.sectionsWithSpritesCount += (flags >>> RenderVisualsService.HAS_SPRITES) & 1;

        this.sectionsWithEntities[this.sectionsWithEntitiesCount] = (byte) index;
        this.sectionsWithEntitiesCount += (flags >>> RenderVisualsService.HAS_BLOCK_ENTITIES) & 1;

        this.sectionsNeedingDynamicSort[this.sectionsNeedingDynamicSortCount] = (byte) index;
        this.sectionsNeedingDynamicSortCount += (flags >>> RenderVisualsService.NEEDS_DYNAMIC_SORT) & 1;
    }

    // Returns an iterator over the sections that need sorting or null.
    public @Nullable ByteIterator sectionsNeedingDynamicSortIterator() {
        if (this.sectionsNeedingDynamicSortCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsNeedingDynamicSort, this.sectionsNeedingDynamicSortCount);
    }

    // Returns an iterator over the sections with geometry or null.
    public @Nullable ByteIterator sectionsWithGeometryIterator() {
        if (this.sectionsWithGeometryCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithGeometry, this.sectionsWithGeometryCount);
    }

    // Returns an iterator over the sections with sprites or null.
    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    // Returns an iterator over the sections with block entities or null.
    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    // Returns the array of the section indices with geometry where only the counted entries are valid and the array must not be changed.
    public byte[] getSectionsWithGeometry() {
        return this.sectionsWithGeometry;
    }

    // Returns the number of sections with geometry.
    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    // Returns the number of sections with sprites.
    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    // Returns the number of sections with block entities.
    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    // Returns the region of the sections.
    public RenderRegion getRegion() {
        return this.region;
    }

    // Returns the number of sections listed.
    public int size() {
        return this.size;
    }

    // Returns the chunk coordinates of the sections with geometry as text.
    @Override
    public String toString() {
        var iterator = this.sectionsWithGeometryIterator();
        if (iterator == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int originX = this.region.getChunkX();
        int originY = this.region.getChunkY();
        int originZ = this.region.getChunkZ();
        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();
            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);
            sb.append("(").append(chunkX).append(", ").append(chunkY).append(", ").append(chunkZ).append(")");
            if (iterator.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
