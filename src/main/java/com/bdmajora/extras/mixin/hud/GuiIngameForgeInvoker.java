package com.bdmajora.extras.mixin.hud;

import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GuiIngameForge.class, remap = false)
public interface GuiIngameForgeInvoker {
    @Invoker("renderCrosshairs")
    void impetus$renderCrosshairs(float partialTicks);
}
