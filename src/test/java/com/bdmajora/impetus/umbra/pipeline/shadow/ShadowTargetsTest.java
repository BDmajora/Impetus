package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Covers the shadow textures and framebuffer owner
class ShadowTargetsTest {
    // Two flags that are both off
    private static final boolean[] OFF = {false, false};

    // GL model of the test
    private PipelineGl gl;
    // Texture names handed out by glGenTextures in order
    private final List<Integer> generated = new ArrayList<>();
    // Framebuffer names handed out by glGenFramebuffers in order
    private final List<Integer> framebuffers = new ArrayList<>();
    // Draw buffer masks passed to glDrawBuffers in order
    private final List<String> drawMasks = new ArrayList<>();
    // Every clear and mask call in order
    private final List<String> events = new ArrayList<>();

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Opens a GL model that records names and masks
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        GlCache.reset();
        LWJGLService service = TestGl.gl();
        AtomicInteger next = new AtomicInteger(1000);
        doAnswer(inv -> {
            int name = next.incrementAndGet();
            this.generated.add(name);
            return name;
        }).when(service).glGenTextures();
        doAnswer(inv -> {
            int name = next.incrementAndGet();
            this.framebuffers.add(name);
            return name;
        }).when(service).glGenFramebuffers();
        doAnswer(inv -> {
            IntBuffer buffer = inv.getArgument(0);
            int[] values = new int[buffer.remaining()];
            buffer.duplicate().get(values);
            for (int i = 0; i < values.length; i++) {
                values[i] -= GL30.GL_COLOR_ATTACHMENT0;
            }
            String mask = Arrays.toString(values);
            this.drawMasks.add(mask);
            this.events.add("mask " + mask);
            return null;
        }).when(service).glDrawBuffers(any(IntBuffer.class));
        doAnswer(inv -> this.events.add("clear " + inv.getArgument(0))).when(service).glClear(anyInt());
    }

    // Closes the GL model and resets the caches
    @AfterEach
    void close() {
        GlCache.reset();
        this.gl.close();
    }

    // Returns settings for a number of buffers from const directives
    private static ShadowColorSettings settings(int count, String... directives) {
        return ShadowColorSettings.parse(new ConstDirectives(String.join("\n", directives)), count);
    }

    // Returns a bit set with the given bits
    private static BitSet bits(int... indices) {
        BitSet set = new BitSet();
        for (int index : indices) {
            set.set(index);
        }
        return set;
    }

    // Returns targets with every flag off for the given settings and used buffers
    private static ShadowTargets targets(int resolution, ShadowColorSettings settings, BitSet used) {
        return new ShadowTargets(resolution, OFF, OFF, OFF, false, settings, used);
    }

    // Checks that buffers 0 and 1 always exist and the rest only when used and within the count
    @Test
    void theFirstTwoBuffersAndEveryUsedOneWithinTheCountAreAllocated() {
        ShadowTargets targets = targets(256, settings(8), bits(3, 6, 9));
        assertEquals(8, targets.colorBufferCount());
        assertEquals(256, targets.resolution());
        int[] expected = {1, 1, 0, 1, 0, 0, 1, 0};
        for (int index = 0; index < 8; index++) {
            int texture = targets.colorTextureId(index);
            assertEquals(expected[index] == 1, texture != 0, "buffer " + index);
            assertEquals(texture, targets.framebuffer().getColorAttachment(index));
        }
        assertTrue(targets.framebuffer().hasDepthAttachment());
        assertNotEquals(targets.depthTextureId(), targets.depthNoTranslucentsTextureId());
        assertEquals(List.of("[0, 1, 3, 6]"), this.drawMasks);
        assertEquals(6, this.generated.size());
    }

    // Checks that lookups outside the addressable buffers answer 0 and formats come from the settings
    @Test
    void lookupsOutsideTheBuffersAnswerZero() {
        ShadowTargets targets = targets(64, settings(8, "const int shadowcolor5Format = RGBA16F;"), bits());
        for (int index : new int[]{-1, 8, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(0, targets.colorTextureId(index));
            assertEquals(0, targets.colorInternalFormat(index));
        }
        assertEquals(0, targets.colorTextureId(5));
        assertEquals(GL30.GL_RGBA16F, targets.colorInternalFormat(5));
        assertEquals(GL11.GL_RGBA8, targets.colorInternalFormat(0));
    }

    // Checks that zero and one addressable buffers allocate nothing past the count
    @Test
    void zeroAndOneBufferCountsAllocateOnlyWhatFits() {
        ShadowTargets none = targets(32, settings(0), bits(0, 1, 2));
        assertEquals(0, none.colorBufferCount());
        assertEquals(0, none.colorTextureId(0));
        assertArrayEquals(new int[0], none.drawMask(new int[]{0}));
        assertArrayEquals(new int[0], none.drawMask(null));
        verify(TestGl.gl()).glDrawBuffers(GL11.GL_NONE);
        ShadowTargets one = targets(32, settings(1), bits(1));
        assertEquals(1, one.colorBufferCount());
        assertNotEquals(0, one.colorTextureId(0));
        assertEquals(0, one.colorTextureId(1));
        assertArrayEquals(new int[]{0}, one.drawMask(new int[]{1}));
    }

    // Checks that the draw mask keeps allocated buffers in their declared order and falls back to buffer 0
    @Test
    void theDrawMaskKeepsAllocatedBuffersAndFallsBackToBufferZero() {
        ShadowTargets targets = targets(16, settings(8), bits(3, 6));
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[0]));
        assertArrayEquals(new int[]{0}, targets.drawMask(null));
        assertArrayEquals(new int[]{1}, targets.drawMask(new int[]{1, 5}));
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[]{2}));
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[]{9}));
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[]{-1}));
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}));
        assertArrayEquals(new int[]{3, 0}, targets.drawMask(new int[]{3, 3, 0}));
        assertArrayEquals(new int[]{6, 3, 1}, targets.drawMask(new int[]{6, 3, 1}));
        int[] first = targets.drawMask(new int[]{2});
        first[0] = 7;
        assertArrayEquals(new int[]{0}, targets.drawMask(new int[]{2}));
    }

    // Checks that invalid arguments fail before any GL object is created
    @Test
    void invalidArgumentsFailBeforeAnyGlWork() {
        ShadowColorSettings two = settings(2);
        for (int resolution : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> targets(resolution, two, bits()));
        }
        assertThrows(NullPointerException.class, () -> new ShadowTargets(8, null, OFF, OFF, false, two, bits()));
        assertThrows(NullPointerException.class, () -> new ShadowTargets(8, OFF, null, OFF, false, two, bits()));
        assertThrows(NullPointerException.class, () -> new ShadowTargets(8, OFF, OFF, null, false, two, bits()));
        assertThrows(IllegalArgumentException.class, () -> new ShadowTargets(8, new boolean[1], OFF, OFF, false, two, bits()));
        assertThrows(IllegalArgumentException.class, () -> new ShadowTargets(8, OFF, new boolean[0], OFF, false, two, bits()));
        assertThrows(IllegalArgumentException.class, () -> new ShadowTargets(8, OFF, OFF, new boolean[1], false, two, bits()));
        assertThrows(NullPointerException.class, () -> new ShadowTargets(8, OFF, OFF, OFF, false, null, bits()));
        assertThrows(NullPointerException.class, () -> new ShadowTargets(8, OFF, OFF, OFF, false, two, null));
        verify(TestGl.gl(), never()).glGenTextures();
        verify(TestGl.gl(), never()).glGenFramebuffers();
    }

    // Checks that flag arrays longer than two are accepted and later changes to them are ignored
    @Test
    void flagArraysAreCopiedAndExtraEntriesIgnored() {
        boolean[] mipmap = {false, false, true};
        ShadowTargets targets = new ShadowTargets(8, new boolean[3], mipmap, new boolean[3], false, settings(2), bits());
        mipmap[0] = true;
        mipmap[1] = true;
        targets.generateMipmaps();
        verify(TestGl.gl(), never()).glGenerateMipmap(anyInt());
    }

    // Checks that a framebuffer without colour attachments fails and every created object is deleted
    @Test
    void aFailedFramebufferReleasesEverythingItMade() {
        this.gl.limits(48, 8, 0, 8);
        assertThrows(IllegalArgumentException.class, () -> targets(32, settings(2), bits()));
        assertEquals(3, this.generated.size());
        assertEquals(this.generated, GlCalls.deletedTextures().stream().sorted().toList());
        verify(TestGl.gl()).glDeleteFramebuffers(this.framebuffers.get(0));
    }

    // Checks that a failure on the second depth texture deletes the first one and creates no framebuffer
    @Test
    void aFailedSecondDepthTextureReleasesTheFirst() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("driver");
            }
            return null;
        }).when(TestGl.gl()).glTexParameteriv(anyInt(), anyInt(), any(int[].class));
        assertThrows(IllegalStateException.class, () -> targets(32, settings(2), bits()));
        assertEquals(2, this.generated.size());
        assertEquals(this.generated, GlCalls.deletedTextures().stream().sorted().toList());
        assertTrue(this.framebuffers.isEmpty());
    }

    // Checks that a failure on a colour texture deletes it and every object made before it
    @Test
    void aFailedColourTextureReleasesEverythingMadeBefore() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 4) {
                throw new IllegalStateException("out of memory");
            }
            return null;
        }).when(TestGl.gl()).glTexImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                nullable(ByteBuffer.class));
        assertThrows(IllegalStateException.class, () -> targets(32, settings(2), bits()));
        assertEquals(4, this.generated.size());
        assertEquals(this.generated, GlCalls.deletedTextures().stream().sorted().toList());
        verify(TestGl.gl()).glDeleteFramebuffers(this.framebuffers.get(0));
    }

    // Checks that bind selects the framebuffer and covers the whole map up to the int limit
    @Test
    void bindSelectsTheFramebufferAndTheWholeViewport() {
        ShadowTargets targets = targets(Integer.MAX_VALUE, settings(2), bits());
        Mockito.clearInvocations(TestGl.gl());
        targets.bind();
        verify(TestGl.gl()).glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffers.get(0));
        verify(TestGl.gl()).glViewport(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    // Checks that clear runs the colour clears then the depth clear then the draw mask
    @Test
    void clearRunsColourThenDepthThenTheMask() {
        ShadowTargets targets = targets(16, settings(2, "const bool shadowcolor1Clear = false;"), bits());
        this.events.clear();
        targets.clear(new int[]{1, 0});
        assertEquals(List.of("mask [0]", "clear " + GL11.GL_COLOR_BUFFER_BIT, "clear " + GL11.GL_DEPTH_BUFFER_BIT,
                "mask [1, 0]"), this.events);
        Mockito.clearInvocations(TestGl.gl());
        targets.clear(new int[0]);
        verify(TestGl.gl()).glDrawBuffers(GL11.GL_NONE);
    }

    // Checks that the depth copy reads the whole map into shadowtex1 on the scratch unit and returns to unit 0
    @Test
    void theDepthCopyFillsShadowtexOneOnTheScratchUnit() {
        ShadowTargets targets = targets(300, settings(2), bits());
        this.gl.textures[0] = 9;
        int[] seen = new int[2];
        doAnswer(inv -> {
            seen[0] = this.gl.activeUnit;
            seen[1] = this.gl.textures[this.gl.activeUnit];
            return null;
        }).when(TestGl.gl()).glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        targets.copyDepthWithoutTranslucents();
        verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 300, 300);
        assertEquals(ShadowTextures.SETUP_UNIT, seen[0]);
        assertEquals(targets.depthNoTranslucentsTextureId(), seen[1]);
        assertEquals(0, this.gl.textures[ShadowTextures.SETUP_UNIT]);
        assertEquals(9, this.gl.textures[0]);
        assertEquals(0, this.gl.activeUnit);
    }

    // Checks that mip chains are built for each requested texture with one scratch selection
    @Test
    void mipmapsAreBuiltForEachRequestedTextureWithOneScratchSelection() {
        ShadowTargets targets = new ShadowTargets(64, OFF, new boolean[]{true, false}, new boolean[]{false, true}, false,
                settings(2, "const bool shadowcolor1Mipmap = true;"), bits());
        Mockito.clearInvocations(TestGl.gl());
        this.gl.activeUnit = 5;
        targets.generateMipmaps();
        verify(TestGl.gl(), Mockito.times(2)).glGenerateMipmap(GL11.GL_TEXTURE_2D);
        verify(TestGl.gl(), Mockito.times(1)).glActiveTexture(GL13.GL_TEXTURE0 + ShadowTextures.SETUP_UNIT);
        assertEquals(List.of(GL11.GL_LINEAR_MIPMAP_LINEAR), GlCalls.texParameter(GL11.GL_TEXTURE_MIN_FILTER));
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, targets.depthTextureId());
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, targets.colorTextureId(1));
        verify(TestGl.gl(), never()).glBindTexture(GL11.GL_TEXTURE_2D, targets.depthNoTranslucentsTextureId());
        assertEquals(0, this.gl.textures[ShadowTextures.SETUP_UNIT]);
        assertEquals(0, this.gl.activeUnit);

        ShadowTargets nearest = new ShadowTargets(64, OFF, new boolean[]{false, true}, new boolean[]{false, true}, false,
                settings(2), bits());
        Mockito.clearInvocations(TestGl.gl());
        nearest.generateMipmaps();
        assertEquals(List.of(GL11.GL_NEAREST_MIPMAP_NEAREST), GlCalls.texParameter(GL11.GL_TEXTURE_MIN_FILTER));
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, nearest.depthNoTranslucentsTextureId());
    }

    // Checks that without mipmaps nothing is generated and the selector still returns to unit 0
    @Test
    void withoutMipmapsOnlyTheUnitIsReset() {
        ShadowTargets targets = targets(64, settings(2), bits());
        Mockito.clearInvocations(TestGl.gl());
        this.gl.activeUnit = 5;
        targets.generateMipmaps();
        verify(TestGl.gl(), never()).glGenerateMipmap(anyInt());
        verify(TestGl.gl(), never()).glActiveTexture(anyInt());
        assertEquals(0, this.gl.activeUnit);
    }

    // Checks that destroy deletes everything once and texture lookups fail afterwards
    @Test
    void destroyDeletesEverythingOnceAndLookupsFailAfterwards() {
        ShadowTargets targets = targets(64, settings(8), bits(4));
        int depth = targets.depthTextureId();
        int depthNoTranslucents = targets.depthNoTranslucentsTextureId();
        int[] colours = {targets.colorTextureId(0), targets.colorTextureId(1), targets.colorTextureId(4)};
        Mockito.clearInvocations(TestGl.gl());
        targets.destroy();
        verify(TestGl.gl()).glDeleteFramebuffers(this.framebuffers.get(0));
        verify(TestGl.gl()).glDeleteTextures(depth);
        verify(TestGl.gl()).glDeleteTextures(depthNoTranslucents);
        verify(TestGl.gl()).glDeleteTextures(aryEq(colours));
        targets.destroy();
        assertEquals(5, GlCalls.deletedTextures().size());
        assertThrows(IllegalStateException.class, targets::depthTextureId);
        assertThrows(IllegalStateException.class, targets::depthNoTranslucentsTextureId);
        assertThrows(IllegalStateException.class, () -> targets.colorTextureId(0));
        assertThrows(IllegalStateException.class, () -> targets.colorTextureId(-1));
        assertEquals(GL11.GL_RGBA8, targets.colorInternalFormat(4));
        assertEquals(8, targets.colorBufferCount());
        assertEquals(64, targets.resolution());
    }

    // Checks that a colour format reaches the texture allocation
    @Test
    void eachColourBufferUsesItsFormat() {
        targets(16, settings(2, "const int shadowcolor1Format = R32UI;"), bits());
        verify(TestGl.gl()).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), eq(InternalTextureFormat.R32UI.getInternalFormat()),
                eq(16), eq(16), eq(0), eq(GL30.GL_RED_INTEGER), eq(GL11.GL_UNSIGNED_INT), nullable(ByteBuffer.class));
    }
}
