package com.bdmajora.impetus.engine.impl.gl.functions;

import org.lwjgl.opengl.GL15;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;

import java.nio.ByteBuffer;

public enum BufferMapRangeFunctions {
    CORE {
        // Implementation for this GL level
        @Override
        public ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            return LWJGL.glMapBufferRange(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), offset, length, flags.getBitField());
        }
    },
    MAP_FULL_AND_SLICE {
        // Implementation for this GL level
        @Override
        public ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            if (flags.contains(GlBufferMapFlags.EXPLICIT_FLUSH)) {
                throw new UnsupportedOperationException("Explicit flush not supported for MAP_FULL_AND_SLICE strategy");
            }
            int access;
            if (flags.contains(GlBufferMapFlags.WRITE) && flags.contains(GlBufferMapFlags.READ)) {
                access = GL15.GL_READ_WRITE;
            } else if (flags.contains(GlBufferMapFlags.WRITE)) {
                access = GL15.GL_WRITE_ONLY;
            } else {
                access = GL15.GL_READ_ONLY;
            }
            ByteBuffer buf = LWJGL.glMapBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), access);
            if (buf == null) {
                return null;
            }
            // Avoid slicing the buffer if we can prove that the full buffer is being mapped
            if (buffer instanceof GlMutableBuffer mutBuffer && mutBuffer.getSize() == length && offset == 0) {
                return buf;
            }
            return buf.position(Math.toIntExact(offset))
                    .limit(Math.toIntExact(offset) + Math.toIntExact(length))
                    .slice();
        }
    };

    public abstract ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags);

    // Core map range, then a full-map fallback
    public static BufferMapRangeFunctions pickBest(RenderDevice device) {
        if (LWJGL.isOpenGLVersionSupported(3, 0)
                || LWJGL.isExtensionSupported(GLExtension.ARB_map_buffer_range)) {
            return CORE;
        } else {
            return MAP_FULL_AND_SLICE;
        }
    }
}
