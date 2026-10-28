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
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.state.GlStateTracker;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlIndexType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice;
import com.bdmajora.impetus.engine.impl.gpu.device.OpenGlDeviceInfo;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Render device that issues every command to the LWJGL service at once and caches the buffer and vertex array bindings
public class GLRenderDevice implements RenderDevice {
    // GL enum of the array buffer binding point
    private static final int ARRAY_BUFFER = GlBufferTarget.ARRAY_BUFFER.getTargetParameter();
    // GL enum of the copy read binding point
    private static final int COPY_READ_BUFFER = GlBufferTarget.COPY_READ_BUFFER.getTargetParameter();

    // GL bit of a persistent mapping request
    private static final int PERSISTENT_BIT = GlBufferMapFlags.PERSISTENT.getBits();
    // GL bit of a writable mapping request
    private static final int WRITE_BIT = GlBufferMapFlags.WRITE.getBits();
    // GL bits of the mapping requests that the buffer storage must allow
    private static final int CHECKED_MAP_BITS = GlBufferMapFlags.READ.getBits() | WRITE_BIT | PERSISTENT_BIT;
    // GL bits of the mapping requests that every mutable buffer allows
    private static final int MUTABLE_STORAGE_BITS = GlBufferStorageFlags.MAP_READ.getBits() | GlBufferStorageFlags.MAP_WRITE.getBits();

    // Mask of the low bits that an indirect draw offset must leave clear
    private static final long INDIRECT_ALIGNMENT_MASK = Integer.BYTES - 1;

    // Message of every refusal outside managed code
    private static final String UNMANAGED = "Tried to access device from unmanaged context";
    // Message of every refusal while no tessellation is in progress
    private static final String NO_TESSELLATION = "No tessellation is in progress";

    // Cache of the buffer and vertex array bindings
    private final GlStateTracker stateTracker = new GlStateTracker();
    // Command list that the device hands out
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    // Draw command list that every tessellation draws through
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();
    // Copy and map functions picked for the GL context
    private final DeviceFunctions functions = new DeviceFunctions(this);

    // Activation flag that makeActive sets and makeInactive clears
    private boolean isActive;
    // Tessellation in progress or null when none is bound
    private GlTessellation activeTessellation;
    // Capability description captured on the first activation
    private GpuDevice deviceInfo;

    // Returns the command list and rejects an inactive device
    @Override
    public CommandList createCommandList() {
        if (!this.isActive) {
            throw new IllegalStateException(UNMANAGED);
        }

        return this.commandList;
    }

    // Unbinds the array buffer and clears the binding cache and captures the device once and marks the device active
    @Override
    public void makeActive() {
        if (this.isActive) {
            return;
        }

        unbindArrayBuffer();

        this.stateTracker.clear();

        // Leaves the device inactive when the capture throws
        if (this.deviceInfo == null) {
            this.deviceInfo = OpenGlDeviceInfo.capture(this.functions);
        }

        this.isActive = true;
    }

    // Unbinds the array buffer and clears the binding cache and marks the device inactive
    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        unbindArrayBuffer();

