package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LegacyV2Adapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The pack a format-2 adapter wraps, listed in its place
@Mixin(LegacyV2Adapter.class)
public interface LegacyV2AdapterAccessor {
    @Accessor("pack")
    IResourcePack coarctatio$pack();
}
