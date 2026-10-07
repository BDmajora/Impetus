package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawElementsBaseVertex(DirectMultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType);

    // Commands are read from the buffer bound to GL_DRAW_INDIRECT_BUFFER, starting offset bytes in
    void multiDrawElementsIndirect(long offset, int count, GlPrimitiveType primitiveType, GlIndexType indexType);

    void endTessellating();

    void flush();

    // Ends tessellation, so try-with-resources always unbinds
    @Override
    default void close() {
        this.flush();
    }
}
