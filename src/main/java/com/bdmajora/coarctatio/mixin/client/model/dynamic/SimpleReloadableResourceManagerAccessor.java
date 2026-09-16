package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// The per-domain managers, the only route to the pack list
@Mixin(SimpleReloadableResourceManager.class)
public interface SimpleReloadableResourceManagerAccessor {
    @Accessor("domainResourceManagers")
    Map<String, FallbackResourceManager> coarctatio$domainManagers();
}
