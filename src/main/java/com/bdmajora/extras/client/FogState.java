package com.bdmajora.extras.client;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;

// Tells whether the fog comes from gameplay.
public final class FogState {
    // Prevents instantiation
    private FogState() {
    }

    // Returns whether blindness or a fluid causes the fog.
    public static boolean isGameplayFog() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) {
            return false;
        }

        if (view instanceof EntityLivingBase
                && ((EntityLivingBase) view).isPotionActive(MobEffects.BLINDNESS)) {
            return true;
        }

        return view.isInsideOfMaterial(Material.WATER) || view.isInsideOfMaterial(Material.LAVA);
    }
}
