package com.bdmajora.extras.mixin.render.block_entity;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.tileentity.TileEntityPistonRenderer;
import net.minecraft.tileentity.TileEntityPiston;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityPistonRenderer.
@Mixin(TileEntityPistonRenderer.class)
public class TileEntityPistonRendererMixin {
    // Runs at the start of render and can cancel it.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntityPiston;DDDFIF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$render(TileEntityPiston piston, double x, double y, double z,
                                // Partial ticks and destroy stage and alpha.
                                float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (!Extras.options().render.pistons) {
            ci.cancel();
        }
    }
}
