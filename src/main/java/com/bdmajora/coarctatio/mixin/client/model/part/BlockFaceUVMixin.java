package com.bdmajora.coarctatio.mixin.client.model.part;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes BlockFaceUV.
@Mixin(BlockFaceUV.class)
public abstract class BlockFaceUVMixin {
    // Reference to the field uvs of the target.
    @Shadow
    public float[] uvs;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$poolUvs(float[] uvs, int rotation, CallbackInfo ci) {
        this.uvs = ModelCaches.FACE_UVS.deduplicate(this.uvs);
    }

    // Runs before every return of setUvs.
    @Inject(method = "setUvs", at = @At("RETURN"))
    private void coarctatio$poolSetUvs(float[] uvs, CallbackInfo ci) {
        this.uvs = ModelCaches.FACE_UVS.deduplicate(this.uvs);
    }
}
