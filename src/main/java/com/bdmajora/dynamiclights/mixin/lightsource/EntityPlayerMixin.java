package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Mixin that changes EntityPlayer.
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin extends EntityLivingBase implements DynamicLightSource {
    // Reference to the method is spectator of the target.
    @Shadow
    public abstract boolean isSpectator();

    // Field player luminance added to the target.
    @Unique
    private int impetus$playerLuminance;

    // Field last world added to the target.
    @Unique
    private World impetus$lastWorld;

    // Creates an entity player mixin from its world.
    private EntityPlayerMixin(World world) {
        super(world);
    }

    // Handles dynamic light tick.
    @Override
    public void impetus$dynamicLightTick() {
        EntityPlayer self = (EntityPlayer) (Object) this;

        if (!DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$playerLuminance = 0;
            return;
        }

        if (this.isBurning() || this.isGlowing()) {
            this.impetus$playerLuminance = 14;
        } else {
            this.impetus$playerLuminance = Math.max(
                    DynamicLightHandlers.getLuminanceFrom(self),
                    DynamicLightsEngine.getLivingEntityLuminanceFromItems(self));
        }

        if (this.isSpectator()) {
            this.impetus$playerLuminance = 0;
        }

        if (this.impetus$lastWorld != this.world) {
            this.impetus$lastWorld = this.world;
            this.impetus$playerLuminance = 0;
        }
    }

    // Returns the luminance.
    @Override
    public int impetus$getLuminance() {
        return this.impetus$playerLuminance;
    }
}
