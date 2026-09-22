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

// One hopper's memory of the inventory on one side, keyed on the block state singleton (identity answers "changed?"); chests are never cached since a double chest's other half changes no state, nor is the entity fallback
public final class HopperInventoryCache {
    // The state that was at the cached position when it was last resolved. Null means unresolved.
    private IBlockState state;

    // The tile entity found at that position, or null if there was none.
    private TileEntity tileEntity;

    // The inventory derived from #tileEntity. Null when the tile entity was not one.
    private IInventory inventory;

    // Resolves the inventory at a position, mirroring TileEntityHopper.getInventoryAtPosition exactly (order, random pick, flooring) except the tile entity may come from cache and the entity query is skipped when provably empty
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

    // Resolves the inventory at a position, handling chests separately so they never enter the cache
    @Nullable
    private IInventory resolveTileEntityInventory(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Chests are re-resolved every time, checked before the cache so the cache never holds a chest and no stale entry exists if a chest replaces something here
        if (block instanceof BlockChest) {
            TileEntity tileEntity = this.lookup(world, pos);

            if (tileEntity instanceof TileEntityChest) {
                return ((BlockChest) block).getContainer(world, pos, true);
            }

            return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
        }

        if (this.state == state) {
            // Block unchanged, but the tile entity is only valid if not invalidated: breaking and replacing the same kind of machine yields the same state and a different tile entity
            if (this.tileEntity == null) {
                return null;
            }

            if (!this.tileEntity.isInvalid()) {
                return this.inventory;
            }
        }

        this.state = state;
        this.tileEntity = null;
        this.inventory = null;

        // Vanilla asks hasTileEntity before looking one up, and mods rely on that being asked.
        if (block.hasTileEntity(state)) {
            TileEntity tileEntity = this.lookup(world, pos);

            if (tileEntity instanceof IInventory) {
                this.tileEntity = tileEntity;
                this.inventory = (IInventory) tileEntity;
            }
        }

        return this.inventory;
    }

    // Prefers the non-creating lookup, so probing an empty position cannot force a tile entity into being
    @Nullable
    private TileEntity lookup(World world, BlockPos pos) {
        if (world instanceof TileEntityAccess) {
            return ((TileEntityAccess) world).equilibrium$getExistingTileEntity(pos);
        }

        return world.getTileEntity(pos);
    }

    // Drops the cached entry. Called when the hopper is invalidated or moves.
    public void clear() {
        this.state = null;
        this.tileEntity = null;
        this.inventory = null;
    }
}
