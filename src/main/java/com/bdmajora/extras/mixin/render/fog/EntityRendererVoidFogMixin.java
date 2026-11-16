package com.bdmajora.extras.mixin.render.fog;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererVoidFogMixin {
    // Replaces the call of WorldProvider.getVoidFogYFactor in updateFogColor.
    @Redirect(
            method = "updateFogColor(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getVoidFogYFactor()D")
    )
    private double impetus$voidFogFactor(WorldProvider provider) {
        if (!Extras.options().detail.voidFog) {
            return 1.0D;
        }
        return provider.getVoidFogYFactor();
    }
}
