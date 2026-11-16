package com.bdmajora.impetus.umbra.gl.buffer;

import org.lwjgl.opengl.GL15;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Shader storage buffers of a pack sized from its directives and the screen.
public final class ShaderStorageBufferHolder {
    // Logger of allocation failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // GL enum of the shader storage buffer target.
    private static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    // GL enum of the block size limit.
    private static final int GL_MAX_SHADER_STORAGE_BLOCK_SIZE = 0x90DE;
    // GL enum of the binding limit.
    private static final int GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS = 0x90DD;
    // GL enum of no error.
    private static final int GL_NO_ERROR = 0;

    // GL enum of the free video memory in kilobytes.
    private static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;

    // Free video memory assumed without the memory info extension.
    private static final long ASSUMED_VRAM_BYTES = 4L * 1024 * 1024 * 1024;

    // GL enum of the R8 format.
    private static final int GL_R8 = 0x8229;
    // GL enum of the red pixel format.
    private static final int GL_RED = 0x1903;
    // GL enum of unsigned bytes.
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    // Buffer size limit used when the driver reports none.
    private static final long FALLBACK_MAX_BUFFER_BYTES = 512L * 1024 * 1024;

    // Bytes of one upload of a client side zero fill.
    private static final int ZERO_FILL_CHUNK_BYTES = 4 * 1024 * 1024;

    // Immutable storage support or null before the check.
    private static Boolean immutableStorageAvailable;
    // Server side clear support or null before the check.
    private static Boolean serverSideClearAvailable;

    // Buffer size limit or zero before the query.
    private static long cachedMaxBufferBytes;

    // Binding limit or zero before the query.
    private static int cachedMaxBindings;

    // Sizes of the live buffers of every holder.
    private static final Map<Integer, Long> ACTIVE_BUFFERS = new LinkedHashMap<>();

    // Directive of one buffer.
    public static final class Definition {
        // Binding index.
        final int index;
        // Bytes or bytes per scaled pixel.
        final long size;
        // Screen relative state of the size.
        final boolean relative;
        // Horizontal and vertical screen scales.
        final float scaleX, scaleY;

        // Creates a directive.
        public Definition(int index, long size, boolean relative, float scaleX, float scaleY) {
            this.index = index;
            this.size = size;
            this.relative = relative;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        // Returns the bytes of the buffer at a screen size and saturates on overflow.
        long byteSize(int width, int height) {
            if (!this.relative) {
                return this.size;
            }
            long w = Math.max(1, (long) Math.ceil(width * (double) this.scaleX));
            long h = Math.max(1, (long) Math.ceil(height * (double) this.scaleY));
            // Saturates a size that overflows so the limit check rejects it
            try {
                return Math.multiplyExact(Math.multiplyExact(this.size, w), h);
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
        }
    }

    // Directives by binding index.
    private final Map<Integer, Definition> definitions;
    // GL names of the buffers by binding index.
    private final Map<Integer, Integer> buffers = new LinkedHashMap<>();
    // Screen size of the last allocation.
    private int width = -1, height = -1;
    // Screen relative state of any directive.
    private boolean anyRelative;

    // Creates and binds the buffers at a screen size.
    public ShaderStorageBufferHolder(Map<Integer, Definition> definitions, int width, int height) {
        this.definitions = definitions;
        for (Definition definition : definitions.values()) {
            this.anyRelative |= definition.relative;
        }
        allocate(width, height);
    }

    // Returns whether the pack declares no buffer.
    public boolean isEmpty() {
        return this.definitions.isEmpty();
    }

    // Returns the GL name of a buffer or minus one.
    public int getBufferId(int index) {
        Integer buffer = this.buffers.get(index);
        return buffer != null ? buffer : -1;
    }

    // Reallocates the screen relative buffers when the screen size changes.
    public void onResize(int newWidth, int newHeight) {
        if (!this.anyRelative || (newWidth == this.width && newHeight == this.height)) {
            this.width = newWidth;
            this.height = newHeight;
            return;
        }
        allocate(newWidth, newHeight);
    }

    // Binds every buffer to its index.
    public void bindAll() {
        for (Integer index : this.definitions.keySet()) {
            Integer buffer = this.buffers.get(index);
            LWJGL.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, buffer != null ? buffer : 0);
        }
    }

