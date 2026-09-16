package com.bdmajora.extras.mixin.hud;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiIngame.class)
public interface GuiIngameInvoker {
    @Invoker("renderVignette")
    void impetus$renderVignette(float lightLevel, ScaledResolution resolution);
}
