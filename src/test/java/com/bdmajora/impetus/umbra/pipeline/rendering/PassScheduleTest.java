package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.buffer.ShaderStorageBufferHolder;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static com.bdmajora.impetus.umbra.pipeline.rendering.PassFactoryTest.VERTEX;
import static com.bdmajora.impetus.umbra.pipeline.rendering.PassFactoryTest.bits;
import static com.bdmajora.impetus.umbra.pipeline.rendering.PassFactoryTest.fragment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassScheduleTest {
    private static final String COMPUTE = "#version 430\nlayout(local_size_x = 8) in;\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() {}";
    private PipelineGl gl;
    private UmbraRenderTargets targets;
    private ProgramCache programs;
    private BufferFlipper flipper;
    private UmbraShadowRenderer shadow;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.targets = new UmbraRenderTargets(320, 240);
        this.flipper = new BufferFlipper();
        this.shadow = mock(UmbraShadowRenderer.class);
        when(this.shadow.getColorTextureId(0)).thenReturn(600);
        when(this.shadow.getResolution()).thenReturn(256);
    }

    @AfterEach
    void close() {
        if (this.programs != null) {
            this.programs.destroy();
        }
        this.targets.destroy();
        this.gl.close();
    }

    private PassSchedule build(String... pathsAndSources) {
        ShaderPack pack = PackSourcesTest.pack(pathsAndSources);
        BitSet flipped = new BitSet();
        this.programs = new ProgramCache(Map.of(), stage -> Map.of(), flipped);
        PackSources sources = new PackSources(pack.getProgramSet(), Map.of());
        PassFactory factory = new PassFactory(pack.getProperties(), sources, this.programs, this.targets, this.shadow, flipped);
        ComputeBuilder computes = new ComputeBuilder(Map.of(), stage -> Map.of(), flipped, Map.of(),
                mock(ShaderStorageBufferHolder.class), null);
        GbufferPrograms gbufferPrograms = mock(GbufferPrograms.class);
        when(gbufferPrograms.entries()).thenReturn(List.of());
        GbufferTargets gbuffers = new GbufferTargets(pack, gbufferPrograms, Map.of(), this.targets);
        PassSchedule schedule = new PassSchedule();
        schedule.build(pack.getProgramSet(), pack.getProperties(), factory, computes, gbuffers, this.flipper);
        return schedule;
    }

    @Test
    void familiesAreBakedInUmbrasOrderWithFlipSnapshotsAroundTheDeferredChain() {
        PassSchedule schedule = build(
                "/setup.csh", COMPUTE,
                "/begin.vsh", VERTEX, "/begin.fsh", fragment("8"),
                "/prepare.csh", COMPUTE,
                "/deferred.vsh", VERTEX, "/deferred.fsh", fragment("04"),
                "/composite.vsh", VERTEX, "/composite.fsh", "#version 120\n/* DRAWBUFFERS:0 */\nconst bool colortex1MipmapEnabled = true;\nvoid main() {}",
                "/final.vsh", VERTEX, "/final.fsh", "#version 120\nconst bool colortex2MipmapEnabled = true;\nvoid main() {}");
        assertEquals(1, schedule.setup.size());
        assertNull(schedule.setup.get(0).program);
        assertEquals(1, schedule.begin.size());
        assertEquals(1, schedule.prepare.size());
        assertNull(schedule.prepare.get(0).program);
        assertEquals(1, schedule.deferred.size());
        // composite then final
        assertEquals(List.of("composite", "final"), schedule.composite.stream().map(pass -> pass.name).toList());
        assertNull(schedule.blitSource);
        // begin flipped 8 before the gbuffer was baked; deferred flipped 0 and 4 before the translucent one
        assertEquals(bits(8), schedule.preTranslucentFlips);
        assertEquals(bits(0, 4, 8), schedule.translucentFlips);
        assertNotSame(schedule.gbuffer, schedule.translucentGbuffer);
        assertEquals(bits(1, 2), schedule.mipmapped);
        assertEquals(6, schedule.families().size());
    }

    @Test
    void withoutADeferredChainOneGbufferServesTheWholeFrame() {
        PassSchedule schedule = build("/composite.vsh", VERTEX, "/composite.fsh", fragment("0"),
                "/final.vsh", VERTEX, "/final.fsh", "void main() {}");
        assertSame(schedule.gbuffer, schedule.translucentGbuffer);
        assertEquals(schedule.preTranslucentFlips, schedule.translucentFlips);
        Mockito.clearInvocations(TestGl.gl());
        schedule.destroy();
        // The shared FBO is deleted once
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteFramebuffers(anyInt());
    }

    @Test
    void aStraySetupDrawIsNeverScheduledSinceSetupIsComputeOnly() {
        PassSchedule schedule = build("/setup.vsh", VERTEX, "/setup.fsh", fragment("5"), "/setup.csh", COMPUTE,
                "/setup1.vsh", VERTEX, "/setup1.fsh", fragment("6"));
        // The draw used to be scheduled and flip colortex5 for a draw that never ran
        assertEquals(1, schedule.setup.size());
        assertNull(schedule.setup.get(0).program);
        assertFalse(this.flipper.isFlipped(5));
        assertFalse(this.flipper.isFlipped(6));
    }

    @Test
    void disabledProgramsAreSkippedTheShadowcompRasterIncluded() {
        PassSchedule schedule = build("/shaders.properties", String.join("\n", "program.shadowcomp.enabled = false",
                        "program.composite.enabled = false", "program.final.enabled = false"),
                "/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", fragment("0"),
                "/shadowcomp1.vsh", VERTEX, "/shadowcomp1.fsh", fragment("0"), "/shadowcomp1.csh", COMPUTE,
                "/composite.vsh", VERTEX, "/composite.fsh", fragment("0"),
                "/final.vsh", VERTEX, "/final.fsh", "void main() {}");
        assertEquals(List.of("shadowcomp1"), schedule.shadowComposite.stream().map(pass -> pass.name).toList());
        assertNotNull(schedule.shadowComposite.get(0).program);
        assertEquals(1, schedule.shadowComposite.get(0).computes.size());
        assertTrue(schedule.composite.isEmpty());
        // No working final program shows colortex0 by blitting it
        assertNotNull(schedule.blitSource);
    }

    @Test
    void aFailedDrawStillRunsItsComputesAndAFailedFinalFallsBackToTheBlit() {
        // Links run shadowcomp's compute, deferred's compute, then deferred's draw, which fails
        when(TestGl.gl().glGetProgrami(anyInt(), eq(35714))).thenReturn(1, 1, 0);
        PassSchedule schedule = build("/deferred.vsh", VERTEX, "/deferred.fsh", fragment("0"),
                "/deferred.csh", COMPUTE,
                "/shadowcomp.csh", COMPUTE);
        assertEquals(1, schedule.deferred.size());
        assertNull(schedule.deferred.get(0).program);
        assertEquals(1, schedule.deferred.get(0).computes.size());
        assertEquals(1, schedule.shadowComposite.size());
        assertNull(schedule.shadowComposite.get(0).program);
        assertNotNull(schedule.blitSource);
    }

    @Test
    void destroyFreesComputesTheShadowcompFramebufferTheBlitAndBothGbuffers() {
        PassSchedule schedule = build("/shadowcomp.vsh", VERTEX, "/shadowcomp.fsh", fragment("0"), "/shadowcomp.csh", COMPUTE,
                "/deferred.vsh", VERTEX, "/deferred.fsh", fragment("0"));
        assertNotNull(schedule.shadowComposite.get(0).framebuffer);
        Mockito.clearInvocations(TestGl.gl());
        schedule.destroy();
        // shadowcomp FBO (it used to leak), blit source, gbuffer and translucent gbuffer
        Mockito.verify(TestGl.gl(), Mockito.times(4)).glDeleteFramebuffers(anyInt());
        // The compute, not the draw programs, which the cache owns
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteProgram(anyInt());
        for (List<FullscreenPass> family : schedule.families()) {
            assertTrue(family.isEmpty());
        }
        assertNull(schedule.gbuffer);
        assertNull(schedule.blitSource);
        Mockito.clearInvocations(TestGl.gl());
        schedule.destroy();
        Mockito.verify(TestGl.gl(), Mockito.never()).glDeleteFramebuffers(anyInt());
        new PassSchedule().destroy();
    }
}
