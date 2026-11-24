package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldTileEntityMixin {
    // Reference to the field is remote of the target.
    @Shadow
    @Final
    public boolean isRemote;

    // Unloading a chunk drops its block entities without invalidating them, so their lights are released here. Every reload
    // would otherwise leave one more dead source behind.
    @Inject(method = "markTileEntityForRemoval", at = @At("HEAD"))
    private void impetus$untrackUnloadedLight(TileEntity tileEntity, CallbackInfo ci) {
        if (this.isRemote && tileEntity instanceof DynamicLightSource source) {
            source.impetus$setDynamicLightEnabled(false);
        }
    }
}
