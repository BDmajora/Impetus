package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes BakedQuad.
@Mixin(BakedQuad.class)
public class CoarctatioBakedQuadMixin {
    // Reference to the field vertex data of the target.
    @Mutable
    @Shadow
    @Final
    protected int[] vertexData;

    // Runs before every return of constructor.
    @Inject(
            method = "<init>([IILnet/minecraft/util/EnumFacing;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZLnet/minecraft/client/renderer/vertex/VertexFormat;)V",
            at = @At("RETURN")
    )
    private void coarctatio$poolVertexData(CallbackInfo ci) {
        if (((Object) this).getClass() != BakedQuad.class) {
            ModelCaches.recordSkippedQuad(((Object) this).getClass());
            return;
        }

        this.vertexData = ModelCaches.QUADS.deduplicate(this.vertexData);
    }
}
