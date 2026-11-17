package com.bdmajora.coarctatio.mixin.client.model.bake;

import net.minecraftforge.common.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.vecmath.Matrix4f;

// Mixin that changes TRSRTransformation.
@Mixin(value = TRSRTransformation.class, remap = false)
public abstract class TRSRTransformationIdentityMixin {
    // Reference to the field matrix of the target.
    @Shadow
    @Final
    private Matrix4f matrix;

    // Whether the matrix is the identity.
    private boolean coarctatio$isIdentity;

    // Runs before every return of <init>*.
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void coarctatio$cacheIdentity(CallbackInfo ci) {
        this.coarctatio$isIdentity = coarctatio$isIdentity(this.matrix);
    }

    // Replaces the whole method isIdentity.
    @Overwrite
    public boolean isIdentity() {
        return this.coarctatio$isIdentity;
    }

    // Returns whether the matrix equals the identity.
    private static boolean coarctatio$isIdentity(Matrix4f m) {
        return m != null
                && m.m00 == 1.0F && m.m01 == 0.0F && m.m02 == 0.0F && m.m03 == 0.0F
                && m.m10 == 0.0F && m.m11 == 1.0F && m.m12 == 0.0F && m.m13 == 0.0F
                && m.m20 == 0.0F && m.m21 == 0.0F && m.m22 == 1.0F && m.m23 == 0.0F
                && m.m30 == 0.0F && m.m31 == 0.0F && m.m32 == 0.0F && m.m33 == 1.0F;
    }
}
