package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

// Mixin that changes WorldTickEvent.
@Mixin(value = TickEvent.WorldTickEvent.class, remap = false)
public abstract class WorldTickEventMixin implements RecyclableEvent {
    // Reference to the field world of the target.
    @Shadow
    @Final
    @Mutable
    public World world;

    // Handles refresh world.
    @Override
    public void coarctatio$refreshWorld(World world) {
        this.world = world;
    }
}
