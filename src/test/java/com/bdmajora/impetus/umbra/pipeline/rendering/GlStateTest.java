package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Vector2i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlStateTest {
    private PipelineGl gl;

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
    }

    @AfterEach
    void close() {
        CapturedRenderingState.INSTANCE.setAtlasSize(0, 0);
        this.gl.close();
    }

    @Test
    void theMainTargetIsVanillasFramebufferOrTheDefaultOne() {
        GlState.bindMainRenderTarget(this.gl.client);
        verify(this.gl.client.getFramebuffer()).bindFramebuffer(true);
        GlState.restoreMainDrawReadBuffers();
        verify(TestGl.gl()).glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        verify(TestGl.gl()).glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        OpenGlHelper.framebufferSupported = false;
        GlState.bindMainRenderTarget(this.gl.client);
        verify(TestGl.gl()).glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        verify(TestGl.gl()).glViewport(0, 0, 320, 240);
        GlState.restoreMainDrawReadBuffers();
        verify(TestGl.gl()).glDrawBuffers(0x0405);
        verify(TestGl.gl()).glReadBuffer(0x0405);
    }

    @Test
    void fullscreenMatricesArePushedAsTheUnitOrthoAndPoppedBack() {
        GlState.pushFullscreenMatrices();
        this.gl.gl11.verify(() -> GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0));
        this.gl.gl11.verify(GL11::glPushMatrix, times(3));
        this.gl.gl11.verify(GL11::glLoadIdentity, times(3));
        GlState.popFixedFunctionMatrices();
        this.gl.gl11.verify(GL11::glPopMatrix, times(3));
        // Modelview is the mode left selected, as vanilla expects
        this.gl.gl11.verify(() -> GL11.glMatrixMode(GlState.GL_MODELVIEW_MODE), times(2));
    }

    @Test
    void theVanillaVertexPipelineGetsEveryAliasedSlotBack() {
        Mockito.clearInvocations(TestGl.gl());
        GlState.resetVanillaVertexArrayState();
        verify(TestGl.gl()).glBindVertexArray(0);
        verify(TestGl.gl()).glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        verify(TestGl.gl()).glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        for (int slot = 0; slot < 16; slot++) {
            verify(TestGl.gl()).glDisableVertexAttribArray(slot);
        }
        verify(TestGl.gl(), times(16)).glDisableVertexAttribArray(anyInt());
    }

    @Test
    void unitZeroIsResyncedEvenWhenTheCacheIsStale() {
        // The cache believes unit 0 holds texture 7 while GL actually has something else there
        net.minecraft.client.renderer.GlStateManager.bindTexture(7);
        this.gl.textures[0] = 99;
        GlState.resyncTextureUnitZero();
        assertEquals(0, PipelineGl.cachedUnit());
        assertEquals(0, PipelineGl.cachedTexture(0));
        assertEquals(0, this.gl.textures[0]);
        assertEquals(0, this.gl.activeUnit);
    }

    @Test
    void indexedBlendStaysInsideTheGpusDrawBuffers() {
        GlState.disableIndexedBlend(12, 8);
        verify(TestGl.gl(), times(8)).glDisablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        verify(TestGl.gl(), never()).glDisablei(GL11.GL_BLEND, 8);
        Mockito.clearInvocations(TestGl.gl());
        GlState.translucentGbufferBlend(12, 8);
        verify(TestGl.gl(), times(8)).glEnablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        verify(TestGl.gl()).glBlendFuncSeparatei(7, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        verify(TestGl.gl(), never()).glEnablei(GL11.GL_BLEND, 8);
        Mockito.clearInvocations(TestGl.gl());
        // depthMask is cached, so it is turned off first to see the opaque blend turn it back on
        net.minecraft.client.renderer.GlStateManager.depthMask(false);
        GlState.opaqueGbufferBlend(3, 8);
        verify(TestGl.gl(), times(3)).glDisablei(Mockito.eq(GL11.GL_BLEND), anyInt());
        this.gl.gl11.verify(() -> GL11.glDepthMask(true), Mockito.atLeastOnce());
        // Without per-buffer blending only the global state moves
        Mockito.clearInvocations(TestGl.gl());
        when(TestGl.gl().supportsBufferBlending()).thenReturn(false);
        GlState.disableIndexedBlend(4, 8);
        GlState.translucentGbufferBlend(4, 8);
        verify(TestGl.gl(), never()).glDisablei(anyInt(), anyInt());
        verify(TestGl.gl(), never()).glEnablei(anyInt(), anyInt());
    }

    @Test
    void theScratchTextureBindPutsBackWhateverTheUnitHeld() {
        this.gl.activeUnit = SamplerLayout.DEPTH_COPY_SCRATCH_UNIT;
        this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT] = 41;
        int previous = GlState.bindScratchTexture2D(5);
        assertEquals(41, previous);
        assertEquals(SamplerLayout.DEPTH_COPY_SCRATCH_UNIT, this.gl.activeUnit);
        assertEquals(5, this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT]);
        GlState.restoreScratchTexture2D(previous);
        assertEquals(41, this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT]);
        assertEquals(0, this.gl.activeUnit);
        assertEquals(0, PipelineGl.cachedUnit());
    }

    @Test
    void anIncompleteFramebufferFailsLoudlyWithItsPurpose() {
        UmbraFramebuffer framebuffer = mock(UmbraFramebuffer.class);
        when(framebuffer.getStatus()).thenReturn(GL30.GL_FRAMEBUFFER_COMPLETE);
        GlState.checkComplete(framebuffer, "test", new int[]{0});
        when(framebuffer.getStatus()).thenReturn(0x8CD6);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> GlState.checkComplete(framebuffer, "gbuffer", new int[]{0, 4}));
        assertEquals("Incomplete Umbra gbuffer framebuffer for buffers [0, 4]: status=36054", failure.getMessage());
    }

    @Test
    void theAtlasSizeIsReadOffTheScratchUnitOrLeftAloneWithoutAnAtlas() {
        when(TestGl.gl().glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)).thenReturn(1024);
        when(TestGl.gl().glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)).thenReturn(512);
        GlState.captureAtlasSize(this.gl.client);
        Vector2i size = CapturedRenderingState.INSTANCE.getAtlasSize();
        assertEquals(new Vector2i(1024, 512), size);
        // The atlas was bound on the scratch unit and the unit restored
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 3);
        assertEquals(0, this.gl.textures[SamplerLayout.DEPTH_COPY_SCRATCH_UNIT]);
        when(this.gl.client.getTextureManager().getTexture(Mockito.any())).thenReturn(null);
        CapturedRenderingState.INSTANCE.setAtlasSize(1, 1);
        GlState.captureAtlasSize(this.gl.client);
        assertEquals(new Vector2i(1, 1), CapturedRenderingState.INSTANCE.getAtlasSize());
    }

    @Test
    void drainingStopsAtTheFirstClearReadAndGivesUpOnAStuckContext() {
        when(TestGl.gl().glGetError()).thenReturn(0x502, 0x500, 0, 0x501);
        GlState.drainGlError();
        verify(TestGl.gl(), times(3)).glGetError();
        Mockito.reset(TestGl.gl());
        // A lost context reports forever; draining used to spin the render thread
        when(TestGl.gl().glGetError()).thenReturn(0x0507);
        GlState.drainGlError();
        verify(TestGl.gl(), times(64)).glGetError();
        Mockito.reset(TestGl.gl());
        when(TestGl.gl().glGetError()).thenReturn(0x502, 0);
        GlState.reportGlError("here");
        GlState.reportGlError("there");
        verify(TestGl.gl(), times(2)).glGetError();
    }
}
