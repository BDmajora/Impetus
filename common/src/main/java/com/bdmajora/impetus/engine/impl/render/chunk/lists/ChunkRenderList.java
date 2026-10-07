package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.LocalSectionIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteIterator;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteArrayIterator;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;

public class ChunkRenderList {
    private final RenderRegion region;

    private final byte[] sectionsWithGeometry = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithGeometryCount = 0;

    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    private final byte[] sectionsNeedingDynamicSort = new byte[RenderRegion.REGION_SIZE];
    private int sectionsNeedingDynamicSortCount = 0;

    private int size;

    public ChunkRenderList(RenderRegion region) {
        this.region = region;
    }

    public void add(int index, int flags) {
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

    public @Nullable ByteIterator sectionsNeedingDynamicSortIterator() {
        if (this.sectionsNeedingDynamicSortCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsNeedingDynamicSort, this.sectionsNeedingDynamicSortCount);
    }

    public @Nullable ByteIterator sectionsWithGeometryIterator() {
        if (this.sectionsWithGeometryCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithGeometry, this.sectionsWithGeometryCount);
    }

    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    /**
     * {@return the backing array of local section indices which have block geometry}
     * <p>
     * Only the first {@link #getSectionsWithGeometryCount()} entries are meaningful, and each index is stored narrowed
     * to a byte, so mask with {@code 0xFF} before use. Exposed so that draw-command assembly can walk the list without
     * allocating a {@link ByteIterator} or paying a virtual call per section. Callers must not mutate it.
     */
    public byte[] getSectionsWithGeometry() {
        return this.sectionsWithGeometry;
    }

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    // For sprite animation
    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    // For the block entity pass
    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    // The region every listed section belongs to
    public RenderRegion getRegion() {
        return this.region;
    }

    // Sections listed
    public int size() {
        return this.size;
    }

    // For debugging
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
