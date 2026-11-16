package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

// Data a finished build produced for one section.
public class BuiltRenderSectionData {
    // Whether the section has block geometry.
    public boolean hasBlockGeometry;
    // Visibility between the faces of the section.
    public long visibilityData;

    // Boxes that occlude other sections or null.
    public int @Nullable [] occluderBoxes;

    // Returns the bit mask of the visual contents of the section.
    public int getVisualBitmaskForSection() {
        return this.hasBlockGeometry ? (1 << RenderVisualsService.HAS_BLOCK_GEOMETRY) : 0;
    }

    // Makes the collections of the data unmodifiable.
    @MustBeInvokedByOverriders
    public void bake() {

    }

    // Compares the contents.
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BuiltRenderSectionData that = (BuiltRenderSectionData) o;
        return hasBlockGeometry == that.hasBlockGeometry && visibilityData == that.visibilityData
                && Arrays.equals(occluderBoxes, that.occluderBoxes);
    }

    // Combines the contents.
    @Override
    public int hashCode() {
        return (Objects.hash(hasBlockGeometry, visibilityData) * 31) + Arrays.hashCode(occluderBoxes);
    }
}
