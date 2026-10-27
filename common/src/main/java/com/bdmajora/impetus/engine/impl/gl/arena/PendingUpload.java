package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import org.jetbrains.annotations.Nullable;

// Native buffer that waits for an arena and the segment that receives its bytes
public final class PendingUpload {
    // Bytes to upload
    private final NativeBuffer data;

    // Capacity of the data in bytes
    private final int length;

    // Segment that holds the uploaded bytes or null until the arena places them
    private GlBufferSegment result;

    // Creates an upload of the buffer and stores its capacity
    private PendingUpload(NativeBuffer data) {
        this.data = data;
        this.length = data.getLength();
    }

    // Returns a new upload of the buffer or null for a null buffer
    public static @Nullable PendingUpload of(@Nullable NativeBuffer data) {
        return data == null ? null : new PendingUpload(data);
    }

    // Returns the bytes to upload
    public NativeBuffer getDataBuffer() {
        return this.data;
    }

    // Stores the segment that holds the uploaded bytes and rejects a null segment or a second result
    protected void setResult(GlBufferSegment result) {
        // Reads true for a null segment or an upload that already holds a result
        if (result == null | this.result != null) {
            throw resultError(result);
        }

        this.result = result;
    }

    // Creates the exception for a null segment or a second result
    private static RuntimeException resultError(GlBufferSegment result) {
        if (result == null) {
            return new NullPointerException("Result must not be null");
        }

        return new IllegalStateException("Result already provided");
    }

    // Returns the segment that holds the uploaded bytes and rejects an upload that was not placed
    public GlBufferSegment getResult() {
        GlBufferSegment result = this.result;

        if (result == null) {
            throw new IllegalStateException("Result not computed");
        }

        return result;
    }

    // Returns the capacity of the data in bytes
    public int getLength() {
        return this.length;
    }
}
