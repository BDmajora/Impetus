package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Mixin that changes ItemRenderer.
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    // Reference to the field item stack main hand of the target.
    @Shadow
    private ItemStack itemStackMainHand;

    // Reference to the field item stack off hand of the target.
    @Shadow
    private ItemStack itemStackOffHand;

    // Reference to the field mc of the target.
    @Shadow
    @Final
    private Minecraft mc;

    // Changes an argument of the call of WorldClient in setLightmap.
    @ModifyArg(
            method = "setLightmap",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;"
                            + "getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"),
            index = 1)
    private int impetus$handLightFloor(int minimumBlockLight) {
        AbstractClientPlayer player = this.mc.player;

        if (!DynamicLights.options().mode.isEnabled() || player == null) {
            return minimumBlockLight;
        }

        boolean submerged = DynamicLightsEngine.isEyeSubmergedInFluid(player);
        int held = Math.max(
                DynamicLightsEngine.getLuminanceFromItemStack(this.itemStackMainHand, submerged),
                DynamicLightsEngine.getLuminanceFromItemStack(this.itemStackOffHand, submerged));

        int surrounding = (int) DynamicLights.engine().getDynamicLightLevel(
                MathHelper.floor(player.posX), MathHelper.floor(player.posY + player.getEyeHeight()), MathHelper.floor(player.posZ));

        return Math.max(minimumBlockLight, Math.max(held, surrounding));
    }
}
