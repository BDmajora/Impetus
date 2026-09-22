package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Lights the first-person hand and held item, which are drawn after the world with their own lightmap and would otherwise stay dark; raises getCombinedLight's MINIMUM BLOCK LIGHT argument, vanilla's own mechanism for held light
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private ItemStack itemStackMainHand;

    @Shadow
    private ItemStack itemStackOffHand;

    @Shadow
    @Final
    private Minecraft mc;

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

        // Also take the light of anything else nearby, so standing next to a lit creeper lights the hand.
        int surrounding = (int) DynamicLights.engine().getDynamicLightLevel(
                MathHelper.floor(player.posX), MathHelper.floor(player.posY + player.getEyeHeight()), MathHelper.floor(player.posZ));

        return Math.max(minimumBlockLight, Math.max(held, surrounding));
    }
}
