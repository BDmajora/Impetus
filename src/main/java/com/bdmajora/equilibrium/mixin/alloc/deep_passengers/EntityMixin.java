package com.bdmajora.equilibrium.mixin.alloc.deep_passengers;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.List;
import java.util.Set;

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityMixin {
    // Reference to the method get passengers of the target.
    @Shadow
    public abstract List<Entity> getPassengers();

    // Reference to the method get recursive passengers by type of the target.
    @Shadow
    private <T extends Entity> void getRecursivePassengersByType(Class<T> entityClass, Set<T> theSet) {
        throw new AssertionError();
    }

    // Replaces the whole method getRecursivePassengers.
    @Overwrite
    public Collection<Entity> getRecursivePassengers() {
        Set<Entity> passengers = new ReferenceOpenHashSet<>(this.getPassengers().isEmpty() ? 0 : 4);

        this.getRecursivePassengersByType(Entity.class, passengers);

        return passengers;
    }

    // Replaces the whole method getRecursivePassengersByType.
    @Overwrite
    public <T extends Entity> Collection<T> getRecursivePassengersByType(Class<T> entityClass) {
        Set<T> passengers = new ReferenceOpenHashSet<>(this.getPassengers().isEmpty() ? 0 : 4);

        this.getRecursivePassengersByType(entityClass, passengers);

        return passengers;
    }
}
