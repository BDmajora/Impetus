package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Mixin that changes SimpleReloadableResourceManager.
@Mixin(SimpleReloadableResourceManager.class)
public interface SimpleReloadableResourceManagerAccessor {
    // Gives access to the field domain managers.
    @Accessor("domainResourceManagers")
    Map<String, FallbackResourceManager> coarctatio$domainManagers();
}
