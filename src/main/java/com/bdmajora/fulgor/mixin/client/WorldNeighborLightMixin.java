package com.bdmajora.fulgor.mixin.client;

import com.bdmajora.fulgor.lighting.FaceLightRules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Face-aware neighbour brightness for entity and item rendering, which reads through World; limited to client worlds so the integrated server's spawn light checks keep vanilla's answer
@Mixin(World.class)
public abstract class WorldNeighborLightMixin {
    @Shadow
    public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    @Shadow
    public abstract boolean isValid(BlockPos pos);

    @Shadow
    public abstract boolean isBlockLoaded(BlockPos pos);

    @Inject(method = "getLightFromNeighborsFor", at = @At("HEAD"), cancellable = true)
    private void fulgor$faceAwareNeighborLight(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        World self = (World) (Object) this;
        if (!self.isRemote) {
            return;
        }
        if (type == EnumSkyBlock.SKY && !self.provider.hasSkyLight()) {
            cir.setReturnValue(0);
            return;
        }
        if (pos.getY() < 0) {
            pos = new BlockPos(pos.getX(), 0, pos.getZ());
        }
        if (!isValid(pos) || !isBlockLoaded(pos)) {
            cir.setReturnValue(type.defaultLightValue);
            return;
        }
        int faces = FaceLightRules.openFaces(getBlockState(pos));
        int level = getLightFor(type, pos);
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (level >= 15) {
                break;
            }
            if ((faces & (1 << facing.ordinal())) != 0) {
                level = FaceLightRules.fold(level, getLightFor(type, pos.offset(facing)), type);
            }
        }
        cir.setReturnValue(level);
    }
}
