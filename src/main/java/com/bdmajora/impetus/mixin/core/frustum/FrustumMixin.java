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
        return ((IClippingHelper)clippingHelper).impetus$getJomlFrustum().testAab((float) (minX - this.x), (float) (minY - this.y), (float) (minZ - this.z), (float) (maxX - this.x), (float) (maxY - this.y), (float) (maxZ - this.z));
    }

    // Returns a viewport of the frustum.
    @Override
    public Viewport impetus$createViewport() {
        var frustum = ((IClippingHelper)clippingHelper).impetus$getJomlFrustum();
        return new Viewport(frustum::testAab, new org.joml.Vector3d(this.x, this.y, this.z).add(CameraHelper.getThirdPersonOffset()));
    }
}
