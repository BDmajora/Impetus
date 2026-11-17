package com.bdmajora.extras.mixin.render.block_entity;

import com.bdmajora.extras.Extras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityBeaconRenderer.
@Mixin(TileEntityBeaconRenderer.class)
public class TileEntityBeaconRendererMixin {
    // Beacon being drawn.
    @Unique
    private TileEntityBeacon impetus$currentBeacon;

    // Runs at the start of render and can cancel it.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$beginRender(TileEntityBeacon beacon, double x, double y, double z,
                                     // Partial ticks and destroy stage and alpha.
                                     float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        this.impetus$currentBeacon = null;

        if (!Extras.options().render.beacons) {
            ci.cancel();
            return;
        }

        this.impetus$currentBeacon = beacon;
    }

    // Runs before every return of render.
    @Inject(
            method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V",
            at = @At("RETURN")
    )
    private void impetus$endRender(TileEntityBeacon beacon, double x, double y, double z,
                                   // Partial ticks and destroy stage and alpha.
                                   float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        this.impetus$currentBeacon = null;
    }

    // Wraps the call of TileEntityBeaconRenderer.renderBeamSegment in renderBeacon.
    @WrapOperation(
            method = "renderBeacon(DDDDDLjava/util/List;D)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/tileentity/TileEntityBeaconRenderer;renderBeamSegment(DDDDDDII[F)V")
    )
    private void impetus$limitBeamSegment(double x, double y, double z, double partialTicks,
                                          // Beam scale and world time.
                                          double beamScale, double worldTime,
                                          // Segment range and colors.
                                          int segmentStart, int segmentHeight, float[] colors,
                                          Operation<Void> original) {
        TileEntityBeacon beacon = this.impetus$currentBeacon;

        if (Extras.options().render.limitBeaconBeamHeight && beacon != null && beacon.getWorld() != null) {
            int remaining = beacon.getWorld().getHeight() - beacon.getPos().getY() - segmentStart;
            segmentHeight = Math.min(segmentHeight, Math.max(0, remaining));
        }

        if (segmentHeight > 0) {
            original.call(x, y, z, partialTicks, beamScale, worldTime, segmentStart, segmentHeight, colors);
        }
    }
}
