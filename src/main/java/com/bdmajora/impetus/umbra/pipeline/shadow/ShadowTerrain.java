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

// Selects the terrain sections of the shadow pass and draws their layers
public final class ShadowTerrain {
    // Culling mode and terrain switches of the pack
    private final ShadowContentSettings content;
    // Distance in blocks that the section filter culls against
    private final float cullDistance;
    // Radius in blocks of the always drawn zone under reversed culling
    private final float voxelDistance;
    // Flag set when the pack voxelizes during the shadow pass
    private final boolean packVoxelizes;
    // Tilt of the daily sun path in degrees
    private final float sunPathRotation;
    // Scratch position of the player viewport
    private final Vector3d playerPosition = new Vector3d();
    // Scratch product of the captured projection and model view
    private final Matrix4f playerViewProjection = new Matrix4f();
    // Section filter of the latest pass or null before the first one
    private Frustum cullingFrustum;
    // Stamp passed to the section search and incremented on each pass
    private int frame;

    // Creates the terrain stage from the pack culling settings
    public ShadowTerrain(ShadowContentSettings content, float cullDistance, float voxelDistance, boolean packVoxelizes,
                         float sunPathRotation) {
        this.content = Objects.requireNonNull(content, "content");
        this.cullDistance = cullDistance;
        this.voxelDistance = voxelDistance;
        this.packVoxelizes = packVoxelizes;
        this.sunPathRotation = sunPathRotation;
    }

    // Builds the section filter for this pass and runs the player and shadow section searches
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

    // Returns the player view from the captured matrices at the camera plus the third person offset
    public Viewport playerViewport(Vector3d camera) {
        Vector3d position = this.playerPosition.set(camera).add(CameraHelper.getThirdPersonOffset());
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldDisableFrustumCulling()) {
            return new Viewport(ShadowFrustums.NON_CULLING, position);
        }
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        // Creates a new intersection on each pass and reuses only the scratch matrix
        FrustumIntersection frustum = new FrustumIntersection().set(
                this.playerViewProjection.set(state.getGbufferProjection()).mul(state.getGbufferModelView()), true);
        return new Viewport(frustum::testAab, position);
    }

    // Draws the solid and cutout layers
    public void drawOpaque(ImpetusWorldRenderer renderer, Vector3d camera) {
        renderer.drawChunkLayer(BlockRenderLayer.SOLID, camera.x, camera.y, camera.z);
        renderer.drawChunkLayer(BlockRenderLayer.CUTOUT_MIPPED, camera.x, camera.y, camera.z);
        renderer.drawChunkLayer(BlockRenderLayer.CUTOUT, camera.x, camera.y, camera.z);
    }

    // Draws the translucent layer
    public void drawTranslucent(ImpetusWorldRenderer renderer, Vector3d camera) {
        renderer.drawChunkLayer(BlockRenderLayer.TRANSLUCENT, camera.x, camera.y, camera.z);
    }

    // Returns the section filter of the latest pass or null before the first one
    public Frustum cullingFrustum() {
        return this.cullingFrustum;
    }
}
