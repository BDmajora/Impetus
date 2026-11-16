package com.bdmajora.impetus.umbra.compat.dh;

import org.lwjgl.opengl.GL11;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterColorDepthTextureCreatedEvent;
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
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Handlers of the Distant Horizons render events.
public final class LodRendererEvents {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Flag set once the handlers are registered.
    private static boolean eventHandlersBound;
    // Flag set during the translucent LOD pass.
    private static boolean atTranslucent;
    // Width of the LOD textures.
    private static int textureWidth;
    // Height of the LOD textures.
    private static int textureHeight;

    // Prevents instantiation
    private LodRendererEvents() {
    }

    // Sets up the event handlers.
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
                Umbra.loadCurrentShaderpack();
                LOGGER.info("[Umbra] Distant Horizons LOD event handlers bound.");
            }
        });
    }

    // Returns whether a shader pack is active.
    private static boolean shadersActive() {
        return Umbra.getRenderingPipeline() != null;
    }

    // Returns the instance.
    private static DhCompatInternal getInstance() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null || pipeline.getDhCompat() == null) {
            return DhCompatInternal.SHADERLESS;
        }
        Object instance = pipeline.getDhCompat().getInstance();
        return instance instanceof DhCompatInternal ? (DhCompatInternal) instance : DhCompatInternal.SHADERLESS;
    }

    // Sets up the set deferred before rendering event.
    private static void setupSetDeferredBeforeRenderingEvent() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                boolean override = shadersActive() && getInstance().shouldOverride();
                DhApi.Delayed.renderProxy.setDeferTransparentRendering(override);
                if (override) {
                    DhApi.Delayed.configs.graphics().fog().enableDhFog().setValue(false);
                } else {
                    DhApi.Delayed.configs.graphics().fog().enableDhFog().clearValue();
                }
            }
        });
    }

    // Sets up the reconnect depth texture event.
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

    // Sets up the generic events.
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

    // Sets up the create depth texture event.
    private static void setupCreateDepthTextureEvent() {
        DhApi.events.bind(DhApiAfterColorDepthTextureCreatedEvent.class, new DhApiAfterColorDepthTextureCreatedEvent() {
            @Override
            public void onResize(DhApiEventParam<DhApiTextureCreatedParam> event) {
                textureWidth = event.value.newWidth;
                textureHeight = event.value.newHeight;
            }
        });
    }

    // Sets up the shadow pass cancelling.
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

    // Sets up the before render cleanup event.
    private static void setupBeforeRenderCleanupEvent() {
        DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, new DhApiBeforeRenderCleanupEvent() {
            @Override
            public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                if (!instance.shouldOverride()) {
                    return;
                }
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

    // Sets up the before buffer clear event.
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
                    GlStateManager.clearDepth(1.0);
                    LWJGL.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    event.cancelEvent();
                }
            }
        });
    }

    // Sets up the before buffer render event.
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

    // Sets up the before render framebuffer binding.
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

    // Removes the framebuffer and program overrides.
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

    // Sets up the before render pass event.
    private static void setupBeforeRenderPassEvent() {
        DhApi.events.bind(DhApiBeforeRenderPassEvent.class, new DhApiBeforeRenderPassEvent() {
            @Override
            public void beforeRender(DhApiEventParam<DhApiRenderParam> event) {
                DhCompatInternal instance = getInstance();
                boolean shadowPass = UmbraShadowRenderer.isShadowPass();

                if (instance.shouldOverride()) {
                    DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().setValue(false);
                    DhApi.Delayed.configs.graphics().fog().enableDhFog().setValue(false);
                    if (event.value.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) {
                        LOGGER.error("[Umbra] Unexpected: DH ran its combined opaque + translucent pass with shaders on");
                    }
                } else {
                    DhApi.Delayed.configs.graphics().ambientOcclusion().enabled().clearValue();
                    DhApi.Delayed.configs.graphics().fog().enableDhFog().clearValue();
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
                        GlStateManager.disableCull();
                    }
                }
            }
        });
    }

    // Returns the LOD projection of a frame.
    private static Matrix4f lodProjection(DhApiRenderParam param) {
        Matrix4fc projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        return new Matrix4f().setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(),
                param.nearClipPlane, param.farClipPlane);
    }

    // Returns a JOML copy of a Distant Horizons matrix.
    private static Matrix4f toJoml(DhApiMat4f matrix) {
        return new Matrix4f().setTransposed(matrix.getValuesAsArray());
    }

    // Sets up the before apply shader event.
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
