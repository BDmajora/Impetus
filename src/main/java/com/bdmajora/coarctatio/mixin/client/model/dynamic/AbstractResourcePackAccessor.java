package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

// Mixin that changes AbstractResourcePack.
@Mixin(AbstractResourcePack.class)
public interface AbstractResourcePackAccessor {
    // Gives access to the field file.
    @Accessor("resourcePackFile")
    File coarctatio$file();
}
