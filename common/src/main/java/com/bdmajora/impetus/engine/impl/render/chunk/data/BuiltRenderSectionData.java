package com.bdmajora.impetus.engine.impl.render.chunk.data;

import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderVisualsService;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

// What a finished build produced for one render section; extended per game version so a version-specific renderer can hang its own data off it
public class BuiltRenderSectionData {
    public boolean hasBlockGeometry;
    public long visibilityData;

    public int @Nullable [] occluderBoxes;

    public int getVisualBitmaskForSection() {
        return this.hasBlockGeometry ? (1 << RenderVisualsService.HAS_BLOCK_GEOMETRY) : 0;
    }

    // Freezes collections after building
    @MustBeInvokedByOverriders
    public void bake() {

    }

    // By contents
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BuiltRenderSectionData that = (BuiltRenderSectionData) o;
        return hasBlockGeometry == that.hasBlockGeometry && visibilityData == that.visibilityData
                && Arrays.equals(occluderBoxes, that.occluderBoxes);
    }

    // By contents
    @Override
    public int hashCode() {
        return (Objects.hash(hasBlockGeometry, visibilityData) * 31) + Arrays.hashCode(occluderBoxes);
    }
}
