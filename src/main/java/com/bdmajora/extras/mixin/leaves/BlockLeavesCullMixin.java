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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes BlockLeaves.
@Mixin(BlockLeaves.class)
public abstract class BlockLeavesCullMixin {
    // Reference to the field leaves fancy of the target.
    @Shadow
    protected boolean leavesFancy;

    // Runs at the start of shouldSideBeRendered and can cancel it.
    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void impetus$cullInteriorFaces(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        ExtrasConfig.LeafSettings settings = Extras.options().leaves;
        if (settings.cullingMode == ExtrasConfig.LeafCulling.DEFAULT || !this.leavesFancy) {
            return;
        }
        BlockPos.MutableBlockPos cursor = impetus$CURSOR.get();
        if (!impetus$hides(world.getBlockState(cursor.setPos(pos).move(side)))) {
            return;
        }
        switch (settings.cullingMode) {
            case CHECK -> {
                for (EnumFacing other : EnumFacing.VALUES) {
                    if (other != side && !impetus$hides(world.getBlockState(cursor.setPos(pos).move(other)))) {
                        return;
                    }
                }
                cir.setReturnValue(false);
            }
            case DEPTH -> {
                cursor.setPos(pos).move(side);
                for (int i = 1; i <= settings.cullingDepth; i++) {
                    cursor.move(side);
                    IBlockState behind = world.getBlockState(cursor);
                    if (behind.getBlock().isAir(behind, world, cursor)) {
                        return;
                    }
                }
                cir.setReturnValue(false);
            }
            default -> { }
        }
    }

    // Field cursor added to the target.
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> impetus$CURSOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    // Handles hides.
    private static boolean impetus$hides(IBlockState state) {
        return state.getBlock() instanceof BlockLeaves || state.isOpaqueCube();
    }
}
