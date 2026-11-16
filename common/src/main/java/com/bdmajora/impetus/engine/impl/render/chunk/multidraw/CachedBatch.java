package com.bdmajora.impetus.engine.impl.render.chunk.multidraw;

import com.bdmajora.impetus.engine.impl.gl.device.MultiDrawBatch;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// Draw batch that stays valid while the visible sections and the camera cell stay the same.
public class CachedBatch {
    // Cached batch or null.
    private final @Nullable MultiDrawBatch batch;

    // Cached tessellation or null.
    private final @Nullable GlTessellation tessellation;

    // Sections the batch was built for.
    private final byte[] sections;

    // Camera cell on the x axis.
    private final int cameraMinX, cameraMaxX;
    // Camera cell on the y axis.
    private final int cameraMinY, cameraMaxY;
    // Camera cell on the z axis.
    private final int cameraMinZ, cameraMaxZ;

    // Creates a cached batch and rejects a section count outside the array.
    public CachedBatch(@Nullable MultiDrawBatch batch,
                       @Nullable GlTessellation tessellation,
                       byte[] sections, int sectionCount,
                       int cameraMinX, int cameraMaxX,
                       int cameraMinY, int cameraMaxY,
                       int cameraMinZ, int cameraMaxZ) {
        java.util.Objects.checkFromIndexSize(0, sectionCount, sections.length);
        this.batch = batch;
        this.tessellation = tessellation;
        this.sections = Arrays.copyOf(sections, sectionCount);
        this.cameraMinX = cameraMinX;
        this.cameraMaxX = cameraMaxX;
        this.cameraMinY = cameraMinY;
        this.cameraMaxY = cameraMaxY;
        this.cameraMinZ = cameraMinZ;
        this.cameraMaxZ = cameraMaxZ;
    }

    // Returns whether the batch fits the sections and the camera position.
    public boolean isValidFor(byte[] sections, int sectionCount, int cameraX, int cameraY, int cameraZ) {
        return cameraX >= this.cameraMinX && cameraX < this.cameraMaxX
                && cameraY >= this.cameraMinY && cameraY < this.cameraMaxY
                && cameraZ >= this.cameraMinZ && cameraZ < this.cameraMaxZ
                && sectionCount == this.sections.length
                && Arrays.equals(this.sections, 0, sectionCount, sections, 0, sectionCount);
    }

    // Returns the batch or null.
    public @Nullable MultiDrawBatch getBatch() {
        return this.batch;
    }

    // Returns the tessellation or null.
    public @Nullable GlTessellation getTessellation() {
        return this.tessellation;
    }

    // Deletes the batch.
    public void delete() {
        if (this.batch != null) {
            this.batch.delete();
        }
    }
}
