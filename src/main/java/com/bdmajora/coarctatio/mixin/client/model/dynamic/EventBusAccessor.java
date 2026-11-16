package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes EventBus.
@Mixin(value = EventBus.class, remap = false)
public interface EventBusAccessor {
    // Gives access to the field bus id.
    @Accessor("busID")
    int coarctatio$busId();
}
