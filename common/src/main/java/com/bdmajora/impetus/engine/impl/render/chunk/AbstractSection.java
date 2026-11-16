package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;

// Position of a 16 block section.
public abstract class AbstractSection {
    // Coordinates of the section.
    private final int chunkX, chunkY, chunkZ;

    // Index of the section inside its region.
    private final int sectionIndex;

    // Creates a section at coordinates.
    public AbstractSection(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        int rX = this.getChunkX() & (RenderRegion.REGION_WIDTH - 1);
        int rY = this.getChunkY() & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = this.getChunkZ() & (RenderRegion.REGION_LENGTH - 1);

        // Packs the position inside the region
        this.sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);
    }

    // Returns the first block x.
    public final int getOriginX() {
        return this.chunkX << 4;
    }

    // Returns the first block y.
    public final int getOriginY() {
        return this.chunkY << 4;
    }

    // Returns the first block z.
    public final int getOriginZ() {
        return this.chunkZ << 4;
    }

    // Returns the center block x.
    public final int getCenterX() {
        return this.getOriginX() + 8;
    }

    // Returns the center block y.
    public final int getCenterY() {
        return this.getOriginY() + 8;
    }

    // Returns the center block z.
    public final int getCenterZ() {
        return this.getOriginZ() + 8;
    }

    // Returns the section x.
    public final int getChunkX() {
        return this.chunkX;
    }

    // Returns the section y.
    public final int getChunkY() {
        return this.chunkY;
    }

    // Returns the section z.
    public final int getChunkZ() {
        return this.chunkZ;
    }

    // Returns the squared distance from the section center to a point.
    public final float getSquaredDistance(float x, float y, float z) {
        float xDist = x - this.getCenterX();
        float yDist = y - this.getCenterY();
        float zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    // Returns whether another section shares a coordinate.
    public final boolean isAlignedWithSectionOnGrid(int otherX, int otherY, int otherZ) {
        return this.chunkX == otherX || this.chunkY == otherY || this.chunkZ == otherZ;
    }

    // Returns the index inside the region.
    public final int getSectionIndex() {
        return this.sectionIndex;
    }

    // Returns the squared distance from the section center to the center of a block.
    public final float getSquaredDistanceFromBlockCenter(int x, int y, int z) {
        return this.getSquaredDistance(x + 0.5f, y + 0.5f, z + 0.5f);
    }

    // Returns the packed section coordinates.
    public final long positionAsLong() {
        return PositionUtil.packSection(this.chunkX, this.chunkY, this.chunkZ);
    }

    // Returns the position and block range as text.
    @Override
    public String toString() {
        return String.format("%s at chunk (%d, %d, %d) from (%d, %d, %d) to (%d, %d, %d)",
                this.getClass().getSimpleName(),
                this.chunkX, this.chunkY, this.chunkZ,
                this.getOriginX(), this.getOriginY(), this.getOriginZ(),
                this.getOriginX() + 15, this.getOriginY() + 15, this.getOriginZ() + 15);
    }
}
