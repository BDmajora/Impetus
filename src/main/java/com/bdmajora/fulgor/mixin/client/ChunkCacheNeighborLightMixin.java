package com.bdmajora.fulgor.mixin.client;

import com.bdmajora.fulgor.lighting.FaceLightRules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ChunkCache.
@SideOnly(Side.CLIENT)
@Mixin(ChunkCache.class)
public abstract class ChunkCacheNeighborLightMixin {
    // Reference to the field world of the target.
    @Shadow
    @Final
    protected World world;

    // Reference to the method get light for of the target.
    @Shadow
    public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

    // Reference to the method get block state of the target.
    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    // Runs at the start of getLightForExt and can cancel it.
    @Inject(method = "getLightForExt", at = @At("HEAD"), cancellable = true)
    private void fulgor$faceAwareNeighborLight(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (type == EnumSkyBlock.SKY && !this.world.provider.hasSkyLight()) {
            cir.setReturnValue(0);
            return;
        }
        if (pos.getY() < 0 || pos.getY() >= 256) {
            cir.setReturnValue(type.defaultLightValue);
            return;
        }
        int faces = FaceLightRules.openFaces(getBlockState(pos));
        if (faces == 0) {
            return;
        }
        cir.setReturnValue(FaceLightRules.foldOpenFaces(faces, getLightFor(type, pos), type, pos, this::getLightFor));
    }
}
