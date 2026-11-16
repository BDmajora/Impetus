package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes LegacyV2Adapter.
@Mixin(LegacyV2Adapter.class)
public interface LegacyV2AdapterAccessor {
    // Gives access to the field pack.
    @Accessor("pack")
    IResourcePack coarctatio$pack();
}
