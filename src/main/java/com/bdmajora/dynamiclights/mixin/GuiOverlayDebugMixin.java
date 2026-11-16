package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.util.text.TextFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

// Mixin that changes GuiOverlayDebug.
@Mixin(GuiOverlayDebug.class)
public abstract class GuiOverlayDebugMixin {
    // Changes an expression value in renderDebugInfoLeft.
    @ModifyExpressionValue(
            method = "renderDebugInfoLeft",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiOverlayDebug;call()Ljava/util/List;"))
    private List<String> impetus$addDynamicLightInfo(List<String> lines) {
        if (!DynamicLights.options().showDebugInfo) {
            return lines;
        }

        DynamicLightsEngine engine = DynamicLights.engine();
        StringBuilder line = new StringBuilder("Dynamic Light Sources: ")
                .append(engine.getLightSourcesCount())
                .append(" (U: ")
                .append(engine.getLastUpdateCount());

        if (!DynamicLights.options().mode.isEnabled()) {
            line.append("; ").append(TextFormatting.RED).append("Disabled").append(TextFormatting.RESET);
        }

        lines.add(line.append(')').toString());
        return lines;
    }
}
