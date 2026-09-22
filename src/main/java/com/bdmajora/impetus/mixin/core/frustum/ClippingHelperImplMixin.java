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

// Patches ClippingHelperImpl to keep a JOML FrustumIntersection in sync with the vanilla matrices, for faster AABB tests
@Mixin(ClippingHelperImpl.class)
public abstract class ClippingHelperImplMixin extends ClippingHelper implements IClippingHelper {
    @Unique
    private final FrustumIntersection impetus$frustum = new FrustumIntersection();
    // Scratch matrices for the per-frame sync, so it allocates nothing
    @Unique
    private final Matrix4f impetus$jomlProjection = new Matrix4f();
    @Unique
    private final Matrix4f impetus$jomlModelview = new Matrix4f();

    // Mirrors vanilla's matrices into the JOML frustum every time vanilla recomputes its planes
    @Inject(method = "init", at = @At("RETURN"))
    private void updateJoml(CallbackInfo ci) {
        this.impetus$jomlProjection.set(projectionMatrix);
        this.impetus$jomlModelview.set(modelviewMatrix);
        this.impetus$frustum.set(this.impetus$jomlProjection.mul(this.impetus$jomlModelview), true);
    }

    @Override
    public FrustumIntersection impetus$getJomlFrustum() {
        return this.impetus$frustum;
    }
}
