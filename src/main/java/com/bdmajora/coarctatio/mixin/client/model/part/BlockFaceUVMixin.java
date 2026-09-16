package com.bdmajora.coarctatio.mixin.client.model.part;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pools the four-float UV array of every model face; the baker only reads it, and the default UVs BlockPart derives from the element bounds repeat across the whole model set (StellarCore)
@Mixin(BlockFaceUV.class)
public abstract class BlockFaceUVMixin {
    @Shadow
    public float[] uvs;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$poolUvs(float[] uvs, int rotation, CallbackInfo ci) {
        this.uvs = ModelCaches.FACE_UVS.deduplicate(this.uvs);
    }

    // The setter only fills in a null array, so the pooled instance is stored in its place
    @Inject(method = "setUvs", at = @At("RETURN"))
    private void coarctatio$poolSetUvs(float[] uvs, CallbackInfo ci) {
        this.uvs = ModelCaches.FACE_UVS.deduplicate(this.uvs);
    }
}
