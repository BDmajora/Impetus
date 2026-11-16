package com.bdmajora.impetus.mixin.features.options;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.bdmajora.impetus.impl.gui.ImpetusVideoOptionsScreen;

// Mixin that changes GuiOptions.
@Mixin(GuiOptions.class)
public class MixinGuiOptions extends GuiScreen {

    // Runs at the start of actionPerformed and can cancel it.
    @Dynamic
    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void open(GuiButton button, CallbackInfo ci) {
        if(button.enabled && button.id == 101) {
            this.mc.displayGuiScreen(new ImpetusVideoOptionsScreen(this));

            ci.cancel();
        }
    }
}