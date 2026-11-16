package com.bdmajora.equilibrium.mixin.util.data_storage;

import com.bdmajora.equilibrium.common.world.CountedInventoryEntity;
import com.bdmajora.equilibrium.common.world.InventoryEntityTracker;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements InventoryEntityTracker {
    // Field inventory entities added to the target.
    @Unique
    private int equilibrium$inventoryEntities;

    // Runs at the start of onEntityAdded.
    @Inject(method = "onEntityAdded", at = @At("HEAD"))
    private void equilibrium$countInventoryEntityAdded(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof IInventory) || !(entity instanceof CountedInventoryEntity)) {
            return;
        }

        CountedInventoryEntity counted = (CountedInventoryEntity) entity;

        if (counted.equilibrium$getCountedWorld() != (Object) this) {
            counted.equilibrium$setCountedWorld((World) (Object) this);
            this.equilibrium$inventoryEntities++;
        }
    }

    // Runs at the start of onEntityRemoved.
    @Inject(method = "onEntityRemoved", at = @At("HEAD"))
    private void equilibrium$countInventoryEntityRemoved(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof CountedInventoryEntity)) {
            return;
        }

        CountedInventoryEntity counted = (CountedInventoryEntity) entity;

        if (counted.equilibrium$getCountedWorld() == (Object) this) {
            counted.equilibrium$setCountedWorld(null);
            this.equilibrium$inventoryEntities--;
        }
    }

    // Handles may have inventory entities.
    @Override
    public boolean equilibrium$mayHaveInventoryEntities() {
        return this.equilibrium$inventoryEntities > 0;
    }
}
