package com.bdmajora.impetus.engine.impl.gl.functions;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.lwjgl.GLExtension;

public enum BufferCopyFunctions {
    CORE {
        // Implementation for this GL level
        @Override
        public void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            commandList.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
            commandList.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);
            LWJGL.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, readOffset, writeOffset, bytes);
        }
    },
    PIXEL_PACK {
        // Implementation for this GL level
        @Override
        public void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            if (src.getActiveMapping() != null || dst.getActiveMapping() != null) {
                throw new IllegalStateException("Cannot use PIXEL_PACK copy strategy on mapped buffers");
            }
            commandList.bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, src);
            commandList.bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, dst);
            long srcBufPtr = LWJGL.nglMapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter(), GL15.GL_READ_ONLY);
            if (srcBufPtr == 0L) {
                throw new IllegalStateException("Source buffer could not be mapped");
            }
            long dstBufPtr = LWJGL.nglMapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter(), GL15.GL_WRITE_ONLY);
            if (dstBufPtr == 0L) {
                throw new IllegalStateException("Destination buffer could not be mapped");
            }
            LWJGL.memCopy(srcBufPtr + readOffset, dstBufPtr + writeOffset, bytes);
            LWJGL.glUnmapBuffer(GlBufferTarget.PIXEL_PACK_BUFFER.getTargetParameter());
            LWJGL.glUnmapBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER.getTargetParameter());
            commandList.bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);
            commandList.bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, null);
        }
    };

    public abstract void copyBufferSubData(CommandList commandList, GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes);

    // Core copy, then a map-and-memcpy fallback
    public static BufferCopyFunctions pickBest(RenderDevice device) {
        if (LWJGL.isOpenGLVersionSupported(3, 1) || LWJGL.isExtensionSupported(GLExtension.ARB_copy_buffer)) {
            return CORE;
        } else {
            return PIXEL_PACK;
        }
    }
}
