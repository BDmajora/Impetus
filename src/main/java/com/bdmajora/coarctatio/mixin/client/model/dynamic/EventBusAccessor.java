package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The bus id that selects a listener list's entries, so ModelBakeEvent can be posted one listener at a time. remap = false, Forge class
@Mixin(value = EventBus.class, remap = false)
public interface EventBusAccessor {
    @Accessor("busID")
    int coarctatio$busId();
}
