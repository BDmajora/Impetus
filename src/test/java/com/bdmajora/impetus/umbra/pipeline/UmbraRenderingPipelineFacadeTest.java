package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraRenderingPipelineFacadeTest {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<GL14> gl14;
    private boolean framebufferSupported;
    private int defaultTexUnit;
    private int lightmapTexUnit;
    private UmbraRenderingPipeline pipeline;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void open() {
        Minecraft client = Mc.client();
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        settings.fboEnable = true;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 320;
        client.displayHeight = 240;
        when(client.getFramebuffer()).thenReturn(mock(Framebuffer.class));
        TextureManager textures = mock(TextureManager.class);
        when(textures.getTexture(any())).thenReturn(mock(ITextureObject.class));
        when(client.getTextureManager()).thenReturn(textures);
        this.framebufferSupported = OpenGlHelper.framebufferSupported;
        this.defaultTexUnit = OpenGlHelper.defaultTexUnit;
        this.lightmapTexUnit = OpenGlHelper.lightmapTexUnit;
        OpenGlHelper.framebufferSupported = true;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE0 + 1;
        Devices.active().makeInactive();
        this.gl11 = Mockito.mockStatic(GL11.class);
        this.gl13 = Mockito.mockStatic(GL13.class);
        this.gl14 = Mockito.mockStatic(GL14.class);
        when(TestGl.gl().glGetInteger(0x8872)).thenReturn(48);
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x8CDF)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(8);
        Mockito.doNothing().when(TestGl.gl()).glDisablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glEnablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glBlendFuncSeparatei(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, String.join("\n", "frustum.culling = false", "rain.depth = true",
                "beacon.beam.depth = true", "skipAllRendering = true", "separateEntityDraws = true", "backFace.translucent = false"));
        sources.put(AbsolutePackPath.fromAbsolutePath("/composite.vsh"), VERTEX);
        sources.put(AbsolutePackPath.fromAbsolutePath("/composite.fsh"), "void main() {}");
        this.pipeline = Mockito.spy(new UmbraRenderingPipeline(new ShaderPack(sources)));
    }

    @AfterEach
    void close() {
        try {
            if (this.pipeline != null) {
                this.pipeline.destroy();
            }
        } finally {
            this.gl14.close();
            this.gl13.close();
            this.gl11.close();
            OpenGlHelper.framebufferSupported = this.framebufferSupported;
            OpenGlHelper.defaultTexUnit = this.defaultTexUnit;
            OpenGlHelper.lightmapTexUnit = this.lightmapTexUnit;
        }
    }

    @Test
    void theEyesAndGlintBracketsSwitchThroughTheHookedSinglePhaseEntryPoint() {
        // Outside a frame the brackets bail before switching
        this.pipeline.beginEyes();
        this.pipeline.beginArmorGlint();
        verify(this.pipeline, never()).setPhase(ArgumentMatchers.<ProgramId>any());
        this.pipeline.beginWorldRendering(0.0f);
        this.pipeline.beginEyes();
        verify(this.pipeline).setPhase(ProgramId.SpiderEyes);
        this.pipeline.endEyes();
        this.pipeline.beginArmorGlint();
        verify(this.pipeline).setPhase(ProgramId.ArmorGlint);
        this.pipeline.endArmorGlint();
        // The ends restore through the two-argument switch, as before the split
        verify(this.pipeline, Mockito.times(2)).setPhase(ArgumentMatchers.<ProgramId>any());
        this.pipeline.finishWorldRendering();
    }

    @Test
    void theShortOverloadsCarryTheirDefaults() {
        this.pipeline.beginWorldRendering(0.0f);
        this.pipeline.setPhase(ProgramId.Clouds);
        verify(this.pipeline).setPhase(ProgramId.Clouds, 20);
        assertEquals(ProgramId.Clouds, this.pipeline.getCurrentPhase());
        int[] buffers = {0};
        ProgramBlendState blend = ProgramBlendState.empty();
        this.pipeline.onTerrainDraw(buffers, blend);
        verify(this.pipeline).onTerrainDraw(eq(buffers), eq(blend), any(ProgramAlphaTest.class), eq(false));
        this.pipeline.finishWorldRendering();
    }

    @Test
    void theSwitchGettersReadThePackAndCullingIsReportedAsWhatToDisable() {
        assertTrue(this.pipeline.shouldDisableFrustumCulling());
        assertFalse(this.pipeline.shouldDisableOcclusionCulling());
        assertTrue(this.pipeline.shouldWriteRainAndSnowToDepthBuffer());
        assertTrue(this.pipeline.shouldWriteBeaconBeamToDepthBuffer());
        assertTrue(this.pipeline.skipAllRendering());
        assertTrue(this.pipeline.shouldSeparateEntityDraws());
        assertEquals("mixed", this.pipeline.getParticleOrdering());
        assertFalse(this.pipeline.shouldCullBackFaces(3));
        assertTrue(this.pipeline.shouldCullBackFaces(0));
        assertFalse(this.pipeline.shouldDisableVanillaEntityShadows());
        assertNull(this.pipeline.getShadowRenderer());
        assertNotNull(this.pipeline.getDhCompat());
        assertFalse(this.pipeline.isWorldRenderingActive());
        assertFalse(this.pipeline.isCameraPassActive());
        assertFalse(this.pipeline.isRenderingPostDeferredTranslucents());
        assertFalse(this.pipeline.hasGbufferProgram(ProgramId.Entities));
        assertFalse(this.pipeline.hasDirectGbufferProgram(ProgramId.Entities));
        assertEquals(ProgramId.Entities, this.pipeline.getTranslucentEntityPhase());
    }

    @Test
    void theHorizonOnlyDrawsInsideAFrameAndTheHandPassesUseTheirOwnPrograms() {
        this.gl11.clearInvocations();
        this.pipeline.drawSkyHorizon();
        this.gl11.verify(() -> GL11.glColor4f(Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat()), never());
        this.pipeline.beginWorldRendering(0.0f);
        this.pipeline.captureRenderingState();
        this.pipeline.renderShadowMap();
        this.pipeline.drawSkyHorizon();
        this.pipeline.beginTranslucents();
        this.pipeline.beginHand();
        assertTrue(this.pipeline.beginHandRendering());
        assertEquals(ProgramId.Hand, this.pipeline.getCurrentPhase());
        this.pipeline.endHandRendering();
        assertTrue(this.pipeline.beginHandTranslucentRendering());
        assertEquals(ProgramId.HandWater, this.pipeline.getCurrentPhase());
        this.pipeline.endHandRendering();
        this.pipeline.refreshDynamicUniforms();
        this.pipeline.setLightmapEnabled(true);
        this.pipeline.afterTerrainDraw(1);
        this.pipeline.applyShadowSamplerKinds(null, false);
        this.pipeline.bindCustomImages();
        assertNotNull(this.pipeline.createDhFramebuffer(new int[]{0}, false));
        this.pipeline.onDhLodDraw(mock(com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer.class), new int[]{0}, ProgramBlendState.empty(), false);
        this.pipeline.afterDhLodDraw(1);
        assertTrue(this.pipeline.isCameraPassActive());
        this.pipeline.finishWorldRendering();
        assertFalse(this.pipeline.isWorldRenderingActive());
        verify(TestGl.gl(), Mockito.atLeastOnce()).glMultiTexCoord2f(anyInt(), Mockito.anyFloat(), Mockito.anyFloat());
    }

    @Test
    void anUnconfiguredPackKeepsVanillaCulling() {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, "");
        UmbraRenderingPipeline plain = new UmbraRenderingPipeline(new ShaderPack(sources));
        try {
            assertFalse(plain.shouldDisableFrustumCulling());
            assertFalse(plain.shouldDisableOcclusionCulling());
            assertTrue(plain.shouldCullBackFaces(3));
        } finally {
            plain.destroy();
        }
    }
}
