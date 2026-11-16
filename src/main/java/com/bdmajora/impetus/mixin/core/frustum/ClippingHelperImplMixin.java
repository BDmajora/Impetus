package com.bdmajora.impetus.mixin.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.impl.render.frustum.IClippingHelper;

// Mixin that changes ClippingHelperImpl.
@Mixin(ClippingHelperImpl.class)
public abstract class ClippingHelperImplMixin extends ClippingHelper implements IClippingHelper {
    // Field frustum added to the target.
    @Unique
    private final FrustumIntersection impetus$frustum = new FrustumIntersection();
    // Field joml projection added to the target.
    @Unique
    private final Matrix4f impetus$jomlProjection = new Matrix4f();
    // Field joml modelview added to the target.
    @Unique
    private final Matrix4f impetus$jomlModelview = new Matrix4f();

    // Runs before every return of init.
    @Inject(method = "init", at = @At("RETURN"))
    private void updateJoml(CallbackInfo ci) {
        this.impetus$jomlProjection.set(projectionMatrix);
        this.impetus$jomlModelview.set(modelviewMatrix);
        this.impetus$frustum.set(this.impetus$jomlProjection.mul(this.impetus$jomlModelview), true);
    }

    // Returns the joml frustum.
    @Override
    public FrustumIntersection impetus$getJomlFrustum() {
        return this.impetus$frustum;
    }
}
