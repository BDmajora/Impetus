package com.bdmajora.extras.client.booster;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Immediate-mode draws (GUI, items, entities, particles, block entities) through one streamed vertex buffer instead of client-side arrays: vanilla hands the driver a pointer into Java memory for every Tessellator draw, which the driver must copy before it can return and which the slower drivers route through a compatibility path. With buffer storage the bytes are memcpy'd straight into a persistently mapped, coherent ring split into fenced regions, so no driver call sits between the copy and the draw; without it the ring is a classic orphaned STREAM_DRAW buffer. Attribute pointers become offsets, and the array buffer is left unbound afterwards, matching what the chunk renderer expects
public final class StreamingUploader {
    public static boolean enabled;
    // GPUBooster's vertex format cache: one VAO per vertex layout with every pointer aimed at the ring, so a draw is bind, draw at the right first vertex, unbind
    public static boolean formatCache;

    // Formats past this many keep the per-draw pointer path, so a mod minting layouts per draw cannot grow the cache without bound
    static final int MAX_CACHED_FORMATS = 64;
    private static final Map<VertexFormat, Integer> VERTEX_ARRAYS = new HashMap<>();

    // Four regions of four mebibytes: a region is only reused after three others filled, and then only once its fence says the GPU is done reading it. The ring is never waited on: a region still in flight sends that one draw down vanilla's path instead (see reserve), since a blocking wait here serialises the CPU behind the GPU for every wrap, which with a heavy shader pack turned a busy scene into a stall per few megabytes of immediate-mode geometry
    private static final int REGIONS = 4;
    private static final int REGION_SIZE = 4 << 20;
    private static final int CAPACITY = REGIONS * REGION_SIZE;

    private static int buffer = -1;
    private static boolean unavailable;
    private static boolean persistent;
    private static ByteBuffer mapped;
    // LWJGL3 fence handles; 0 is "no fence" (glFenceSync never returns it for a live sync)
    private static final long[] fences = new long[REGIONS];
    private static int region;
    private static int cursor;

    private StreamingUploader() {
    }

    // Draws the builder through the ring; false hands the draw back to vanilla untouched
    public static boolean draw(BufferBuilder builder) {
        if (!enabled || unavailable) {
            return false;
        }

        int vertexCount = builder.getVertexCount();
        if (vertexCount <= 0) {
            return false;
        }

        VertexFormat format = builder.getVertexFormat();
        int stride = format.getSize();
        int bytes = vertexCount * stride;
        if (bytes > (persistent ? REGION_SIZE : CAPACITY)) {
            return false;
        }

        if (buffer == -1 && !initialize()) {
            return false;
        }

        // A cached layout reads the ring from offset zero, so its data has to start on a whole vertex
        int vertexArray = formatCache ? vertexArrayFor(format) : 0;
        int alignment = vertexArray != 0 ? stride : 4;

        // Persistent ring: find room before touching any state, so a region the GPU still reads costs nothing but this check and vanilla draws the call from client memory
        if (persistent && !reserve(bytes + alignment - 1)) {
            return false;
        }

        ByteBuffer data = builder.getByteBuffer();
        data.position(0);
        data.limit(bytes);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        long base = persistent ? writePersistent(data, bytes, alignment) : writeOrphaning(data, bytes, alignment);
        data.limit(data.capacity());

        if (vertexArray != 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(vertexArray);
            GlStateManager.glDrawArrays(builder.getDrawMode(), (int) (base / stride), vertexCount);
            GL30.glBindVertexArray(0);
            // The colour array leaves the current colour undefined, as on the pointer path
            if (format.hasColor()) {
                GlStateManager.resetColor();
            }
            builder.reset();
            return true;
        }

        List<VertexFormatElement> elements = format.getElements();
        int count = elements.size();
        for (int i = 0; i < count; i++) {
            enable(elements.get(i), base + format.getOffset(i), stride);
        }

        GlStateManager.glDrawArrays(builder.getDrawMode(), 0, vertexCount);

        for (int i = 0; i < count; i++) {
            disable(elements.get(i));
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        builder.reset();
        return true;
    }

    // Makes sure the current region can take the draw, fencing the full one and moving on when it cannot; false when the next region's fence has not signalled yet, in which case nothing changes and the caller draws through vanilla. The fence is only checked, never waited on
    private static boolean reserve(int bytes) {
        if (cursor + bytes <= REGION_SIZE) {
            return true;
        }
        int next = (region + 1) % REGIONS;
        if (!regionFree(next)) {
            return false;
        }
        if (fences[region] == 0L) {
            fences[region] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        }
        region = next;
        cursor = 0;
        return true;
    }

    // Appends into the current region on the given byte alignment; reserve() has already made room for the padding too
    private static long writePersistent(ByteBuffer data, int bytes, int alignment) {
        long regionStart = (long) region * REGION_SIZE;
        long base = alignUp(regionStart + cursor, alignment);
        cursor = (int) (base - regionStart);
        mapped.clear();
        mapped.position((int) base);
        mapped.put(data);
        cursor = (cursor + bytes + 3) & ~3;
        return base;
    }

    // Whether the GPU has finished every draw that read this region: a zero-timeout poll of its fence, with the flush bit so the fence itself is submitted and can signal. A failed wait retires the ring for the session
    private static boolean regionFree(int index) {
        long fence = fences[index];
        if (fence == 0L) {
            return true;
        }

        int result = GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 0L);
        if (result == GL32.GL_TIMEOUT_EXPIRED) {
            return false;
        }
        GL32.glDeleteSync(fence);
        fences[index] = 0L;
        if (result == GL32.GL_WAIT_FAILED) {
            Extras.LOGGER.warn("Fence wait failed on the streamed vertex buffer; falling back to vanilla uploads");
            unavailable = true;
            return false;
        }
        return true;
    }

