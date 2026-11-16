package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.impl.render.terrain.CameraHelper;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockRenderLayer;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.Objects;

// Selector and drawer of the terrain of the shadow pass.
public final class ShadowTerrain {
    // Settings of what the shadow pass draws.
    private final ShadowContentSettings content;
    // Distance within which terrain draws.
    private final float cullDistance;
    // Distance of voxelized terrain.
    private final float voxelDistance;
    // Flag set when the pack voxelizes terrain.
    private final boolean packVoxelizes;
    // Sun path rotation.
    private final float sunPathRotation;
    // Player position of the frame.
    private final Vector3d playerPosition = new Vector3d();
    // Player view projection of the frame.
    private final Matrix4f playerViewProjection = new Matrix4f();
    // Frustum of the shadow selection.
    private Frustum cullingFrustum;
    // Frame counter of the selection.
    private int frame;

    // Creates a shadow terrain from its content and cull distance and voxel distance and pack voxelizes.
    public ShadowTerrain(ShadowContentSettings content, float cullDistance, float voxelDistance, boolean packVoxelizes,
                         float sunPathRotation) {
        this.content = Objects.requireNonNull(content, "content");
        this.cullDistance = cullDistance;
        this.voxelDistance = voxelDistance;
        this.packVoxelizes = packVoxelizes;
        this.sunPathRotation = sunPathRotation;
    }

    // Selects the sections visible to the shadow camera.
    public void select(ImpetusWorldRenderer renderer, Minecraft mc, Vector3d camera, Matrix4fc viewProjection) {
        this.cullingFrustum = ShadowFrustums.create(this.content.getCulling(), this.cullDistance, this.voxelDistance,
                this.packVoxelizes, mc.gameSettings.renderDistanceChunks * 16, this.sunPathRotation, viewProjection);
        renderer.setupShadowTerrain(
                playerViewport(camera),
                new Viewport(this.cullingFrustum, camera),
                ImpetusWorldRenderer.captureCameraState(mc.getRenderPartialTicks()),
                ++this.frame,
                mc.player != null && mc.player.isSpectator());
    }

    // Returns the viewport of the player camera.
    public Viewport playerViewport(Vector3d camera) {
        Vector3d position = this.playerPosition.set(camera).add(CameraHelper.getThirdPersonOffset());
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldDisableFrustumCulling()) {
            return new Viewport(ShadowFrustums.NON_CULLING, position);
        }
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        FrustumIntersection frustum = new FrustumIntersection().set(
                this.playerViewProjection.set(state.getGbufferProjection()).mul(state.getGbufferModelView()), true);
        return new Viewport(frustum::testAab, position);
    }

    // Draws the opaque terrain.
    public void drawOpaque(ImpetusWorldRenderer renderer, Vector3d camera) {
        renderer.drawChunkLayer(BlockRenderLayer.SOLID, camera.x, camera.y, camera.z);
        renderer.drawChunkLayer(BlockRenderLayer.CUTOUT_MIPPED, camera.x, camera.y, camera.z);
        renderer.drawChunkLayer(BlockRenderLayer.CUTOUT, camera.x, camera.y, camera.z);
    }

    // Draws the translucent terrain.
    public void drawTranslucent(ImpetusWorldRenderer renderer, Vector3d camera) {
        renderer.drawChunkLayer(BlockRenderLayer.TRANSLUCENT, camera.x, camera.y, camera.z);
    }

    // Returns the shadow culling frustum.
    public Frustum cullingFrustum() {
        return this.cullingFrustum;
    }
}
