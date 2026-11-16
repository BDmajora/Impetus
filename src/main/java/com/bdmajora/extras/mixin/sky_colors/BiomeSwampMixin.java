package com.bdmajora.extras.mixin.sky_colors;

import com.bdmajora.extras.Extras;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeSwamp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes BiomeSwamp.
@Mixin(BiomeSwamp.class)
public class BiomeSwampMixin {
    // Runs at the start of getGrassColorAtPos and can cancel it.
    @Inject(method = "getGrassColorAtPos", at = @At("HEAD"), cancellable = true)
    private void impetus$grassColor(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().detail.swampColors) {
            Biome self = (Biome) (Object) this;
            cir.setReturnValue(ColorizerGrass.getGrassColor(
                    impetus$temperature(self, pos), impetus$rainfall(self)));
        }
    }

    // Runs at the start of getFoliageColorAtPos and can cancel it.
    @Inject(method = "getFoliageColorAtPos", at = @At("HEAD"), cancellable = true)
    private void impetus$foliageColor(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Extras.options().detail.swampColors) {
            Biome self = (Biome) (Object) this;
            cir.setReturnValue(ColorizerFoliage.getFoliageColor(
                    impetus$temperature(self, pos), impetus$rainfall(self)));
        }
    }

    // Helper method temperature added to the target.
    @Unique
    private static double impetus$temperature(Biome biome, BlockPos pos) {
        return MathHelper.clamp(biome.getTemperature(pos), 0.0F, 1.0F);
    }

    // Helper method rainfall added to the target.
    @Unique
    private static double impetus$rainfall(Biome biome) {
        return MathHelper.clamp(biome.getRainfall(), 0.0F, 1.0F);
    }
}
