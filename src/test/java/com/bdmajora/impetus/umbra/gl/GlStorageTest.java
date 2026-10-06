package com.bdmajora.impetus.umbra.gl;

import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.gl.texture.PlainTexture;
import com.bdmajora.impetus.umbra.gl.texture.PngTexture;
import com.bdmajora.impetus.umbra.gl.texture.StubShadowMap;
import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureFilteringData;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlStorageTest {
    private static final int SSBO = 0x90D2;

    @BeforeEach
    @AfterEach
    void forgetDriverAnswers() {
        Class<?> holder = ShaderStorageBufferHolder.class;
        Mixins.set(holder, "immutableStorageAvailable", null);
        Mixins.set(holder, "serverSideClearAvailable", null);
        Mixins.set(holder, "cachedMaxBufferBytes", 0L);
        Mixins.set(holder, "cachedMaxBindings", 0);
        Mixins.<Map<?, ?>>get(holder, "ACTIVE_BUFFERS").clear();
    }

    @Test
    void everyFormatHasALegalClientPair() {
        for (InternalTextureFormat format : InternalTextureFormat.values()) {
            assertTrue(format.getInternalFormat() > 0, format.name());
            assertTrue(format.getPixelFormat() > 0, format.name());
            assertTrue(format.getPixelType() > 0, format.name());
            boolean integerClient = java.util.Set.of(GL30.GL_RED_INTEGER, GL30.GL_RG_INTEGER, GL30.GL_RGB_INTEGER,
                    GL30.GL_RGBA_INTEGER).contains(format.getPixelFormat());
            assertEquals(integerClient, format.isInteger(), format.name());
        }
        assertEquals(InternalTextureFormat.RGBA16F, InternalTextureFormat.fromString("rgba16f").orElseThrow());
        assertTrue(InternalTextureFormat.fromString("RGBA64").isEmpty());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80FF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void textureObjectsUploadAndFree() throws IOException {
        assertNotNull(Mixins.construct(TextureParameters.class));
        TextureParameters.setFilter2D(GL11.GL_LINEAR);
        verify(TestGl.gl()).glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        PlainTexture plain = new PlainTexture(128, 128, 255, 255);
        assertEquals(plain.getGlId(), plain.getTextureId());
        plain.destroy();
        StubShadowMap stub = new StubShadowMap();
        assertEquals(stub.getGlId(), stub.getTextureId());
        stub.destroy();
        verify(TestGl.gl(), times(2)).glDeleteTextures(anyInt());

        PngTexture blurred = new PngTexture(new CustomTextureData.PngData(new TextureFilteringData(true, true), png(2, 3)));
        assertEquals(2, blurred.getWidth());
        assertEquals(3, blurred.getHeight());
        assertEquals(blurred.getGlId(), blurred.getTextureId());
        verify(TestGl.gl()).glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        PngTexture sharp = new PngTexture(new CustomTextureData.PngData(new TextureFilteringData(false, false), png(1, 1)));
        verify(TestGl.gl(), atLeast(1)).glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        sharp.destroy();
        blurred.destroy();
        assertThrows(IOException.class, () -> new PngTexture(new CustomTextureData.PngData(new TextureFilteringData(false, false), new byte[] {1, 2, 3})));
    }

    @Test
    void framebuffersTrackLogicalAndPhysicalAttachments() {
        when(TestGl.gl().glGetInteger(GL30.GL_MAX_DRAW_BUFFERS)).thenReturn(4);
        when(TestGl.gl().glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)).thenReturn(4);
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        framebuffer.bind();
        framebuffer.bindAsReadBuffer();
        framebuffer.bindAsDrawBuffer();
        verify(TestGl.gl()).glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.getGlId());
        framebuffer.addColorAttachment(0, 100);
        framebuffer.addColorAttachment(5, 1, 105);
        // The same pairing again is harmless
        framebuffer.addColorAttachment(5, 1, 105);
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(16, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(6, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(6, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(5, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.addColorAttachment(6, 1, 1));
        assertEquals(105, framebuffer.getColorAttachment(5));
        assertEquals(0, framebuffer.getColorAttachment(-1));
        assertEquals(0, framebuffer.getColorAttachment(16));
        assertFalse(framebuffer.hasDepthAttachment());
        framebuffer.addDepthAttachment(200);
        assertTrue(framebuffer.hasDepthAttachment());

        // Keeping only colortex5 detaches colortex0's texture from its slot
        framebuffer.retainColorAttachments(1 << 5);
        verify(TestGl.gl()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        framebuffer.noDrawBuffers();
        framebuffer.drawBuffers(null);
        verify(TestGl.gl(), times(2)).glDrawBuffers(GL11.GL_NONE);
        framebuffer.drawBuffers(new int[] {0, -1, 1});
        verify(TestGl.gl()).glDrawBuffers(any(IntBuffer.class));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.drawBuffers(new int[] {0, 1, 2, 3, 0}));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.drawBuffers(new int[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> framebuffer.drawBuffers(new int[] {2}));
        framebuffer.readBuffer(1);
        verify(TestGl.gl()).glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + 1);
        assertThrows(IllegalArgumentException.class, () -> framebuffer.readBuffer(3));
        assertTrue(framebuffer.isComplete());
        when(TestGl.gl().glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)).thenReturn(0);
        assertFalse(framebuffer.isComplete());
        framebuffer.destroy();
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
    }

    private static Map<Integer, ShaderStorageBufferHolder.Definition> definitions(String... entries) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            raw.put(entries[i], entries[i + 1]);
        }
        return ShaderStorageBufferHolder.parseDefinitions(raw);
    }

    @Test
    void storageBuffersParseAllocateAndResize() {
        Map<Integer, ShaderStorageBufferHolder.Definition> parsed = definitions(
                "bufferObject.0", "1024",
                "bufferObject.1", "16 true 0.5 0.5",
                "bufferObject.2", "8 true",
                "bufferObject.bad", "1",
                "bufferObject.3", "lots",
                "unrelated", "1",
                "bufferObject.20", "64",
                "bufferObject.4", "0",
                "bufferObject.5", "999999999999");
        assertEquals(java.util.List.of(0, 1, 2, 20, 4, 5), java.util.List.copyOf(parsed.keySet()));
        when(TestGl.gl().glGetInteger(0x90DD)).thenReturn(16);
        when(TestGl.gl().glGetInteger(0x90DE)).thenReturn(1 << 30);
        ShaderStorageBufferHolder holder = new ShaderStorageBufferHolder(parsed, 100, 50);
        assertFalse(holder.isEmpty());
        int fixed = holder.getBufferId(0);
        int relative = holder.getBufferId(1);
        assertTrue(fixed > 0 && relative > 0);
        // An index past the driver's bindings, an empty size and one past the per-buffer limit are all refused
        assertEquals(-1, holder.getBufferId(20));
        assertEquals(-1, holder.getBufferId(4));
        assertEquals(-1, holder.getBufferId(5));
        verify(TestGl.gl()).glBufferStorage(SSBO, 1024L, 0);
        verify(TestGl.gl()).glBufferStorage(SSBO, 16L * 50 * 25, 0);
        verify(TestGl.gl(), atLeast(1)).glClearBufferData(eq(SSBO), anyInt(), anyInt(), anyInt(), any(ByteBuffer.class));
        // A failed index still binds, to buffer 0
        verify(TestGl.gl()).glBindBufferBase(SSBO, 20, 0);

        // Same size is a no-op; a new size rebuilds only the screen-relative buffers
        holder.onResize(100, 50);
        holder.onResize(200, 100);
        assertEquals(fixed, holder.getBufferId(0));
        assertNotEquals(relative, holder.getBufferId(1));
        verify(TestGl.gl()).glDeleteBuffers(relative);
        holder.bindAll();

        // A holder never destroyed is cleaned up at teardown
        ShaderStorageBufferHolder.forceDeleteBuffers();
        verify(TestGl.gl()).glDeleteBuffers(fixed);
        holder.destroy();
        ShaderStorageBufferHolder.forceDeleteBuffers();
        ShaderStorageBufferHolder fixedOnly = new ShaderStorageBufferHolder(definitions("bufferObject.0", "8"), 1, 1);
        fixedOnly.onResize(2, 2);
        fixedOnly.destroy();
        assertTrue(new ShaderStorageBufferHolder(Map.of(), 1, 1).isEmpty());
    }

    @Test
    void storageBuffersFallBackOnOlderDrivers() {
        // No limits reported: eight bindings and a 512 MiB cap; no buffer storage or server clear, so the zero fill is staged in chunks
        when(TestGl.gl().isOpenGLVersionSupported(anyInt(), anyInt())).thenReturn(false);
        when(TestGl.gl().isExtensionSupported(any(GLExtension.class))).thenReturn(false);
        ShaderStorageBufferHolder holder = new ShaderStorageBufferHolder(definitions("bufferObject.0", String.valueOf(5 * 1024 * 1024)), 1, 1);
        assertTrue(holder.getBufferId(0) > 0);
        verify(TestGl.gl()).glBufferData(SSBO, 5L * 1024 * 1024, 0x88E6);
        verify(TestGl.gl(), times(2)).glBufferSubData(eq(SSBO), anyLong(), any(ByteBuffer.class));
        holder.destroy();
    }

    @Test
    void storageBuffersRespectFreeVideoMemoryAndAllocationErrors() {
        // NVIDIA reports 1 KiB free, so a 2 KiB buffer is refused
        when(TestGl.gl().glGetInteger(0x9049)).thenReturn(1);
        when(TestGl.gl().glGetInteger(0x90DE)).thenReturn(-1);
        ShaderStorageBufferHolder refused = new ShaderStorageBufferHolder(definitions("bufferObject.0", "2048"), 1, 1);
        assertEquals(-1, refused.getBufferId(0));
        assertEquals((long) Integer.MAX_VALUE, (long) Mixins.<Long>get(ShaderStorageBufferHolder.class, "cachedMaxBufferBytes"));
        // The driver erroring on the query falls back to assumptions
        forgetDriverAnswers();
        when(TestGl.gl().glGetError()).thenReturn(0, 0x500, 0, 0x500, 0, 0x500, 0, 0x505);
        ShaderStorageBufferHolder failed = new ShaderStorageBufferHolder(definitions("bufferObject.0", "64"), 1, 1);
        assertEquals(-1, failed.getBufferId(0));
        verify(TestGl.gl(), never()).glClearBufferData(anyInt(), anyInt(), anyInt(), anyInt(), any(ByteBuffer.class));
        assertEquals(8, (int) Mixins.<Integer>get(ShaderStorageBufferHolder.class, "cachedMaxBindings"));
    }
}
