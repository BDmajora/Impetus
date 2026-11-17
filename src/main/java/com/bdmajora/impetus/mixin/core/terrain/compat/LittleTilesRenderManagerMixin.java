package com.bdmajora.impetus.mixin.core.terrain.compat;

import com.bdmajora.impetus.impl.compat.littletiles.BakedLightTracker;
import com.bdmajora.impetus.impl.compat.littletiles.LittleTilesCompat;
import com.creativemd.littletiles.client.render.world.TileEntityRenderManager;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes TileEntityRenderManager.
@Pseudo
@Mixin(value = TileEntityRenderManager.class, remap = false)
public abstract class LittleTilesRenderManagerMixin implements BakedLightTracker {
    // Reference to the field te of the target.
    @Shadow
    private TileEntityLittleTiles te;

    // Light fingerprint of the last LittleTiles bake.
    @Unique
    private int impetus$light;
    // Whether a fingerprint was taken yet.
    @Unique
    private boolean impetus$lightSeen;

    // Returns whether the light changed since the last bake and stores the new fingerprint.
    @Override
    public boolean impetus$lightChanged(int fingerprint) {
        boolean changed = !this.impetus$lightSeen || this.impetus$light != fingerprint;
        this.impetus$light = fingerprint;
        this.impetus$lightSeen = true;
        return changed;
    }

    // Runs before every return of finishBuildingCache.
    @Inject(method = "finishBuildingCache", at = @At("RETURN"), remap = false)
    private void impetus$remeshSection(int index, int renderState, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && renderState != -1) {
            LittleTilesCompat.queueRemesh(this.te);
        }
    }
}
