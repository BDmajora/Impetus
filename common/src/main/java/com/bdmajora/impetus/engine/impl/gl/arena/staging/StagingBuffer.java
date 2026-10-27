package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.nio.ByteBuffer;

// Staging path that copies native bytes into GPU buffers
public interface StagingBuffer {
    // Stages the remaining bytes of the direct data for a copy into the destination at the write offset
    void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset);

    // Issues every staged copy that is still pending
    void flush(CommandList commandList);

    // Frees every resource on the first call and ignores later calls
    void delete(CommandList commandList);

    // Reclaims the staging space of copies the GPU has completed
    void flip();
}
