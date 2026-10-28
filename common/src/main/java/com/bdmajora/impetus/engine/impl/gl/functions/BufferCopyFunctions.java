package com.bdmajora.impetus.engine.impl.gl.functions;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.util.Objects;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

// Strategies that copy a byte range between buffer objects
public enum BufferCopyFunctions {
    // Strategy that copies on the GPU through the copy binding points
    CORE {
        // Rejects invalid arguments and binds both buffers to the copy binding points and forwards the range to GL unchanged
        @Override
        public void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            requireCopy(commandList, src, dst, readOffset, writeOffset, bytes);

            commandList.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
            commandList.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);
            LWJGL.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, readOffset, writeOffset, bytes);
        }
    },
    // Strategy that maps whole buffers through the pixel binding points and copies on the CPU
    PIXEL_PACK {
        // Rejects invalid arguments or a mapped buffer or an out of bounds or overlapping range and copies the range through whole buffer mappings
        @Override
        public void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            requireCopy(commandList, src, dst, readOffset, writeOffset, bytes);

            boolean same = src == dst;

            // Rejects a mapped buffer or a range past the end of either buffer or overlapping ranges within one buffer
            if (src.getActiveMapping() != null | dst.getActiveMapping() != null
                    | readOffset + bytes > sizeOf(src) | writeOffset + bytes > sizeOf(dst)
                    | (same && readOffset < writeOffset + bytes && writeOffset < readOffset + bytes)) {
                throw pixelCopyError(src, dst, readOffset, writeOffset, bytes);
            }

            // Returns without a bind or a mapping for an empty range
            if (bytes == 0L) {
                return;
            }

            try {
                commandList.bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, src);

                if (same) {
                    this.copyWithin(readOffset, writeOffset, bytes);
                } else {
                    commandList.bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, dst);
                    this.copyBetween(readOffset, writeOffset, bytes);
                }
            } finally {
                try {
                    commandList.bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);
                } finally {
                    commandList.bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, null);
                }
            }
        }

        // Maps the pixel pack buffer for reading and the pixel unpack buffer for writing and copies the range between the mappings
        private void copyBetween(long readOffset, long writeOffset, long bytes) {
            long srcAddress = map(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, "Source buffer could not be mapped");

            try {
                long dstAddress = map(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, "Destination buffer could not be mapped");

                try {
                    MemoryUtil.memCopy(srcAddress + readOffset, dstAddress + writeOffset, bytes);
                } finally {
                    LWJGL.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
                }
            } finally {
                LWJGL.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
        }

        // Maps the pixel pack buffer for reading and writing and copies the range within the mapping
        private void copyWithin(long readOffset, long writeOffset, long bytes) {
            long address = map(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_WRITE, "Source buffer could not be mapped");

            try {
                MemoryUtil.memCopy(address + readOffset, address + writeOffset, bytes);
            } finally {
                LWJGL.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
        }

        // Maps the whole buffer bound to the target with the access and rejects a null mapping with the message
        private static long map(int target, int access, String failure) {
            long address = LWJGL.nglMapBuffer(target, access);

            if (address == MemoryUtil.NULL) {
                throw new IllegalStateException(failure);
            }

            return address;
        }

        // Returns the recorded storage size of a mutable buffer or the long limit for a buffer without a recorded size
        private static long sizeOf(GlBuffer buffer) {
            return buffer instanceof GlMutableBuffer mutable ? mutable.getSize() : Long.MAX_VALUE;
        }

        // Returns the refusal for a copy that the mapping strategy rejects after the shared checks
        private static RuntimeException pixelCopyError(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            if (src.getActiveMapping() != null || dst.getActiveMapping() != null) {
                return new IllegalStateException("Cannot use PIXEL_PACK copy strategy on mapped buffers");
            }

            long srcSize = sizeOf(src);

            if (readOffset + bytes > srcSize) {
                return new IllegalArgumentException("Copy range ends past the source buffer: " + (readOffset + bytes) + " > " + srcSize);
            }

            long dstSize = sizeOf(dst);

            if (writeOffset + bytes > dstSize) {
                return new IllegalArgumentException("Copy range ends past the destination buffer: " + (writeOffset + bytes) + " > " + dstSize);
            }

            return new IllegalArgumentException("Copy ranges overlap within one buffer: read " + readOffset + " write " + writeOffset + " size " + bytes);
        }
    };

    // Copies the given number of bytes from the read offset of the source to the write offset of the destination
    public abstract void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes);

    // Returns the GPU strategy when the context has GL 3.1 or ARB_copy_buffer and the mapping strategy otherwise
    public static BufferCopyFunctions pickBest(RenderDevice device) {
        return LWJGL.isOpenGLVersionSupported(3, 1) || LWJGL.isExtensionSupported(GLExtension.ARB_copy_buffer) ? CORE : PIXEL_PACK;
    }

    // Rejects a null argument or a negative or overflowing range or a deleted buffer
    private static void requireCopy(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(src, "Source buffer must not be null");
        Objects.requireNonNull(dst, "Destination buffer must not be null");

        // Rejects a negative offset or size or a range end past the long limit
        if ((readOffset | writeOffset | bytes | (readOffset + bytes) | (writeOffset + bytes)) < 0L) {
            throw copyRangeError(readOffset, writeOffset, bytes);
        }

        // Rejects a deleted source or destination
        src.handle();
        dst.handle();
    }

    // Returns the refusal for a range that the shared checks reject
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
}