    // Deletes every buffer of the holder.
    public void destroy() {
        for (int buffer : this.buffers.values()) {
            LWJGL.glDeleteBuffers(buffer);
            ACTIVE_BUFFERS.remove(buffer);
        }
        this.buffers.clear();
    }

    // Deletes every live buffer of every holder and reports the leak.
    public static void forceDeleteBuffers() {
        if (ACTIVE_BUFFERS.isEmpty()) {
            return;
        }
        long leaked = ACTIVE_BUFFERS.values().stream().mapToLong(Long::longValue).sum();
        LOGGER.warn("[Umbra] {} shader storage buffer(s) totalling {} bytes were never released by their holder; "
                + "deleting them now", ACTIVE_BUFFERS.size(), leaked);
        ACTIVE_BUFFERS.keySet().forEach(LWJGL::glDeleteBuffers);
        ACTIVE_BUFFERS.clear();
    }

    // Allocates and zeroes every buffer that is missing or relative and skips those that cannot fit.
    private void allocate(int newWidth, int newHeight) {
        for (Definition definition : this.definitions.values()) {
            if (this.buffers.containsKey(definition.index) && !definition.relative) {
                continue;
            }

            int maxBindings = maxBindings();
            if (definition.index < 0 || definition.index >= maxBindings) {
                LOGGER.error("[Umbra] bufferObject.{} asks for shader storage binding {}, but this driver only has {} "
                                + "({}); ignoring it",
                        definition.index, definition.index, maxBindings, "GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS");
                continue;
            }

            long bytes = definition.byteSize(newWidth, newHeight);
            long limit = maxBufferBytes();
            if (bytes <= 0 || bytes > limit) {
                LOGGER.error("[Umbra] bufferObject.{} requests {} bytes, above this driver's per-buffer limit of {}; "
                                + "shaders reading that block will see undefined data",
                        definition.index, bytes, limit);
                continue;
            }

            long availableVram = availableVideoMemoryBytes();
            if (bytes > availableVram) {
                LOGGER.error("[Umbra] bufferObject.{} requests {} bytes but only {} bytes of video memory are free; "
                                + "refusing the allocation. Lower the pack's colored lighting resolution or turn off "
                                + "world-space reflections. Shaders reading that block will see undefined data.",
                        definition.index, bytes, availableVram);
                continue;
            }

            Integer existing = this.buffers.remove(definition.index);
            if (existing != null) {
                LWJGL.glDeleteBuffers(existing);
                ACTIVE_BUFFERS.remove(existing);
            }

            int buffer = LWJGL.glGenBuffers();
            LWJGL.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            LWJGL.glGetError();
            allocateStorage(bytes);
            int error = LWJGL.glGetError();
            if (error != GL_NO_ERROR) {
                LWJGL.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
                LWJGL.glDeleteBuffers(buffer);
                LOGGER.error("[Umbra] bufferObject.{} ({} bytes) failed to allocate (GL error 0x{}); the pack's "
                                + "shaders will read undefined data from that block. Lowering the pack's colored "
                                + "lighting resolution or disabling world-space reflections reduces the request.",
                        definition.index, bytes, Integer.toHexString(error));
                continue;
            }
            zeroFill(bytes);
            LWJGL.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            this.buffers.put(definition.index, buffer);
            ACTIVE_BUFFERS.put(buffer, bytes);
        }

        this.width = newWidth;
        this.height = newHeight;
        bindAll();
    }

