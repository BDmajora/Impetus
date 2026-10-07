package com.bdmajora.linuxextras.mixin;

import com.bdmajora.linuxextras.desktop.XdgOpen;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

// Every "open folder" button (resource packs, world folder, Forge's error screens) goes through here, which Cleanroom answers with AWT's Desktop.open
@Mixin(OpenGlHelper.class)
public abstract class OpenGlHelperOpenFileMixin {
    @Inject(method = "openFile(Ljava/io/File;)V", at = @At("HEAD"), cancellable = true)
    private static void linuxextras$openWithXdg(File file, CallbackInfo ci) {
        if (XdgOpen.open(file.getAbsolutePath())) {
            ci.cancel();
        }
    }
}
