package com.bdmajora.extras.mixin.profiler;

import com.bdmajora.extras.client.ProfilerHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityRendererDispatcher.
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherMixin {
    // Runs at the start of render.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V",
            at = @At("HEAD")
    )
    private void impetus$beginRender(TileEntity tileEntity, double x, double y, double z,
                                     // Partial ticks and destroy stage and alpha.
                                     float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        ProfilerHelper.startSection(tileEntity.getWorld(),
                TileEntityRendererDispatcher.instance.getRenderer(tileEntity.getClass()));
    }

    // Runs at the end of render.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V",
            at = @At("TAIL")
    )
    private void impetus$endRender(TileEntity tileEntity, double x, double y, double z,
                                   // Partial ticks and destroy stage and alpha.
                                   float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        ProfilerHelper.endSection(tileEntity.getWorld(),
                TileEntityRendererDispatcher.instance.getRenderer(tileEntity.getClass()));
    }
}
