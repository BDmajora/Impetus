package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;

// Buffer binding points with the GL enum of each target and of its binding query
public enum GlBufferTarget {
    // Binding point of vertex attribute data
    ARRAY_BUFFER(GL15.GL_ARRAY_BUFFER, GL15.GL_ARRAY_BUFFER_BINDING),
    // Binding point of vertex index data
    ELEMENT_BUFFER(GL15.GL_ELEMENT_ARRAY_BUFFER, GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
    // Binding point of the copy source whose binding query reuses the target enum
    COPY_READ_BUFFER(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_READ_BUFFER),
    // Binding point of the copy destination whose binding query reuses the target enum
    COPY_WRITE_BUFFER(GL31.GL_COPY_WRITE_BUFFER, GL31.GL_COPY_WRITE_BUFFER),
    // Binding point of indirect draw commands
    DRAW_INDIRECT_BUFFER(GL40.GL_DRAW_INDIRECT_BUFFER, GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),
    // Binding point of pixel data that GL writes into a buffer
    PIXEL_PACK_BUFFER(GL21.GL_PIXEL_PACK_BUFFER, GL21.GL_PIXEL_PACK_BUFFER_BINDING),
    // Binding point of pixel data that GL reads from a buffer
    PIXEL_UNPACK_BUFFER(GL21.GL_PIXEL_UNPACK_BUFFER, GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);

    // Single shared array of every target in declaration order
    public static final GlBufferTarget[] VALUES = values();
    // Number of targets
    public static final int COUNT = VALUES.length;

    // GL enum of the binding point
    private final int target;
    // GL enum that queries the buffer bound to the binding point
    private final int binding;

    // Stores the GL enum of the binding point and the GL enum of its binding query
    GlBufferTarget(int target, int binding) {
        this.target = target;
        this.binding = binding;
    }

    // Returns the GL enum of the binding point
    public int getTargetParameter() {
        return this.target;
    }

    // Returns the GL enum that queries the buffer bound to the binding point
    public int getBindingParameter() {
        return this.binding;
    }
}
