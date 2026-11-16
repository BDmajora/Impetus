package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import net.minecraft.client.resources.FileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.util.zip.ZipFile;

// Mixin that changes FileResourcePack.
@Mixin(FileResourcePack.class)
public interface FileResourcePackAccessor {
    // Calls the method zip file.
    @Invoker("getResourcePackZipFile")
    ZipFile coarctatio$zipFile() throws IOException;
}
