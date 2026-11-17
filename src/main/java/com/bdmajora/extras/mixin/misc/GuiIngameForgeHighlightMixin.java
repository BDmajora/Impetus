package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes GuiIngameForge.
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeHighlightMixin {
    // Adds the switch to the game's own heldItemTooltips test.
    @ModifyExpressionValue(method = "renderToolHighlight", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;heldItemTooltips:Z", remap = true))
    private boolean impetus$heldItemTooltips(boolean vanilla) {
        return vanilla && Extras.options().detail.heldItemTooltips;
    }
}
