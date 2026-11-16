package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.BakedEntityContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes BlockRendererDispatcher.
@Mixin(BlockRendererDispatcher.class)
public abstract class BlockRendererDispatcherContextMixin {
    // Runs at the start of renderBlock.
    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void impetus$pinContext(IBlockState state, BlockPos pos, IBlockAccess access, BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        BakedEntityContext.set(access, pos);
    }

    // Runs before every return of renderBlock.
    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void impetus$unpinContext(IBlockState state, BlockPos pos, IBlockAccess access, BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        BakedEntityContext.clear();
    }

    // Runs at the start of renderBlockDamage.
    @Inject(method = "renderBlockDamage", at = @At("HEAD"))
    private void impetus$pinDamageContext(IBlockState state, BlockPos pos, TextureAtlasSprite sprite, IBlockAccess access, CallbackInfo ci) {
        BakedEntityContext.set(access, pos);
    }

    // Runs before every return of renderBlockDamage.
    @Inject(method = "renderBlockDamage", at = @At("RETURN"))
    private void impetus$unpinDamageContext(IBlockState state, BlockPos pos, TextureAtlasSprite sprite, IBlockAccess access, CallbackInfo ci) {
        BakedEntityContext.clear();
    }
}
