package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

// Covers the per pass clears of the shadow colour buffers
class ShadowColorClearsTest {
    // Texture names of eight addressable buffers with 0 for unallocated ones
    private static final int[] TEXTURES = {10, 11, 12, 13, 0, 0, 0, 0};

    // GL model of the test
    private PipelineGl gl;
    // Every clear related call in order
    private final List<String> events = new ArrayList<>();
    // Byte buffers passed to glClearTexImage in order
    private final List<ByteBuffer> integerData = new ArrayList<>();
    // Framebuffer whose draw buffer calls are recorded
    private UmbraFramebuffer framebuffer;

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Opens a GL model that records every clear call
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        GlCache.reset();
        this.framebuffer = mock(UmbraFramebuffer.class);
        doAnswer(inv -> this.events.add("draw " + Arrays.toString(inv.<int[]>getArgument(0)))).when(this.framebuffer)
                .drawBuffers(any(int[].class));
        this.gl.gl11.when(() -> GL11.glClearColor(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenAnswer(inv -> {
            this.events.add("colour " + inv.getArgument(0) + " " + inv.getArgument(1) + " " + inv.getArgument(2) + " " + inv.getArgument(3));
            return null;
        });
        doAnswer(inv -> this.events.add("clear " + inv.getArgument(0))).when(TestGl.gl()).glClear(anyInt());
        doAnswer(inv -> {
            ByteBuffer data = inv.getArgument(4);
            this.integerData.add(data);
            this.events.add("texture " + inv.getArgument(0) + " " + inv.getArgument(2) + " " + inv.getArgument(3)
                    + " " + data.getInt(0) + " " + data.getInt(4) + " " + data.getInt(8) + " " + data.getInt(12));
            return null;
        }).when(TestGl.gl()).glClearTexImage(anyInt(), anyInt(), anyInt(), anyInt(), any(ByteBuffer.class));
    }

    // Closes the GL model and resets the caches
    @AfterEach
    void close() {
        GlCache.reset();
        this.gl.close();
    }

    // Returns settings for eight buffers where 1 keeps its contents and 2 is integer and 3 has its own colour
    private static ShadowColorSettings settings() {
        return ShadowColorSettings.parse(new ConstDirectives(String.join("\n",
                "const bool shadowcolor1Clear = false;",
                "const int shadowcolor2Format = R32UI;",
                "const vec4 shadowcolor2ClearColor = vec4(1.9, -1.5, 3000000000.0, 0.0);",
                "const int shadowcolor3Format = RGBA16F;",
                "const vec4 shadowcolor3ClearColor = vec4(0.25, 0.5, 0.75, 1.0);")), ShadowColorSettings.IRIS_BUFFERS);
    }

    // Checks the order and content of every clear with integer clears supported
    @Test
    void eachBufferClearsInIndexOrderAndIntegerFormatsClearTheTexture() {
        ShadowColorClears clears = new ShadowColorClears(settings(), new int[]{0, 1, 2, 3}, TEXTURES, true);
        assertEquals(3, clears.size());
        clears.run(this.framebuffer);
        assertEquals(List.of(
                "draw [0]", "colour 1.0 1.0 1.0 1.0", "clear " + GL11.GL_COLOR_BUFFER_BIT,
                "texture 12 " + GL30.GL_RED_INTEGER + " " + GL11.GL_INT + " 1 -1 " + Integer.MAX_VALUE + " 0",
                "draw [3]", "colour 0.25 0.5 0.75 1.0", "clear " + GL11.GL_COLOR_BUFFER_BIT), this.events);
        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 1.0f}, GlCache.clearColor());
    }

    // Checks that integer data is written in native byte order
    @Test
    void integerColoursUseNativeByteOrder() {
        new ShadowColorClears(settings(), new int[]{2}, TEXTURES, true).run(this.framebuffer);
        ByteBuffer data = this.integerData.get(0);
        assertTrue(data.isDirect());
        assertEquals(1, data.duplicate().order(ByteOrder.nativeOrder()).getInt(0));
        assertEquals(0, data.position());
        assertEquals(16, data.limit());
    }

    // Checks that without integer clear support an integer buffer clears through the framebuffer with its float colour
    @Test
    void withoutIntegerClearSupportIntegerFormatsClearThroughTheFramebuffer() {
        new ShadowColorClears(settings(), new int[]{2}, TEXTURES, false).run(this.framebuffer);
        assertEquals(List.of("draw [2]", "colour 1.9 -1.5 3.0E9 0.0", "clear " + GL11.GL_COLOR_BUFFER_BIT), this.events);
        assertTrue(this.integerData.isEmpty());
    }

    // Checks that repeated runs issue the same calls and reuse the same integer data buffer
    @Test
    void repeatedRunsReuseTheirBuffers() {
        ShadowColorClears clears = new ShadowColorClears(settings(), new int[]{0, 2, 3}, TEXTURES, true);
        clears.run(this.framebuffer);
        List<String> first = new ArrayList<>(this.events);
        this.events.clear();
        GlCache.reset();
        clears.run(this.framebuffer);
        assertEquals(first, this.events);
        assertSame(this.integerData.get(0), this.integerData.get(1));
    }

    // Checks that float to int conversion saturates at the int limits and maps NaN to zero
    @Test
    void integerColoursSaturateAtTheIntLimits() {
        ShadowColorSettings extremes = ShadowColorSettings.parse(new ConstDirectives(String.join("\n",
                "const int shadowcolor0Format = RGBA32I;",
                "const vec4 shadowcolor0ClearColor = vec4(-3000000000.0, 2147483647.0, -2147483648.0, 0.99);")), 2);
        new ShadowColorClears(extremes, new int[]{0}, new int[]{40, 0}, true).run(this.framebuffer);
        assertEquals(1, this.integerData.size());
        ByteBuffer data = this.integerData.get(0);
        assertEquals(Integer.MIN_VALUE, data.getInt(0));
        assertEquals(Integer.MAX_VALUE, data.getInt(4));
        assertEquals(Integer.MIN_VALUE, data.getInt(8));
        assertEquals(0, data.getInt(12));
    }

    // Checks that no allocated buffers and buffers that keep their contents produce no calls
    @Test
    void nothingToClearIssuesNoCalls() {
        ShadowColorClears empty = new ShadowColorClears(settings(), new int[0], TEXTURES, true);
        assertEquals(0, empty.size());
        empty.run(this.framebuffer);
        ShadowColorClears kept = new ShadowColorClears(settings(), new int[]{1}, TEXTURES, true);
        assertEquals(0, kept.size());
        kept.run(this.framebuffer);
        verifyNoInteractions(this.framebuffer);
        assertTrue(this.events.isEmpty());
    }

    // Checks that a single allocated buffer clears once
    @Test
    void aSingleBufferClearsOnce() {
        ShadowColorClears single = new ShadowColorClears(settings(), new int[]{0}, TEXTURES, true);
        assertEquals(1, single.size());
        single.run(this.framebuffer);
        assertEquals(3, this.events.size());
    }

    // Checks that null arguments are rejected
    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ShadowColorClears(null, new int[0], TEXTURES, true));
        assertThrows(NullPointerException.class, () -> new ShadowColorClears(settings(), null, TEXTURES, true));
        assertThrows(NullPointerException.class, () -> new ShadowColorClears(settings(), new int[0], null, true));
        ShadowColorClears clears = new ShadowColorClears(settings(), new int[]{0}, TEXTURES, true);
        assertThrows(NullPointerException.class, () -> clears.run(null));
    }

    // Checks that an allocated index outside the settings is rejected
    @Test
    void anIndexOutsideTheSettingsIsRejected() {
        assertThrows(IndexOutOfBoundsException.class, () -> new ShadowColorClears(settings(), new int[]{8}, TEXTURES, true));
        assertThrows(IndexOutOfBoundsException.class, () -> new ShadowColorClears(settings(), new int[]{-1}, TEXTURES, true));
    }
}
