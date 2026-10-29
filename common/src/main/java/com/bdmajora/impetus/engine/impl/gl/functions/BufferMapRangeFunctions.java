package com.bdmajora.impetus.engine.impl.gl.functions;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.nio.ByteBuffer;
import java.util.Objects;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

// Strategies that map a byte range of the buffer bound to the array binding point
public enum BufferMapRangeFunctions {
    // Strategy that maps the range through glMapBufferRange
    CORE {
        // Rejects invalid arguments and forwards the range and the flag bits to GL unchanged
        @Override
        public ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            int bits = requireMap(buffer, offset, length, flags);

            return LWJGL.glMapBufferRange(ARRAY_BUFFER, offset, length, bits);
        }
    },
    // Strategy that maps the whole buffer through glMapBuffer and returns a view of the range
    MAP_FULL_AND_SLICE {
        // Rejects invalid arguments or explicit flushes or a range end past the int limit and maps the whole buffer and returns the range
        @Override
        public ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            int bits = requireMap(buffer, offset, length, flags);

            // Rejects explicit flushes or a range end that no memory buffer reaches
            if ((bits & GL30.GL_MAP_FLUSH_EXPLICIT_BIT) != 0 | offset + length > Integer.MAX_VALUE) {
                throw sliceError(bits, offset, length);
            }

            ByteBuffer whole = LWJGL.glMapBuffer(ARRAY_BUFFER, access(bits));

            // Returns null when GL fails to map the buffer
            if (whole == null) {
                return null;
            }

            int start = (int) offset;
            int size = (int) length;
            int capacity = whole.capacity();

            // Returns the whole mapping for a range that covers it
            if (start == 0 & size == capacity) {
                return whole;
            }

            // Unmaps the buffer and rejects a range end past the mapping
            if (capacity - start < size) {
                throw pastMapping(offset + length, capacity);
            }

            return whole.slice(start, size).order(whole.order());
        }

        // Returns the glMapBuffer access that matches the read and write bits
        private static int access(int bits) {
            return switch (bits & (GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT)) {
                case GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT -> GL15.GL_READ_WRITE;
                case GL30.GL_MAP_WRITE_BIT -> GL15.GL_WRITE_ONLY;
                default -> GL15.GL_READ_ONLY;
            };
        }

        // Returns the refusal for flags or a range that the whole buffer strategy rejects before mapping
        private static RuntimeException sliceError(int bits, long offset, long length) {
            if ((bits & GL30.GL_MAP_FLUSH_EXPLICIT_BIT) != 0) {
                return new UnsupportedOperationException("Explicit flush not supported for MAP_FULL_AND_SLICE strategy");
            }

            return new IllegalArgumentException("Map range ends past " + Integer.MAX_VALUE + ": " + (offset + length));
        }

        // Unmaps the array buffer and returns the refusal of a range end past the mapping with any unmap failure suppressed
        private static IllegalArgumentException pastMapping(long end, int capacity) {
            IllegalArgumentException refusal = new IllegalArgumentException("Map range ends past the buffer: " + end + " > " + capacity);

            try {
                LWJGL.glUnmapBuffer(ARRAY_BUFFER);
            } catch (Throwable unmapFailure) {
                refusal.addSuppressed(unmapFailure);
            }

            return refusal;
        }
    };

    // GL enum of the array binding point that holds the buffer to map
    private static final int ARRAY_BUFFER = GL15.GL_ARRAY_BUFFER;

    // Maps the given number of bytes from the offset of the buffer bound to the array binding point and returns null when GL fails
    public abstract ByteBuffer mapBufferRange(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags);

    // Returns the range strategy when the context has GL 3.0 or ARB_map_buffer_range and the whole buffer strategy otherwise
    public static BufferMapRangeFunctions pickBest(RenderDevice device) {
        return LWJGL.isOpenGLVersionSupported(3, 0) || LWJGL.isExtensionSupported(GLExtension.ARB_map_buffer_range) ? CORE : MAP_FULL_AND_SLICE;
    }

    // Rejects a null argument or a negative or overflowing range or a length past the int limit or a deleted buffer and returns the flag bits
    private static int requireMap(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
        Objects.requireNonNull(buffer, "Buffer must not be null");
        int bits = Objects.requireNonNull(flags, "Flags must not be null").getBitField();

        // Rejects a negative offset or length or a range end past the long limit or a length past the int limit
        if ((offset | length | (offset + length) | (Integer.MAX_VALUE - length)) < 0L) {
            throw rangeError(offset, length);
        }

        // Rejects a deleted buffer
        buffer.handle();

        return bits;
    }

    // Returns the refusal for a range that the shared checks reject
    private static IllegalArgumentException rangeError(long offset, long length) {
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
}
