package com.bdmajora.impetus.engine.impl.gl.state;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// Cache of the buffer bound to each binding point and of the bound vertex array with a known bit per binding
public class GlStateTracker {
    // Name that binds no buffer and no vertex array
    private static final int NULL_NAME = 0;
    // Bit of the known mask that marks the vertex array binding as known
    private static final int VERTEX_ARRAY_BIT = 1 << GlBufferTarget.COUNT;

    // Buffer name of each binding point by ordinal that is valid while its known bit is set
    private final int[] bufferState = new int[GlBufferTarget.COUNT];
    // Vertex array name that is valid while the vertex array bit is set
    private int vertexArrayState;
    // Mask with the bit of each binding point ordinal and the vertex array bit for every binding that matches GL
    private int known;

    // Creates a tracker that knows no binding
    public GlStateTracker() {

    }

    // Rejects null and forgets every binding when the array is bound or the vertex array binding is unknown
    public void notifyVertexArrayDeleted(GlVertexArray vertexArray) {
        int name = Objects.requireNonNull(vertexArray, "Vertex array must not be null").handle();

        // Forgets every binding when the deleted array is or may be the bound one
        if ((this.known & VERTEX_ARRAY_BIT) == 0 | this.vertexArrayState == name) {
            this.known = 0;
        }
    }

    // Rejects null and forgets every binding point that holds the buffer
    public void notifyBufferDeleted(GlBuffer buffer) {
        int name = Objects.requireNonNull(buffer, "Buffer must not be null").handle();
        int[] state = this.bufferState;
        int known = this.known;

        for (int index = 0; index < state.length; index++) {
            if (state[index] == name) {
                known &= ~(1 << index);
            }
        }

        this.known = known;
    }

    // Rejects a null target and records the buffer or name zero for null and returns whether GL needs the bind
    public boolean makeBufferActive(GlBufferTarget target, @Nullable GlBuffer buffer) {
        int index = Objects.requireNonNull(target, "Target must not be null").ordinal();
        int name = buffer == null ? NULL_NAME : buffer.handle();
        int bit = 1 << index;

        if ((this.known & bit) != 0 & this.bufferState[index] == name) {
            return false;
        }

        this.bufferState[index] = name;
        this.known |= bit;

        return true;
    }

    // Records the array or name zero for null and forgets every buffer binding on a change and returns whether GL needs the bind
    public boolean makeVertexArrayActive(@Nullable GlVertexArray array) {
        int name = array == null ? NULL_NAME : array.handle();

        if ((this.known & VERTEX_ARRAY_BIT) != 0 & this.vertexArrayState == name) {
            return false;
        }

        this.vertexArrayState = name;
        this.known = VERTEX_ARRAY_BIT;

        return true;
    }

    // Forgets every binding
    public void clear() {
        this.known = 0;
    }
}
