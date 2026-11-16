package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Mixin that changes FallbackResourceManager.
@Mixin(FallbackResourceManager.class)
public interface FallbackResourceManagerAccessor {
    // Gives access to the field packs.
    @Accessor("resourcePacks")
    List<IResourcePack> coarctatio$packs();
}
