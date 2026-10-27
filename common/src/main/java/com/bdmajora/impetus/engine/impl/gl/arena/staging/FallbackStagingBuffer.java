package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.nio.ByteBuffer;
import java.util.Objects;

// Staging buffer that uploads each copy through a scratch buffer at the moment it is enqueued
public class FallbackStagingBuffer implements StagingBuffer {
    // Scratch buffer that receives each upload before the copy into the destination
    private final GlMutableBuffer fallbackBufferObject;
    // Flag set while the scratch buffer holds storage that flush releases
    private boolean holdsStorage;
    // Flag set once delete has freed the scratch buffer
    private boolean deleted;

    // Creates the scratch buffer through the command list
    public FallbackStagingBuffer(CommandList commandList) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        this.fallbackBufferObject = commandList.createMutableBuffer();
    }

    // Uploads the remaining bytes of the data into the scratch buffer and copies them into the destination at the write offset
    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        // Rejects a null command list or data or destination
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(data, "Data must not be null");
        Objects.requireNonNull(dst, "Destination must not be null");

        // Rejects data outside native memory
        if (!data.isDirect()) {
            throw new IllegalArgumentException("Data must be a direct buffer");
        }

        int length = data.remaining();

        // Rejects a negative write offset or a write end past the long limit
        if ((writeOffset | (writeOffset + length)) < 0L) {
            throw new IllegalArgumentException("Write offset " + writeOffset + " with " + length + " bytes is out of range");
        }

        // Rejects use after delete
        if (this.deleted) {
            throw new IllegalStateException("Staging buffer is deleted");
        }

        // Skips the upload and the copy of empty data
        if (length == 0) {
            return;
        }

        this.holdsStorage = true;

        commandList.uploadData(this.fallbackBufferObject, data, GlBufferUsage.STREAM_COPY);
        commandList.copyBufferSubData(this.fallbackBufferObject, dst, 0L, writeOffset, length);
    }

    // Releases the storage of the scratch buffer
    @Override
    public void flush(CommandList commandList) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        // Rejects use after delete
        if (this.deleted) {
            throw new IllegalStateException("Staging buffer is deleted");
        }

        // Skips the release when the scratch buffer holds no storage
        if (!this.holdsStorage) {
            return;
        }

        commandList.allocateStorage(this.fallbackBufferObject, 0L, GlBufferUsage.STREAM_COPY);

        this.holdsStorage = false;
    }

    // Frees the scratch buffer on the first call and ignores later calls
    @Override
    public void delete(CommandList commandList) {
        // Rejects a null command list
        Objects.requireNonNull(commandList, "Command list must not be null");

        // Skips a scratch buffer that is already freed
        if (this.deleted) {
            return;
        }

        commandList.deleteBuffer(this.fallbackBufferObject);

        this.deleted = true;
    }

    // Leaves the state unchanged
    @Override
    public void flip() {
    }

    // Returns the label of this staging path on the debug screen
    @Override
    public String toString() {
        return "Fallback";
    }
}
