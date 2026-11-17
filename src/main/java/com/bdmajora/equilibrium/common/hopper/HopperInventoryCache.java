package com.bdmajora.equilibrium.common.hopper;

import net.minecraft.util.math.MathHelper;
import com.bdmajora.equilibrium.common.world.TileEntityAccess;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

// Remembers the inventory next to a hopper.
public final class HopperInventoryCache {
    // State of the block when the inventory was found.
    private IBlockState state;

    // Packed position the state was read at.
    private long position;

    // Block entity that holds the inventory.
    private TileEntity tileEntity;

    // Inventory that was found.
    private IInventory inventory;

    // Returns the inventory at the position or null.
    @Nullable
    public IInventory get(World world, double x, double y, double z) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);

        BlockPos pos = new BlockPos(blockX, blockY, blockZ);

        IInventory inventory = this.resolveTileEntityInventory(world, pos);

        if (inventory != null) {
            return inventory;
        }

        return HopperEntityLookup.findInventoryEntity(world, x, y, z);
    }

    // Returns the block entity inventory and reuses the last result while the state is unchanged.
    @Nullable
    private IInventory resolveTileEntityInventory(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BlockChest) {
            TileEntity tileEntity = this.lookup(world, pos);

            if (tileEntity instanceof TileEntityChest) {
                return ((BlockChest) block).getContainer(world, pos, true);
            }

            return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
        }

        long position = pos.toLong();
        if (this.state == state && this.position == position) {
            if (this.tileEntity == null) {
                return null;
            }

            if (!this.tileEntity.isInvalid()) {
                return this.inventory;
            }
        }

        this.state = state;
        this.position = position;
        this.tileEntity = null;
        this.inventory = null;

        if (block.hasTileEntity(state)) {
            TileEntity tileEntity = this.lookup(world, pos);

            if (tileEntity instanceof IInventory) {
                this.tileEntity = tileEntity;
                this.inventory = (IInventory) tileEntity;
            }
        }

        return this.inventory;
    }

    // Returns the existing block entity at the position.
    @Nullable
    private TileEntity lookup(World world, BlockPos pos) {
        if (world instanceof TileEntityAccess) {
            return ((TileEntityAccess) world).equilibrium$getExistingTileEntity(pos);
        }

        return world.getTileEntity(pos);
    }

    // Forgets the remembered inventory.
    public void clear() {
        this.state = null;
        this.tileEntity = null;
        this.inventory = null;
    }
}
