package com.bdmajora.impetus.umbra.vertices;

// Attribute locations of pack terrain.
public final class UmbraVertexAttributes {
    // Name of the block id attribute.
    public static final String MC_ENTITY = "mc_Entity";
    // Location of the block id attribute.
    public static final int MC_ENTITY_SLOT = 10;

    // Name of the texture center attribute.
    public static final String MC_MID_TEX_COORD = "mc_midTexCoord";
    // Location of the texture center attribute.
    public static final int MC_MID_TEX_COORD_SLOT = 11;

    // Name of the tangent attribute.
    public static final String AT_TANGENT = "at_tangent";
    // Location of the tangent attribute.
    public static final int AT_TANGENT_SLOT = 12;

    // Prevents instantiation
    private UmbraVertexAttributes() {
    }
}
