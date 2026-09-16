package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// The packs serving one domain, in lookup order
@Mixin(FallbackResourceManager.class)
public interface FallbackResourceManagerAccessor {
    @Accessor("resourcePacks")
    List<IResourcePack> coarctatio$packs();
}