    // Orphans the whole buffer when the ring wraps so the driver hands over fresh storage rather than waiting on in-flight draws
    private static long writeOrphaning(ByteBuffer data, int bytes, int alignment) {
        cursor = (int) alignUp(cursor, alignment);
        if (cursor + bytes > CAPACITY) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, CAPACITY, GL15.GL_STREAM_DRAW);
            cursor = 0;
        }

        long base = cursor;
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, base, data);
        cursor = (cursor + bytes + 3) & ~3;
        return base;
    }

    // Rounds up to a multiple of the alignment, which need not be a power of two since vertex strides are not
    static long alignUp(long value, int alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    // The cached VAO for a layout, built the first time it is drawn with every attribute aimed at the ring; 0 when VAOs are missing or the cache is full, which keeps the pointer path
    private static int vertexArrayFor(VertexFormat format) {
        Integer cached = VERTEX_ARRAYS.get(format);
        if (cached != null) {
            return cached;
        }
        if (VERTEX_ARRAYS.size() >= MAX_CACHED_FORMATS || !GL.getCapabilities().OpenGL30) {
            return 0;
        }

        int vertexArray = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        int stride = format.getSize();
        List<VertexFormatElement> elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) {
            enable(elements.get(i), format.getOffset(i), stride);
        }
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        VERTEX_ARRAYS.put(format, vertexArray);
        return vertexArray;
    }

    // Persistent mapping needs buffer storage (4.4 or its ARB form), map-range (3.0) and fences (3.2); anything with core 1.5 buffers gets the orphaning ring. The ARB-suffixed VBO path vanilla keeps for pre-2003 hardware is not worth a second code path
    private static boolean initialize() {
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.OpenGL15) {
            unavailable = true;
            return false;
        }

        buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);

        boolean storage = caps.OpenGL44 || caps.GL_ARB_buffer_storage;
        boolean sync = caps.OpenGL32 || caps.GL_ARB_sync;
        if (storage && sync && caps.OpenGL30) {
            int flags = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
            if (caps.OpenGL44) {
                GL44.glBufferStorage(GL15.GL_ARRAY_BUFFER, CAPACITY, flags);
            } else {
                ARBBufferStorage.glBufferStorage(GL15.GL_ARRAY_BUFFER, CAPACITY, flags);
            }
            mapped = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, CAPACITY, flags, null);
            persistent = mapped != null;
            if (!persistent) {
                // Immutable storage cannot be re-specified, so start over with a mutable buffer for the orphaning path
                GL15.glDeleteBuffers(buffer);
                buffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            }
        }

        if (!persistent) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, CAPACITY, GL15.GL_STREAM_DRAW);
        }
        cursor = 0;
        region = 0;
        Extras.LOGGER.info("Streamed vertex uploads: {} ring of {} KiB", persistent ? "persistently mapped" : "orphaning", CAPACITY >> 10);
        return true;
    }

    // Mirrors ForgeHooksClient.preDraw with buffer offsets in place of client pointers
    private static void enable(VertexFormatElement element, long offset, int stride) {
        int count = element.getElementCount();
        int type = element.getType().getGlConstant();
        switch (element.getUsage()) {
            case POSITION -> {
                GL11.glVertexPointer(count, type, stride, offset);
                GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            }
            case NORMAL -> {
                GL11.glNormalPointer(type, stride, offset);
                GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            }
            case COLOR -> {
                GL11.glColorPointer(count, type, stride, offset);
                GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            }
            case UV -> {
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + element.getIndex());
                GL11.glTexCoordPointer(count, type, stride, offset);
                GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            case GENERIC -> {
                GL20.glEnableVertexAttribArray(element.getIndex());
                GL20.glVertexAttribPointer(element.getIndex(), count, type, false, stride, offset);
            }
            default -> {
            }
        }
    }

    // Mirrors ForgeHooksClient.postDraw, including its colour reset after a colour array
    private static void disable(VertexFormatElement element) {
        switch (element.getUsage()) {
            case POSITION -> GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            case NORMAL -> GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            case COLOR -> {
                GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                GlStateManager.resetColor();
            }
            case UV -> {
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + element.getIndex());
                GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            case GENERIC -> GL20.glDisableVertexAttribArray(element.getIndex());
            default -> {
            }
        }
    }
}
