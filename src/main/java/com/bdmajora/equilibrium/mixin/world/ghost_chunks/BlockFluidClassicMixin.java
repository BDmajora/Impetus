package com.bdmajora.equilibrium.mixin.world.ghost_chunks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

// Forge's classic fluid looks up to four blocks sideways for somewhere to flow on every tick, loading whatever chunk lies that way (FoamFix's ghostbuster, fluids); vanilla liquids already refuse to tick unless the area is loaded, so the same guard is applied here. Forge class, but updateTick overrides Block's and is func_180650_b in production, so the selector must remap
@Mixin(BlockFluidClassic.class)
public abstract class BlockFluidClassicMixin {
    @Inject(method = "updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V", at = @At("HEAD"), cancellable = true)
    private void equilibrium$requireLoadedArea(World world, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        if (!world.isAreaLoaded(pos, 4)) {
            ci.cancel();
        }
    }
}