        this.stateTracker.clear();
        this.isActive = false;
    }

    // Binds buffer zero to the array buffer binding point
    private static void unbindArrayBuffer() {
        LWJGL.glBindBuffer(ARRAY_BUFFER, 0);
    }

    // Returns the copy and map functions picked for the GL context
    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    // Returns the capability description and rejects a device that never became active
    @Override
    public GpuDevice getGpuDevice() {
        GpuDevice info = this.deviceInfo;

        if (info == null) {
            throw new IllegalStateException(UNMANAGED);
        }

        return info;
    }

    // Rejects a draw while no tessellation is in progress
    private void requireTessellation() {
        if (this.activeTessellation == null) {
            throw new IllegalStateException(NO_TESSELLATION);
        }
    }

    // Unbinds the tessellation and then clears the tessellation in progress
    private void endTessellation(GlTessellation tessellation) {
        tessellation.unbind(this.commandList);

        this.activeTessellation = null;
    }

    // Returns the refusal for a copy range that copyBufferSubData rejects
    private static IllegalArgumentException copyRangeError(long readOffset, long writeOffset, long bytes) {
        if (readOffset < 0L) {
            return new IllegalArgumentException("Read offset must not be negative: " + readOffset);
        }

        if (writeOffset < 0L) {
            return new IllegalArgumentException("Write offset must not be negative: " + writeOffset);
        }

        if (bytes < 0L) {
            return new IllegalArgumentException("Size must not be negative: " + bytes);
        }

        return new IllegalArgumentException("Copy range overflows: " + Math.max(readOffset, writeOffset) + " + " + bytes);
    }

    // Returns the refusal for a mapping request that mapBuffer rejects
    private static RuntimeException mapError(GlBuffer buffer, long offset, long length, int missingBits) {
        if (buffer.getActiveMapping() != null) {
            return new IllegalStateException("Buffer is already mapped");
        }

        if ((missingBits & PERSISTENT_BIT) != 0) {
            return buffer instanceof GlImmutableBuffer
                    ? new IllegalArgumentException("Tried to map non-persistent buffer as persistent")
                    : new IllegalStateException("Tried to map mutable buffer as persistent");
        }

        if ((missingBits & WRITE_BIT) != 0) {
            return new IllegalStateException("Tried to map non-writable buffer as writable");
        }

        if (missingBits != 0) {
            return new IllegalStateException("Tried to map non-readable buffer as readable");
        }

        if (offset < 0L) {
            return new IllegalArgumentException("Offset must not be negative: " + offset);
        }

        if (length < 0L) {
            return new IllegalArgumentException("Length must not be negative: " + length);
        }

        if (length > Integer.MAX_VALUE) {
            return new IllegalArgumentException("Length must not exceed " + Integer.MAX_VALUE + ": " + length);
        }

        return new IllegalArgumentException("Map range overflows: " + offset + " + " + length);
    }

    // Returns the refusal for a mapping that is disposed or not the open mapping of its buffer
    private static IllegalStateException mappingError(GlBufferMapping map) {
        return new IllegalStateException(map.isDisposed() ? "Buffer mapping is already disposed" : "Mapping is not the open mapping of its buffer");
    }

    // Returns the refusal for a flush that flushMappedRange rejects
    private static RuntimeException flushError(GlBufferMapping map, int offset, int length, int capacity) {
        if (map.isDisposed() | map.getBufferObject().getActiveMapping() != map) {
            return mappingError(map);
        }

        if (offset < 0) {
            return new IllegalArgumentException("Offset must not be negative: " + offset);
        }

        if (length < 0) {
            return new IllegalArgumentException("Length must not be negative: " + length);
        }

        return new IllegalArgumentException("Flush range exceeds the mapping of " + capacity + " bytes: " + offset + " + " + length);
    }

    // Returns the refusal for an indirect draw that multiDrawElementsIndirect rejects
    private static IllegalArgumentException indirectError(long offset, int count) {
        if (offset < 0L) {
            return new IllegalArgumentException("Offset must not be negative: " + offset);
        }

        if ((offset & INDIRECT_ALIGNMENT_MASK) != 0L) {
            return new IllegalArgumentException("Offset must be a multiple of " + Integer.BYTES + ": " + offset);
        }

        return new IllegalArgumentException("Count must not be negative: " + count);
    }

    // Command list that issues each command to the LWJGL service at once
    private final class ImmediateCommandList implements CommandList {
        // Cache of the buffer and vertex array bindings of the device
        private final GlStateTracker stateTracker;

        // Stores the binding cache of the device
        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        // Rejects a null array and binds the array unless the cache already holds it
        @Override
        public void bindVertexArray(GlVertexArray array) {
            Objects.requireNonNull(array, "Vertex array must not be null");

            if (this.stateTracker.makeVertexArrayActive(array)) {
                LWJGL.glBindVertexArray(array.handle());
            }
        }

        // Rejects null arguments or heap data and replaces the storage with the remaining bytes and records the size
        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            Objects.requireNonNull(glBuffer, "Buffer must not be null");
            Objects.requireNonNull(byteBuffer, "Data must not be null");
            int usageId = Objects.requireNonNull(usage, "Usage must not be null").getId();

            if (!byteBuffer.isDirect()) {
                throw new IllegalArgumentException("Data must be a direct buffer");
            }

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            LWJGL.glBufferData(ARRAY_BUFFER, byteBuffer, usageId);
            glBuffer.setSize(byteBuffer.remaining());
        }

        // Rejects null arguments or a negative size and replaces the storage with the bytes at the address and records the size
        @Override
        public void uploadData(GlMutableBuffer glBuffer, long ptr, long bytes, GlBufferUsage usage) {
            Objects.requireNonNull(glBuffer, "Buffer must not be null");
            int usageId = Objects.requireNonNull(usage, "Usage must not be null").getId();

            if (bytes < 0L) {
                throw new IllegalArgumentException("Size must not be negative");
            }

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            LWJGL.glBufferData(ARRAY_BUFFER, bytes, ptr, usageId);
            glBuffer.setSize(bytes);
        }

        // Rejects null buffers or a negative or overflowing range and copies through the picked copy function
        @Override
        public void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            Objects.requireNonNull(src, "Source buffer must not be null");
            Objects.requireNonNull(dst, "Destination buffer must not be null");

            // Rejects a negative offset or size or a range end past the long limit
            if ((readOffset | writeOffset | bytes | (readOffset + bytes) | (writeOffset + bytes)) < 0L) {
                throw copyRangeError(readOffset, writeOffset, bytes);
            }

            GLRenderDevice.this.functions.bufferCopyFunctions().copyBufferSubData(this, src, dst, readOffset, writeOffset, bytes);
        }

        // Rejects a null target and binds the buffer unless the cache already holds it and binds zero for null
        @Override
        public void bindBuffer(GlBufferTarget target, @Nullable GlBuffer buffer) {
            Objects.requireNonNull(target, "Target must not be null");

            if (this.stateTracker.makeBufferActive(target, buffer)) {
                LWJGL.glBindBuffer(target.getTargetParameter(), buffer != null ? buffer.handle() : 0);
            }
        }

        // Binds vertex array zero unless the cache already holds it
        @Override
        public void unbindVertexArray() {
            if (this.stateTracker.makeVertexArrayActive(null)) {
                LWJGL.glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
            }
        }

        // Rejects null arguments or a negative size and replaces the storage with uninitialised storage and records the size
        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            Objects.requireNonNull(buffer, "Buffer must not be null");
            int usageId = Objects.requireNonNull(usage, "Usage must not be null").getId();

            if (bufferSize < 0L) {
                throw new IllegalArgumentException("Size must not be negative");
            }

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            LWJGL.glBufferData(ARRAY_BUFFER, bufferSize, usageId);
            buffer.setSize(bufferSize);
        }

        // Rejects a null buffer and unmaps the open mapping and clears the cached bindings and deletes the buffer
        @Override
        public void deleteBuffer(GlBuffer buffer) {
            GlBufferMapping mapping = Objects.requireNonNull(buffer, "Buffer must not be null").getActiveMapping();

            // Unmaps the open mapping even when it was disposed directly
            if (mapping != null) {
                this.release(buffer, mapping);
            }

            this.stateTracker.notifyBufferDeleted(buffer);

            buffer.delete();
        }

        // Rejects a null array and clears the cached binding and deletes the array
        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            Objects.requireNonNull(vertexArray, "Vertex array must not be null");

            this.stateTracker.notifyVertexArrayDeleted(vertexArray);

            vertexArray.delete();
        }

        // Returns without a GL call
        @Override
        public void flush() {
        }

        // Rejects null and ends a different tessellation in progress and binds the tessellation and returns the draw command list
        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            Objects.requireNonNull(tessellation, "Tessellation must not be null");

            GlTessellation active = GLRenderDevice.this.activeTessellation;

            if (active != null && active != tessellation) {
                GLRenderDevice.this.endTessellation(active);
            }

            GLRenderDevice.this.activeTessellation = tessellation;
            tessellation.bind(this);

            return GLRenderDevice.this.drawCommandList;
        }

        // Rejects null and ends the tessellation when it is in progress and deletes its GL objects
        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            Objects.requireNonNull(tessellation, "Tessellation must not be null");

            if (tessellation == GLRenderDevice.this.activeTessellation) {
                GLRenderDevice.this.endTessellation(tessellation);
            }

            tessellation.delete(this);
        }

        // Rejects null arguments or a mapped buffer or disallowed flags or an invalid range and maps the range and records the mapping
        @Override
        public GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            Objects.requireNonNull(buffer, "Buffer must not be null");
            int mapBits = Objects.requireNonNull(flags, "Flags must not be null").getBitField();
            int allowedBits = buffer instanceof GlImmutableBuffer immutable ? immutable.getFlags().getBitField() : MUTABLE_STORAGE_BITS;
            int missingBits = mapBits & CHECKED_MAP_BITS & ~allowedBits;

            // Rejects a mapped buffer or flags the storage lacks or a negative or overflowing range or a range longer than a memory buffer
            if (buffer.getActiveMapping() != null | missingBits != 0
                    | (offset | length | (offset + length) | (Integer.MAX_VALUE - length)) < 0L) {
                throw mapError(buffer, offset, length, missingBits);
            }

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            ByteBuffer memory = GLRenderDevice.this.functions.bufferMapRangeFunctions()
                    .mapBufferRange(buffer, offset, length, flags);

            if (memory == null) {
                throw new RuntimeException("Failed to map buffer");
            }

            GlBufferMapping mapping = new GlBufferMapping(buffer, memory);

            buffer.setActiveMapping(mapping);

            return mapping;
        }

        // Rejects a null or disposed mapping or one that is not the open mapping of its buffer and unmaps the buffer
        @Override
        public void unmap(GlBufferMapping map) {
            GlBuffer buffer = Objects.requireNonNull(map, "Mapping must not be null").getBufferObject();

            if (map.isDisposed() | buffer.getActiveMapping() != map) {
                throw mappingError(map);
            }

            this.release(buffer, map);
        }

        // Binds the buffer and unmaps it and then clears and disposes the mapping
        private void release(GlBuffer buffer, GlBufferMapping mapping) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
            LWJGL.glUnmapBuffer(ARRAY_BUFFER);

            buffer.setActiveMapping(null);
            mapping.dispose();
        }

        // Rejects a null or disposed or inactive mapping or a range outside the mapping and flushes the range through the copy read binding point
        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            GlBuffer buffer = Objects.requireNonNull(map, "Mapping must not be null").getBufferObject();
            int capacity = map.getMemoryBuffer().capacity();

            // Rejects a disposed or inactive mapping or a negative offset or length or a range end past the capacity
            if (map.isDisposed() | buffer.getActiveMapping() != map
                    | ((offset | length) | ((long) capacity - offset - length)) < 0L) {
                throw flushError(map, offset, length, capacity);
            }

            this.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, buffer);
            LWJGL.glFlushMappedBufferRange(COPY_READ_BUFFER, offset, length);
        }

        // Creates a fence after every command issued so far and rejects a zero sync object
        @Override
        public GlFence createFence() {
            long sync = LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

            if (sync == 0L) {
                throw new IllegalStateException("Failed to create a fence");
            }

            return new GlFence(sync);
        }

        // Creates a mutable buffer with a new name and no storage
        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        // Rejects null flags or a size below 1 and creates an immutable buffer and deletes it again when the storage call throws
        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            int storageBits = Objects.requireNonNull(flags, "Flags must not be null").getBitField();

            if (bufferSize <= 0L) {
                throw new IllegalArgumentException("Size must be positive: " + bufferSize);
            }

            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);

            try {
                this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
                LWJGL.glBufferStorage(ARRAY_BUFFER, bufferSize, storageBits);
            } catch (Throwable failure) {
                this.discard(buffer, failure);
                throw failure;
            }

            return buffer;
        }

        // Deletes the buffer and adds a failure of the deletion to the suppressed list of the original failure
        private void discard(GlBuffer buffer, Throwable failure) {
            try {
                this.deleteBuffer(buffer);
            } catch (Throwable deleteFailure) {
                if (deleteFailure != failure) {
                    failure.addSuppressed(deleteFailure);
                }
            }
        }
    }

    // Draw command list that issues each draw to the LWJGL service at once
    private final class ImmediateDrawCommandList implements DrawCommandList {
        // Rejects null arguments or a deleted batch or a draw outside a tessellation and draws every command of the batch
        @Override
        public void multiDrawElementsBaseVertex(DirectMultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType) {
            int mode = Objects.requireNonNull(primitiveType, "Primitive type must not be null").getId();
            int type = Objects.requireNonNull(indexType, "Index type must not be null").getFormatId();
            Objects.requireNonNull(batch, "Batch must not be null").requireLive();
            GLRenderDevice.this.requireTessellation();

            LWJGL.glMultiDrawElementsBaseVertex(mode, batch.pElementCount, type, batch.pElementPointer, batch.size(), batch.pBaseVertex);
        }

        // Rejects null types or a negative count or a negative or misaligned offset or a draw outside a tessellation and draws from the indirect buffer
        @Override
        public void multiDrawElementsIndirect(long offset, int count, GlPrimitiveType primitiveType, GlIndexType indexType) {
            int mode = Objects.requireNonNull(primitiveType, "Primitive type must not be null").getId();
            int type = Objects.requireNonNull(indexType, "Index type must not be null").getFormatId();

            // Rejects a negative offset or count or an offset with any low bit set
            if ((offset | count | -(offset & INDIRECT_ALIGNMENT_MASK)) < 0L) {
                throw indirectError(offset, count);
            }

            GLRenderDevice.this.requireTessellation();

            LWJGL.glMultiDrawElementsIndirect(mode, type, offset, count, 0);
        }

        // Rejects a call with no tessellation in progress and unbinds the tessellation in progress and ends it
        @Override
        public void endTessellating() {
            GlTessellation active = GLRenderDevice.this.activeTessellation;

            if (active == null) {
                throw new IllegalStateException(NO_TESSELLATION);
            }

            GLRenderDevice.this.endTessellation(active);
        }

        // Ends the tessellation in progress when one is bound
        @Override
        public void flush() {
            GlTessellation active = GLRenderDevice.this.activeTessellation;

            if (active != null) {
                GLRenderDevice.this.endTessellation(active);
            }
        }
    }
}
