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
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.DeferredBlockOutline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.VanillaFeatureToggles;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;

// Switches the sky phase to gbuffers_skytextured for the sun and moon inside renderSky and back to gbuffers_skybasic afterwards (OptiFine's preCelestialRotate split); the outer "sky" anchor already selected skybasic
@Mixin(RenderGlobal.class)
public class RenderGlobalMixin {
    private static final String SUN_TEXTURES_FIELD =
            "Lnet/minecraft/client/renderer/RenderGlobal;SUN_TEXTURES:Lnet/minecraft/util/ResourceLocation;";
    private static final String MOON_TEXTURES_FIELD =
            "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;";

    // Right before the sky disc VBO draws, with skybasic active, draw OptiFine's horizon fill so the thin uncovered band lands in colortex1 with the sky colour instead of stale HDR (Shaders.preSkyList's call site)
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;bindBuffer()V", ordinal = 0))
    private void impetus$drawHorizon(float partialTicks, int pass, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.drawSkyHorizon();
        }
    }

    // OptiFine's preCelestialRotate: rotate the live modelview by the pack's sunPathRotation between vanilla's -90 Y-rotation and its time-of-day X-rotation, so the drawn sun matches sunPosition and the shadow projection (Umbra rotates only the uniform, which is visibly wrong on 1.12's vanilla sun disc; 19 of 22 packs set it)
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

    @Inject(method = "renderSky(FI)V",
            at = @At(value = "FIELD", target = SUN_TEXTURES_FIELD, opcode = Opcodes.GETSTATIC))
    private void impetus$beginSunMoon(float partialTicks, int pass, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyTextured, 4); // MC_RENDER_STAGE_SUN
    }

    // The moon needs its own stage: vanilla draws sun then moon through one program, and 13 call sites across the installed packs branch on MC_RENDER_STAGE_MOON (Spooklementary, Pastel key moon tinting on it)
    @Inject(method = "renderSky(FI)V",
            at = @At(value = "FIELD", target = MOON_TEXTURES_FIELD, opcode = Opcodes.GETSTATIC),
            require = 0)
    private void impetus$beginMoon(float partialTicks, int pass, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyTextured, 5); // MC_RENDER_STAGE_MOON
    }

    @Inject(method = "renderSky(FI)V",
            slice = @Slice(from = @At(value = "FIELD", target = SUN_TEXTURES_FIELD,
                    opcode = Opcodes.GETSTATIC)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V", ordinal = 0))
    private void impetus$endSunMoon(float partialTicks, int pass, CallbackInfo ci) {
        // Vanilla's disableTexture2D() right after the sun/moon quads is where the star field begins; stars go back through gbuffers_skybasic, so the finer stage is published explicitly (Clarity emits stars only under MC_RENDER_STAGE_STARS)
        Umbra.setPhase(ProgramId.SkyBasic, 6); // MC_RENDER_STAGE_STARS
    }

    // OptiFine brackets the cloud geometry inside RenderGlobal.renderClouds with beginClouds()/endClouds(); done at the geometry boundary rather than the profiler label because a pack can cancel the dispatcher and a leaked phase leaves colortex4 selected. Only fires on the vanilla fallback path
    @Inject(method = "renderClouds(FIDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableCull()V",
                    ordinal = 0),
            require = 0)
    private void impetus$beginFastClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Clouds);
    }

    @Inject(method = "renderClouds(FIDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;enableCull()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            require = 0)
    private void impetus$endFastClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // No clouds directive check here any more: GameSettingsCloudsMixin folds the pack's setting into shouldRenderClouds(), so reaching this means fancy is the effective mode
    @Inject(method = "renderCloudsFancy(FIDDD)V", at = @At("HEAD"), require = 0)
    private void impetus$beginFancyClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Clouds);
    }

    @Inject(method = "renderCloudsFancy(FIDDD)V", at = @At("RETURN"), require = 0)
    private void impetus$endFancyClouds(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Binds gbuffers_line (fallback gbuffers_basic) for the block selection box like Umbra; the "outline" section runs right after "entities", so without this it inherits gbuffers_entities and is written into the entity DRAWBUFFERS, normals and material targets included (OptiFine's disableTexture2D hook does the same)
    @Inject(method = "drawSelectionBox", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$beginBlockOutline(EntityPlayer player,
                                           RayTraceResult target, int execute,
                                           float partialTicks, CallbackInfo ci) {
        // Only route the outline through the pack when it ships `gbuffers_line` (Complementary handles it deliberately); with no such program the fallback lands in gbuffers_basic, and RedHat's stamps land material with no normal/specular into a depthMask(false) draw so the deferred pass produces a view-dependent red line across torches, whereas fixed-function keeps vanilla's blend, alpha and draw-buffer mask
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null || DeferredBlockOutline.isReplaying()) {
            // No pack, or this *is* the post-composite replay: let vanilla draw exactly as it always does.
            return;
        }
        if (pipeline.hasDirectGbufferProgram(ProgramId.Line)) {
            Umbra.setPhase(ProgramId.Line);
            return;
        }
        // No gbuffers_line: defer the whole draw past the composite chain (see DeferredBlockOutline); drawing now would only tint albedo, which the composite relights
        DeferredBlockOutline.capture(player, target, partialTicks);
        ci.cancel();
    }

    @Inject(method = "drawSelectionBox", at = @At("RETURN"), require = 0)
    private void impetus$endBlockOutline(EntityPlayer player,
                                         RayTraceResult target, int execute,
                                         float partialTicks, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Deliberately no blend override for the selection box: drawBoundingBox hides its GL_LINE_STRIP connectors with alpha = 0 vertices, so suppressing blend draws them at full strength and makes the outline replace the gbuffer (a zero normal stamped along every edge under Pastel's DRAWBUFFERS 0/3/6/7 came back as a bright cage); OptiFine and Umbra both keep vanilla's blend, and setPhase already applies an explicit blend.gbuffers_line directive

    // sky = false: draw no vanilla sky geometry, since the pack paints the sky in its composite chain; stronger than the individual sun/moon/stars switches
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$suppressSky(float partialTicks, int pass, CallbackInfo ci) {
        if (!VanillaFeatureToggles.shouldRenderSky()) {
            ci.cancel();
        }
    }

    // backFace.<layer>: vanilla culls back faces for every terrain layer, so a pack shading both sides asks for a layer's back faces and culling is turned off around that draw
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), require = 0)
    private void impetus$applyBackFaceCulling(BlockRenderLayer layer, double partialTicks, int pass,
                                              Entity entity,
                                              CallbackInfoReturnable<Integer> cir) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && !pipeline.shouldCullBackFaces(layer.ordinal())) {
            GlStateManager.disableCull();
            impetus$restoreCull = true;
        }
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("RETURN"), require = 0)
    private void impetus$restoreBackFaceCulling(BlockRenderLayer layer, double partialTicks,
                                                int pass, Entity entity,
                                                CallbackInfoReturnable<Integer> cir) {
        if (impetus$restoreCull) {
            impetus$restoreCull = false;
            GlStateManager.enableCull();
        }
    }

    @Unique
    private boolean impetus$restoreCull;

    // skipAllRendering: draw no terrain, leaving the composite chain to produce the whole image; used by debug and benchmark packs
    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$skipTerrain(BlockRenderLayer layer, double partialTicks, int pass,
                                     Entity entity,
                                     CallbackInfoReturnable<Integer> cir) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.skipAllRendering()) {
            cir.setReturnValue(0);
        }
    }

    // The sun, moon and stars toggles; all three draw inline in renderSky, so the celestial quads are suppressed with a fully transparent texture (additive, so it contributes nothing) and the star field by skipping its draw
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

    @Redirect(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V"),
            require = 0)
    private void impetus$suppressStarVbo(VertexBuffer buffer, int mode) {
        if (VanillaFeatureToggles.shouldRenderStars()) {
            buffer.drawArrays(mode);
        }
    }

    @Redirect(method = "renderSky(FI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"),
            require = 0)
    private void impetus$suppressStarList(int list) {
        if (VanillaFeatureToggles.shouldRenderStars()) {
            GlStateManager.callList(list);
        }
    }

    // A 1x1 fully transparent texture, so a suppressed celestial quad still draws but contributes nothing.
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

    @Unique
    private static ResourceLocation impetus$transparent;

}
