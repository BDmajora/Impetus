package com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters;

// Bit layout of the parameters of a material.
public class MaterialParameters {
    // Bit offset of the mipmap flag.
    public static final int OFFSET_USE_MIP = 0;
    // Bit offset of the alpha cutoff.
    public static final int OFFSET_ALPHA_CUTOFF = 1;

    // Returns the cutoff and the mipmap flag as material bits.
    public static int pack(AlphaCutoffParameter alphaCutoff, boolean useMipmaps) {
        return (((useMipmaps ? 1 : 0) << OFFSET_USE_MIP) |
                ((alphaCutoff.ordinal()) << OFFSET_ALPHA_CUTOFF));
    }
}
