package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Ticks item frame and painting light, since EntityHanging#onUpdate never calls up to Entity#onUpdate and the shared hook never fires; luminance still comes from the base implementation via the EntityItemFrame handler
@Mixin(EntityHanging.class)
public abstract class EntityHangingMixin extends Entity implements DynamicLightSource {
    private EntityHangingMixin(World world) {
        super(world);
    }

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onHangingTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }
}
