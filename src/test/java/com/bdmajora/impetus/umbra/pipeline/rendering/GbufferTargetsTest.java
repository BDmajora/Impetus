package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.mockito.Mockito;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GbufferTargetsTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private PipelineGl gl;
    private UmbraRenderTargets targets;
    private Logger logger;
    private Logger realLogger;
    private final List<int[]> drawBufferCalls = new ArrayList<>();

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(320, 240);
        this.realLogger = Statics.get(GbufferTargets.class, "LOGGER");
        this.logger = mock(Logger.class);
        Statics.set(GbufferTargets.class, "LOGGER", this.logger);
        Mockito.doAnswer(inv -> {
            IntBuffer buffer = inv.getArgument(0);
            int[] copy = new int[buffer.remaining()];
            buffer.get(buffer.position(), copy);
            this.drawBufferCalls.add(copy);
            return null;
        }).when(TestGl.gl()).glDrawBuffers(any(IntBuffer.class));
    }

    @AfterEach
    void close() {
        Statics.set(GbufferTargets.class, "LOGGER", this.realLogger);
        ShaderMacros.setPackLegacyPrograms(Set.of());
        this.targets.destroy();
        this.gl.close();
    }

    private static GbufferPrograms programs(int[]... masks) {
        GbufferPrograms programs = mock(GbufferPrograms.class);
        List<GbufferPrograms.Entry> entries = new ArrayList<>();
        for (int[] mask : masks) {
            GbufferPrograms.Entry entry = mock(GbufferPrograms.Entry.class);
            when(entry.drawBuffersReadOnly()).thenReturn(mask);
            entries.add(entry);
        }
        when(programs.entries()).thenReturn(entries);
        return programs;
    }

    private GbufferTargets targetsFor(ShaderPack pack, GbufferPrograms programs) {
        return new GbufferTargets(pack, programs, pack.getEnvironmentDefines(), this.targets);
    }

    private static ShaderPack terrainAndWater(String terrain, String water) {
        return PackSourcesTest.pack("/gbuffers_terrain.vsh", VERTEX, "/gbuffers_terrain.fsh", "/* DRAWBUFFERS:" + terrain + " */\nvoid main() {}",
                "/gbuffers_water.vsh", VERTEX, "/gbuffers_water.fsh", "/* DRAWBUFFERS:" + water + " */\nvoid main() {}");
    }

    private int[] lastDrawBuffers() {
        return this.drawBufferCalls.get(this.drawBufferCalls.size() - 1);
    }

    @Test
    void theAttachmentsAreTheUnionOfEveryGbufferMaskPlusColortex0() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("2", "64"), programs(new int[]{7}, new int[]{2, 9}));
        assertArrayEquals(new int[]{0, 2, 4, 6, 7, 9}, gbuffers.attachments());
        // A pack with no terrain or water program still attaches colortex0
        assertArrayEquals(new int[]{0}, targetsFor(PackSourcesTest.pack("/shaders.properties", ""), programs()).attachments());
    }

    @Test
    void aMaskUnionPastTheGpusAttachmentsDropsTheHighestTargetsInsteadOfFailingThePipeline() {
        this.gl.limits(48, 8, 4, 8);
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0245", "06"), programs());
        assertArrayEquals(new int[]{0, 2, 4, 5}, gbuffers.attachments());
        verify(this.logger).warn(anyString(), eq(5), eq(4), eq("[6]"));
        UmbraFramebuffer framebuffer = gbuffers.create(new BufferFlipper());
        gbuffers.drawBuffers(framebuffer, new int[]{0, 6});
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL11.GL_NONE}, lastDrawBuffers());
    }

    @Test
    void eachProgramsMaskIsReadWithTheMacrosItCompilesWith() {
        ShaderPack pack = PackSourcesTest.pack("/gbuffers_water.vsh", VERTEX, "/gbuffers_water.fsh",
                "#ifdef IS_IRIS\n/* DRAWBUFFERS:41 */\n#else\n/* DRAWBUFFERS:412 */\n#endif\nvoid main() {}");
        assertArrayEquals(new int[]{4, 1}, GbufferTargets.programDrawBuffers(pack, ProgramId.Water, "gbuffers_water", pack.getEnvironmentDefines()));
        ShaderMacros.setPackLegacyPrograms(Set.of("gbuffers_water"));
        assertArrayEquals(new int[]{4, 1, 2}, GbufferTargets.programDrawBuffers(pack, ProgramId.Water, "gbuffers_water", pack.getEnvironmentDefines()));
        // No program at all writes colortex0, and an out-of-range target is dropped
        assertArrayEquals(new int[]{0}, GbufferTargets.programDrawBuffers(pack, ProgramId.Terrain, "gbuffers_terrain", Map.of()));
        assertArrayEquals(new int[]{0, 3}, GbufferTargets.sanitize(new int[]{0, 3, 99, -1, 3}));
    }

    @Test
    void theGbufferAttachesEachFrontSideDenselyWithTheSharedDepth() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("04", "0"), programs());
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(4);
        UmbraFramebuffer framebuffer = gbuffers.create(flipper);
        assertNotNull(framebuffer);
        // Attached, then kept live by the colour-only mask
        verify(TestGl.gl(), times(2)).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                this.targets.get(0).getMainTexture(), 0);
        verify(TestGl.gl()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + 1, GL11.GL_TEXTURE_2D,
                this.targets.get(4).getAltTexture(), 0);
        verify(TestGl.gl()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                this.targets.getDepthTexture().getTextureId(), 0);
        // Colour-only to start: colortex0 alone
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0}, lastDrawBuffers());
    }

    @Test
    void anIncompleteGbufferIsFreedBeforeTheFailureEscapes() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0", "0"), programs());
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> gbuffers.create(new BufferFlipper()));
        assertTrue(failure.getMessage().contains("gbuffer"), failure.getMessage());
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
    }

    @Test
    void drawBuffersRouteUnattachedTargetsToNoneAndReportEachOnlyOnce() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("024", "0"), programs());
        UmbraFramebuffer framebuffer = gbuffers.create(new BufferFlipper());
        gbuffers.drawBuffers(framebuffer, new int[]{4, 9, 0});
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0 + 2, GL11.GL_NONE, GL30.GL_COLOR_ATTACHMENT0}, lastDrawBuffers());
        // Only the written attachments stay live: colortex2 (point 1) is detached for this program
        verify(TestGl.gl(), Mockito.atLeastOnce()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + 1, GL11.GL_TEXTURE_2D, 0, 0);
        // Every phase switch used to log the unattached target again
        gbuffers.drawBuffers(framebuffer, new int[]{9});
        gbuffers.drawBuffers(framebuffer, new int[]{9, 0});
        verify(this.logger, times(1)).warn(anyString(), eq(9), anyInt());
        gbuffers.drawBuffers(framebuffer, new int[]{-3});
        gbuffers.drawBuffers(framebuffer, new int[]{-3});
        verify(this.logger, times(2)).warn(anyString(), eq(-3), anyInt());
        assertArrayEquals(new int[]{GL11.GL_NONE}, lastDrawBuffers());
    }

    @Test
    void aMaskLongerThanTheGpuDrawsIsCutOnceReportedInsteadOfThrowingMidFrame() {
        this.gl.limits(48, 2, 8, 8);
        GbufferTargets narrow = targetsFor(terrainAndWater("0123", "0"), programs());
        UmbraFramebuffer narrowFramebuffer = narrow.create(new BufferFlipper());
        narrow.drawBuffers(narrowFramebuffer, new int[]{0, 1, 2, 3});
        narrow.drawBuffers(narrowFramebuffer, new int[]{0, 1, 2});
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT0 + 1}, lastDrawBuffers());
        verify(this.logger, times(1)).warn(anyString(), eq(4), eq(2));
    }

    @Test
    void aDistantHorizonsFramebufferAttachesThePhasesFrontSidesDenselyAndStaysInsideTheGpu() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0", "0"), programs());
        BitSet flips = new BitSet();
        flips.set(4);
        Mockito.clearInvocations(TestGl.gl());
        UmbraFramebuffer framebuffer = gbuffers.createDh(new int[]{0, 4, 4, -1}, flips);
        verify(TestGl.gl()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                this.targets.get(0).getMainTexture(), 0);
        verify(TestGl.gl()).glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + 1, GL11.GL_TEXTURE_2D,
                this.targets.get(4).getAltTexture(), 0);
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT0 + 1}, lastDrawBuffers());
        assertNotNull(framebuffer);
        this.gl.limits(48, 8, 2, 8);
        GbufferTargets narrow = targetsFor(terrainAndWater("0", "0"), programs());
        narrow.createDh(new int[]{0, 1, 2}, new BitSet());
        assertArrayEquals(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT0 + 1}, lastDrawBuffers());
        verify(this.logger).warn(anyString(), eq(3), eq(2));
    }

    @Test
    void aDistantHorizonsFramebufferThatCannotAttachIsFreed() {
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0", "0"), programs());
        // A driver reporting no colour attachments refuses the first one
        when(TestGl.gl().glGetInteger(0x8CDF)).thenReturn(0, 8);
        Mockito.clearInvocations(TestGl.gl());
        assertThrows(IllegalArgumentException.class, () -> gbuffers.createDh(new int[]{0}, new BitSet()));
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
        verify(TestGl.gl(), never()).glDrawBuffers(any(IntBuffer.class));
    }

    @Test
    void aFailedLimitQueryNeitherCutsTheMaskNorTheAttachments() {
        this.gl.limits(48, 0, 0, 8);
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0245", "6"), programs());
        assertArrayEquals(new int[]{0, 2, 4, 5, 6}, gbuffers.attachments());
        assertEquals(Integer.MAX_VALUE, (int) com.bdmajora.testing.Mixins.<Integer>get(gbuffers, "maxDrawBuffers"));
        assertEquals(16, (int) com.bdmajora.testing.Mixins.<Integer>get(gbuffers, "maxAttachments"));
    }

    @Test
    void aGbufferOutputWithItsOwnSizeIsReportedAsAPackBug() {
        this.targets.setColorSize(2, 64, 32, new boolean[]{false, false});
        targetsFor(terrainAndWater("02", "0"), programs());
        verify(this.logger).warn(anyString(), eq(2), eq(64), eq(32));
    }

    @Test
    void masksPastTheBuiltInScratchAndIndicesPastTheTablesStillMapToNone() {
        this.gl.limits(48, 32, 8, 8);
        GbufferTargets gbuffers = targetsFor(terrainAndWater("0", "0"), programs());
        UmbraFramebuffer framebuffer = gbuffers.create(new BufferFlipper());
        int[] wide = new int[20];
        java.util.Arrays.fill(wide, -1);
        wide[0] = 0;
        wide[1] = 20;
        wide[2] = 40;
        gbuffers.drawBuffers(framebuffer, wide);
        int[] drawn = lastDrawBuffers();
        assertEquals(20, drawn.length);
        assertEquals(GL30.GL_COLOR_ATTACHMENT0, drawn[0]);
        assertEquals(GL11.GL_NONE, drawn[1]);
        assertEquals(GL11.GL_NONE, drawn[2]);
        // 20 is past the attachment table but has a warned bit of its own; 40 is past the bit mask and so is reported every time
        gbuffers.drawBuffers(framebuffer, new int[]{20, 40});
        verify(this.logger, times(1)).warn(anyString(), eq(20), anyInt());
        verify(this.logger, times(2)).warn(anyString(), eq(40), anyInt());
    }
}
