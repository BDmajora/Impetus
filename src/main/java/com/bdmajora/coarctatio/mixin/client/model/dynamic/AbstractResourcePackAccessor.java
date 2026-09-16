package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

// The folder or file a pack reads from
@Mixin(AbstractResourcePack.class)
public interface AbstractResourcePackAccessor {
    @Accessor("resourcePackFile")
    File coarctatio$file();
}
