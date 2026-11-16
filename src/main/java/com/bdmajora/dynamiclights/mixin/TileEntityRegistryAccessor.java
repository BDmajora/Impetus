package com.bdmajora.dynamiclights.mixin;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes TileEntity.
@Mixin(TileEntity.class)
public interface TileEntityRegistryAccessor {
    // Gives access to the field get registry.
    @Accessor("REGISTRY")
    static RegistryNamespaced<ResourceLocation, Class<? extends TileEntity>> impetus$getRegistry() {
        throw new AssertionError("Untransformed accessor");
    }
}
