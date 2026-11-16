package com.bdmajora.impetus.mixin.core.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import java.nio.FloatBuffer;
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

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    // Profiler section switch before the hand.
    private static final String PROFILER_END_START =
            "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V";

    // Field hand projection added to the target.
    @Unique
    private static final Matrix4f impetus$handProjection = new Matrix4f();
    // Field hand projection buffer added to the target.
    @Unique
    private static final FloatBuffer impetus$handProjectionBuffer = BufferUtils.createFloatBuffer(16);

    // Depth scale of the hand.
    private static final float HAND_DEPTH_MUL = 0.125f;

    // Reference to the field fog color red of the target.
    @Shadow
    private float fogColorRed;
    // Reference to the field fog color green of the target.
    @Shadow
    private float fogColorGreen;
    // Reference to the field fog color blue of the target.
    @Shadow
    private float fogColorBlue;
    // Reference to the field mc of the target.
    @Shadow
    @Final
    private Minecraft mc;
    // Reference to the field item renderer of the target.
    @Shadow
    private ItemRenderer itemRenderer;
    // Reference to the field debug view of the target.
    @Shadow
    private boolean debugView;
    // Reference to the field far plane distance of the target.
    @Shadow
    private float farPlaneDistance;
    // Reference to the field render hand of the target.
    @Shadow
    private boolean renderHand;

    // Reference to the method render hand of the target.
    @Shadow
    private void renderHand(float partialTicks, int pass) {
    }

    // Reference to the method get fov modifier of the target.
    @Shadow
    private float getFOVModifier(float partialTicks, boolean useFOVSetting) {
        return 0.0f;
    }

    // Reference to the method hurt camera effect of the target.
    @Shadow
    private void hurtCameraEffect(float partialTicks) {
    }

    // Reference to the method apply bobbing of the target.
    @Shadow
    private void applyBobbing(float partialTicks) {
    }

    // Reference to the method enable lightmap of the target.
    @Shadow
    public void enableLightmap() {
    }

    // Reference to the method disable lightmap of the target.
    @Shadow
    public void disableLightmap() {
    }

    // Runs before every return of enableLightmap.
    @Inject(method = "enableLightmap", at = @At("RETURN"))
    private void impetus$onEnableLightmap(CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setLightmapEnabled(true);
        }
    }

    // Runs before every return of disableLightmap.
    @Inject(method = "disableLightmap", at = @At("RETURN"))
    private void impetus$onDisableLightmap(CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setLightmapEnabled(false);
        }
    }

    // Runs at the start of renderWorld.
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void impetus$beginShaderFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.beginFrame();
        if (pipeline != null) {
            pipeline.beginWorldRendering(partialTicks);
        }
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=frustum"))
    private void impetus$captureRenderingState(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            CapturedRenderingState.INSTANCE.setFogColor(this.fogColorRed, this.fogColorGreen, this.fogColorBlue);
            pipeline.captureRenderingState();
            pipeline.renderShadowMap();
        }
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=sky"))
    private void impetus$phaseSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.SkyBasic);
    }

    // Changes a constant in renderWorldPass.
    @ModifyConstant(method = "renderWorldPass", constant = {
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

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=terrain"))
    private void impetus$phaseTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=entities"))
    private void impetus$phaseEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Entities);
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=destroyProgress"))
    private void impetus$phaseBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.DamagedBlock);
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass", at = {
            @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=litParticles"),
            @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=particles")})
    private void impetus$phaseParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Particles);
    }

    // Replaces the call of ParticleManager.renderParticles in renderWorldPass.
    @Redirect(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles"
                            + "(Lnet/minecraft/entity/Entity;F)V"),
            require = 0)
    private void impetus$orderParticles(ParticleManager manager,
                                        // Entity of the view.
                                        Entity entity, float partialTicks) {
        if (impetus$particlesDrawnEarly) {
            impetus$particlesDrawnEarly = false;
            return;
        }
        manager.renderParticles(entity, partialTicks);
    }

    // Field particles drawn early added to the target.
    @Unique
    private boolean impetus$particlesDrawnEarly;

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=weather"))
    private void impetus$phaseWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(ProgramId.Weather);
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null && pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            GlStateManager.depthMask(true);
        }
    }

    // Runs inside renderWorldPass.
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE_STRING", target = PROFILER_END_START, args = "ldc=forge_render_last"))
    private void impetus$phaseRenderLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Umbra.setPhase(null);
    }

    // Flag set once the shader drew the hand.
    private boolean impetus$shaderHandRendered;

    // Runs inside renderWorldPass.
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
                this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            }
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

    // Runs inside renderWorldPass.
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
            DeferredBlockOutline.drawIfPending();
        }
    }

    // Replaces the call of EntityRenderer.renderHand in renderWorldPass.
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

    // Draws the held item through the shader pipeline.
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
            GlStateManager.blendFunc(770, 771);
        }
    }

    // Draws first person overlays after the composite passes.
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

    // Returns whether view entity sleeping.
    private boolean impetus$isViewEntitySleeping() {
        Entity viewEntity = this.mc.getRenderViewEntity();
        return viewEntity instanceof EntityLivingBase living && living.isPlayerSleeping();
    }

    // Sets up the hand projection.
    private void impetus$setupHandProjection(float partialTicks, int pass) {
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        if (this.mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (-(pass * 2 - 1)) * 0.07f, 0.0f, 0.0f);
        }
        GlStateManager.scale(1.0f, 1.0f, HAND_DEPTH_MUL);
        impetus$handProjection.setPerspective((float) Math.toRadians(this.getFOVModifier(partialTicks, false)),
                (float) Math.max(1, this.mc.displayWidth) / (float) Math.max(1, this.mc.displayHeight),
                0.05f, this.farPlaneDistance * 2.0f).get(impetus$handProjectionBuffer);
        GlStateManager.multMatrix(impetus$handProjectionBuffer);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        if (this.mc.gameSettings.anaglyph) {
            GlStateManager.translate((float) (pass * 2 - 1) * 0.1f, 0.0f, 0.0f);
        }
    }

    // Runs before every return of renderWorld.
    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void impetus$finishShaderFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline != null) {
            pipeline.finishWorldRendering();
        }
        DeferredBlockOutline.discard();
    }
}
