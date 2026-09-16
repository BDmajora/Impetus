package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.util.ResourceLocation;

// Implemented on ModelLoader by mixin; the blockstate definition for a location, loaded through the dynamic path so multipart definitions carry their state container
public interface DefinitionLoader {
    ModelBlockDefinition coarctatio$definition(ResourceLocation location);
}
