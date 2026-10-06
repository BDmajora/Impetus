package com.bdmajora.impetus.engine.impl.gl.tessellation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;


// The primitive topologies the terrain renderer draws with, each carrying its GL enum
public enum GlPrimitiveType {
    POINTS(GL11.GL_POINTS),

    LINES(GL11.GL_LINES),
    LINE_STRIP(GL11.GL_LINE_STRIP),
    LINE_LOOP(GL11.GL_LINE_LOOP),

    TRIANGLES(GL11.GL_TRIANGLES),
    TRIANGLE_STRIP(GL11.GL_TRIANGLE_STRIP),
    TRIANGLE_FAN(GL11.GL_TRIANGLE_FAN),

    LINES_ADJACENCY(GL32.GL_LINES_ADJACENCY),
    LINE_STRIP_ADJACENCY(GL32.GL_LINE_STRIP_ADJACENCY),
    TRIANGLES_ADJACENCY(GL32.GL_TRIANGLES_ADJACENCY),
    TRIANGLE_STRIP_ADJACENCY(GL32.GL_TRIANGLE_STRIP_ADJACENCY),

    PATCHES(GL40.GL_PATCHES),

    QUADS(GL30.GL_QUADS);

    private final int id;

    GlPrimitiveType(int id) {
        this.id = id;
    }

    // GL mode constant
    public int getId() {
        return this.id;
    }
}
