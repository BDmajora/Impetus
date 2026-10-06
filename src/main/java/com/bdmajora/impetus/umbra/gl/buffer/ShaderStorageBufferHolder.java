package com.bdmajora.impetus.umbra.gl.buffer;

import org.lwjgl.opengl.GL15;
import com.bdmajora.impetus.lwjgl.GLExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The shader storage buffers a pack declares (`bufferObject.<index> = <byteSize>` or the screen-relative form); zero-initialised, bound every frame, and PERSISTENT across frames since packs accumulate history in them
public final class ShaderStorageBufferHolder {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // GL43 constants spelled out rather than imported; a hard dependency on the generated constant classes is not worth two values
    private static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    private static final int GL_MAX_SHADER_STORAGE_BLOCK_SIZE = 0x90DE;
    private static final int GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS = 0x90DD;
    private static final int GL_NO_ERROR = 0;

    // NVX_gpu_memory_info's free-video-memory query in KiB; NVIDIA-only, and the closest thing to a portable availability query
    private static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;

    // What Iris assumes when the vendor query is unavailable, matching its IrisRenderSystem.getVRAM
    private static final long ASSUMED_VRAM_BYTES = 4L * 1024 * 1024 * 1024;

    // Fill descriptor for the server-side zero, one unsigned byte per byte so the clear covers exactly the allocation
    private static final int GL_R8 = 0x8229;
    private static final int GL_RED = 0x1903;
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    // Used ONLY when the driver will not report GL_MAX_SHADER_STORAGE_BLOCK_SIZE; as a hard cap it rejected Complementary's 773 MiB reflection buffer, leaving binding 0 empty and reflective surfaces glowing from undefined blockDataSSBO reads
    private static final long FALLBACK_MAX_BUFFER_BYTES = 512L * 1024 * 1024;

    // Host-staged zero-fill granularity where glClearBufferData is unavailable, chunked so a 773 MiB buffer needs no 773 MiB host staging
    private static final int ZERO_FILL_CHUNK_BYTES = 4 * 1024 * 1024;

    // Capability answers resolved once against a live context; boxed so null means "not yet queried", distinct from false
    private static Boolean immutableStorageAvailable;
    private static Boolean serverSideClearAvailable;

    // The driver's per-buffer ceiling, queried once against a live context; 0 until then
    private static long cachedMaxBufferBytes;

    // The driver's count of indexed shader-storage binding points, queried once; 0 until then
    private static int cachedMaxBindings;

    // Every buffer created and not yet deleted, id -> byte size; Iris keeps the same registry since destroy() is not guaranteed and a replaced pipeline would strand close to a gigabyte with no reference to reclaim it
    private static final Map<Integer, Long> ACTIVE_BUFFERS = new LinkedHashMap<>();

    public static final class Definition {
        final int index;
        final long size;
        final boolean relative;
        final float scaleX, scaleY;

        public Definition(int index, long size, boolean relative, float scaleX, float scaleY) {
            this.index = index;
            this.size = size;
            this.relative = relative;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        long byteSize(int width, int height) {
            if (!this.relative) {
                return this.size;
            }
            long w = Math.max(1, (long) Math.ceil(width * (double) this.scaleX));
            long h = Math.max(1, (long) Math.ceil(height * (double) this.scaleY));
            return this.size * w * h;
        }
    }

    private final Map<Integer, Definition> definitions;
    private final Map<Integer, Integer> buffers = new LinkedHashMap<>();
    private int width = -1, height = -1;
    private boolean anyRelative;

    public ShaderStorageBufferHolder(Map<Integer, Definition> definitions, int width, int height) {
        this.definitions = definitions;
        for (Definition definition : definitions.values()) {
            this.anyRelative |= definition.relative;
        }
        allocate(width, height);
    }

    // Whether the pack declared any buffers at all
    public boolean isEmpty() {
        return this.definitions.isEmpty();
    }

    // The GL buffer id bound at that index, or -1 when the pack declared none there
    public int getBufferId(int index) {
        Integer buffer = this.buffers.get(index);
        return buffer != null ? buffer : -1;
    }

    // Recreates the screen-relative buffers at the new size; fixed-size buffers are left alone so a pack's accumulated history survives a resize
    public void onResize(int newWidth, int newHeight) {
        if (!this.anyRelative || (newWidth == this.width && newHeight == this.height)) {
            this.width = newWidth;
            this.height = newHeight;
            return;
        }
        allocate(newWidth, newHeight);
    }

    // Rebinds every buffer to its declared index; cheap enough per frame rather than tracking whether anything unbound them
    public void bindAll() {
        this.definitions.keySet().forEach(index -> {
            Integer buffer = this.buffers.get(index);
            // A declared index whose allocation failed is bound to 0 rather than left alone, so its reads are consistent instead of picking up another pass's leftovers
            LWJGL.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, buffer != null ? buffer : 0);
        });
    }

    // Frees every buffer
    public void destroy() {
        for (int buffer : this.buffers.values()) {
            LWJGL.glDeleteBuffers(buffer);
            ACTIVE_BUFFERS.remove(buffer);
        }
        this.buffers.clear();
    }

    // Deletes any buffer whose owning holder never destroyed it and reports the total; Iris does the same at teardown since at Complementary's settings each leak is hundreds of megabytes
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

