package com.bdmajora.extras.mixin.leaves;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fancy leaves draw every face of every leaf block, including the ones buried inside a canopy that no camera angle reaches (More Culling's leaf modes, Cull Less Leaves' depth rule); this is the same shouldSideBeRendered the chunk mesher asks, so it covers vanilla and Impetus meshing alike. Check: a face toward a leaf or opaque block is dropped only when this block is boxed in on every other side too. Depth: a face toward leaves is dropped when the next N blocks along that direction are all solid
@Mixin(BlockLeaves.class)
public abstract class BlockLeavesCullMixin {
    @Shadow
    protected boolean leavesFancy;

    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void impetus$cullInteriorFaces(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        ExtrasConfig.LeafSettings settings = Extras.options().leaves;
        if (settings.cullingMode == ExtrasConfig.LeafCulling.DEFAULT || !this.leavesFancy) {
            return;
        }
        BlockPos sidePos = pos.offset(side);
        IBlockState sideState = world.getBlockState(sidePos);
        if (!impetus$hides(sideState)) {
            return;
        }
        switch (settings.cullingMode) {
            case CHECK:
                for (EnumFacing other : EnumFacing.VALUES) {
                    if (other != side && !impetus$hides(world.getBlockState(pos.offset(other)))) {
                        return;
                    }
                }
                cir.setReturnValue(false);
                return;
            case DEPTH:
                for (int i = 1; i <= settings.cullingDepth; i++) {
                    IBlockState behind = world.getBlockState(sidePos.offset(side, i));
                    if (behind.getBlock().isAir(behind, world, sidePos)) {
                        return;
                    }
                }
                cir.setReturnValue(false);
                return;
            default:
        }
    }

    private static boolean impetus$hides(IBlockState state) {
        return state.getBlock() instanceof BlockLeaves || state.isOpaqueCube();
    }
}
