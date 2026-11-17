package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Mixin that changes EntityLivingBase.
@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseMixin extends Entity implements DynamicLightSource {
    // Light level the mob gives off now.
    @Unique
    private int impetus$livingLuminance;

    // Creates an entity living base mixin from its world.
    private EntityLivingBaseMixin(World world) {
        super(world);
    }

    // Updates the mob's light from fire and glowing and held items and its handlers.
    @Override
    public void impetus$dynamicLightTick() {
        EntityLivingBase self = (EntityLivingBase) (Object) this;

        if (!DynamicLights.options().entitiesLightSource || !DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$livingLuminance = 0;
            return;
        }

        if (this.isBurning() || this.isGlowing()) {
            this.impetus$livingLuminance = 14;
        } else {
            this.impetus$livingLuminance = DynamicLightsEngine.getLivingEntityLuminanceFromItems(self);
        }

        this.impetus$livingLuminance = Math.max(this.impetus$livingLuminance,
                DynamicLightHandlers.getLuminanceFrom(self));
    }

    // Returns the luminance.
    @Override
    public int impetus$getLuminance() {
        return this.impetus$livingLuminance;
    }
}
