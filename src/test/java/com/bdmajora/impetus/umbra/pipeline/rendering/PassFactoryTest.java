package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PassFactoryTest {
    static final String VERTEX = "#version 120\nvarying vec2 uv;\nvoid main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.xy; }";
    private static final int GL_LINK_STATUS = 35714;
    private PipelineGl gl;
    private UmbraRenderTargets targets;
    private ProgramCache programs;
    private BitSet flipped;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(320, 240);
        this.flipped = new BitSet();
        this.programs = new ProgramCache(Map.of(), stage -> Map.of(), this.flipped);
    }

    @AfterEach
    void close() {
        this.programs.destroy();
        this.targets.destroy();
        this.gl.close();
    }

    static String fragment(String drawBuffers) {
        return "#version 120\n/* DRAWBUFFERS:" + drawBuffers + " */\nvoid main() { gl_FragData[0] = vec4(1.0); }";
    }

    private PassFactory factory(ShaderPack pack, UmbraShadowRenderer shadow) {
        return factory(pack, shadow, this.programs);
    }

    private PassFactory factory(ShaderPack pack, UmbraShadowRenderer shadow, ProgramCache cache) {
        return new PassFactory(pack.getProperties(), new PackSources(pack.getProgramSet(), Map.of()), cache,
                this.targets, shadow, this.flipped);
    }

    private static ProgramSource composite(ShaderPack pack, int index) {
        return pack.getProgramSet().get(ProgramArrayId.Composite, index).orElseThrow();
    }

    @Test
    void aPassReadsTheFrontWritesTheBackAndFlipsWhatItWrote() {
        ShaderPack pack = PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", fragment("03"));
        PassFactory factory = factory(pack, null);
        this.targets.getOrCreate(1);
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(1);
        FullscreenPass pass = factory.composite(composite(pack, 0), flipper, List.of());
        assertNotNull(pass);
        assertArrayEquals(new int[]{0, 3}, pass.drawBuffers);
        // Sampled before the flip: colortex1 on its alt side; colortex0/3 did not exist until this pass wrote them, so it samples nothing there
        assertEquals(this.targets.get(1).getAltTexture(), pass.colorSamplers[1]);
        assertEquals(0, pass.colorSamplers[0]);
        assertEquals(0, pass.colorSamplers[5]);
        // It writes the back sides
        verify(TestGl.gl()).glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0 + 1,
                org.lwjgl.opengl.GL11.GL_TEXTURE_2D, this.targets.get(3).getAltTexture(), 0);
        assertTrue(pass.flipsBefore.get(1));
        assertFalse(pass.flipsBefore.get(0));
        assertTrue(flipper.isFlipped(0));
        assertTrue(flipper.isFlipped(3));
        assertTrue(flipper.isFlipped(1));
        assertEquals(bits(0, 3), this.flipped);
        assertFalse(pass.modern);
        assertSame(this.programs.get(composite(pack, 0)).program(), pass.program);
    }

    @Test
    void explicitFlipsSuppressAddAndDoubleFlipLikeIris() {
        ShaderPack pack = PackSourcesTest.pack("/shaders.properties", String.join("\n",
                        "flip.composite.colortex3 = false", "flip.composite.colortex5 = true", "flip.composite.colortex0 = true",
                        "flip.composite.colortex99 = true"),
                "/composite.vsh", VERTEX, "/composite.fsh", fragment("03"));
        BufferFlipper flipper = new BufferFlipper();
        FullscreenPass pass = factory(pack, null).composite(composite(pack, 0), flipper, List.of());
        assertNotNull(pass);
        // colortex0 is written AND explicitly flipped, so it flips twice; colortex3 is held; colortex5 flips unwritten; colortex99 is no target
        assertFalse(flipper.isFlipped(0));
        assertFalse(flipper.isFlipped(3));
        assertTrue(flipper.isFlipped(5));
        assertFalse(flipper.isFlipped(99));
        assertEquals(bits(0, 5), this.flipped);
    }

    @Test
    void aPassThatCannotBeBuiltLeavesTheFlipStateUntouched() {
        ShaderPack pack = PackSourcesTest.pack("/composite.vsh", VERTEX, "/composite.fsh", fragment("03"),
                "/composite1.vsh", VERTEX, "/composite1.fsh", fragment("0"));
        PassFactory factory = factory(pack, null);
        BufferFlipper flipper = new BufferFlipper();
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(0);
        assertNull(factory.composite(composite(pack, 0), flipper, List.of()));
        // The failure is cached, so asking again neither recompiles nor throws
        assertNull(factory.composite(composite(pack, 0), flipper, List.of()));
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(1);
        // An incomplete colour framebuffer fails after the program built
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        assertNull(factory.composite(composite(pack, 1), flipper, List.of()));
        assertEquals(new BitSet(), flipper.snapshot());
        assertTrue(this.flipped.isEmpty());
    }

    @Test
    void theFinalPassDrawsIntoTheMainFramebufferUnderTheCurrentFlips() {
        ShaderPack pack = PackSourcesTest.pack("/final.vsh", VERTEX, "/final.fsh", "#version 120\nconst bool colortex0MipmapEnabled = true;\nvoid main() { gl_FragColor = vec4(1.0); }");
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(0);
        FullscreenPass pass = factory(pack, null).finalPass(pack.getProgramSet().get(ProgramId.Final).orElseThrow(), flipper);
        assertNotNull(pass);
        assertNull(pass.framebuffer);
        assertArrayEquals(new int[]{0}, pass.drawBuffers);
        assertTrue(pass.computes.isEmpty());
        assertTrue(pass.flipsBefore.get(0));
        assertEquals(bits(0), pass.mipmappedBuffers);
        // Final never flips
        assertTrue(flipper.isFlipped(0));
        ShaderPack broken = PackSourcesTest.pack("/final.vsh", VERTEX, "/final.fsh", "void main() {}");
        // Each case gets its own cache, since the programs are cached by name
        ShaderPack vertexOnly = PackSourcesTest.pack("/final.vsh", VERTEX, "/final.csh", "void main() {}");
        ProgramCache empty = new ProgramCache(Map.of(), stage -> Map.of(), new BitSet());
        assertNull(factory(vertexOnly, null, empty).finalPass(vertexOnly.getProgramSet().get(ProgramId.Final).orElseThrow(), flipper));
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(0);
        ProgramCache failing = new ProgramCache(Map.of(), stage -> Map.of(), new BitSet());
        assertNull(factory(broken, null, failing).finalPass(broken.getProgramSet().get(ProgramId.Final).orElseThrow(), flipper));
    }

    @Test
    void aRasterShadowcompDrawsIntoTheShadowColoursItCanAttach() {
        ShaderPack pack = PackSourcesTest.pack("/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", fragment("019"));
        ProgramSource source = pack.getProgramSet().get(ProgramArrayId.ShadowComposite, 0).orElseThrow();
        assertNull(factory(pack, null).shadowComposite(source, new BufferFlipper(), List.of()));
        UmbraShadowRenderer shadow = mock(UmbraShadowRenderer.class);
        when(shadow.getResolution()).thenReturn(512);
        when(shadow.getColorTextureId(0)).thenReturn(600);
        when(shadow.getColorTextureId(1)).thenReturn(0);
        when(shadow.getColorTextureId(9)).thenReturn(0);
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(2);
        FullscreenPass pass = factory(pack, shadow).shadowComposite(source, flipper, List.of());
        assertNotNull(pass);
        assertArrayEquals(new int[]{0}, pass.drawBuffers);
        assertNotNull(pass.framebuffer);
        // Its place in the chain: the images and samplers hit the sides the rest of the frame uses
        assertTrue(pass.flipsBefore.get(2));
        flipper.flip(2);
        assertTrue(pass.flipsBefore.get(2));
        pass.applyViewport(320, 240);
        verify(TestGl.gl()).glViewport(0, 0, 512, 512);
        pass.framebuffer.destroy();
    }

    @Test
    void aShadowcompWithNothingToAttachOrAnIncompleteFramebufferIsSkippedAndFreed() {
        ShaderPack pack = PackSourcesTest.pack("/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", fragment("1"));
        ProgramSource source = pack.getProgramSet().get(ProgramArrayId.ShadowComposite, 0).orElseThrow();
        UmbraShadowRenderer shadow = mock(UmbraShadowRenderer.class);
        assertNull(factory(pack, shadow).shadowComposite(source, new BufferFlipper(), List.of()));
        when(shadow.getColorTextureId(1)).thenReturn(601);
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        Mockito.clearInvocations(TestGl.gl());
        assertNull(factory(pack, shadow).shadowComposite(source, new BufferFlipper(), List.of()));
        verify(TestGl.gl(), times(1)).glDeleteFramebuffers(anyInt());
    }

    @Test
    void aPassWritingBuffersOfDifferentSizesUsesTheFirstSize() {
        UmbraRenderTargets sized = new UmbraRenderTargets(320, 240);
        try {
            sized.setColorSize(4, 64, 32, new boolean[]{false, false});
            sized.setColorSize(5, 0.5f, 0.5f, new boolean[]{true, true});
            PassFactory factory = new PassFactory(PackSourcesTest.pack("/shaders.properties", "").getProperties(), null,
                    this.programs, sized, null, this.flipped);
            assertArrayEquals(new int[]{0, 0}, factory.viewportSize("p", new int[]{0, 1}));
            assertArrayEquals(new int[]{64, 32}, factory.viewportSize("p", new int[]{0, 4, 5}));
            assertArrayEquals(new int[]{160, 120}, factory.viewportSize("p", new int[]{5, 4}));
            // Two buffers of one size agree; a height-only difference is still a mismatch
            sized.setColorSize(6, 64, 32, new boolean[]{false, false});
            sized.setColorSize(7, 64, 16, new boolean[]{false, false});
            assertArrayEquals(new int[]{64, 32}, factory.viewportSize("p", new int[]{4, 6, 7}));
            assertEquals(0, factory.snapshotFrontTextures(new BufferFlipper())[4]);
            int front = factory.front(new BufferFlipper(), 4);
            assertEquals(sized.get(4).getMainTexture(), front);
        } finally {
            sized.destroy();
        }
    }

    @Test
    void flipDirectivesNamingNoTargetAreDroppedAndPreFlipsDoNotCountAsWrites() {
        Map<Integer, Boolean> valid = new LinkedHashMap<>(Map.of(1, true, 2, false));
        assertSame(valid, PassFactory.validFlips(valid, "composite"));
        Map<Integer, Boolean> mixed = new LinkedHashMap<>();
        mixed.put(-1, true);
        mixed.put(3, true);
        mixed.put(16, true);
        mixed.put(4, false);
        assertEquals(Map.of(3, true, 4, false), PassFactory.validFlips(mixed, "composite"));
        BufferFlipper flipper = new BufferFlipper();
        PassFactory.applyPreFlips(mixed, flipper, "deferred_pre");
        assertEquals(bits(3), flipper.snapshot());
        assertTrue(this.flipped.isEmpty());
        PassFactory.applyPreFlips(Map.of(), flipper, "composite_pre");
        assertEquals(bits(3), flipper.snapshot());
    }

    static BitSet bits(int... indices) {
        BitSet set = new BitSet();
        for (int index : indices) {
            set.set(index);
        }
        return set;
    }

    @Test
    void aShadowcompWhoseProgramFailedIsSkippedOnEveryLaterRequest() {
        ShaderPack pack = PackSourcesTest.pack("/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", fragment("0"));
        ProgramSource source = pack.getProgramSet().get(ProgramArrayId.ShadowComposite, 0).orElseThrow();
        UmbraShadowRenderer shadow = mock(UmbraShadowRenderer.class);
        when(shadow.getColorTextureId(0)).thenReturn(600);
        when(TestGl.gl().glGetProgrami(anyInt(), eq(GL_LINK_STATUS))).thenReturn(0);
        PassFactory factory = factory(pack, shadow);
        assertNull(factory.shadowComposite(source, new BufferFlipper(), List.of()));
        assertNull(factory.shadowComposite(source, new BufferFlipper(), List.of()));
        verify(TestGl.gl(), times(1)).glCreateProgram();
    }
}
