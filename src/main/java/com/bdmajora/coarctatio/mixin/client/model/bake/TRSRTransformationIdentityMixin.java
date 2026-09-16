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

// isIdentity compared sixteen floats against the identity instance on every call, and baking asks it per vertex; the transformation is immutable so the answer is computed once. remap = false, Forge class
@Mixin(value = TRSRTransformation.class, remap = false)
public abstract class TRSRTransformationIdentityMixin {
    private static final Matrix4f coarctatio$IDENTITY = coarctatio$identity();

    @Shadow
    @Final
    private Matrix4f matrix;

    private boolean coarctatio$isIdentity;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void coarctatio$cacheIdentity(CallbackInfo ci) {
        this.coarctatio$isIdentity = this.matrix.equals(coarctatio$IDENTITY);
    }

    /**
     * @author embeddedt, bdmajora
     * @reason The answer is fixed at construction
     */
    @Overwrite
    public boolean isIdentity() {
        return this.coarctatio$isIdentity;
    }

    private static Matrix4f coarctatio$identity() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        return identity;
    }
}
