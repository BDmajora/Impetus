package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.ShaderCompileException;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowBoxCuller;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.terrain.DhTerrainTransformer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDhInitEvent;
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
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.common.commonMixins.IFramebufferDepthTexture;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DhCompatTest {
    private static final String LEGACY_VERTEX = "#version 120\nvarying vec4 color;\nvoid main() { gl_Position = ftransform(); color = gl_Color; }";
    private static final String LEGACY_FRAGMENT = "#version 120\n/* DRAWBUFFERS:02 */\nvarying vec4 color;\nvoid main() { gl_FragData[0] = color; gl_FragData[1] = vec4(0.0); }";
    private static final String MODERN_VERTEX = "#version 330 compatibility\nout vec4 color;\nvoid main() { gl_Position = ftransform(); color = gl_Color; }";
    private static final String MODERN_FRAGMENT = "#version 330 compatibility\n/* RENDERTARGETS: 0 */\nin vec4 color;\nlayout(location = 0) out vec4 outColor;\nvoid main() { outColor = color; }";

    // Distinct locations for the uniforms the DH programs set themselves; every other name is undeclared
    private static final Map<String, Integer> LOCATIONS = Map.ofEntries(
            entry(DhTerrainTransformer.MODEL_VIEW, 1),
            entry(DhTerrainTransformer.MODEL_VIEW_INVERSE, 2),
            entry(DhTerrainTransformer.PROJECTION, 3),
            entry(DhTerrainTransformer.PROJECTION_INVERSE, 4),
            entry(DhTerrainTransformer.NORMAL_MATRIX, 5),
            entry("lightmap", 6),
            entry("dhBlockAtlas", 7),
            entry("modelOffset", 11),
            entry("worldYOffset", 12),
            entry("mircoOffset", 13),
            entry("clipDistance", 14),
            entry("uOffsetChunk", 21),
            entry("uOffsetSubChunk", 22),
            entry("uCameraPosChunk", 23),
            entry("uCameraPosSubChunk", 24),
            entry("uBlockLight", 25),
            entry("uSkyLight", 26));

    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private int defaultTexUnit;
    private IDhApiConfig configs;
    private IDhApiRenderProxy renderProxy;
    private UmbraRenderingPipeline pipeline;
    private UmbraShadowRenderer shadowRenderer;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void freshDh() {
        Minecraft client = Mc.client();
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 320;
        client.displayHeight = 240;
        // No lightmap texture yet, so the LOD programs bind the missing texture there
        client.entityRenderer = Mc.uninitialized(EntityRenderer.class);
        defaultTexUnit = OpenGlHelper.defaultTexUnit;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x8CDF)).thenReturn(8);
        when(TestGl.gl().glGetUniformLocation(anyInt(), any(CharSequence.class)))
                .thenAnswer(invocation -> LOCATIONS.getOrDefault(invocation.getArgument(1).toString(), -1));

        configs = mock(IDhApiConfig.class, Mockito.RETURNS_DEEP_STUBS);
        when(configs.graphics().renderingEnabled().getValue()).thenReturn(true);
        when(configs.graphics().chunkRenderDistance().getValue()).thenReturn(32);
        renderProxy = mock(IDhApiRenderProxy.class);
        when(renderProxy.getNearClipPlaneDistanceInBlocks(anyFloat())).thenReturn(24.0F);
        when(renderProxy.getDhDepthTextureId()).thenReturn(DhApiResult.createSuccess(55));
        DhApi.Delayed.configs = configs;
        DhApi.Delayed.renderProxy = renderProxy;

        pipeline = mock(UmbraRenderingPipeline.class);
        shadowRenderer = mock(UmbraShadowRenderer.class);
        when(pipeline.createDhFramebuffer(any(), anyBoolean())).thenAnswer(invocation -> new UmbraFramebuffer());
        when(pipeline.getShadowRenderer()).thenReturn(shadowRenderer);
        UmbraFramebuffer shadowMap = new UmbraFramebuffer();
        when(shadowRenderer.getFramebuffer()).thenReturn(shadowMap);
        when(shadowRenderer.getCullingFrustum()).thenReturn(new ShadowBoxCuller(64));
    }

    @AfterEach
    void forgetDh() {
        gl13.close();
        gl11.close();
        OpenGlHelper.defaultTexUnit = defaultTexUnit;
        DhApi.Delayed.configs = null;
        DhApi.Delayed.renderProxy = null;
        ApiEventInjector.INSTANCE.clear();
        OverrideInjector.INSTANCE.clear();
        ModAccessorInjector.INSTANCE.clear();
        Statics.set(DhCompat.class, "dhPresent", false);
        Statics.set(DhCompat.class, "lastIncompatible", false);
        Statics.set(LodRendererEvents.class, "eventHandlersBound", false);
        Statics.set(LodRendererEvents.class, "atTranslucent", false);
        Statics.set(DhCompatInternal.class, "clientApiResolved", false);
        Statics.set(DhCompatInternal.class, "blockAtlasResolved", false);
        DhCompatInternal.dhEnabled = false;
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(Umbra.class, "pipelineNeedsInit", false);
        ClientApi.lods = 0;
        ClientApi.deferredLods = 0;
        ClientApi.fail = false;
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f());
        CapturedRenderingState.INSTANCE.setCameraPosition(0, 0, 0);
    }

    // A pack with the named programs; dh_water is written in the modern style, the rest legacy
    private static ShaderPack pack(String properties, String... programs) {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, properties);
        for (String program : programs) {
            boolean modern = "dh_water".equals(program);
            sources.put(AbsolutePackPath.fromAbsolutePath("/" + program + ".vsh"), modern ? MODERN_VERTEX : LEGACY_VERTEX);
            sources.put(AbsolutePackPath.fromAbsolutePath("/" + program + ".fsh"), modern ? MODERN_FRAGMENT : LEGACY_FRAGMENT);
        }
        return new ShaderPack(sources);
    }

    private static ShaderPack everyDhProgram() {
        return pack(String.join("\n",
                        "dhClouds = off",
                        "alphaTest.dh_terrain = GREATER 0.1",
                        "blend.dh_water = SRC_ALPHA ONE_MINUS_SRC_ALPHA"),
                "dh_terrain", "dh_water", "dh_shadow", "dh_generic");
    }

    private static DhApiCancelableEventParam<DhApiRenderParam> pass(EDhApiRenderPass renderPass) {
        DhApiRenderParam param = new DhApiRenderParam();
        param.renderPass = renderPass;
        param.nearClipPlane = 24.0F;
        param.farClipPlane = 1000.0F;
        param.worldYOffset = -64;
        param.partialTicks = 0.5F;
        return new DhApiCancelableEventParam<>(param);
    }

    private static DhApiEventParam<DhApiBeforeBufferRenderEvent.EventParam> buffer(float x, float y, float z) {
        DhApiBeforeBufferRenderEvent.EventParam param = new DhApiBeforeBufferRenderEvent.EventParam();
        param.modelPos = new DhApiVec3f(x, y, z);
        return new DhApiEventParam<>(param);
    }

    private static DhApiCancelableEventParam<DhApiBeforeGenericObjectRenderEvent.EventParam> genericObject(String path) {
        DhApiBeforeGenericObjectRenderEvent.EventParam param = new DhApiBeforeGenericObjectRenderEvent.EventParam();
        param.resourceLocationPath = path;
        return new DhApiCancelableEventParam<>(param);
    }

    private static <T extends com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent> T handler(Class<T> event, int index) {
        return ApiEventInjector.INSTANCE.getAll(event).get(index);
    }

    @Test
    void withoutDhEveryAnswerIsTheVanillaOne() {
        Mc.forge();
        DhCompat.run();
        assertFalse(DhCompat.isPresent());
        assertFalse(DhCompat.lastPackIncompatible());
        assertEquals(0.01F, DhCompat.getFarPlane());
        assertEquals(0.01F, DhCompat.getNearPlane());
        assertEquals(128, DhCompat.getRenderDistance());
        assertFalse(DhCompat.checkFrame());
        assertFalse(DhCompat.hasRenderingEnabled());
        Matrix4f projection = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 256.0F);
        CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
        assertEquals(projection, DhCompat.getProjection());
        DhCompat.renderShadowSolid();
        DhCompat.renderDeferredLods();
        assertEquals(0, ClientApi.lods);

        DhCompat compat = new DhCompat(pipeline, pack(""), true);
        assertNull(compat.getInstance());
        assertFalse(compat.shouldRenderShadows());
        compat.clearPipeline();
        assertEquals(-1, compat.getDepthTex());
        assertEquals(-1, compat.getDepthTexNoTranslucent());
    }

    @Test
    void theStaticsFollowDhsConfigAndRenderProxy() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        assertTrue(DhCompat.isPresent());
        // Only the after-init handler is queued until DH finishes setting up, and only once
        LodRendererEvents.setupEventHandlers();
        assertEquals(1, ApiEventInjector.INSTANCE.getAll(DhApiAfterDhInitEvent.class).size());
        assertNotNull(Mixins.construct(LodRendererEvents.class));

        assertEquals((float) ((32 * 16 + 512) * Math.sqrt(2)), DhCompat.getFarPlane());
        assertEquals(24.0F, DhCompat.getNearPlane());
        // Until DH reports rendering on, the uniform falls back to the player's own distance
        assertEquals(128, DhCompat.getRenderDistance());
        assertFalse(DhCompat.hasRenderingEnabled());
        // With no pack in use there is nothing to reload, so the toggle is not taken up yet
        assertFalse(DhCompat.checkFrame());
        Mixins.set(Umbra.class, "currentPack", pack(""));
        assertTrue(DhCompat.checkFrame());
        assertTrue(DhCompat.hasRenderingEnabled());
        // The reload found no config to load from, so the pack is gone
        assertNull(Umbra.getCurrentPack());
        assertEquals(512, DhCompat.getRenderDistance());
        assertTrue(DhCompat.checkFrame());

        // The LOD projection keeps the camera's field of view and aspect with DH's planes
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 256.0F));
        Matrix4f lod = DhCompat.getProjection();
        assertEquals(1.2F, lod.perspectiveFov(), 1e-4F);
        assertEquals(24.0F, lod.perspectiveNear(), 1e-2F);
        assertEquals(DhCompat.getFarPlane(), lod.perspectiveFar(), 1.0F);

        // Before DH's config and render proxy exist every plane is zero and the toggle stays where it was
        DhApi.Delayed.configs = null;
        DhApi.Delayed.renderProxy = null;
        assertEquals(0.0F, DhCompatInternal.getFarPlane());
        assertEquals(0.0F, DhCompatInternal.getNearPlane());
        assertEquals(128, DhCompatInternal.getRenderDistance());
        assertTrue(DhCompatInternal.checkFrame());
        // API 7.0 has no block atlas getter, which is looked for once
        assertEquals(-1, DhCompatInternal.getBlockAtlasTextureId());
        assertEquals(-1, DhCompatInternal.getBlockAtlasTextureId());
    }

    @Test
    void theShadowPassDrawsThroughDhsOwnEntryPoints() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        // While DH is not rendering the shadow pass leaves it alone
        DhCompat.renderShadowSolid();
        DhCompat.renderDeferredLods();
        assertEquals(0, ClientApi.lods);
        DhCompatInternal.dhEnabled = true;
        DhCompat.renderShadowSolid();
        DhCompat.renderDeferredLods();
        DhCompat.renderDeferredLods();
        assertEquals(1, ClientApi.lods);
        assertEquals(2, ClientApi.deferredLods);
        // A failure inside DH surfaces rather than silently dropping the LOD shadows
        ClientApi.fail = true;
        assertThrows(RuntimeException.class, DhCompat::renderShadowSolid);
        assertThrows(RuntimeException.class, DhCompat::renderDeferredLods);
    }

    @Test
    void impetusRegistersAsDhsShaderModWhenDhInitialises() {
        // Without DH nothing is queued
        Mc.forge();
        DhCompat.registerIrisAccessor();
        assertNull(ApiEventInjector.INSTANCE.get(DhApiBeforeDhInitEvent.class));

        // With it the accessor is bound as DH starts its init, ahead of the point DH reads it
        Mc.forge("distanthorizons");
        DhCompat.registerIrisAccessor();
        assertNull(ModAccessorInjector.INSTANCE.get(IIrisAccessor.class));
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
        IIrisAccessor accessor = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        assertNotNull(accessor);
        assertEquals("Impetus", accessor.getModName());
        assertTrue(accessor.getDelayedSetupComplete());
        accessor.finishDelayedSetup();
        assertFalse(accessor.isReverseZDuringShaders());
        // An accessor already in place (Actinium's, or this one) is left alone rather than bound twice
        ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
        assertEquals(1, ModAccessorInjector.INSTANCE.getAll(IIrisAccessor.class).size());

        // It answers from Umbra's live state
        assertFalse(accessor.isShaderPackInUse());
        Mixins.set(Umbra.class, "currentPack", pack(""));
        assertTrue(accessor.isShaderPackInUse());
        assertFalse(accessor.isRenderingShadowPass());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertTrue(accessor.isRenderingShadowPass());

        // The depth texture DH's framebuffer mixin made, and -1 for a framebuffer it never patched
        IFramebufferDepthTexture patched = mock(IFramebufferDepthTexture.class);
        when(patched.distantHorizons$getDistantHorizonsDepthTexture()).thenReturn(42);
        assertEquals(42, accessor.getFramebufferDepthTextureId(patched));
        assertEquals(-1, accessor.getFramebufferDepthTextureId(new Object()));

        // It is its own identity
        assertEquals(accessor, accessor);
        assertNotEquals(accessor, new Object());
        assertEquals(System.identityHashCode(accessor), accessor.hashCode());
        assertEquals("Impetus IIrisAccessor", accessor.toString());
    }

    @Test
    void packsWithoutUsableDhProgramsLeaveDhItsOwnShaders() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        DhCompatInternal.dhEnabled = true;
        DhCompat none = new DhCompat(pipeline, pack(""), true);
        DhCompatInternal internal = (DhCompatInternal) none.getInstance();
        assertTrue(internal.incompatiblePack());
        assertTrue(DhCompat.lastPackIncompatible());
        assertFalse(internal.shouldOverride());
        assertFalse(none.shouldRenderShadows());
        assertEquals(-1, none.getDepthTex());
        assertEquals(0, none.getDepthTexNoTranslucent());
        internal.copyTranslucents(320, 240);
        internal.reconnectDHTextures(55, 0, 0);
        assertEquals(-1, internal.getStoredDepthTex());
        none.clearPipeline();
        verify(TestGl.gl(), never()).glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        // A dh_* program that fails to build is dropped along with whatever it had made
        when(pipeline.createDhFramebuffer(any(), anyBoolean())).thenThrow(new IllegalStateException("incomplete"));
        DhCompatInternal broken = new DhCompatInternal(pipeline, pack("", "dh_terrain"), true);
        assertTrue(broken.incompatiblePack());
        assertNull(broken.getSolidShader());
        assertEquals(0, broken.getDepthTexNoTranslucent());
        // A stage the pack never shipped cannot be bridged
        ShaderPack empty = pack("");
        assertThrows(IllegalStateException.class, () -> DhRenderProgram.transform(empty,
                new ProgramSource("dh_terrain", null, null, null, null, LEGACY_FRAGMENT, null), false));
        assertThrows(IllegalStateException.class, () -> DhRenderProgram.transform(empty,
                new ProgramSource("dh_terrain", LEGACY_VERTEX, null, null, null, null, null), false));

        // With DH's rendering off, before its config exists, or without a pipeline, nothing is built or flagged
        when(configs.graphics().renderingEnabled().getValue()).thenReturn(false);
        assertFalse(new DhCompatInternal(pipeline, pack("", "dh_terrain"), true).incompatiblePack());
        DhApi.Delayed.configs = null;
        assertFalse(new DhCompatInternal(pipeline, pack("", "dh_terrain"), true).incompatiblePack());
        assertFalse(new DhCompatInternal(null, null, false).shouldOverride());
        assertFalse(DhCompatInternal.SHADERLESS.shouldOverrideShadow());
        assertNull(DhCompatInternal.SHADERLESS.getPipeline());
    }

    @Test
    void aBrokenPackFailsTheWayOtherProgramsDo() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        // A compile failure fails the pack as a whole, anything else is wrapped with its cause
        ShaderPack failing = mock(ShaderPack.class);
        when(failing.getProgramSet()).thenThrow(new ShaderCompileException("dh_terrain.fsh", "syntax error"));
        assertThrows(ShaderCompileException.class, () -> new DhCompat(pipeline, failing, true));
        ShaderPack odd = mock(ShaderPack.class);
        when(odd.getProgramSet()).thenThrow(new IllegalArgumentException("odd"));
        RuntimeException wrapped = assertThrows(RuntimeException.class, () -> new DhCompat(pipeline, odd, true));
        assertInstanceOf(IllegalArgumentException.class, wrapped.getCause());
        assertFalse(DhCompat.lastPackIncompatible());
    }

    @Test
    void aDhPackBuildsEveryLodProgramAndFramebuffer() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        DhCompatInternal.dhEnabled = true;
        DhCompat compat = new DhCompat(pipeline, everyDhProgram(), true);
        DhCompatInternal internal = (DhCompatInternal) compat.getInstance();
        assertFalse(internal.incompatiblePack());
        assertFalse(DhCompat.lastPackIncompatible());
        assertTrue(internal.shouldOverride());
        assertTrue(internal.shouldOverrideShadow());
        assertTrue(compat.shouldRenderShadows());
        assertTrue(internal.avoidRenderingClouds());
        assertSame(pipeline, internal.getPipeline());
        DhLodRenderProgram solid = internal.getSolidShader();
        DhLodRenderProgram water = internal.getTranslucentShader();
        assertEquals("dh_terrain", solid.getName());
        assertFalse(solid.isTranslucent());
        assertEquals("dh_water", water.getName());
        assertTrue(water.isTranslucent());
        assertArrayEquals(new int[] {0, 2}, solid.getDrawBuffers());
        assertArrayEquals(new int[] {0}, water.getDrawBuffers());
        assertNotNull(water.getBlendState());
        assertSame(ShadowSamplerKinds.ALL_COMPARE, solid.getShadowSamplerKinds());
        assertEquals("dh_shadow", internal.getShadowShader().getName());
        assertNotNull(internal.getSolidFB());
        assertNotNull(internal.getTranslucentFB());
        assertNotNull(internal.getGenericFB());
        assertNotNull(internal.getSolidFBWrapper());
        assertNotNull(internal.getShadowFBWrapper());
        assertNotNull(internal.getShadowCullingFrustum());
        // The atlas sampler is pointed at unit 0 once, at link
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUniform1i(7, 0);

        // DH's depth texture is attached once per id, and the pre-translucent copy follows DH's texture size
        int initialCopy = compat.getDepthTexNoTranslucent();
        internal.reconnectDHTextures(55, 640, 480);
        assertEquals(55, compat.getDepthTex());
        assertNotEquals(initialCopy, compat.getDepthTexNoTranslucent());
        internal.reconnectDHTextures(55, 640, 480);
        internal.reconnectDHTextures(56, 0, 0);
        assertEquals(56, compat.getDepthTex());
        internal.copyTranslucents(800, 300);
        verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 640, 300);

        // The LOD programs take DH's pass matrices, offsets and near clip
        Matrix4f projection = new Matrix4f().perspective(1.2F, 1.5F, 24.0F, 1000.0F);
        solid.fillUniformData(projection, new Matrix4f().translate(1, 2, 3), 5, 0.5F);
        verify(TestGl.gl()).glUniform1f(13, 0.01F);
        verify(TestGl.gl()).glUniform1f(12, 5.0F);
        verify(TestGl.gl()).glUniform1f(14, 24.0F);
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUniformMatrix4fv(eq(1), eq(false), any(FloatBuffer.class));
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUniformMatrix3fv(eq(5), eq(false), any(FloatBuffer.class));
        solid.setModelPos(new DhApiVec3f(1, 2, 3));
        verify(TestGl.gl()).glUniform3f(11, 1, 2, 3);
        // Without DH's render proxy the near clip is left as it was
        DhApi.Delayed.renderProxy = null;
        water.fillUniformData(projection, new Matrix4f(), 0, 0.5F);
        verify(TestGl.gl(), times(1)).glUniform1f(eq(14), anyFloat());
        solid.bind();
        solid.unbind();
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUseProgram(0);

        // dh_generic draws DH's instanced boxes, and only while a pipeline is live
        DhGenericRenderProgram generic = (DhGenericRenderProgram) internal.getGenericShader();
        assertArrayEquals(new int[] {0, 2}, generic.getDrawBuffers());
        assertFalse(generic.overrideThisFrame());
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        assertTrue(generic.overrideThisFrame());
        assertTrue(generic.getId() > 0);
        DhApiRenderParam param = new DhApiRenderParam();
        generic.bind(param);
        generic.bindVertexBuffer(9);
        verify(TestGl.gl()).glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        IDhApiRenderableBoxGroup group = mock(IDhApiRenderableBoxGroup.class);
        when(group.getOriginBlockPos()).thenReturn(new DhApiVec3d(40, -20, 7));
        when(group.getBlockLight()).thenReturn(3);
        when(group.getSkyLight()).thenReturn(15);
        generic.fillIndirectUniformData(param, null, group, new DhApiVec3d(-17, 64, 3.5));
        // Positions split into whole chunks and the remainder, for precision far from the origin
        verify(TestGl.gl()).glUniform3i(21, 2, -2, 0);
        verify(TestGl.gl()).glUniform3f(22, 8, 12, 7);
        verify(TestGl.gl()).glUniform3i(23, -2, 4, 0);
        verify(TestGl.gl()).glUniform3f(24, 15, 0, 3.5F);
        verify(TestGl.gl()).glUniform1i(25, 3);
        verify(TestGl.gl()).glUniform1i(26, 15);
        assertThrows(IllegalStateException.class, () -> generic.fillSharedDirectUniformData(param, null, group, null));
        assertThrows(IllegalStateException.class, () -> generic.fillDirectUniformData(param, group, null, null));
        generic.unbind();
        verify(TestGl.gl(), Mockito.atLeastOnce()).glBindVertexArray(0);

        // Teardown frees every program and drops the overrides DH might still call
        compat.clearPipeline();
        assertNull(internal.getSolidShader());
        assertNull(internal.getShadowShader());
        assertNull(internal.getGenericShader());
        assertNull(internal.getSolidFB());
        assertNull(internal.getTranslucentFB());
        assertNull(internal.getGenericFB());
        assertNull(internal.getSolidFBWrapper());
        assertNull(internal.getShadowFBWrapper());
        assertNull(internal.getShadowCullingFrustum());
        assertEquals(-1, internal.getStoredDepthTex());
        assertEquals(0, internal.getDepthTexNoTranslucent());
        verify(TestGl.gl()).glDeleteVertexArrays(anyInt());
    }

    @Test
    void theLodProgramsFallBackTheWayTheProgramSetDoes() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        // Only dh_water: it stands in for the terrain, clouds follow the pack's vanilla setting, and no shadow renderer means no LOD shadows
        when(pipeline.getShadowRenderer()).thenReturn(null);
        DhCompatInternal waterOnly = new DhCompatInternal(pipeline, pack("clouds = off", "dh_water", "dh_shadow"), true);
        assertEquals("dh_water", waterOnly.getSolidShader().getName());
        assertTrue(waterOnly.getTranslucentShader().isTranslucent());
        assertTrue(waterOnly.avoidRenderingClouds());
        assertFalse(waterOnly.shouldOverrideShadow());
        assertNull(waterOnly.getGenericShader());
        waterOnly.clear();

        // dh_terrain alone serves water and generic too; dhShadow.enabled=false keeps LOD shadows off and fancy DH clouds stay
        when(pipeline.getShadowRenderer()).thenReturn(shadowRenderer);
        DhCompatInternal terrainOnly = new DhCompatInternal(pipeline,
                pack("dhClouds = fancy\nclouds = off", "dh_terrain", "dh_shadow"), false);
        assertFalse(terrainOnly.shouldOverrideShadow());
        assertFalse(terrainOnly.avoidRenderingClouds());
        assertNotNull(terrainOnly.getGenericShader());
        assertEquals("dh_terrain", terrainOnly.getTranslucentShader().getName());
        terrainOnly.clear();
        DhCompatInternal plain = new DhCompatInternal(pipeline, pack("", "dh_terrain"), true);
        assertFalse(plain.avoidRenderingClouds());
        plain.clear();
    }

    @Test
    void theWrapperHandsDhThePipelinesFramebufferAndTheFrustumItsCulling() {
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        DhFramebufferWrapper wrapper = new DhFramebufferWrapper(framebuffer);
        assertTrue(wrapper.overrideThisFrame());
        wrapper.bind();
        // DH's attachment calls are ignored: the compat attaches what belongs there itself
        wrapper.addDepthAttachment(4, true);
        wrapper.addColorAttachment(0, 5);
        wrapper.destroy();
        verify(TestGl.gl(), never()).glFramebufferTexture2D(anyInt(), anyInt(), anyInt(), eq(4), anyInt());
        assertEquals(framebuffer.getGlId(), wrapper.getId());
        assertEquals(0x8CD5, wrapper.getStatus());

        DhShadowCullingFrustum frustum = new DhShadowCullingFrustum(shadowRenderer);
        frustum.update(-64, 320, new DhApiMat4f());
        CapturedRenderingState.INSTANCE.setCameraPosition(1000, 70, 1000);
        // Columns are tested camera-relative over the world's whole height
        assertTrue(frustum.intersects(990, 990, 32, 0));
        assertFalse(frustum.intersects(2000, 2000, 32, 0));
        when(shadowRenderer.getCullingFrustum()).thenReturn(null);
        assertTrue(frustum.intersects(2000, 2000, 32, 0));
    }

    @Test
    void theEventHandlersHandDhsPassesToThePack() {
        Mc.forge("distanthorizons");
        DhCompat.run();
        handler(DhApiAfterDhInitEvent.class, 0).afterDistantHorizonsInit(new DhApiEventParam<>(null));
        assertTrue(DhCompat.hasRenderingEnabled());
        DhApiBeforeRenderEvent deferSetup = handler(DhApiBeforeRenderEvent.class, 0);
        DhApiBeforeRenderEvent shadowCancel = handler(DhApiBeforeRenderEvent.class, 1);
        DhApiBeforeTextureClearEvent reconnect = handler(DhApiBeforeTextureClearEvent.class, 0);
        DhApiBeforeTextureClearEvent clear = handler(DhApiBeforeTextureClearEvent.class, 1);
        DhApiBeforeGenericRenderSetupEvent genericSetup = handler(DhApiBeforeGenericRenderSetupEvent.class, 0);
        DhApiBeforeGenericObjectRenderEvent genericObject = handler(DhApiBeforeGenericObjectRenderEvent.class, 0);
        DhApiColorDepthTextureCreatedEvent resize = handler(DhApiColorDepthTextureCreatedEvent.class, 0);
        DhApiBeforeDeferredRenderEvent deferredCancel = handler(DhApiBeforeDeferredRenderEvent.class, 0);
        DhApiBeforeRenderCleanupEvent cleanup = handler(DhApiBeforeRenderCleanupEvent.class, 0);
        DhApiBeforeBufferRenderEvent bufferRender = handler(DhApiBeforeBufferRenderEvent.class, 0);
        DhApiBeforeRenderSetupEvent setup = handler(DhApiBeforeRenderSetupEvent.class, 0);
        DhApiBeforeRenderPassEvent renderPass = handler(DhApiBeforeRenderPassEvent.class, 0);
        DhApiBeforeApplyShaderRenderEvent applyShader = handler(DhApiBeforeApplyShaderRenderEvent.class, 0);

        // Shaders off: DH renders exactly as it would without Impetus
        DhApiCancelableEventParam<DhApiRenderParam> frame = pass(EDhApiRenderPass.OPAQUE);
        deferSetup.beforeRender(frame);
        shadowCancel.beforeRender(frame);
        assertFalse(frame.isEventCanceled());
        verify(renderProxy).setDeferTransparentRendering(false);
        verify(configs.graphics().fog().drawMode()).clearValue();
        DhApiCancelableEventParam<DhApiRenderParam> ownClear = pass(EDhApiRenderPass.OPAQUE);
        clear.beforeClear(ownClear);
        assertFalse(ownClear.isEventCanceled());
        setup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        assertNull(OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class));
        renderPass.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        renderPass.beforeRender(pass(EDhApiRenderPass.TRANSPARENT));
        verify(configs.graphics().ambientOcclusion().enabled(), times(2)).clearValue();
        cleanup.beforeCleanup(pass(EDhApiRenderPass.OPAQUE));
        bufferRender.beforeRender(buffer(1, 2, 3));
        genericSetup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        DhApiCancelableEventParam<DhApiRenderParam> ownApply = pass(EDhApiRenderPass.OPAQUE);
        applyShader.beforeRender(ownApply);
        assertFalse(ownApply.isEventCanceled());
        // A pipeline without DH state, or whose compat never bound, is treated the same
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        deferSetup.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        when(pipeline.getDhCompat()).thenReturn(mock(DhCompat.class));
        deferSetup.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        verify(renderProxy, times(3)).setDeferTransparentRendering(false);
        // ...and with no dh_shadow the LODs stay out of the shadow map, nothing cleared or drawn there
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        DhApiCancelableEventParam<DhApiRenderParam> shadowFrame = pass(EDhApiRenderPass.OPAQUE);
        shadowCancel.beforeRender(shadowFrame);
        assertTrue(shadowFrame.isEventCanceled());
        DhApiCancelableEventParam<DhApiRenderParam> shadowDeferred = pass(EDhApiRenderPass.TRANSPARENT);
        deferredCancel.beforeRender(shadowDeferred);
        assertTrue(shadowDeferred.isEventCanceled());
        DhApiCancelableEventParam<DhApiRenderParam> shadowClear = pass(EDhApiRenderPass.OPAQUE);
        clear.beforeClear(shadowClear);
        assertTrue(shadowClear.isEventCanceled());
        renderPass.beforeRender(pass(EDhApiRenderPass.TRANSPARENT));
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);

        // A pack shading LODs
        DhCompat compat = new DhCompat(pipeline, everyDhProgram(), true);
        DhCompatInternal internal = (DhCompatInternal) compat.getInstance();
        when(pipeline.getDhCompat()).thenReturn(compat);

        // DH's texture size and depth id reach the LOD framebuffers before its clear
        resize.onResize(new DhApiEventParam<>(new DhApiColorDepthTextureCreatedEvent.EventParam(0, 0, 640, 480)));
        reconnect.beforeClear(pass(EDhApiRenderPass.OPAQUE));
        assertEquals(55, internal.getStoredDepthTex());
        when(renderProxy.getDhDepthTextureId()).thenReturn(DhApiResult.createFail("not yet"));
        reconnect.beforeClear(pass(EDhApiRenderPass.OPAQUE));
        assertEquals(55, internal.getStoredDepthTex());

        // Before DH's frame: its water defers and its fog is off
        DhApiCancelableEventParam<DhApiRenderParam> packFrame = pass(EDhApiRenderPass.OPAQUE);
        deferSetup.beforeRender(packFrame);
        shadowCancel.beforeRender(packFrame);
        assertFalse(packFrame.isEventCanceled());
        verify(renderProxy).setDeferTransparentRendering(true);
        verify(configs.graphics().fog().drawMode()).setValue(EDhApiFogDrawMode.FOG_DISABLED);

        // The camera pass: the LOD gbuffer and dh_generic become DH's overrides, rebinding without a clash
        setup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        setup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        assertSame(internal.getSolidFBWrapper(), OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class));
        assertSame(internal.getGenericShader(), OverrideInjector.INSTANCE.get(IDhApiGenericObjectShaderProgram.class));
        assertNull(OverrideInjector.INSTANCE.get(IDhApiShadowCullingFrustum.class));

        // Only DH's own depth is cleared, forward-Z, and only at the opaque pass
        DhApiCancelableEventParam<DhApiRenderParam> depthClear = pass(EDhApiRenderPass.OPAQUE);
        clear.beforeClear(depthClear);
        assertTrue(depthClear.isEventCanceled());
        verify(TestGl.gl()).glClear(GL11.GL_DEPTH_BUFFER_BIT);
        DhApiCancelableEventParam<DhApiRenderParam> translucentClear = pass(EDhApiRenderPass.TRANSPARENT);
        clear.beforeClear(translucentClear);
        assertFalse(translucentClear.isEventCanceled());

        // The opaque pass: dh_terrain with DH's matrices into the LOD gbuffer, the model offset per buffer
        renderPass.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        verify(configs.graphics().ambientOcclusion().enabled()).setValue(false);
        DhLodRenderProgram solid = internal.getSolidShader();
        verify(pipeline).onDhLodDraw(internal.getSolidFB(), solid.getDrawBuffers(), solid.getBlendState(), false);
        bufferRender.beforeRender(buffer(1, 2, 3));
        verify(TestGl.gl()).glUniform3f(11, 1, 2, 3);
        cleanup.beforeCleanup(pass(EDhApiRenderPass.OPAQUE));
        verify(pipeline).afterDhLodDraw(2);

        // The deferred translucent pass: dhDepthTex1 is snapshotted, then dh_water draws blended
        renderPass.beforeRender(pass(EDhApiRenderPass.TRANSPARENT));
        verify(TestGl.gl()).glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 640, 480);
        DhLodRenderProgram water = internal.getTranslucentShader();
        verify(pipeline).onDhLodDraw(internal.getTranslucentFB(), water.getDrawBuffers(), water.getBlendState(), true);
        bufferRender.beforeRender(buffer(4, 5, 6));
        verify(TestGl.gl()).glUniform3f(11, 4, 5, 6);
        cleanup.beforeCleanup(pass(EDhApiRenderPass.TRANSPARENT));
        verify(pipeline).afterDhLodDraw(1);
        // DH's combined pass should never run with shaders on, and is only reported
        renderPass.beforeRender(pass(EDhApiRenderPass.OPAQUE_AND_TRANSPARENT));

        // DH's generic objects draw with dh_generic into their own framebuffer; its clouds are dropped for this pack
        genericSetup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        verify(pipeline).onDhLodDraw(eq(internal.getGenericFB()), any(), any(), eq(true));
        DhApiCancelableEventParam<DhApiBeforeGenericObjectRenderEvent.EventParam> clouds = genericObject("Clouds");
        genericObject.beforeRender(clouds);
        assertTrue(clouds.isEventCanceled());
        DhApiCancelableEventParam<DhApiBeforeGenericObjectRenderEvent.EventParam> beacon = genericObject("beacon_beam");
        genericObject.beforeRender(beacon);
        assertFalse(beacon.isEventCanceled());

        // The shadow pass: dh_shadow into the shadow map, culled by the shadow frustum, nothing cleared
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        DhApiCancelableEventParam<DhApiRenderParam> lodShadows = pass(EDhApiRenderPass.OPAQUE);
        shadowCancel.beforeRender(lodShadows);
        assertFalse(lodShadows.isEventCanceled());
        DhApiCancelableEventParam<DhApiRenderParam> lodShadowsDeferred = pass(EDhApiRenderPass.TRANSPARENT);
        deferredCancel.beforeRender(lodShadowsDeferred);
        assertFalse(lodShadowsDeferred.isEventCanceled());
        setup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        assertSame(internal.getShadowFBWrapper(), OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class));
        assertSame(internal.getShadowCullingFrustum(), OverrideInjector.INSTANCE.get(IDhApiShadowCullingFrustum.class));
        DhApiCancelableEventParam<DhApiRenderParam> mapClear = pass(EDhApiRenderPass.OPAQUE);
        clear.beforeClear(mapClear);
        assertTrue(mapClear.isEventCanceled());
        renderPass.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        bufferRender.beforeRender(buffer(7, 8, 9));
        verify(TestGl.gl()).glUniform3f(11, 7, 8, 9);
        renderPass.beforeRender(pass(EDhApiRenderPass.TRANSPARENT));
        cleanup.beforeCleanup(pass(EDhApiRenderPass.OPAQUE));
        // The generic setup inside the shadow pass leaves the gbuffer alone
        genericSetup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        verify(pipeline, times(1)).onDhLodDraw(eq(internal.getGenericFB()), any(), any(), anyBoolean());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);

        // DH's own composite onto MC's framebuffer is cancelled, and the overrides dropped until the next pass
        DhApiCancelableEventParam<DhApiRenderParam> apply = pass(EDhApiRenderPass.OPAQUE);
        applyShader.beforeRender(apply);
        assertTrue(apply.isEventCanceled());
        assertNull(OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class));
        assertNull(OverrideInjector.INSTANCE.get(IDhApiShadowCullingFrustum.class));
        assertNull(OverrideInjector.INSTANCE.get(IDhApiGenericObjectShaderProgram.class));

        // A pack without dh_shadow: the shadow pass binds nothing of its own and draws nothing
        DhCompat noShadow = new DhCompat(pipeline, pack("", "dh_terrain"), true);
        when(pipeline.getDhCompat()).thenReturn(noShadow);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        setup.beforeSetup(pass(EDhApiRenderPass.OPAQUE));
        assertSame(((DhCompatInternal) noShadow.getInstance()).getSolidFBWrapper(), OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class));
        renderPass.beforeRender(pass(EDhApiRenderPass.OPAQUE));
        renderPass.beforeRender(pass(EDhApiRenderPass.TRANSPARENT));
        bufferRender.beforeRender(buffer(10, 11, 12));
        verify(TestGl.gl(), never()).glUniform3f(11, 10, 11, 12);
        cleanup.beforeCleanup(pass(EDhApiRenderPass.OPAQUE));
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        applyShader.beforeRender(pass(EDhApiRenderPass.OPAQUE));

        // Shaders off again: the composite is DH's own
        Mixins.set(Umbra.class, "renderingPipeline", null);
        DhApiCancelableEventParam<DhApiRenderParam> own = pass(EDhApiRenderPass.OPAQUE);
        applyShader.beforeRender(own);
        assertFalse(own.isEventCanceled());
        compat.clearPipeline();
        noShadow.clearPipeline();
    }
}
