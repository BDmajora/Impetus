package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityHanging.
@Mixin(EntityHanging.class)
public abstract class EntityHangingMixin extends Entity implements DynamicLightSource {
    // Creates an entity hanging mixin from its world.
    private EntityHangingMixin(World world) {
        super(world);
    }

    // Runs at the end of onUpdate.
    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onHangingTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }
}
