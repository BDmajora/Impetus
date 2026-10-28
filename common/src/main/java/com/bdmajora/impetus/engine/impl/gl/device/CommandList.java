package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;

import java.nio.ByteBuffer;

// Commands that create and bind and fill and map and delete GL objects of the render device
public interface CommandList extends AutoCloseable {
    // Creates a mutable buffer with a new name and no storage
    GlMutableBuffer createMutableBuffer();

    // Creates an immutable buffer with fixed storage of the given size in bytes and the given flags
    GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags);

    // Binds the vertex array unless it is already bound
    void bindVertexArray(GlVertexArray array);

    // Replaces the storage of the buffer with the remaining bytes of the data and records the new size
    void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage);

    // Replaces the storage of the buffer with the given number of bytes at the native address and records the new size
    void uploadData(GlMutableBuffer glBuffer, long ptr, long bytes, GlBufferUsage usage);

    // Copies the given number of bytes from the read offset of the source to the write offset of the destination
    void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes);

    // Binds the buffer to the target unless it is already bound there and unbinds the target when given null
    void bindBuffer(GlBufferTarget target, GlBuffer buffer);

    // Binds vertex array zero unless it is already bound
    void unbindVertexArray();

    // Replaces the storage of the buffer with uninitialised storage of the given size in bytes and records the new size
    void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage);

    // Unmaps the buffer when it is mapped and forgets its bindings and deletes it
    void deleteBuffer(GlBuffer buffer);

    // Forgets the binding of the vertex array and deletes it
    void deleteVertexArray(GlVertexArray vertexArray);

    // Issues every command that is still pending
    void flush();

    // Binds the tessellation and returns the draw command list that draws from it
    DrawCommandList beginTessellating(GlTessellation tessellation);

    // Deletes the GL objects behind the tessellation
    void deleteTessellation(GlTessellation tessellation);

    // Flushes the command list and leaves it open for further commands
    @Override
    default void close() {
        this.flush();
    }

    // Maps the byte range of the buffer with the given flags and records the mapping on the buffer
    GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags);

    // Unmaps the buffer behind the mapping and disposes the mapping
    void unmap(GlBufferMapping map);

    // Flushes the byte range at the offset within an explicitly flushed mapping
    void flushMappedRange(GlBufferMapping map, int offset, int length);

    // Creates a fence that signals once the GPU completes every command issued before it
    GlFence createFence();
}