    // Returns the buffer size limit of the driver.
    private static long maxBufferBytes() {
        if (cachedMaxBufferBytes > 0) {
            return cachedMaxBufferBytes;
        }
        LWJGL.glGetError();
        int reported = LWJGL.glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        if (LWJGL.glGetError() != GL_NO_ERROR || reported == 0) {
            cachedMaxBufferBytes = FALLBACK_MAX_BUFFER_BYTES;
            LOGGER.warn("[Umbra] Driver did not report GL_MAX_SHADER_STORAGE_BLOCK_SIZE; capping buffers at {} bytes",
                    cachedMaxBufferBytes);
        } else {
            cachedMaxBufferBytes = reported < 0 ? Integer.MAX_VALUE : reported;
        }
        return cachedMaxBufferBytes;
    }

    // Returns the binding limit of the driver.
    private static int maxBindings() {
        if (cachedMaxBindings > 0) {
            return cachedMaxBindings;
        }
        LWJGL.glGetError();
        int reported = LWJGL.glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        cachedMaxBindings = (LWJGL.glGetError() != GL_NO_ERROR || reported <= 0) ? 8 : reported;
        return cachedMaxBindings;
    }

    // Returns the free video memory or an assumed amount.
    private static long availableVideoMemoryBytes() {
        if (!LWJGL.isExtensionSupported(GLExtension.NVX_gpu_memory_info)) {
            return ASSUMED_VRAM_BYTES;
        }
        LWJGL.glGetError();
        int kib = LWJGL.glGetInteger(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
        if (LWJGL.glGetError() != GL_NO_ERROR || kib <= 0) {
            return ASSUMED_VRAM_BYTES;
        }
        return kib * 1024L;
    }

    // Allocates storage for the bound buffer.
    private static void allocateStorage(long bytes) {
        if (immutableStorageAvailable == null) {
            immutableStorageAvailable = (LWJGL.isOpenGLVersionSupported(4, 4)
                    || LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage))
                    && canClearServerSide();
        }
        if (immutableStorageAvailable) {
            LWJGL.glBufferStorage(GL_SHADER_STORAGE_BUFFER, bytes, 0);
        } else {
            LWJGL.glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL15.GL_STATIC_COPY);
        }
    }

    // Clears the bound buffer to zero.
    private static void zeroFill(long bytes) {
        if (canClearServerSide()) {
            LWJGL.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R8, GL_RED, GL_UNSIGNED_BYTE, null);
            return;
        }
        int chunk = (int) Math.min(bytes, ZERO_FILL_CHUNK_BYTES);
        ByteBuffer zeros = ByteBuffer.allocateDirect(chunk);
        for (long offset = 0; offset < bytes; offset += chunk) {
            zeros.clear();
            zeros.limit((int) Math.min(chunk, bytes - offset));
            LWJGL.glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, zeros);
        }
    }

    // Returns whether buffers can be cleared on the GPU.
    private static boolean canClearServerSide() {
        if (serverSideClearAvailable == null) {
            serverSideClearAvailable = LWJGL.isOpenGLVersionSupported(4, 3)
                    || LWJGL.isExtensionSupported(GLExtension.ARB_clear_buffer_object);
        }
        return serverSideClearAvailable;
    }

    // Parses the buffer directives and skips malformed ones.
    public static Map<Integer, Definition> parseDefinitions(Map<String, String> rawProperties) {
        Map<Integer, Definition> definitions = new LinkedHashMap<>();
        rawProperties.forEach((key, value) -> {
            if (!key.startsWith("bufferObject.")) {
                return;
            }
            try {
                int index = Integer.parseInt(key.substring("bufferObject.".length()));
                String[] parts = value.trim().split("\\s+");
                long size = Long.parseLong(parts[0]);
                boolean relative = parts.length >= 2 && Boolean.parseBoolean(parts[1]);
                float scaleX = parts.length >= 3 ? Float.parseFloat(parts[2]) : 1.0f;
                float scaleY = parts.length >= 4 ? Float.parseFloat(parts[3]) : 1.0f;
                definitions.put(index, new Definition(index, size, relative, scaleX, scaleY));
            } catch (RuntimeException e) {
                LOGGER.warn("[Umbra] Malformed bufferObject directive '{} = {}': {}", key, value, e.toString());
            }
        });
        return definitions;
    }
}
