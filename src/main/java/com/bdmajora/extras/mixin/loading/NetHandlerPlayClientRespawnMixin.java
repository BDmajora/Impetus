package com.bdmajora.extras.mixin.loading;

import com.bdmajora.extras.Extras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes NetHandlerPlayClient.
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientRespawnMixin {
    // Wraps the call of Minecraft.displayGuiScreen in handleRespawn.
    @WrapOperation(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void impetus$skipDownloadTerrainScreen(Minecraft mc, GuiScreen screen, Operation<Void> original) {
        if (screen instanceof GuiDownloadTerrain && Extras.options().loading.smoothDimensionChange) {
            original.call(mc, (GuiScreen) null);
            return;
        }
        original.call(mc, screen);
    }
}
