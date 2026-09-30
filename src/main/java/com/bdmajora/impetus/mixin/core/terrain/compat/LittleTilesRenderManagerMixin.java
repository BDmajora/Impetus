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

// Vanilla LittleTiles remeshes by marking the RenderChunk a finished bake belongs to, which Impetus never draws, so a finished bake queues the Impetus section instead; the mesher's last light reading for the tile entity is kept here too. Applied only when LittleTiles is present
@Pseudo
@Mixin(value = TileEntityRenderManager.class, remap = false)
public abstract class LittleTilesRenderManagerMixin implements BakedLightTracker {
    @Shadow
    private TileEntityLittleTiles te;

    @Unique
    private int impetus$light;
    @Unique
    private boolean impetus$lightSeen;

    // A first sighting counts as a change, since the tile entity may have been baked before its section was ever meshed
    @Override
    public boolean impetus$lightChanged(int fingerprint) {
        boolean changed = !this.impetus$lightSeen || this.impetus$light != fingerprint;
        this.impetus$light = fingerprint;
        this.impetus$lightSeen = true;
        return changed;
    }

    // False means a newer request is outstanding and the RenderingThread bakes again first; render state -1 is a failed or abandoned bake, which a remesh would only queue again
    @Inject(method = "finishBuildingCache", at = @At("RETURN"), remap = false)
    private void impetus$remeshSection(int index, int renderState, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && renderState != -1) {
            LittleTilesCompat.queueRemesh(this.te);
        }
    }
}
