package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.joml.Vector3d;

// Shadow frustum that Distant Horizons uses to cull LODs.
public final class DhShadowCullingFrustum implements IDhApiShadowCullingFrustum {
    // Shadow renderer.
    private final UmbraShadowRenderer shadowRenderer;
    // Lowest y of the world.
    private int worldMinY;
    // Highest y of the world.
    private int worldMaxY;

    // Creates a dh shadow culling frustum from its shadow renderer.
    public DhShadowCullingFrustum(UmbraShadowRenderer shadowRenderer) {
        this.shadowRenderer = shadowRenderer;
    }

    // Records the world height range of the frame.
    @Override
    public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {
        this.worldMinY = worldMinBlockY;
        this.worldMaxY = worldMaxBlockY;
    }

    // Returns whether an LOD column is inside the shadow frustum.
    @Override
    public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
        Frustum frustum = this.shadowRenderer.getCullingFrustum();
        if (frustum == null) {
            return true;
        }
        Vector3d camera = CapturedRenderingState.INSTANCE.getCameraPosition();
        return frustum.testAab(
                (float) (lodBlockPosMinX - camera.x),
                (float) (this.worldMinY - camera.y),
                (float) (lodBlockPosMinZ - camera.z),
                (float) (lodBlockPosMinX + lodBlockWidth - camera.x),
                (float) (this.worldMaxY - camera.y),
                (float) (lodBlockPosMinZ + lodBlockWidth - camera.z));
    }
}
