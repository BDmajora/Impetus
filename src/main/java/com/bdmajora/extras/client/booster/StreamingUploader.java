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

// Uploads immediate mode vertex data through one shared buffer.
public final class StreamingUploader {
    // Whether uploads are streamed.
    public static boolean enabled;
    // Whether vertex arrays are cached for each format.
    public static boolean formatCache;

    // Largest number of cached vertex arrays.
    static final int MAX_CACHED_FORMATS = 64;
    // Vertex array of each format.
    private static final Map<VertexFormat, Integer> VERTEX_ARRAYS = new HashMap<>();

    // Number of regions of the buffer.
    private static final int REGIONS = 4;
    // Size of one region.
    private static final int REGION_SIZE = 4 << 20;
    // Size of the whole buffer.
    private static final int CAPACITY = REGIONS * REGION_SIZE;

    // Buffer name or minus one.
    private static int buffer = -1;
    // Whether streaming failed for good.
    private static boolean unavailable;
    // Whether the buffer is mapped for good.
    private static boolean persistent;
    // Mapped memory of the buffer.
    private static ByteBuffer mapped;
    // Fence of each region.
    private static final long[] fences = new long[REGIONS];
    // Region in use.
    private static int region;
    // Write position inside the region.
    private static int cursor;

    // Prevents instantiation
    private StreamingUploader() {
    }

    // Uploads the vertices and draws them and returns whether it handled the draw.
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
        long wide = (long) vertexCount * stride;
        if (stride <= 0 || wide > (persistent ? REGION_SIZE : CAPACITY)) {
            return false;
        }
        int bytes = (int) wide;

        if (buffer == -1 && !initialize()) {
            return false;
        }

        int vertexArray = formatCache ? vertexArrayFor(format) : 0;
        int alignment = vertexArray != 0 ? stride : 4;

        if (persistent && (long) bytes + alignment - 1 > REGION_SIZE) {
            return false;
        }

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

    // Returns whether the region has room and moves to the next region when it does not.
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

    // Copies the data into the mapped buffer and returns its offset.
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

    // Returns whether the graphics card finished with the region.
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

    // Copies the data into the buffer and returns its offset.
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

    // Returns the value rounded up to a multiple of the alignment.
    static long alignUp(long value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        long remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    // Returns the cached vertex array of the format or zero.
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

    // Creates the buffer and chooses the upload method.
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

    // Sets up the attribute of the element.
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

    // Turns off the attribute of the element.
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