    // Sizes screen-relative buffers for the current resolution; fixed ones are unaffected
    private void allocate(int newWidth, int newHeight) {
        for (Definition definition : this.definitions.values()) {
            // Fixed-size buffers survive resizes (they may hold accumulated history data).
            if (this.buffers.containsKey(definition.index) && !definition.relative) {
                continue;
            }

            // Umbra rejects an out-of-range binding index outright; glBindBufferBase would otherwise raise GL_INVALID_VALUE every frame for a binding no program can reach
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
                // Not recoverable: the pack's shaders still declare the block and will read it regardless.
                LOGGER.error("[Umbra] bufferObject.{} requests {} bytes, above this driver's per-buffer limit of {}; "
                                + "shaders reading that block will see undefined data",
                        definition.index, bytes, limit);
                continue;
            }

            // GL_MAX_SHADER_STORAGE_BLOCK_SIZE is a *format* ceiling, not availability (NVIDIA reports gigabytes regardless of free VRAM), so gate on free VRAM like Umbra does; without it Complementary's 773 MiB reflection buffer was allocated beside ~1.1 GiB of lighting volumes unchecked
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
            LWJGL.glGetError(); // discard anything already pending so the check below is about this allocation
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

    // The driver's own ceiling for a single shader storage block, the only limit that applies (any cap of ours has already been wrong once); queried once, and glGetInteger saturates at Integer.MAX_VALUE for larger 64-bit values, still past any real request
    private static long maxBufferBytes() {
        if (cachedMaxBufferBytes > 0) {
            return cachedMaxBufferBytes;
        }
        LWJGL.glGetError(); // a pending error would make the query result ambiguous
        int reported = LWJGL.glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        if (LWJGL.glGetError() != GL_NO_ERROR || reported == 0) {
            cachedMaxBufferBytes = FALLBACK_MAX_BUFFER_BYTES;
            LOGGER.warn("[Umbra] Driver did not report GL_MAX_SHADER_STORAGE_BLOCK_SIZE; capping buffers at {} bytes",
                    cachedMaxBufferBytes);
        } else {
            // Negative means the 64-bit limit overflowed the int query, i.e. it is at least Integer.MAX_VALUE.
            cachedMaxBufferBytes = reported < 0 ? Integer.MAX_VALUE : reported;
        }
        return cachedMaxBufferBytes;
    }

    // How many indexed GL_SHADER_STORAGE_BUFFER binding points the driver offers, queried once; a pack asking past this is refused with a message naming the index
    private static int maxBindings() {
        if (cachedMaxBindings > 0) {
            return cachedMaxBindings;
        }
        LWJGL.glGetError();
        int reported = LWJGL.glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        // GL 4.3 guarantees at least 8; fall back to that rather than to zero, which would reject every buffer.
        cachedMaxBindings = (LWJGL.glGetError() != GL_NO_ERROR || reported <= 0) ? 8 : reported;
        return cachedMaxBindings;
    }

    // Free video memory in bytes like Iris's getVRAM(): NVX_gpu_memory_info (NVIDIA, KiB) or Iris's flat 4 GiB assumption; CURRENT free rather than total, since render targets, shadow maps and custom images are already resident when a holder is built
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

    // Allocates the bound SSBO preferring immutable GPU-only storage with flags 0 as Iris does (no client map/read/write, so the driver keeps it in VRAM; the old GL_DYNAMIC_DRAW hint cost a 773 MiB host mirror); immutable only when the server-side clear also exists, since flags 0 rejects glBufferSubData and 1.12.2 contexts may predate either
    private static void allocateStorage(long bytes) {
        if (immutableStorageAvailable == null) {
            immutableStorageAvailable = (LWJGL.isOpenGLVersionSupported(4, 4)
                    || LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage))
                    && canClearServerSide();
        }
        if (immutableStorageAvailable) {
            LWJGL.glBufferStorage(GL_SHADER_STORAGE_BUFFER, bytes, 0);
        } else {
            // Still not GL_DYNAMIC_DRAW: this buffer is written and read by the GPU and never by the client.
            LWJGL.glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL15.GL_STATIC_COPY);
        }
    }

    // Zeroes the bound SSBO, since fresh storage is UNDEFINED and a pack only writes entries it visits; glClearBufferData server-side like Iris, else the chunked glBufferSubData fallback, which needs the client-writable allocation allocateStorage already made there
    private static void zeroFill(long bytes) {
        if (canClearServerSide()) {
            // One texel of source data per GL_R8/GL_RED/GL_UNSIGNED_BYTE; LWJGL 2 calls MemoryUtil.getAddress and checkBuffer(data, 1), so unlike Umbra on LWJGL 3 it cannot pass null for "clear to zero"
            ByteBuffer zero = ByteBuffer.allocateDirect(1); // allocateDirect is already zeroed
            LWJGL.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R8, GL_RED, GL_UNSIGNED_BYTE, zero);
            return;
        }
        int chunk = (int) Math.min(bytes, ZERO_FILL_CHUNK_BYTES);
        ByteBuffer zeros = ByteBuffer.allocateDirect(chunk); // allocateDirect is already zeroed
        for (long offset = 0; offset < bytes; offset += chunk) {
            zeros.clear();
            zeros.limit((int) Math.min(chunk, bytes - offset));
            LWJGL.glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, zeros);
        }
    }

    // glClearBufferData is GL 4.3 core like SSBOs themselves, so any driver running such a pack has it; queried anyway because LWJGL 2 THROWS from checkFunctionAddress on a missing entry point
    private static boolean canClearServerSide() {
        if (serverSideClearAvailable == null) {
            serverSideClearAvailable = LWJGL.isOpenGLVersionSupported(4, 3)
                    || LWJGL.isExtensionSupported(GLExtension.ARB_clear_buffer_object);
        }
        return serverSideClearAvailable;
    }

    // Parses every bufferObject.<index> directive out of the raw properties, in both the fixed-size and screen-relative forms
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
