package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericObjectRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeGenericRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeTextureClearEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The DH API event handlers that hijack DH's LOD passes for the active pack (Iris's LodRendererEvents): DH keeps culling, buffer management, draw calls and cleanup, and at each of its hooks these swap in the pipeline's framebuffer (gbuffer colours + DH depth), the pack's program with the pass's matrices, the shadow pass's frustum, and cancel what a pack takes over (DH's fog, SSAO, its composite onto MC's framebuffer). Bound once after DH initialises, they consult the live pipeline on every call, so a pack change or shaders off need no rebinding
public final class LodRendererEvents {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static boolean eventHandlersBound;
    // Whether the current DH pass is its deferred translucent one, so the per-buffer program bind picks dh_water
    private static boolean atTranslucent;
    // DH's colour/depth texture size, from its resize event; the pre-translucent depth copy is sized to it
    private static int textureWidth;
    private static int textureHeight;

    private LodRendererEvents() {
    }

    // Queues everything on DH's after-init event, since DhApi.Delayed.* is null until then
    public static void setupEventHandlers() {
        if (eventHandlersBound) {
            return;
        }
        eventHandlersBound = true;
        LOGGER.info("[Umbra] Queuing Distant Horizons event binding...");
        DhApi.events.bind(DhApiAfterDhInitEvent.class, new DhApiAfterDhInitEvent() {
            @Override
            public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
                LOGGER.info("[Umbra] Distant Horizons ready, binding LOD event handlers...");
                DhCompatInternal.dhEnabled = DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
                setupSetDeferredBeforeRenderingEvent();
                setupReconnectDepthTextureEvent();
                setupGenericEvents();
                setupCreateDepthTextureEvent();
                setupShadowPassCancelling();
                setupBeforeBufferClearEvent();
                setupBeforeRenderCleanupEvent();
                setupBeforeBufferRenderEvent();
                setupBeforeRenderFramebufferBinding();
                setupBeforeRenderPassEvent();
                setupBeforeApplyShaderEvent();
                // A pack parsed before this point compiled without DISTANT_HORIZONS (Iris's loadShaderpackWhenPossible); reload it with DH known
                Umbra.loadCurrentShaderpack();
                LOGGER.info("[Umbra] Distant Horizons LOD event handlers bound.");
            }
        });
    }

    // Whether a pipeline is live this frame (Iris's isPackInUseQuick): a parsed pack whose pipeline failed to build must leave DH's own composite alone
    private static boolean shadersActive() {
        return Umbra.getRenderingPipeline() != null;
    }

    // The live pipeline's compat state, or the shaderless stand-in that leaves DH alone
    private static DhCompatInternal getInstance() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null || pipeline.getDhCompat() == null) {
            return DhCompatInternal.SHADERLESS;
        }
        Object instance = pipeline.getDhCompat().getInstance();
        return instance instanceof DhCompatInternal ? (DhCompatInternal) instance : DhCompatInternal.SHADERLESS;
    }

    // Before DH prepares a frame: with a pack shading LODs, its water must render deferred (after MC's translucents, where dh_water samples the finished opaque gbuffer) and DH's own fog is off (the pack fogs in composite)
    private static void setupSetDeferredBeforeRenderingEvent() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                boolean override = shadersActive() && getInstance().shouldOverride();
                DhApi.Delayed.renderProxy.setDeferTransparentRendering(override);
                if (override) {
                    DhApi.Delayed.configs.graphics().fog().drawMode().setValue(EDhApiFogDrawMode.FOG_DISABLED);
                } else {
                    DhApi.Delayed.configs.graphics().fog().drawMode().clearValue();
                }
            }
        });
    }

    // DH's depth texture id can change (it recreates on resize with a fresh id); attach the current one to the LOD framebuffers before every clear
    private static void setupReconnectDepthTextureEvent() {
        DhApi.events.bind(DhApiBeforeTextureClearEvent.class, new DhApiBeforeTextureClearEvent() {
            @Override
            public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
                DhApiResult<Integer> result = DhApi.Delayed.renderProxy.getDhDepthTextureId();
                if (result.success && result.payload != null) {
                    getInstance().reconnectDHTextures(result.payload, textureWidth, textureHeight);
                }
            }
        });
    }

    // DH's generic objects (beacon beams, clouds) draw into their own framebuffer with the pack's dh_generic; clouds are dropped when the pack turned vanilla clouds off, since it draws its own sky
    private static void setupGenericEvents() {
        DhApi.events.bind(DhApiBeforeGenericRenderSetupEvent.class, new DhApiBeforeGenericRenderSetupEvent() {
            @Override
            public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                if (instance.shouldOverride() && instance.getGenericFB() != null && !UmbraShadowRenderer.isShadowPass()) {
                    instance.getGenericFB().bind();
                    UmbraRenderingPipeline pipeline = instance.getPipeline();
                    DhGenericRenderProgram generic = (DhGenericRenderProgram) instance.getGenericShader();
                    pipeline.onDhLodDraw(instance.getGenericFB(), generic.getDrawBuffers(), generic.getBlendState(), true);
                }
            }
        });
        DhApi.events.bind(DhApiBeforeGenericObjectRenderEvent.class, new DhApiBeforeGenericObjectRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiBeforeGenericObjectRenderEvent.EventParam> event) {
                if ("Clouds".equalsIgnoreCase(event.value.resourceLocationPath) && getInstance().avoidRenderingClouds()) {
                    event.cancelEvent();
                }
            }
        });
    }

    private static void setupCreateDepthTextureEvent() {
        DhApi.events.bind(DhApiColorDepthTextureCreatedEvent.class, new DhApiColorDepthTextureCreatedEvent() {
            @Override
            public void onResize(DhApiEventParam<DhApiColorDepthTextureCreatedEvent.EventParam> event) {
                textureWidth = event.value.newWidth;
                textureHeight = event.value.newHeight;
            }
        });
    }

    // With no dh_shadow program (or dhShadow.enabled=false) the LODs stay out of the shadow map entirely
    private static void setupShadowPassCancelling() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (UmbraShadowRenderer.isShadowPass() && !getInstance().shouldOverrideShadow()) {
                    event.cancelEvent();
                }
            }
        });
        DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, new DhApiBeforeDeferredRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (UmbraShadowRenderer.isShadowPass() && !getInstance().shouldOverrideShadow()) {
                    event.cancelEvent();
                }
            }
        });
    }

    // After DH's draws: drop the pack program (DH's own cleanup unbinds its VAO and restores the 1.12.2 GL state) and hand the gbuffer's blend state back
    private static void setupBeforeRenderCleanupEvent() {
        DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, new DhApiBeforeRenderCleanupEvent() {
            @Override
            public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                if (!instance.shouldOverride()) {
                    return;
                }
                // DH's cleanup, which follows this event, unbinds its atlas from unit 1 RAW; with the cache told the same, vanilla's next enableLightmap() sees a change and really rebinds the lightmap there
                GlTextureUnits.forceBindTexture2D(1, 0);
                if (UmbraShadowRenderer.isShadowPass()) {
                    if (instance.getShadowShader() != null) {
                        instance.getShadowShader().unbind();
                    }
                } else {
                    instance.getSolidShader().unbind();
                    instance.getPipeline().afterDhLodDraw(
                            (atTranslucent ? instance.getTranslucentShader() : instance.getSolidShader()).getDrawBuffers().length);
                }
            }
        });
    }

    // DH clears its colour and depth at the start of its opaque pass; with the gbuffer as the colour target only DH's depth may be cleared, and in the shadow pass nothing at all (the map is already cleared and mid-render)
    private static void setupBeforeBufferClearEvent() {
        DhApi.events.bind(DhApiBeforeTextureClearEvent.class, new DhApiBeforeTextureClearEvent() {
            @Override
            public void beforeClear(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (event.value.renderPass != EDhApiRenderPass.OPAQUE) {
                    return;
                }
                if (UmbraShadowRenderer.isShadowPass()) {
                    event.cancelEvent();
                } else if (getInstance().shouldOverride()) {
                    // DH sets its clear depth inside the clear this cancels; with shaders it renders forward-Z, whose far value is 1
                    GlStateManager.clearDepth(1.0);
                    LWJGL.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    event.cancelEvent();
                }
            }
        });
    }

    // Per LOD buffer, after DH bound its own program and set its model offset: bind the pack's program for this pass and give it the same offset
    private static void setupBeforeBufferRenderEvent() {
        DhApi.events.bind(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent() {
            @Override
            public void beforeRender(DhApiEventParam<DhApiBeforeBufferRenderEvent.EventParam> event) {
                DhCompatInternal instance = getInstance();
                if (!instance.shouldOverride()) {
                    return;
                }
                DhApiVec3f modelPos = event.value.modelPos;
                DhLodRenderProgram program;
                if (UmbraShadowRenderer.isShadowPass()) {
                    program = instance.getShadowShader();
                } else if (atTranslucent) {
                    program = instance.getTranslucentShader();
                } else {
                    program = instance.getSolidShader();
                }
                if (program != null) {
                    program.bind();
                    program.setModelPos(modelPos);
                }
            }
        });
    }

    // Before DH's render setup: the framebuffer override for this pass (the shadow map in the shadow pass, else the LOD gbuffer), the shadow frustum, and the generic program; rebound every pass since the pipeline can change between frames
    private static void setupBeforeRenderFramebufferBinding() {
        DhApi.events.bind(DhApiBeforeRenderSetupEvent.class, new DhApiBeforeRenderSetupEvent() {
            @Override
            public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                unbindOverrides(instance);
                if (!instance.shouldOverride()) {
                    return;
                }
                if (instance.getGenericShader() != null) {
                    DhApi.overrides.bind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());
                }
                if (UmbraShadowRenderer.isShadowPass() && instance.shouldOverrideShadow()) {
                    DhApi.overrides.bind(IDhApiFramebuffer.class, instance.getShadowFBWrapper());
                    DhApi.overrides.bind(IDhApiShadowCullingFrustum.class, instance.getShadowCullingFrustum());
                } else {
                    DhApi.overrides.bind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
                }
            }
        });
    }

    // Removes every override this compat may have bound; unbinding something never bound is a no-op
    private static void unbindOverrides(DhCompatInternal instance) {
        if (instance.getShadowCullingFrustum() != null) {
            DhApi.overrides.unbind(IDhApiShadowCullingFrustum.class, instance.getShadowCullingFrustum());
        }
        if (instance.getShadowFBWrapper() != null) {
            DhApi.overrides.unbind(IDhApiFramebuffer.class, instance.getShadowFBWrapper());
        }
        if (instance.getSolidFBWrapper() != null) {
            DhApi.overrides.unbind(IDhApiFramebuffer.class, instance.getSolidFBWrapper());
        }
        if (instance.getGenericShader() != null) {
            DhApi.overrides.unbind(IDhApiGenericObjectShaderProgram.class, instance.getGenericShader());
        }
    }

    // Right before DH's draws of a pass, after it set its own GL state: the pack's program with the pass's matrices, the gbuffer draw-buffer/blend/sampler setup on the LOD framebuffer, and for the deferred pass the dhDepthTex1 snapshot
    private static void setupBeforeRenderPassEvent() {
        DhApi.events.bind(DhApiBeforeRenderPassEvent.class, new DhApiBeforeRenderPassEvent() {
            @Override
            public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                boolean shadowPass = UmbraShadowRenderer.isShadowPass();

                // Config overrides while a pack shades LODs: no DH SSAO (the pack's own), no DH fog
                if (instance.shouldOverride()) {
                    DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().setValue(false);
                    DhApi.Delayed.configs.graphics().fog().drawMode().setValue(EDhApiFogDrawMode.FOG_DISABLED);
                    if (event.value.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) {
                        LOGGER.error("[Umbra] Unexpected: DH ran its combined opaque + translucent pass with shaders on");
                    }
                } else {
                    DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().clearValue();
                    DhApi.Delayed.configs.graphics().fog().drawMode().clearValue();
                }

                if (event.value.renderPass == EDhApiRenderPass.OPAQUE) {
                    atTranslucent = false;
                    if (!instance.shouldOverride()) {
                        return;
                    }
                    if (shadowPass) {
                        if (instance.getShadowShader() == null) {
                            return;
                        }
                        instance.getShadowShader().fillUniformData(
                                CapturedRenderingState.INSTANCE.getShadowProjection(),
                                CapturedRenderingState.INSTANCE.getShadowModelView(),
                                event.value.worldYOffset, event.value.partialTicks);
                    } else {
                        DhLodRenderProgram solid = instance.getSolidShader();
                        solid.fillUniformData(lodProjection(event.value), toJoml(event.value.dhModelViewMatrix),
                                event.value.worldYOffset, event.value.partialTicks);
                        instance.getPipeline().onDhLodDraw(instance.getSolidFB(), solid.getDrawBuffers(),
                                solid.getBlendState(), false);
                    }
                    return;
                }

                if (event.value.renderPass == EDhApiRenderPass.TRANSPARENT) {
                    atTranslucent = true;
                    if (shadowPass) {
                        if (instance.shouldOverrideShadow() && instance.getShadowShader() != null) {
                            instance.getShadowShader().bind();
                            instance.getShadowFBWrapper().bind();
                            // The shadow pass writes translucent tint unblended (see UmbraShadowRenderer); DH just enabled blending for its water
                            GlStateManager.disableBlend();
                        }
                        return;
                    }
                    if (instance.shouldOverride() && instance.getTranslucentFB() != null) {
                        instance.copyTranslucents(textureWidth, textureHeight);
                        DhLodRenderProgram water = instance.getTranslucentShader();
                        water.fillUniformData(lodProjection(event.value), toJoml(event.value.dhModelViewMatrix),
                                event.value.worldYOffset, event.value.partialTicks);
                        instance.getPipeline().onDhLodDraw(instance.getTranslucentFB(), water.getDrawBuffers(),
                                water.getBlendState(), true);
                        // Iris disables culling here for two-sided LOD water; DH re-enables it right after this event on purpose (its Iris issue 2582 workaround), so this is parity rather than effect
                        GlStateManager.disableCull();
                    }
                }
            }
        });
    }

    // The LOD projection for this pass: the camera's FOV and aspect with DH's near and far planes, the matrix DhCompat.getProjection reports as dhProjection
    private static Matrix4f lodProjection(DhApiRenderParam param) {
        Matrix4fc projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        return new Matrix4f().setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(),
                param.nearClipPlane, param.farClipPlane);
    }

    // DH's matrices are row-major arrays; a transposed set yields JOML's column-major form
    private static Matrix4f toJoml(DhApiMat4f matrix) {
        return new Matrix4f().setTransposed(matrix.getValuesAsArray());
    }

    // DH's last step composites its colour texture onto MC's framebuffer with its own depth test; with a pack the LODs are already in the gbuffer, so that step is cancelled and the overrides dropped until the next pass binds them again
    private static void setupBeforeApplyShaderEvent() {
        DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, new DhApiBeforeApplyShaderRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                if (shadersActive() && getInstance().shouldOverride()) {
                    unbindOverrides(getInstance());
                    event.cancelEvent();
                }
            }
        });
    }
}
