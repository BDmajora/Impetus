package com.bdmajora.equilibrium.mixin.block.hopper;

import com.bdmajora.equilibrium.common.hopper.HopperCacheHolder;
import com.bdmajora.equilibrium.common.hopper.HopperEntityLookup;
import com.bdmajora.equilibrium.common.hopper.HopperInventoryCache;
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
public abstract class TileEntityHopperMixin extends TileEntity implements IHopper, HopperCacheHolder {
    // Remembered inventory the hopper pushes into.
    @Unique
    private HopperInventoryCache equilibrium$destination;

    // Remembered inventory the hopper pulls from.
    @Unique
    private HopperInventoryCache equilibrium$source;

    // Returns the source cache and creates it on first use.
    @Override
    public HopperInventoryCache equilibrium$sourceCache() {
        if (this.equilibrium$source == null) {
            this.equilibrium$source = new HopperInventoryCache();
        }

        return this.equilibrium$source;
    }

    // Returns the destination cache and creates it on first use.
    @Override
    public HopperInventoryCache equilibrium$destinationCache() {
        if (this.equilibrium$destination == null) {
            this.equilibrium$destination = new HopperInventoryCache();
        }

        return this.equilibrium$destination;
    }

    // Replaces the whole method getInventoryForHopperTransfer.
    @Nullable
    @Overwrite
    private IInventory getInventoryForHopperTransfer() {
        EnumFacing facing = BlockHopper.getFacing(this.getBlockMetadata());

        return this.equilibrium$destinationCache().get(this.getWorld(),
                this.getXPos() + facing.getXOffset(),
                this.getYPos() + facing.getYOffset(),
                this.getZPos() + facing.getZOffset());
    }

    // Replaces the whole method getSourceInventory.
    @Nullable
    @Overwrite
    public static IInventory getSourceInventory(IHopper hopper) {
        double x = hopper.getXPos();
        double y = hopper.getYPos() + 1.0D;
        double z = hopper.getZPos();

        if (hopper instanceof HopperCacheHolder) {
            return ((HopperCacheHolder) hopper).equilibrium$sourceCache().get(hopper.getWorld(), x, y, z);
        }

        return equilibrium$resolveUncached(hopper.getWorld(), x, y, z);
    }

    // Vanilla getInventoryAtPosition for hoppers that cannot cache.
    @Unique
    @Nullable
    private static IInventory equilibrium$resolveUncached(World world, double x, double y, double z) {
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
