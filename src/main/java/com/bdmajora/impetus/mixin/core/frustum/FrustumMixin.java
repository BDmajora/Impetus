package com.bdmajora.impetus.mixin.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.ViewportProvider;
import org.spongepowered.asm.mixin.*;
import com.bdmajora.impetus.impl.render.frustum.IClippingHelper;
import com.bdmajora.impetus.impl.render.terrain.CameraHelper;

// Mixin that changes Frustum.
@Mixin(Frustum.class)
public class FrustumMixin implements ViewportProvider {
    // Reference to the field clipping helper of the target.
    @Shadow
    @Final
    private ClippingHelper clippingHelper;

    // Reference to the field x of the target.
    @Shadow
    private double x, y, z;

    // Replaces the whole method isBoxInFrustum.
    @Overwrite
    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ) || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        // Falls back to the helper's own test for a helper without JOML planes.
        if (!(this.clippingHelper instanceof IClippingHelper joml)) {
            return this.clippingHelper.isBoxInFrustum(minX - this.x, minY - this.y, minZ - this.z, maxX - this.x, maxY - this.y, maxZ - this.z);
        }
        return joml.impetus$getJomlFrustum().testAab((float) (minX - this.x), (float) (minY - this.y), (float) (minZ - this.z), (float) (maxX - this.x), (float) (maxY - this.y), (float) (maxZ - this.z));
    }

    // Returns a viewport of the frustum.
    @Override
    public Viewport impetus$createViewport() {
        ClippingHelper helper = this.clippingHelper;
        com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum test = helper instanceof IClippingHelper joml
                ? joml.impetus$getJomlFrustum()::testAab
                : (minX, minY, minZ, maxX, maxY, maxZ) -> helper.isBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
        return new Viewport(test, new org.joml.Vector3d(this.x, this.y, this.z).add(CameraHelper.getThirdPersonOffset()));
    }
}
