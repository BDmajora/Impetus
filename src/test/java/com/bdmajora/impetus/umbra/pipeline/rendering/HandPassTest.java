package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandPassTest {
    private PipelineGl gl;
    private UmbraRenderingPipeline pipeline;
    private PipelineState state;
    private PhaseController phases;
    private HandPass hand;
    private GbufferPrograms programs;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.pipeline = PipelineGl.pipeline("/shaders.properties", "");
        this.state = PipelineGl.state(this.pipeline);
        this.programs = mock(GbufferPrograms.class);
        Mixins.set(this.state, "gbufferPrograms", this.programs);
        this.phases = new PhaseController(this.state);
        this.hand = new HandPass(this.state, this.phases);
        // GlStateManager caches the depth func across tests; start from vanilla's
        net.minecraft.client.renderer.GlStateManager.depthFunc(GL11.GL_LEQUAL);
        this.gl.gl11.clearInvocations();
    }

    @AfterEach
    void close() {
        CapturedRenderingState.INSTANCE.setRenderStage(0);
        this.gl.client.world = null;
        this.gl.client.player = null;
        this.pipeline.destroy();
        this.gl.close();
    }

    private void inWorld(int packedLight) {
        WorldClient world = mock(WorldClient.class);
        when(world.getCombinedLight(any(), anyInt())).thenReturn(packedLight);
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        player.posX = 10.5;
        player.posY = 64;
        player.posZ = -3.25;
        when(player.getEyeHeight()).thenReturn(1.62F);
        this.gl.client.world = world;
        this.gl.client.player = player;
    }

    @Test
    void theHandNeedsALivePipelineInsideAFrame() {
        assertFalse(this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE));
        PipelineGl.enterFrame(this.state);
        this.state.destroyed = true;
        assertFalse(this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE));
        this.state.destroyed = false;
    }

    @Test
    void withNoHandProgramTheHandDrawsFixedFunctionIntoColortex0AtFullBrightOutsideAWorld() {
        PipelineGl.enterFrame(this.state);
        this.gl.program = 9;
        assertTrue(this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE));
        assertEquals(0, this.gl.program);
        assertEquals(ProgramId.Hand, this.phases.current());
        assertEquals(16, CapturedRenderingState.INSTANCE.getRenderStage());
        verify(TestGl.gl()).glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        verify(TestGl.gl()).glDepthRange(0.0, 1.0);
        verify(TestGl.gl()).glViewport(0, 0, 320, 240);
        this.gl.gl11.verify(() -> GL11.glDepthFunc(GL11.GL_ALWAYS));
        // Unit 0 is handed to the arm in a state the cache agrees with
        assertEquals(0, PipelineGl.cachedTexture(0));
        assertEquals(0, this.gl.activeUnit);
        this.hand.end();
        this.gl.gl11.verify(() -> GL11.glDepthFunc(GL11.GL_LEQUAL));
        assertEquals(0, CapturedRenderingState.INSTANCE.getRenderStage());
    }

    @Test
    void aHandProgramGetsThePlayersEyeLightOnItsTexcoordAndBridgeUniform() {
        PipelineGl.enterFrame(this.state);
        inWorld(0x00F000A0);
        GbufferPrograms.Entry entry = mock(GbufferPrograms.Entry.class);
        UmbraProgram program = mock(UmbraProgram.class);
        when(program.getProgram()).thenReturn(mock(GlProgram.class));
        when(entry.getProgram()).thenReturn(program);
        when(entry.getUniforms()).thenReturn(mock(ProgramUniforms.class));
        when(entry.drawBuffersReadOnly()).thenReturn(new int[]{0});
        when(entry.getBlendState()).thenReturn(mock(ProgramBlendState.class));
        when(entry.getAlphaTest()).thenReturn(mock(ProgramAlphaTest.class));
        when(entry.getShadowSamplerKinds()).thenReturn(ShadowSamplerKinds.ALL_COMPARE);
        when(this.programs.get(ProgramId.HandWater)).thenReturn(entry);
        assertTrue(this.hand.begin(ProgramId.HandWater, HandPass.TRANSLUCENT_STAGE));
        verify(program).bind();
        verify(entry).setHandLightmap(160.0f, 240.0f);
        verify(entry.getUniforms()).update();
        verify(entry.getBlendState()).apply(new int[]{0});
        verify(TestGl.gl()).glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, 160.0f, 240.0f);
        assertEquals(23, CapturedRenderingState.INSTANCE.getRenderStage());
        assertEquals(ProgramId.HandWater, this.phases.current());
        // The light is read at the eye, from a position reused across frames
        ArgumentCaptor<BlockPos> eye = ArgumentCaptor.forClass(BlockPos.class);
        verify(this.gl.client.world).getCombinedLight(eye.capture(), org.mockito.ArgumentMatchers.eq(0));
        assertEquals(new BlockPos(10, 65, -4), eye.getValue().toImmutable());
        this.hand.begin(ProgramId.HandWater, HandPass.TRANSLUCENT_STAGE);
        verify(entry, never()).getAlphaTest();
    }

    @Test
    void packedLightSplitsIntoItsTwoLightmapCoordinates() {
        assertEquals(160.0f, HandPass.blockLight(0x00F000A0));
        assertEquals(240.0f, HandPass.skyLight(0x00F000A0));
        assertEquals(65535.0f, HandPass.blockLight(0xFFFFFFFF));
        assertEquals(65535.0f, HandPass.skyLight(0xFFFFFFFF));
        assertEquals(0.0f, HandPass.skyLight(0x0000FFFF));
    }

    @Test
    void aWorldWithoutAPlayerYetLightsTheHandFullBright() {
        PipelineGl.enterFrame(this.state);
        inWorld(0x00100010);
        this.gl.client.player = null;
        assertTrue(this.hand.begin(ProgramId.Hand, HandPass.SOLID_STAGE));
        verify(TestGl.gl()).glMultiTexCoord2f(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        verify(this.gl.client.world, never()).getCombinedLight(any(), anyInt());
    }
}
