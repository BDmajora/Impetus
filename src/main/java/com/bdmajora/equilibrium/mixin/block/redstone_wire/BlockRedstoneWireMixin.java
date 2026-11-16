package com.bdmajora.equilibrium.mixin.block.redstone_wire;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

// Mixin that changes BlockRedstoneWire.
@Mixin(BlockRedstoneWire.class)
public abstract class BlockRedstoneWireMixin {
    // Reference to the field blocks needing update of the target.
    @Shadow
    @Final
    private Set<BlockPos> blocksNeedingUpdate;

    // Reference to the field can provide power of the target.
    @Shadow
    private boolean canProvidePower;

    // Reference to the method get max current strength of the target.
    @Shadow
    private int getMaxCurrentStrength(World worldIn, BlockPos pos, int strength) {
        throw new AssertionError();
    }

    // Replaces the whole method calculateCurrentChanges.
    @Overwrite
    private IBlockState calculateCurrentChanges(World worldIn, BlockPos pos1, BlockPos pos2, IBlockState state) {
        IBlockState previousState = state;

        int previousPower = state.getValue(BlockRedstoneWire.POWER);
        int power = this.getMaxCurrentStrength(worldIn, pos2, 0);

        this.canProvidePower = false;
        int neighbourPower = worldIn.getRedstonePowerFromNeighbors(pos1);
        this.canProvidePower = true;

        if (neighbourPower > 0 && neighbourPower > power - 1) {
            power = neighbourPower;
        }

        int wirePower = 0;

        int aboveIsNormalCube = 0;

        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos1.offset(facing);

            boolean isNotOrigin = neighbourPos.getX() != pos2.getX() || neighbourPos.getZ() != pos2.getZ();

            if (isNotOrigin) {
                wirePower = this.getMaxCurrentStrength(worldIn, neighbourPos, wirePower);
            }

            boolean neighbourIsNormalCube = worldIn.getBlockState(neighbourPos).isNormalCube();

            if (neighbourIsNormalCube) {
                if (aboveIsNormalCube == 0) {
                    aboveIsNormalCube = worldIn.getBlockState(pos1.up()).isNormalCube() ? 1 : -1;
                }

                if (aboveIsNormalCube == -1 && isNotOrigin && pos1.getY() >= pos2.getY()) {
                    wirePower = this.getMaxCurrentStrength(worldIn, neighbourPos.up(), wirePower);
                }
            } else if (isNotOrigin && pos1.getY() <= pos2.getY()) {
                wirePower = this.getMaxCurrentStrength(worldIn, neighbourPos.down(), wirePower);
            }
        }

        if (wirePower > power) {
            power = wirePower - 1;
        } else if (power > 0) {
            --power;
        } else {
            power = 0;
        }

        if (neighbourPower > power - 1) {
            power = neighbourPower;
        }

        if (previousPower != power) {
            state = state.withProperty(BlockRedstoneWire.POWER, power);

            if (worldIn.getBlockState(pos1) == previousState) {
                worldIn.setBlockState(pos1, state, 2);
            }

            this.blocksNeedingUpdate.add(pos1);

            for (EnumFacing facing : EnumFacing.VALUES) {
                this.blocksNeedingUpdate.add(pos1.offset(facing));
            }
        }

        return state;
    }
}
