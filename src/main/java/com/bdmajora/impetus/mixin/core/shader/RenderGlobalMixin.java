package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL15;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.pipeline.DeferredBlockOutline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.VanillaFeatureToggles;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public class RenderGlobalMixin {
    // Name of the sun texture field.
    private static final String SUN_TEXTURES_FIELD =
            "Lnet/minecraft/client/renderer/RenderGlobal;SUN_TEXTURES:Lnet/minecraft/util/ResourceLocation;";
    // Name of the moon texture field.
    private static final String MOON_TEXTURES_FIELD =
            "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;";

    // Runs around the call of VertexBuffer.bindBuffer in renderSky.
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;bindBuffer()V", ordinal = 0))
    private void impetus$drawHorizon(float partialTicks, int pass, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.drawSkyHorizon();
        }
    }

    // Runs around the call of WorldClient.getCelestialAngle in renderSky.
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F", ordinal = 1))
    private void impetus$preCelestialRotate(float partialTicks, int pass, CallbackInfo ci) {
        if (Umbra.getRenderingPipeline() == null) {
            return;
        }
        float rotation = CelestialUniforms.getSunPathRotation();
        if (rotation != 0.0f) {
            GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
        }
    }

    // Runs inside renderSky.
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "FIELD", target = SUN_TEXTURES_FIELD, opcode = Opcodes.GETSTATIC))
    private void impetus$beginSunMoon(float partialTicks, int pass, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyTextured, 4);
    }

    // Runs inside renderSky.
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "FIELD", target = MOON_TEXTURES_FIELD, opcode = Opcodes.GETSTATIC),
            require = 0)
    private void impetus$beginMoon(float partialTicks, int pass, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyTextured, 5);
    }

    // Runs inside renderSky.
    @Inject(method = "renderSky(FI)V",
            slice = @Slice(from = @At(value = "FIELD", target = SUN_TEXTURES_FIELD,
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V", ordinal = 0))
    private void impetus$endSunMoon(float partialTicks, int pass, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyBasic, 6);
    }

    // Runs around the call of GlStateManager.disableCull in renderClouds.
    @Inject(method = "renderClouds(FIDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableCull()V",
                    ordinal = 0),
            require = 0)
    private void impetus$beginFastClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Clouds);
    }

    // Runs around the call of GlStateManager.enableCull in renderClouds.
    @Inject(method = "renderClouds(FIDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;enableCull()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$endFastClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Runs at the start of renderCloudsFancy.
    @Inject(method = "renderCloudsFancy(FIDDD)V", at = @At("HEAD"), require = 0)
    private void impetus$beginFancyClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Clouds);
    }

    // Runs before every return of renderCloudsFancy.
    @Inject(method = "renderCloudsFancy(FIDDD)V", at = @At("RETURN"), require = 0)
    private void impetus$endFancyClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Runs at the start of drawSelectionBox and can cancel it.
    @Inject(method = "drawSelectionBox", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$beginBlockOutline(EntityPlayer player,
                                           // Target and draw flag.
                                           RayTraceResult target, int execute,
                                           // Partial ticks.
                                           float partialTicks, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null || DeferredBlockOutline.isReplaying()) {
            return;
        }
        if (pipeline.hasDirectGbufferProgram(ProgramId.Line)) {
            Umbra.setPhase(ProgramId.Line);
            return;
        }
        DeferredBlockOutline.capture(player, target, partialTicks);
        ci.cancel();
    }

    // Runs before every return of drawSelectionBox.
    @Inject(method = "drawSelectionBox", at = @At("RETURN"), require = 0)
    private void impetus$endBlockOutline(EntityPlayer player,
                                         // Target and draw flag.
                                         RayTraceResult target, int execute,
                                         // Partial ticks.
                                         float partialTicks, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Runs at the start of renderSky and can cancel it.
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$suppressSky(float partialTicks, int pass, CallbackInfo ci) {
        if (!VanillaFeatureToggles.shouldRenderSky()) {
            ci.cancel();
        }
    }

    // Runs at the start of renderBlockLayer.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), require = 0)
    private void impetus$renderDeferredLods(BlockRenderLayer layer, double partialTicks, int pass, Entity entity,
                                            CallbackInfoReturnable<Integer> cir) {
        if (layer != BlockRenderLayer.TRANSLUCENT || !DhCompat.hasRenderingEnabled()) {
            return;
        }
        try {
            DhCompat.renderDeferredLods();
        } finally {
            LWJGL.glUseProgram(0);
            LWJGL.glBindVertexArray(0);
            LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    // Runs at the start of renderBlockLayer.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), require = 0)
    private void impetus$applyBackFaceCulling(BlockRenderLayer layer, double partialTicks, int pass,
                                              // Viewer entity.
                                              Entity entity,
                                              CallbackInfoReturnable<Integer> cir) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && !pipeline.shouldCullBackFaces(layer.ordinal())) {
            GlStateManager.disableCull();
            impetus$restoreCull = true;
        }
    }

    // Runs before every return of renderBlockLayer.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("RETURN"), require = 0)
    private void impetus$restoreBackFaceCulling(BlockRenderLayer layer, double partialTicks,
                                                // Pass and viewer entity.
                                                int pass, Entity entity,
                                                CallbackInfoReturnable<Integer> cir) {
        if (impetus$restoreCull) {
            impetus$restoreCull = false;
            GlStateManager.enableCull();
        }
    }

    // Field restore cull added to the target.
    @Unique
    private boolean impetus$restoreCull;

    // Runs at the start of renderBlockLayer and can cancel it.
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$skipTerrain(BlockRenderLayer layer, double partialTicks, int pass,
                                     // Viewer entity.
                                     Entity entity,
                                     CallbackInfoReturnable<Integer> cir) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.skipAllRendering()) {
            cir.setReturnValue(0);
        }
    }

    // Replaces the call of TextureManager.bindTexture in renderSky.
    @Redirect(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture"
                            + "(Lnet/minecraft/util/ResourceLocation;)V"),
            require = 0)
    private void impetus$suppressCelestialBody(TextureManager manager,
                                               ResourceLocation location) {
        boolean isSun = location.getPath().endsWith("sun.png");
        boolean isMoon = location.getPath().endsWith("moon_phases.png");
        if ((isSun && !VanillaFeatureToggles.shouldRenderSun())
                || (isMoon && !VanillaFeatureToggles.shouldRenderMoon())) {
            manager.bindTexture(impetus$transparentTexture());
            return;
        }
        manager.bindTexture(location);
    }

    // Replaces the call of VertexBuffer.drawArrays in renderSky.
    @Redirect(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V"),
            require = 0)
    private void impetus$suppressStarVbo(VertexBuffer buffer, int mode) {
        if (VanillaFeatureToggles.shouldRenderStars()) {
            buffer.drawArrays(mode);
        }
    }

    // Replaces the call of GlStateManager.callList in renderSky.
    @Redirect(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"),
            require = 0)
    private void impetus$suppressStarList(int list) {
        if (VanillaFeatureToggles.shouldRenderStars()) {
            GlStateManager.callList(list);
        }
    }

    // Helper method transparent texture added to the target.
    @Unique
    private static ResourceLocation impetus$transparentTexture() {
        if (impetus$transparent == null) {
            DynamicTexture texture = new DynamicTexture(1, 1);
            texture.getTextureData()[0] = 0;
            texture.updateDynamicTexture();
            impetus$transparent = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("impetus_transparent", texture);
        }
        return impetus$transparent;
    }

    // Field transparent added to the target.
    @Unique
    private static ResourceLocation impetus$transparent;

}
