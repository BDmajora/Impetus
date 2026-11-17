package com.bdmajora.fulgor.mixin.client;

import com.bdmajora.fulgor.lighting.FaceLightRules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldNeighborLightMixin {
    // Reference to the method get light for of the target.
    @Shadow
    public abstract int getLightFor(EnumSkyBlock type, BlockPos pos);

    // Reference to the method get block state of the target.
    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    // Reference to the method is valid of the target.
    @Shadow
    public abstract boolean isValid(BlockPos pos);

    // Reference to the method is block loaded of the target.
    @Shadow
    public abstract boolean isBlockLoaded(BlockPos pos);

    // Runs at the start of getLightFromNeighborsFor and can cancel it.
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
        // Returns the block's own light when no face is open and the brightest open neighbour otherwise.
        cir.setReturnValue(faces == 0 ? level : FaceLightRules.foldOpenFaces(faces, level, type, pos, this::getLightFor));
    }
}
