package com.bdmajora.extras.client.culling;

import net.minecraft.util.math.AxisAlignedBB;

// Block entity that remembers its occlusion test box.
public interface OcclusionBoxHolder {
    // Returns the cached test box or null before the first measurement.
    AxisAlignedBB impetus$occlusionBox();

    // Stores the cached test box.
    void impetus$setOcclusionBox(AxisAlignedBB box);
}
