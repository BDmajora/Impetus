package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import com.bdmajora.impetus.umbra.pipeline.DeferredBlockOutline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.client.ForgeHooksClient;
import com.bdmajora.extras.Extras;
import com.bdmajora.extras.client.CloudPassState;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;

// Drives the Umbra frame pipeline from vanilla's world render like modern Umbra hooks LevelRenderer: renderWorld HEAD builds the pipeline and binds the gbuffer, the "frustum" profiler anchor captures camera matrices and fog colour, renderWorld RETURN runs the composite chain; all no-ops without a pack
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    private static final String PROFILER_END_START =
            "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V";

    // OptiFine's configHandDepthMul, applied as glScale(1, 1, x) before gluPerspective (Shaders.applyHandDepth); the shader path draws the hand into the gbuffer with world depth present, so its depth must be compressed to the near plane or it clips and gets composite-shaded as a dark blob
    private static final float HAND_DEPTH_MUL = 0.125f;

    @Shadow
    private float fogColorRed;
    @Shadow
    private float fogColorGreen;
    @Shadow
    private float fogColorBlue;
    @Shadow
    @Final
    private Minecraft mc;
    @Shadow
    private ItemRenderer itemRenderer;
    @Shadow
    private boolean debugView;
    @Shadow
    private float farPlaneDistance;
    @Shadow
    private boolean renderHand;

    // Shadow
    @Shadow
    private void renderHand(float partialTicks, int pass) {
    }

    // Shadow
    @Shadow
    private float getFOVModifier(float partialTicks, boolean useFOVSetting) {
        return 0.0f;
    }

    // Shadow
    @Shadow
    private void hurtCameraEffect(float partialTicks) {
    }

    // Shadow
    @Shadow
    private void applyBobbing(float partialTicks) {
    }

    // Shadow
    @Shadow
    public void enableLightmap() {
    }

    // Shadow
    @Shadow
    public void disableLightmap() {
    }

    // OptiFine parity: enableLightmap()/disableLightmap() swap gbuffers_textured and gbuffers_textured_lit; vanilla only flips texture unit 1, and geometry drawn with it off would keep the lit program, sample white and render fullbright (see UmbraRenderingPipeline#setLightmapEnabled)
    @Inject(method = "enableLightmap", at = @At("RETURN"))
    private void impetus$onEnableLightmap(CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setLightmapEnabled(true);
        }
    }

    @Inject(method = "disableLightmap", at = @At("RETURN"))
    private void impetus$onDisableLightmap(CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setLightmapEnabled(false);
        }
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void impetus$beginShaderFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.beginFrame();
        if (pipeline != null) {
            pipeline.beginWorldRendering(partialTicks);
        }
    }

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=frustum"))
    private void impetus$captureRenderingState(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            CapturedRenderingState.INSTANCE.setFogColor(this.fogColorRed, this.fogColorGreen, this.fogColorBlue);
            pipeline.captureRenderingState();
            // Shadow map renders here: matrices are fresh, and nothing has drawn into the gbuffer yet this frame.
            pipeline.renderShadowMap();
        }
    }

    // --- Fixed-function gbuffer phases, anchored on vanilla's profiler sections -----------------------------------

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=sky"))
    private void impetus$phaseSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyBasic);
    }

    // --- Cloud ordering -------------------------------------------------------------------------------------------

    // Moves the cloud draw to Umbra's slot (after the deferred chain) by pushing both of vanilla's y=128 altitude tests below the world, since early clouds land in depthtex1 and packs like Body Camera then treat them as opaque geometry; only while a pipeline is active (it re-enables depth writes for translucents), and the Extras cloud-translucency option shares the constant since two @ModifyConstant handlers cannot, so Umbra's correctness choice wins over the preference
    @ModifyConstant(method = "renderWorldPass", constant = {
            // The two occurrences are the only 128.0D in the method and are the two halves of the same altitude split (`< 128` early draw, `>= 128` late); both move together
            @Constant(doubleValue = 128.0D, ordinal = 0),
            @Constant(doubleValue = 128.0D, ordinal = 1)})
    private double impetus$cloudDrawSlot(double cloudLayer) {
        if (Umbra.getRenderingPipeline() != null) {
            return Double.NEGATIVE_INFINITY;
        }

        return switch (Extras.options().render.cloudTranslucency) {
            case ALWAYS -> Double.NEGATIVE_INFINITY;
            case NEVER -> Double.POSITIVE_INFINITY;
            default -> CloudPassState.cloudHeight((float) cloudLayer);
        };
    }

    // Terrain draws next: reset to the plain fixed-function mask and program 0 so a missing terrain override writes only colortex0 instead of smearing through the last sky program's DRAWBUFFERS
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=terrain"))
    private void impetus$phaseTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Matches both "entities" sections (the main one and the post-translucent leftovers).
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=entities"))
    private void impetus$phaseEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Entities);
    }

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=destroyProgress"))
    private void impetus$phaseBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.DamagedBlock);
    }

    @Inject(method = "renderWorldPass", at = {
            @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=litParticles"),
            @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=particles")})
    private void impetus$phaseParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        // Umbra resolves particles as gbuffers_particles -> gbuffers_textured_lit, so packs shipping the modern program get it and the rest land where they always did (OptiFine uses plain gbuffers_textured unless "litParticles")
        Umbra.setPhase(ProgramId.Particles);
    }

    // particles.ordering: vanilla already draws particles after the "translucent" anchor where the deferred chain runs, which is Umbra's "after" and default; only "before" needs the vanilla draw suppressed and re-issued ahead of the chain, and "mixed" resolves to "after" since 1.12.2 cannot split them
    @Redirect(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles"
                            + "(Lnet/minecraft/entity/Entity;F)V"),
            require = 0)
    private void impetus$orderParticles(ParticleManager manager,
                                        Entity entity, float partialTicks) {
        if (impetus$particlesDrawnEarly) {
            impetus$particlesDrawnEarly = false;
            return;
        }
        manager.renderParticles(entity, partialTicks);
    }

    @Unique
    private boolean impetus$particlesDrawnEarly;

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=weather"))
    private void impetus$phaseWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Weather);
        // `rain.depth`: vanilla draws precipitation with depth writes off, and a pack wanting it in depthtex asks for them back; no restore needed since vanilla calls depthMask(true) right after renderRainSnow
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            GlStateManager.depthMask(true);
        }
    }

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=forge_render_last"))
    private void impetus$phaseRenderLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // --- Mid-frame pipeline stages ---------------------------------------------------------------------------------

    private boolean impetus$shaderHandRendered;

    // OptiFine's order around translucents: solid first-person hand (gbuffers_hand) -> preWater (depth snapshot + deferred) -> translucent terrain; the hand MUST precede the deferred chain since deferred packs light it there, otherwise it leaks raw buffer data
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=translucent"))
    private void impetus$beginTranslucents(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.beginHand();
            this.impetus$shaderHandRendered = false;
            if (this.renderHand && pipeline.beginHandRendering()) {
                this.impetus$shaderHandRendered = true;
                try {
                    this.impetus$renderFirstPersonItemForShader(partialTicks, pass, true);
                } finally {
                    pipeline.endHandRendering();
                }
                // Vanilla bound the block atlas for the translucent layer just before this anchor and the hand render bound skin/item textures over it
                this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            }
            // particles.ordering = before: draw them into the pre-deferred gbuffer so the deferred chain lights them.
            if ("before".equals(pipeline.getParticleOrdering())) {
                Entity viewEntity = this.mc.getRenderViewEntity();
                if (viewEntity != null) {
                    Umbra.setPhase(ProgramId.Particles);
                    this.mc.effectRenderer.renderParticles(viewEntity, partialTicks);
                    this.impetus$particlesDrawnEarly = true;
                }
            }
            pipeline.beginTranslucents();
        }
    }

    // The composite/final chain runs at the "hand" anchor, BEFORE vanilla's clear(256) wipes the depth buffer (OptiFine's renderCompositeFinal position); the hand itself already rendered pre-deferred
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=hand"))
    private void impetus$compositeBeforeHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            if (this.impetus$shaderHandRendered && pipeline.beginHandTranslucentRendering()) {
                try {
                    this.impetus$renderFirstPersonItemForShader(partialTicks, pass, false);
                } finally {
                    pipeline.endHandRendering();
                }
                this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            }
            pipeline.finishWorldRendering();
            // The selection box for packs without gbuffers_line was skipped in the world pass and lands here, darkening the finished image rather than albedo the composite relights (see DeferredBlockOutline)
            DeferredBlockOutline.drawIfPending();
        }
    }

    @Redirect(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V"))
    private void impetus$skipPostCompositeShaderHand(EntityRenderer renderer, float partialTicks, int pass) {
        if (this.impetus$shaderHandRendered) {
            try {
                this.impetus$renderFirstPersonOverlaysAfterComposite(partialTicks, pass);
            } finally {
                this.impetus$shaderHandRendered = false;
            }
        } else {
            this.renderHand(partialTicks, pass);
        }
    }

    // The world's projection and modelview must survive untouched since translucent terrain renders next; OptiFine's beginHand()/endHand() push and pop both for the same reason
    private void impetus$renderFirstPersonItemForShader(float partialTicks, int pass, boolean fireForgeHook) {
        if (this.debugView) {
            return;
        }

        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        try {
            this.impetus$setupHandProjection(partialTicks, pass);
            this.hurtCameraEffect(partialTicks);
            if (this.mc.gameSettings.viewBobbing) {
                this.applyBobbing(partialTicks);
            }

            boolean sleeping = this.impetus$isViewEntitySleeping();
            boolean renderVanillaHand = !fireForgeHook
                    || !ForgeHooksClient.renderFirstPersonHand(this.mc.renderGlobal, partialTicks, pass);
            if (renderVanillaHand && this.mc.gameSettings.thirdPersonView == 0 && !sleeping
                    && !this.mc.gameSettings.hideGUI && !this.mc.playerController.isSpectator()) {
                this.enableLightmap();
                this.itemRenderer.renderItemInFirstPerson(partialTicks);
                this.disableLightmap();
            }
        } finally {
            GlStateManager.matrixMode(5889);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            // OptiFine Shaders.endHand(): restore the standard alpha blend func the hand pass may have changed.
            GlStateManager.blendFunc(770, 771);
        }
    }

    private void impetus$renderFirstPersonOverlaysAfterComposite(float partialTicks, int pass) {
        if (this.debugView) {
            return;
        }

        this.impetus$setupHandProjection(partialTicks, pass);
        boolean sleeping = this.impetus$isViewEntitySleeping();
        this.disableLightmap();
        if (this.mc.gameSettings.thirdPersonView == 0 && !sleeping) {
            this.itemRenderer.renderOverlays(partialTicks);
            this.hurtCameraEffect(partialTicks);
        }
        if (this.mc.gameSettings.viewBobbing) {
            this.applyBobbing(partialTicks);
        }
    }

    // Vanilla hides the first-person hand and overlays while the view entity is in bed
    private boolean impetus$isViewEntitySleeping() {
        Entity viewEntity = this.mc.getRenderViewEntity();
        return viewEntity instanceof EntityLivingBase living && living.isPlayerSleeping();
    }

    private void impetus$setupHandProjection(float partialTicks, int pass) {
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        if (this.mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (-(pass * 2 - 1)) * 0.07f, 0.0f, 0.0f);
        }
        // OptiFine's applyHandDepth: squeeze the hand's clip-space Z so it wins the depth test against gbuffer geometry and lands at the near plane for composite lighting
        GlStateManager.scale(1.0f, 1.0f, HAND_DEPTH_MUL);
        Project.gluPerspective(this.getFOVModifier(partialTicks, false),
                (float) this.mc.displayWidth / (float) this.mc.displayHeight,
                0.05f, this.farPlaneDistance * 2.0f);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        if (this.mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (pass * 2 - 1) * 0.1f, 0.0f, 0.0f);
        }
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void impetus$finishShaderFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.finishWorldRendering();
        }
        // Safety net, the frame's last word: if the composite anchor never ran, drop the pending outline rather than replay a stale capture with the wrong matrices next frame
        DeferredBlockOutline.discard();
    }
}
