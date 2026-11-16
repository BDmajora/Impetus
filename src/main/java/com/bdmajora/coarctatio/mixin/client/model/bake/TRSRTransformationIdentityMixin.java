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
    // Shared identity matrix.
    private static final Matrix4f coarctatio$IDENTITY = coarctatio$identity();

    // Reference to the field matrix of the target.
    @Shadow
    @Final
    private Matrix4f matrix;

    // Value is identity.
    private boolean coarctatio$isIdentity;

    // Runs before every return of <init>*.
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void coarctatio$cacheIdentity(CallbackInfo ci) {
        this.coarctatio$isIdentity = this.matrix.equals(coarctatio$IDENTITY);
    }

    // Replaces the whole method isIdentity.
    @Overwrite
    public boolean isIdentity() {
        return this.coarctatio$isIdentity;
    }

    // Handles identity.
    private static Matrix4f coarctatio$identity() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        return identity;
    }
}
