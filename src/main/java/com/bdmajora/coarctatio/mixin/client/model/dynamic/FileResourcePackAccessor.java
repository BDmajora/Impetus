package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.zip.ZipFile;

// The zip behind a file pack, for listing when the existence index is off
@Mixin(FileResourcePack.class)
public interface FileResourcePackAccessor {
    @Invoker("getResourcePackZipFile")
    ZipFile coarctatio$zipFile() throws IOException;
}
