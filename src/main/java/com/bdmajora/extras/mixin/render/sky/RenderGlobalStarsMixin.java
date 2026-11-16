package com.bdmajora.extras.mixin.render.sky;

import com.bdmajora.extras.Extras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public class RenderGlobalStarsMixin {
    // Wraps the call of WorldClient.getStarBrightness in renderSky.
    @WrapOperation(
            method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")
    )
    private float impetus$starBrightness(WorldClient world, float partialTicks, Operation<Float> original) {
        if (!Extras.options().detail.stars) {
            return 0.0F;
        }
        return original.call(world, partialTicks);
    }

    // Changes a constant in renderStars.
    @ModifyConstant(
            method = "renderStars(Lnet/minecraft/client/renderer/BufferBuilder;)V",
            constant = @Constant(intValue = 1500)
    )
    private int impetus$starCount(int vanillaCount) {
        return Extras.options().detail.totalStars;
    }
}
