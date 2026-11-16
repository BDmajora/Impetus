package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.pipeline.VanillaFeatureToggles;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

// Mixin that changes GameSettings.
@Mixin(GameSettings.class)
public class GameSettingsCloudsMixin {
    // Reference to the field render distance chunks of the target.
    @Shadow
    public int renderDistanceChunks;

    // Runs at the start of shouldRenderClouds and can cancel it.
    @Inject(method = "shouldRenderClouds", at = @At("HEAD"), cancellable = true)
    private void impetus$overrideCloudMode(CallbackInfoReturnable<Integer> cir) {
        if (this.renderDistanceChunks < 4) {
            return;
        }

        OptionalInt mode = VanillaFeatureToggles.getCloudMode();
        if (mode.isPresent()) {
            cir.setReturnValue(mode.getAsInt());
        }
    }
}
