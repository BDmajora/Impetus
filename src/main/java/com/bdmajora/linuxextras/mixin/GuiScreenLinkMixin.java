package com.bdmajora.linuxextras.mixin;

import com.bdmajora.linuxextras.desktop.XdgOpen;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

// Chat links after the confirm screen, and the screenshot link, open through xdg-open instead of AWT's browse, which Linux desktops mostly refuse
@Mixin(GuiScreen.class)
public abstract class GuiScreenLinkMixin {
    @Inject(method = "openWebLink(Ljava/net/URI;)V", at = @At("HEAD"), cancellable = true)
    private void linuxextras$openWithXdg(URI url, CallbackInfo ci) {
        if (XdgOpen.open(url.toString())) {
            ci.cancel();
        }
    }
}
