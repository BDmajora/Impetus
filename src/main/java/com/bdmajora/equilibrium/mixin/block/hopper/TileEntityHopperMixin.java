package com.bdmajora.equilibrium.mixin.block.hopper;

import com.bdmajora.equilibrium.common.hopper.HopperEntityLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

// Mixin that changes TileEntityHopper.
@Mixin(TileEntityHopper.class)
public abstract class TileEntityHopperMixin extends TileEntity implements IHopper {
    // Replaces the whole method getInventoryForHopperTransfer.
    @Nullable
    @Overwrite
    private IInventory getInventoryForHopperTransfer() {
        EnumFacing facing = BlockHopper.getFacing(this.getBlockMetadata());

        return equilibrium$resolve(this.getWorld(),
                this.getXPos() + facing.getXOffset(),
                this.getYPos() + facing.getYOffset(),
                this.getZPos() + facing.getZOffset());
    }

    // Replaces the whole method getSourceInventory.
    @Nullable
    @Overwrite
    public static IInventory getSourceInventory(IHopper hopper) {
        return equilibrium$resolve(hopper.getWorld(), hopper.getXPos(), hopper.getYPos() + 1.0D, hopper.getZPos());
    }

    // Vanilla getInventoryAtPosition with the entity query skipped when no inventory entity exists. The block entity is not
    // remembered: unloading its chunk does not invalidate it, so a hopper at a chunk border kept moving items into the copy
    // from before the neighbouring chunk reloaded.
    @Unique
    @Nullable
    private static IInventory equilibrium$resolve(World world, double x, double y, double z) {
        BlockPos pos = new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        IInventory inventory = null;

        if (block.hasTileEntity(state)) {
            TileEntity tileEntity = world.getTileEntity(pos);

            if (tileEntity instanceof IInventory) {
                inventory = (IInventory) tileEntity;

                if (inventory instanceof TileEntityChest && block instanceof BlockChest) {
                    inventory = ((BlockChest) block).getContainer(world, pos, true);
                }
            }
        }

        return inventory != null ? inventory : HopperEntityLookup.findInventoryEntity(world, x, y, z);
    }
}
