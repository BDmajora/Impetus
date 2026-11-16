package com.bdmajora.impetus.mixin.features.render.tileentity.piston;

import net.minecraft.client.renderer.tileentity.TileEntityPistonRenderer;
import net.minecraft.tileentity.TileEntityPiston;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes TileEntityPistonRenderer.
@Mixin(TileEntityPistonRenderer.class)
public class TileEntityPistonRendererMixin {
    // Replaces the call of TileEntityPiston.getProgress in render.
    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntityPiston;DDDFIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntityPiston;getProgress(F)F", ordinal = 0))
    private float alwaysRenderTESR(TileEntityPiston instance, float ticks) {
        return 0f;
    }
}
