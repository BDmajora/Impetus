package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Mixin(BlockStateMapper.class)
public abstract class BlockStateMapperMixin {
    @Inject(method = "getVariants", at = @At("HEAD"), cancellable = true)
    private void impetus$skipMissingBlockVariants(Block block, CallbackInfoReturnable<Map<IBlockState, ModelResourceLocation>> cir) {
        if (impetus$isMissingBlock(block)) {
            cir.setReturnValue(Collections.emptyMap());
        }
    }

    @Inject(method = "getBlockstateLocations", at = @At("HEAD"), cancellable = true)
    private void impetus$skipMissingBlockLocations(Block block, CallbackInfoReturnable<Set<ResourceLocation>> cir) {
        if (impetus$isMissingBlock(block)) {
            cir.setReturnValue(Collections.emptySet());
        }
    }

    // Forge gives missing save entries their original names but creates them as invisible dummy air.
    @Unique
    private static boolean impetus$isMissingBlock(Block block) {
        return block instanceof BlockAir
                && block.getClass().getName().equals("net.minecraftforge.registries.GameData$BlockCallbacks$BlockDummyAir");
    }
}
