package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.util.ResourceLocation;

// Loader of block state definitions.
public interface DefinitionLoader {
    // Returns the block state definition of the location.
    ModelBlockDefinition coarctatio$definition(ResourceLocation location);
}
