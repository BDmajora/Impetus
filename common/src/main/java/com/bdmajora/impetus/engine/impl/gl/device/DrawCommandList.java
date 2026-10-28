package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;

// Draw commands issued against the tessellation that the command list binds
public interface DrawCommandList extends AutoCloseable {
    // Draws every command of the batch with the primitive type and the index type
    void multiDrawElementsBaseVertex(DirectMultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType);

    // Draws the given number of commands read from the buffer bound to GL_DRAW_INDIRECT_BUFFER starting at the offset in bytes
    void multiDrawElementsIndirect(long offset, int count, GlPrimitiveType primitiveType, GlIndexType indexType);

    // Unbinds the tessellation in progress and ends it
    void endTessellating();

    // Issues every pending draw command and ends the tessellation in progress when one is bound
    void flush();

    // Flushes the draw command list and ends the tessellation in progress
    @Override
    default void close() {
        this.flush();
    }
}
