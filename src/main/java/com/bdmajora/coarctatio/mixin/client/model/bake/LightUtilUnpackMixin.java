package com.bdmajora.coarctatio.mixin.client.model.bake;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes LightUtil.
@Mixin(value = LightUtil.class, remap = false)
public abstract class LightUtilUnpackMixin {
    // Runs before every return of unpack.
    @Inject(method = "unpack", at = @At("RETURN"))
    private static void coarctatio$positionW(int[] from, float[] to, VertexFormat formatFrom, int v, int e, CallbackInfo ci) {
        if (to.length < 4) {
            return;
        }
        VertexFormatElement element = formatFrom.getElement(e);
        if (element.getElementCount() == 3 && element.isPositionElement()) {
            to[3] = 1.0f;
        }
    }
}
