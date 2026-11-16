package com.bdmajora.equilibrium.common.hopper;

import com.bdmajora.equilibrium.common.world.InventoryEntityTracker;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

// Finds inventory entities near a hopper.
public final class HopperEntityLookup {
    // Prevents instantiation
    private HopperEntityLookup() {
    }

    // Returns a random inventory entity inside the block or null.
    @Nullable
    public static IInventory findInventoryEntity(World world, double x, double y, double z) {
        if (world instanceof InventoryEntityTracker
                && !((InventoryEntityTracker) world).equilibrium$mayHaveInventoryEntities()) {
            return null;
        }

        List<Entity> candidates = world.getEntitiesInAABBexcluding(null,
                new AxisAlignedBB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D),
                EntitySelectors.HAS_INVENTORY);

        if (candidates.isEmpty()) {
            return null;
        }

        return (IInventory) candidates.get(world.rand.nextInt(candidates.size()));
    }
}
