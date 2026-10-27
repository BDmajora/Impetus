package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.util.BlockRenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the shadow terrain selection and layer draws
class ShadowTerrainTest {
    // Identity matrix in column order
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    // GL model of the test
    private PipelineGl gl;
    // World renderer whose searches and draws are recorded
    private ImpetusWorldRenderer renderer;
    // Model view of the active render info before the test
    private final float[] savedModelView = new float[16];

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Puts identity matrices in the captured state and the active render info and gives the client a view entity
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.renderer = mock(ImpetusWorldRenderer.class);
        when(this.gl.client.getRenderViewEntity()).thenReturn(mock(EntityPlayerSP.class));
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        modelView.get(0, this.savedModelView);
        modelView.put(0, IDENTITY);
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f());
        CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f());
    }

    // Restores the render info and the captured state and the pipeline
    @AfterEach
    void close() {
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "MODELVIEW").put(0, this.savedModelView);
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f());
        CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f());
        Mixins.set(Umbra.class, "renderingPipeline", null);
        this.gl.close();
    }

    // Returns content settings from shaders.properties lines
    private static ShadowContentSettings content(String... lines) {
        return ShadowContentSettings.from(ShaderProperties.parse(String.join("\n", lines)));
    }

    // Returns the frame stamp of the latest search
    private int lastFrame() {
        ArgumentCaptor<Integer> frame = ArgumentCaptor.forClass(Integer.class);
        verify(this.renderer, Mockito.atLeastOnce()).setupShadowTerrain(any(), any(), any(), frame.capture(), anyBoolean());
        return frame.getValue();
    }

    // Checks that select builds the filter and runs both searches with an increasing stamp
    @Test
    void selectBuildsTheFilterAndRunsBothSearches() {
        ShadowTerrain terrain = new ShadowTerrain(content("shadow.culling = off"), 64, 0, false, 0);
        assertNull(terrain.cullingFrustum());
        Vector3d camera = new Vector3d(8.5, 70.0, -8.5);
        terrain.select(this.renderer, this.gl.client, camera, new Matrix4f());
        assertSame(ShadowFrustums.NON_CULLING, terrain.cullingFrustum());
        ArgumentCaptor<Viewport> player = ArgumentCaptor.forClass(Viewport.class);
        ArgumentCaptor<Viewport> shadow = ArgumentCaptor.forClass(Viewport.class);
        ArgumentCaptor<SimpleWorldRenderer.CameraState> state = ArgumentCaptor.forClass(SimpleWorldRenderer.CameraState.class);
        verify(this.renderer).setupShadowTerrain(player.capture(), shadow.capture(), state.capture(), eq(1), eq(false));
        assertSame(ShadowFrustums.NON_CULLING, shadow.getValue().getFrustum());
        assertEquals(0, shadow.getValue().getChunkCoord().x());
        assertEquals(4, shadow.getValue().getChunkCoord().y());
        assertEquals(-1, shadow.getValue().getChunkCoord().z());
        assertNotNull(state.getValue());
        assertNotSame(player.getValue(), shadow.getValue());
        terrain.select(this.renderer, this.gl.client, camera, new Matrix4f());
        assertEquals(2, lastFrame());
        assertEquals(new Vector3d(8.5, 70.0, -8.5), camera);
    }

    // Checks that the spectator flag follows the player and a missing player counts as not spectating
    @Test
    void theSpectatorFlagFollowsThePlayer() {
        ShadowTerrain terrain = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0);
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        when(player.isSpectator()).thenReturn(true);
        this.gl.client.player = player;
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        verify(this.renderer).setupShadowTerrain(any(), any(), any(), anyInt(), eq(true));
        when(player.isSpectator()).thenReturn(false);
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        verify(this.renderer).setupShadowTerrain(any(), any(), any(), eq(2), eq(false));
        this.gl.client.player = null;
    }

    // Checks that the stamp wraps from the largest int to the smallest
    @Test
    void theStampWrapsAtTheIntLimit() {
        ShadowTerrain terrain = new ShadowTerrain(content("shadow.culling = off"), 64, 0, false, 0);
        Mixins.set(terrain, "frame", Integer.MAX_VALUE);
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        assertEquals(Integer.MIN_VALUE, lastFrame());
    }

    // Checks that the render distance in chunks decides between a distance box and no filter for a voxelizing pack
    @Test
    void theRenderDistanceDecidesTheVoxelFilter() {
        ShadowTerrain terrain = new ShadowTerrain(ShadowContentSettings.defaults(), 100, 0, true, 0);
        this.gl.settings.renderDistanceChunks = 8;
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        assertInstanceOf(ShadowBoxCuller.class, terrain.cullingFrustum());
        this.gl.settings.renderDistanceChunks = 4;
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        assertSame(ShadowFrustums.NON_CULLING, terrain.cullingFrustum());
        this.gl.settings.renderDistanceChunks = 0;
        terrain.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        assertSame(ShadowFrustums.NON_CULLING, terrain.cullingFrustum());
        ShadowTerrain unset = new ShadowTerrain(ShadowContentSettings.defaults(), 0, 0, true, 0);
        this.gl.settings.renderDistanceChunks = 8;
        unset.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f());
        assertSame(ShadowFrustums.NON_CULLING, unset.cullingFrustum());
    }

    // Checks that reversed culling builds a safe zone frustum and the default mode an advanced one
    @Test
    void theCullingModeChoosesTheFrustum() {
        ShadowTerrain reversed = new ShadowTerrain(content("shadow.culling = reversed"), 256, 32, true, 0);
        reversed.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f().perspective(1.5f, 1.0f, 0.05f, 256.0f));
        assertInstanceOf(SafeZoneCullingFrustum.class, reversed.cullingFrustum());
        ShadowTerrain advanced = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0);
        advanced.select(this.renderer, this.gl.client, new Vector3d(), new Matrix4f().perspective(1.5f, 1.0f, 0.05f, 256.0f));
        assertInstanceOf(AdvancedShadowCullingFrustum.class, advanced.cullingFrustum());
    }

    // Checks that the player view is the clip cube around the feet for identity matrices
    @Test
    void thePlayerViewIsTheCapturedClipCube() {
        ShadowTerrain terrain = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0);
        Viewport view = terrain.playerViewport(new Vector3d(100.5, 64, 100.5));
        assertEquals(6, view.getChunkCoord().x());
        assertEquals(4, view.getChunkCoord().y());
        assertTrue(view.isBoxVisible(100.0, 64.0, 100.0, 101.0, 65.0, 101.0));
        assertFalse(view.isBoxVisible(110.0, 64.0, 100.0, 111.0, 65.0, 101.0));
        UmbraRenderingPipeline culled = mock(UmbraRenderingPipeline.class);
        Mixins.set(Umbra.class, "renderingPipeline", culled);
        assertFalse(terrain.playerViewport(new Vector3d(100.5, 64, 100.5)).isBoxVisible(110.0, 64.0, 100.0, 111.0, 65.0, 101.0));
    }

    // Checks that disabled frustum culling gives a player view that accepts every box
    @Test
    void disabledFrustumCullingAcceptsEveryBox() {
        UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
        when(pipeline.shouldDisableFrustumCulling()).thenReturn(true);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        Viewport view = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0)
                .playerViewport(new Vector3d(0, 64, 0));
        assertSame(ShadowFrustums.NON_CULLING, view.getFrustum());
        assertTrue(view.isBoxVisible(-1.0e6, -1.0e6, -1.0e6, -1.0e6 + 1, -1.0e6 + 1, -1.0e6 + 1));
    }

    // Checks that a later player view does not change an earlier one
    @Test
    void consecutivePlayerViewsKeepTheirOwnPositionAndPlanes() {
        ShadowTerrain terrain = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0);
        Viewport first = terrain.playerViewport(new Vector3d(100.5, 64, 100.5));
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f().scale(0.001f));
        Viewport second = terrain.playerViewport(new Vector3d(-100.5, 64, -100.5));
        assertEquals(6, first.getChunkCoord().x());
        assertEquals(-7, second.getChunkCoord().x());
        assertFalse(first.isBoxVisible(110.0, 64.0, 100.0, 111.0, 65.0, 101.0));
        assertTrue(second.isBoxVisible(-90.0, 64.0, -100.0, -89.0, 65.0, -99.0));
    }

    // Checks the layer order of the opaque and translucent draws at the camera position
    @Test
    void layersDrawInOrderAtTheCamera() {
        ShadowTerrain terrain = new ShadowTerrain(ShadowContentSettings.defaults(), 64, 0, false, 0);
        Vector3d camera = new Vector3d(1.5, 2.5, 3.5);
        terrain.drawOpaque(this.renderer, camera);
        terrain.drawTranslucent(this.renderer, camera);
        InOrder order = Mockito.inOrder(this.renderer);
        order.verify(this.renderer).drawChunkLayer(BlockRenderLayer.SOLID, 1.5, 2.5, 3.5);
        order.verify(this.renderer).drawChunkLayer(BlockRenderLayer.CUTOUT_MIPPED, 1.5, 2.5, 3.5);
        order.verify(this.renderer).drawChunkLayer(BlockRenderLayer.CUTOUT, 1.5, 2.5, 3.5);
        order.verify(this.renderer).drawChunkLayer(BlockRenderLayer.TRANSLUCENT, 1.5, 2.5, 3.5);
        Mockito.verifyNoMoreInteractions(this.renderer);
    }

    // Checks that null content is rejected
    @Test
    void nullContentIsRejected() {
        assertThrows(NullPointerException.class, () -> new ShadowTerrain(null, 64, 0, false, 0));
    }
}
