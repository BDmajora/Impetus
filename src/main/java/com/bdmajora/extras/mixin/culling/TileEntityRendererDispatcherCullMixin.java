package com.bdmajora.extras.mixin.culling;

import com.bdmajora.extras.client.culling.OcclusionCulling;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The same three-argument entry the render budget gates; a culled block entity costs one flag read
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherCullMixin {
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipOccluded(TileEntity blockEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (OcclusionCulling.shouldSkipBlockEntity(blockEntity)) {
            ci.cancel();
        }
    }
}
