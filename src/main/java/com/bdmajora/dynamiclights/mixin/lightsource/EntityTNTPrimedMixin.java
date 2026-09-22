package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.ExplosiveLightingMode;
import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Brightens primed TNT as the fuse burns; in FANCY the ramp is quadratic in remaining fuse, barely there when lit and flooding just before it goes off
@Mixin(EntityTNTPrimed.class)
public abstract class EntityTNTPrimedMixin extends Entity implements DynamicLightSource {
    @Shadow
    public abstract int getFuse();

    // The fuse this TNT started with so the ramp is a fraction of its own life; defaults to vanilla's 80 for already-primed arrivals (command, chunk load) that skip the constructor hook
    @Unique
    private int impetus$startFuse = 80;

    @Unique
    private int impetus$tntLuminance;

    private EntityTNTPrimedMixin(World world) {
        super(world);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("TAIL"))
    private void impetus$captureStartFuse(World world, double x, double y, double z,
                                          EntityLivingBase igniter, CallbackInfo ci) {
        this.impetus$startFuse = this.getFuse();
    }

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onTntTick(CallbackInfo ci) {
        if (!this.world.isRemote || !DynamicLights.options().tntLighting.isEnabled()) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    @Override
    public void impetus$dynamicLightTick() {
        Entity self = (Entity) (Object) this;

        if (!DynamicLights.options().entitiesLightSource || !DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$tntLuminance = 0;
            return;
        }

        if (this.isBurning()) {
            this.impetus$tntLuminance = 14;
            return;
        }

        if (DynamicLights.options().tntLighting == ExplosiveLightingMode.FANCY) {
            // Guard against a zero start fuse, which would divide by zero and hand the falloff a NaN
            int start = this.impetus$startFuse > 0 ? this.impetus$startFuse : 80;
            float remaining = (float) this.getFuse() / (float) start;
            this.impetus$tntLuminance = (int) (-(remaining * remaining) * 10.0F) + 10;
        } else {
            this.impetus$tntLuminance = 10;
        }
    }

    @Override
    public int impetus$getLuminance() {
        return this.impetus$tntLuminance;
    }
}
