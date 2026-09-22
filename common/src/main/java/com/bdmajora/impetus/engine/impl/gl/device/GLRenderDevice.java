package com.bdmajora.impetus.engine.impl.gl.device;

import com.bdmajora.impetus.engine.impl.gpu.device.GpuDevice;
import com.bdmajora.impetus.engine.impl.gpu.device.OpenGlDeviceInfo;
import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.*;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.state.GlStateTracker;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.*;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import org.jetbrains.annotations.Nullable;
import com.bdmajora.impetus.lwjgl.GL32;

import java.nio.ByteBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public class GLRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();

    private final DeviceFunctions functions = new DeviceFunctions(this);

    private boolean isActive;
    private GlTessellation activeTessellation;
    private GpuDevice deviceInfo;

    // TODO replace this with something less ugly
    public static Runnable VANILLA_STATE_RESETTER = () -> {
        throw new IllegalStateException("The host mod should replace the VANILLA_STATE_RESETTER with an implementation specific to the platform.");
    };

    // The single command list; GL has no real command buffers
    @Override
    public CommandList createCommandList() {
        GLRenderDevice.this.checkDeviceActive();

        return this.commandList;
    }

    // Marks the device usable on this thread and resets the state tracker
    @Override
    public void makeActive() {
        if (this.isActive) {
            return;
        }

        VANILLA_STATE_RESETTER.run();

        this.stateTracker.clear();
        this.isActive = true;

        if (this.deviceInfo == null) {
            this.deviceInfo = OpenGlDeviceInfo.capture(this.functions);
        }
    }

    // Marks the device unusable; calls after this throw
    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        VANILLA_STATE_RESETTER.run();

        this.stateTracker.clear();
        this.isActive = false;
    }

    // The best available implementations of each optional GL feature
    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    // Capability description captured at creation
    @Override
    public GpuDevice getGpuDevice() {
        if (this.deviceInfo == null) {
            this.checkDeviceActive();
            this.deviceInfo = OpenGlDeviceInfo.capture(this.functions);
        }

        return this.deviceInfo;
    }

    // Throws if used outside enterManagedCode
    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        // Through the state tracker, so a redundant bind is skipped
        @Override
        public void bindVertexArray(GlVertexArray array) {
            if (this.stateTracker.makeVertexArrayActive(array)) {
                LWJGL.glBindVertexArray(array.handle());
            }
        }

        // glBufferData from a buffer, recording the new size
        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            LWJGL.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), byteBuffer, usage.getId());
            glBuffer.setSize(byteBuffer.remaining());
        }

        // glBufferData from a raw pointer, recording the new size
        @Override
        public void uploadData(GlMutableBuffer glBuffer, long ptr, long bytes, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

            LWJGL.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bytes, ptr, usage.getId());
            glBuffer.setSize(bytes);
        }

        // Through the best available copy function
        @Override
        public void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            GLRenderDevice.this.functions.bufferCopyFunctions().copyBufferSubData(this, src, dst, readOffset, writeOffset, bytes);
        }

        // Through the state tracker; null unbinds
        @Override
        public void bindBuffer(GlBufferTarget target, @Nullable GlBuffer buffer) {
            if (this.stateTracker.makeBufferActive(target, buffer)) {
                LWJGL.glBindBuffer(target.getTargetParameter(), buffer != null ? buffer.handle() : 0);
            }
        }

        // Binds VAO zero through the tracker
        @Override
        public void unbindVertexArray() {
            if (this.stateTracker.makeVertexArrayActive(null)) {
                LWJGL.glBindVertexArray(GlVertexArray.NULL_ARRAY_ID);
            }
        }

        // Uninitialised glBufferData, recording the new size
        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            LWJGL.glBufferData(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bufferSize, usage.getId());
            buffer.setSize(bufferSize);
        }

        // Unmaps if mapped, tells the tracker, then deletes
        @Override
        public void deleteBuffer(GlBuffer buffer) {
            if (buffer.getActiveMapping() != null) {
                this.unmap(buffer.getActiveMapping());
            }

            this.stateTracker.notifyBufferDeleted(buffer);

            buffer.delete();
        }

        // Tells the tracker, then deletes
        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            this.stateTracker.notifyVertexArrayDeleted(vertexArray);

            vertexArray.delete();
        }

        // Nothing is batched, so there is nothing to flush
        @Override
        public void flush() {
        }

        // Binds the tessellation and returns the draw list for it
        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            GLRenderDevice.this.activeTessellation = tessellation;
            GLRenderDevice.this.activeTessellation.bind(GLRenderDevice.this.commandList);

            return GLRenderDevice.this.drawCommandList;
        }

        // Frees the VAO or buffer bindings behind it
        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            tessellation.delete(this);
        }

        // Maps a range through the best available function, tracking the mapping on the buffer
        @Override
        public GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            if (buffer.getActiveMapping() != null) {
                throw new IllegalStateException("Buffer is already mapped");
            }

            if (flags.contains(GlBufferMapFlags.PERSISTENT) && !(buffer instanceof GlImmutableBuffer)) {
                throw new IllegalStateException("Tried to map mutable buffer as persistent");
            }

            // TODO: speed this up?
            if (buffer instanceof GlImmutableBuffer) {
                EnumBitField<GlBufferStorageFlags> bufferFlags = ((GlImmutableBuffer) buffer).getFlags();

                if (flags.contains(GlBufferMapFlags.PERSISTENT) && !bufferFlags.contains(GlBufferStorageFlags.PERSISTENT)) {
                    throw new IllegalArgumentException("Tried to map non-persistent buffer as persistent");
                }

                if (flags.contains(GlBufferMapFlags.WRITE) && !bufferFlags.contains(GlBufferStorageFlags.MAP_WRITE)) {
                    throw new IllegalStateException("Tried to map non-writable buffer as writable");
                }

                if (flags.contains(GlBufferMapFlags.READ) && !bufferFlags.contains(GlBufferStorageFlags.MAP_READ)) {
                    throw new IllegalStateException("Tried to map non-readable buffer as readable");
                }
            }

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

            ByteBuffer buf = GLRenderDevice.this.functions.bufferMapRangeFunctions()
                    .mapBufferRange(buffer, offset, length, flags);

            if (buf == null) {
                throw new RuntimeException("Failed to map buffer");
            }

            GlBufferMapping mapping = new GlBufferMapping(buffer, buf);

            buffer.setActiveMapping(mapping);

            return mapping;
        }

        // Unmaps and clears the buffer's active mapping
        @Override
        public void unmap(GlBufferMapping map) {
            checkMapDisposed(map);

            GlBuffer buffer = map.getBufferObject();

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
            LWJGL.glUnmapBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter());

            buffer.setActiveMapping(null);
            map.dispose();
        }

        // For explicit-flush mappings
        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            checkMapDisposed(map);

            GlBuffer buffer = map.getBufferObject();

            this.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, buffer);
            LWJGL.glFlushMappedBufferRange(GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), offset, length);
        }

        // glFenceSync
        @Override
        public GlFence createFence() {
            return new GlFence(LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
        }

        // Throws on use after unmap
        private void checkMapDisposed(GlBufferMapping map) {
            if (map.isDisposed()) {
                throw new IllegalStateException("Buffer mapping is already disposed");
            }
        }

        // A buffer that can be reallocated with glBufferData
        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        // A buffer with fixed storage, via the best available storage function
        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
            GLRenderDevice.this.functions.bufferStorageFunctions()
                    .createBufferStorage(GlBufferTarget.ARRAY_BUFFER, bufferSize, flags);

            return buffer;
        }
    }

    private class ImmediateDrawCommandList implements DrawCommandList {
        public ImmediateDrawCommandList() {

        }

        // Issues the batch through the best available multidraw function
        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlPrimitiveType primitiveType, GlIndexType indexType) {
            GLRenderDevice.this.functions.multidrawFunctions().multiDrawElementsBaseVertex(primitiveType.getId(),
                    batch.pElementCount,
                    indexType.getFormatId(),
                    batch.pElementPointer,
                    batch.size(),
                    batch.pBaseVertex);
        }

        // Draws from an indirect buffer
        @Override
        public void multiDrawElementsIndirect(GlBuffer indirectBuffer, int count, GlPrimitiveType primitiveType, GlIndexType indexType) {
            LWJGL.glMultiDrawElementsIndirect(primitiveType.getId(), indexType.getFormatId(), 0, count, 0);
        }

        // Unbinds the tessellation
        @Override
        public void endTessellating() {
            GLRenderDevice.this.activeTessellation.unbind(GLRenderDevice.this.commandList);
            GLRenderDevice.this.activeTessellation = null;
        }

        // Ends the tessellation still bound, if any
        @Override
        public void flush() {
            if (GLRenderDevice.this.activeTessellation != null) {
                this.endTessellating();
            }
        }
    }
}
