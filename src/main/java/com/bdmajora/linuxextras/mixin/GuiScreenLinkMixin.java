package com.bdmajora.linuxextras.mixin;

import com.bdmajora.linuxextras.desktop.XdgOpen;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

// Mixin that changes GuiScreen.
@Mixin(GuiScreen.class)
public abstract class GuiScreenLinkMixin {
    // Runs at the start of openWebLink and can cancel it.
    @Inject(method = "openWebLink(Ljava/net/URI;)V", at = @At("HEAD"), cancellable = true)
    private void linuxextras$openWithXdg(URI url, CallbackInfo ci) {
        if (XdgOpen.open(url.toString())) {
            ci.cancel();
        }
    }
}
