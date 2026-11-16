package com.bdmajora.extras.mixin.biome_colors;

import com.bdmajora.extras.Extras;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes BiomeColorHelper.
@Mixin(BiomeColorHelper.class)
public class BiomeColorHelperMixin {
    // Field default grass color added to the target.
    @Unique
    private static final int IMPETUS$DEFAULT_GRASS_COLOR = 0x91BD59;
    // Field default water color added to the target.
    @Unique
    private static final int IMPETUS$DEFAULT_WATER_COLOR = 0xFFFFFF;
    // Field default foliage color added to the target.
    @Unique
    private static final int IMPETUS$DEFAULT_FOLIAGE_COLOR = 0x59AE30;

    // Runs at the start of getGrassColorAtPos and can cancel it.
    @Inject(method = "getGrassColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void impetus$grassColor(CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().detail.biomeColors) {
            cir.setReturnValue(IMPETUS$DEFAULT_GRASS_COLOR);
        }
    }

    // Runs at the start of getWaterColorAtPos and can cancel it.
    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void impetus$waterColor(CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().detail.biomeColors) {
            cir.setReturnValue(IMPETUS$DEFAULT_WATER_COLOR);
        }
    }

    // Runs at the start of getFoliageColorAtPos and can cancel it.
    @Inject(method = "getFoliageColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void impetus$foliageColor(CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().detail.biomeColors) {
            cir.setReturnValue(IMPETUS$DEFAULT_FOLIAGE_COLOR);
        }
    }
}
