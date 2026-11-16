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

// Mixin that changes EntityTNTPrimed.
@Mixin(EntityTNTPrimed.class)
public abstract class EntityTNTPrimedMixin extends Entity implements DynamicLightSource {
    // Reference to the method get fuse of the target.
    @Shadow
    public abstract int getFuse();

    // Field start fuse added to the target.
    @Unique
    private int impetus$startFuse = 80;

    // Field tnt luminance added to the target.
    @Unique
    private int impetus$tntLuminance;

    // Creates an entity tnt primed mixin from its world.
    private EntityTNTPrimedMixin(World world) {
        super(world);
    }

    // Runs at the end of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("TAIL"))
    private void impetus$captureStartFuse(World world, double x, double y, double z,
                                          // Entity that lit the TNT.
                                          EntityLivingBase igniter, CallbackInfo ci) {
        this.impetus$startFuse = this.getFuse();
    }

    // Runs at the end of onUpdate.
    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onTntTick(CallbackInfo ci) {
        if (!this.world.isRemote || !DynamicLights.options().tntLighting.isEnabled()) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    // Handles dynamic light tick.
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
            int start = this.impetus$startFuse > 0 ? this.impetus$startFuse : 80;
            float remaining = (float) this.getFuse() / (float) start;
            this.impetus$tntLuminance = (int) (-(remaining * remaining) * 10.0F) + 10;
        } else {
            this.impetus$tntLuminance = 10;
        }
    }

    // Returns the luminance.
    @Override
    public int impetus$getLuminance() {
        return this.impetus$tntLuminance;
    }
}
