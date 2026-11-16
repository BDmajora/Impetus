package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Mixin that changes TileEntityBeaconRenderer.
@Mixin(TileEntityBeaconRenderer.class)
public class BeaconBeamDepthMixin {
    // GL enum of the depth write mask.
    private static final int GL_DEPTH_WRITEMASK = 0x0B72;

    // Field saved depth mask added to the target.
    @Unique
    private boolean impetus$savedDepthMask;
    // Field depth mask overridden added to the target.
    @Unique
    private boolean impetus$depthMaskOverridden;

    // Runs at the start of render and can cancel it.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$noBeamInShadowPass(TileEntityBeacon beacon, double x, double y, double z,
                                            // Partial ticks and destroy stage and alpha.
                                            float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (Umbra.getRenderingPipeline() != null && UmbraShadowRenderer.isShadowPass()) {
            ci.cancel();
        }
    }

    // Runs at the start of render.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V",
            at = @At("HEAD"), require = 0)
    private void impetus$beaconBeamDepthOn(TileEntityBeacon beacon, double x, double y, double z,
                                           // Partial ticks and destroy stage and alpha.
                                           float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (impetus$wantsBeamDepth()) {
            this.impetus$savedDepthMask = LWJGL.glGetBoolean(GL_DEPTH_WRITEMASK);
            this.impetus$depthMaskOverridden = true;
            GlStateManager.depthMask(true);
        }
    }

    // Runs before every return of render.
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFIF)V",
            at = @At("RETURN"), require = 0)
    private void impetus$beaconBeamDepthOff(TileEntityBeacon beacon, double x, double y, double z,
                                            // Partial ticks and destroy stage and alpha.
                                            float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (this.impetus$depthMaskOverridden) {
            this.impetus$depthMaskOverridden = false;
            GlStateManager.depthMask(this.impetus$savedDepthMask);
        }
    }

    // Handles wants beam depth.
    private static boolean impetus$wantsBeamDepth() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        return pipeline != null && pipeline.shouldWriteBeaconBeamToDepthBuffer();
    }
}
