package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes WorldClient.
@Mixin(WorldClient.class)
public abstract class WorldClientMixin {
    // Runs at the start of removeEntityFromWorld.
    @Inject(method = "removeEntityFromWorld", at = @At("HEAD"))
    private void impetus$untrackDynamicLight(int entityId, CallbackInfoReturnable<Entity> cir) {
        Entity entity = ((WorldClient) (Object) this).getEntityByID(entityId);

        if (entity instanceof DynamicLightSource) {
            ((DynamicLightSource) entity).impetus$setDynamicLightEnabled(false);
        }
    }
}
