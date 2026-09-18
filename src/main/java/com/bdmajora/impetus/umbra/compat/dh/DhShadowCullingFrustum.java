package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.joml.Vector3d;

// Culls DH's LOD sections in the shadow pass with the same frustum the shadow pass culls Impetus's sections with (Iris's AdvancedShadowCullingFrustumMixin): DH hands over world-space column bounds, Umbra's frustums test camera-relative boxes, and the world's height range comes from DH's update call
public final class DhShadowCullingFrustum implements IDhApiShadowCullingFrustum {
    private final UmbraShadowRenderer shadowRenderer;
    private int worldMinY;
    private int worldMaxY;

    public DhShadowCullingFrustum(UmbraShadowRenderer shadowRenderer) {
        this.shadowRenderer = shadowRenderer;
    }

    // DH's own view-projection is ignored: the shadow pass's frustum already encodes the light direction and the camera view, which is the point of overriding it
    @Override
    public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {
        this.worldMinY = worldMinBlockY;
        this.worldMaxY = worldMaxBlockY;
    }

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
