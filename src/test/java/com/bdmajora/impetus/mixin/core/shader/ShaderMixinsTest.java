package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.CloudPassState;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.DeferredBlockOutline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

class ShaderMixinsTest {
    private static final float[] EYES = {0.0F, 0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0625F};

    private Minecraft client;
    private GameSettings settings;
    private UmbraRenderingPipeline pipeline;
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private int defaultTexUnit;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void noPack() {
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        when(client.getTextureManager()).thenReturn(mock(TextureManager.class));
        pipeline = mock(UmbraRenderingPipeline.class);
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        defaultTexUnit = OpenGlHelper.defaultTexUnit;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        Mixins.set(Extras.class, "config", new ExtrasConfig());
        Mixins.set(Umbra.class, "pipelineNeedsInit", false);
    }

    @AfterEach
    void forgetPack() {
        gl13.close();
        gl11.close();
        OpenGlHelper.defaultTexUnit = defaultTexUnit;
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(Umbra.class, "pipelineNeedsInit", false);
        Mixins.set(Extras.class, "config", null);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        Statics.set(DeferredBlockOutline.class, "replaying", false);
        DeferredBlockOutline.discard();
        ShadowStubs.clear();
        CelestialUniforms.setSunPathRotation(0.0F);
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.resetEntityColor();
        state.setRenderStage(0);
        state.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void withPipeline() {
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
    }

    private static void pack(String properties) {
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, properties)));
    }

    // Runs a CallbackInfo handler with the given leading arguments and reports whether it cancelled
    private static boolean cancels(Object mixin, String handler, Object... leading) {
        CallbackInfo ci = Mixins.ci();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = ci;
        Mixins.call(mixin, handler, args);
        return ci.isCancelled();
    }

    // Same for a CallbackInfoReturnable handler, handing back what it set
    private static <R> CallbackInfoReturnable<R> returned(Object mixin, String handler, Object... leading) {
        CallbackInfoReturnable<R> cir = Mixins.cir();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = cir;
        Mixins.call(mixin, handler, args);
        return cir;
    }

    private static Object[] eyes(Object entity) {
        Object[] args = new Object[EYES.length + 1];
        args[0] = entity;
        for (int i = 0; i < EYES.length; i++) {
            args[i + 1] = EYES[i];
        }
        return args;
    }

    @Test
    void eyesAndGlintBracketTheirDraws() {
        LayerSpiderEyesMixin spider = Mixins.instance(LayerSpiderEyesMixin.class);
        LayerEndermanEyesMixin enderman = Mixins.instance(LayerEndermanEyesMixin.class);
        LayerEnderDragonEyesMixin dragon = Mixins.instance(LayerEnderDragonEyesMixin.class);
        RenderItemGlintMixin item = Mixins.instance(RenderItemGlintMixin.class);
        // Without a pack each bracket is a no-op
        cancels(spider, "impetus$beginEyes", eyes(null));
        cancels(LayerArmorBaseGlintMixin.class, "impetus$beginArmorGlint", null, null, null, 0F, 0F, 0F, 0F, 0F, 0F, 0F);
        withPipeline();
        for (Object layer : List.of(spider, enderman, dragon)) {
            cancels(layer, "impetus$beginEyes", eyes(null));
            cancels(layer, "impetus$endEyes", eyes(null));
        }
        verify(pipeline, times(3)).beginEyes();
        verify(pipeline, times(3)).endEyes();
        cancels(LayerArmorBaseGlintMixin.class, "impetus$beginArmorGlint", null, null, null, 0F, 0F, 0F, 0F, 0F, 0F, 0F);
        cancels(LayerArmorBaseGlintMixin.class, "impetus$endArmorGlint", null, null, null, 0F, 0F, 0F, 0F, 0F, 0F, 0F);
        cancels(item, "impetus$beginItemGlint", (Object) null);
        cancels(item, "impetus$endItemGlint", (Object) null);
        verify(pipeline, times(2)).beginArmorGlint();
        verify(pipeline, times(2)).endArmorGlint();
    }

    @Test
    void capturedStateFollowsGlStateManager() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        float alpha = state.getCurrentAlphaTest();
        cancels(GlStateManagerAlphaMixin.class, "impetus$captureAlphaFunc", GL11.GL_GREATER, 0.3F);
        assertEquals(0.3F, state.getCurrentAlphaTest());
        state.setCurrentAlphaTest(alpha);
        cancels(GlStateManagerColorMixin.class, "umbra$captureColor4", 0.1F, 0.2F, 0.3F, 0.4F);
        assertEquals(new Vector4f(0.1F, 0.2F, 0.3F, 0.4F), state.getColorModulator());
        cancels(GlStateManagerColorMixin.class, "umbra$captureColor3", 0.5F, 0.6F, 0.7F);
        assertEquals(new Vector4f(0.5F, 0.6F, 0.7F, 1.0F), state.getColorModulator());
        int reloads = state.getTextureReloadCount();
        cancels(Mixins.instance(TextureManagerReloadMixin.class), "impetus$incrementTextureReloadCount", (Object) null);
        assertEquals(reloads + 1, state.getTextureReloadCount());
    }

    @Test
    void lightmapCoordinatesAreClampedOnlyUnderAPack() {
        assertEquals(300.0F, (float) Mixins.call(OpenGlHelperLightmapClampMixin.class, "impetus$clampBlockLight", 300.0F));
        withPipeline();
        assertEquals(240.0F, (float) Mixins.call(OpenGlHelperLightmapClampMixin.class, "impetus$clampBlockLight", 300.0F));
        assertEquals(240.0F, (float) Mixins.call(OpenGlHelperLightmapClampMixin.class, "impetus$clampSkyLight", 255.0F));
        assertEquals(120.0F, (float) Mixins.call(OpenGlHelperLightmapClampMixin.class, "impetus$clampSkyLight", 120.0F));
    }

    @Test
    void theObjectIdsNestAndRestore() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        withPipeline();
        RenderItemItemIdMixin items = Mixins.instance(RenderItemItemIdMixin.class);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack stick = new ItemStack(Items.STICK);
        int before = state.getCurrentRenderedItem();
        cancels(items, "impetus$beginItem", diamond, null);
        assertEquals(WorldRenderingSettings.getItemId(diamond), state.getCurrentRenderedItem());
        // An item drawn inside another hands the outer id back when it finishes
        cancels(items, "impetus$beginItem", stick, null);
        assertEquals(WorldRenderingSettings.getItemId(stick), state.getCurrentRenderedItem());
        cancels(items, "impetus$endItem", stick, null);
        assertEquals(WorldRenderingSettings.getItemId(diamond), state.getCurrentRenderedItem());
        cancels(items, "impetus$endItem", diamond, null);
        assertEquals(before, state.getCurrentRenderedItem());
        // An unmatched end falls back to "no item"
        cancels(items, "impetus$endItem", diamond, null);
        assertEquals(-1, state.getCurrentRenderedItem());
        verify(TestGl.gl(), times(2)).glBindVertexArray(0);

        RenderManagerEntityIdMixin entities = Mixins.instance(RenderManagerEntityIdMixin.class);
        EntityPig pig = Mc.uninitialized(EntityPig.class);
        cancels(entities, "impetus$beginEntity", pig, 0.5F, false);
        assertEquals(WorldRenderingSettings.getEntityId(pig), state.getCurrentRenderedEntity());
        cancels(entities, "impetus$endEntity", pig, 0.5F, false);
        cancels(entities, "impetus$endEntity", pig, 0.5F, false);
        assertEquals(-1, state.getCurrentRenderedEntity());

        TileEntityRendererDispatcherIdMixin blockEntities = Mixins.instance(TileEntityRendererDispatcherIdMixin.class);
        TileEntityChest chest = new TileEntityChest();
        cancels(blockEntities, "impetus$beginBlockEntity", chest, 0.5F, -1);
        assertEquals(WorldRenderingSettings.getBlockEntityId(chest), state.getCurrentRenderedBlockEntity());
        cancels(blockEntities, "impetus$endBlockEntity", chest, 0.5F, -1);
        cancels(blockEntities, "impetus$endBlockEntity", chest, 0.5F, -1);
        assertEquals(-1, state.getCurrentRenderedBlockEntity());
        verify(pipeline, times(11)).refreshDynamicUniforms();
    }

    @Test
    void hurtAndChargeTintsBecomeTheEntityColorUniform() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        RenderLivingBaseEntityColorMixin render = Mixins.instance(RenderLivingBaseEntityColorMixin.class);
        int[] multiplier = {0};
        Mixins.stub(render, "getColorMultiplier", invocation -> multiplier[0]);
        EntityLivingBase entity = mock(EntityLivingBase.class);
        // Without a pack vanilla's own combiners run
        assertFalse(returned(render, "impetus$captureEntityColor", entity, 0.5F, true).isCancelled());
        assertFalse(cancels(render, "impetus$clearEntityColor"));

        withPipeline();
        // Nothing to tint, or a hurt flash this pass does not own
        assertEquals(Boolean.FALSE, returned(render, "impetus$captureEntityColor", entity, 0.5F, true).getReturnValue());
        entity.hurtTime = 5;
        assertEquals(Boolean.FALSE, returned(render, "impetus$captureEntityColor", entity, 0.5F, false).getReturnValue());
        // The hurt flash is OptiFine's translucent red
        assertEquals(Boolean.TRUE, returned(render, "impetus$captureEntityColor", entity, 0.5F, true).getReturnValue());
        assertEquals(new Vector4f(1.0F, 0.0F, 0.0F, 0.3F), state.getEntityColor());
        // A creeper's charge carries its own colour, with the multiplier's alpha as the blend
        entity.hurtTime = 0;
        entity.deathTime = 0;
        multiplier[0] = 0x80FF8000;
        assertEquals(Boolean.TRUE, returned(render, "impetus$captureEntityColor", entity, 0.5F, false).getReturnValue());
        assertEquals(new Vector4f(1.0F, 128 / 255.0F, 0.0F, 1.0F - 128 / 255.0F), state.getEntityColor());
        assertTrue(cancels(render, "impetus$clearEntityColor"));
        assertEquals(0.0F, state.getEntityColor().w);

        // A render that threw before unsetBrightness leaves the tint stranded, which doRender's return clears
        cancels(render, "impetus$clearStrandedEntityColor", entity, 0.0, 0.0, 0.0, 0.0F, 0.5F);
        state.setEntityColor(1.0F, 0.0F, 0.0F, 0.3F);
        cancels(render, "impetus$clearStrandedEntityColor", entity, 0.0, 0.0, 0.0, 0.0F, 0.5F);
        assertEquals(0.0F, state.getEntityColor().w);
        verify(pipeline, times(4)).refreshDynamicUniforms();
    }

    @Test
    void vanillaFeaturesFollowThePacksToggles() {
        Object entityRenderer = Mixins.instance(VanillaFeatureToggleMixin.class);
        Object overlay = Mixins.instance(VanillaOverlayToggleMixin.class);
        Object vignette = Mixins.instance(VanillaVignetteToggleMixin.class);
        Object render = Mixins.instance(RenderMixin.class);
        GameSettingsCloudsMixin clouds = Mixins.instance(GameSettingsCloudsMixin.class);
        // No pack: everything vanilla draws stays
        assertFalse(cancels(entityRenderer, "impetus$suppressWeather", 0.5F));
        assertFalse(cancels(overlay, "impetus$suppressUnderwaterOverlay", 0.5F));
        assertFalse(cancels(vignette, "impetus$suppressVignette", 1.0F, null));
        assertTrue((boolean) Mixins.call(render, "impetus$disableVanillaEntityShadowsWithShaderShadows", true));
        clouds.renderDistanceChunks = 8;
        assertFalse(returned(clouds, "impetus$overrideCloudMode").isCancelled());

        pack(String.join("\n", "weather = false", "underwaterOverlay = false", "vignette = false", "clouds = fast"));
        withPipeline();
        assertTrue(cancels(entityRenderer, "impetus$suppressWeather", 0.5F));
        assertTrue(cancels(overlay, "impetus$suppressUnderwaterOverlay", 0.5F));
        assertTrue(cancels(vignette, "impetus$suppressVignette", 1.0F, null));
        assertEquals(1, returned(clouds, "impetus$overrideCloudMode").getReturnValue());
        // Vanilla's short-render-distance gate is kept, so a pack cannot force clouds on there
        clouds.renderDistanceChunks = 3;
        assertFalse(returned(clouds, "impetus$overrideCloudMode").isCancelled());
        // Shader shadows replace vanilla's blob shadows only when the pack says so
        assertFalse((boolean) Mixins.call(render, "impetus$disableVanillaEntityShadowsWithShaderShadows", false));
        assertTrue((boolean) Mixins.call(render, "impetus$disableVanillaEntityShadowsWithShaderShadows", true));
        when(pipeline.shouldDisableVanillaEntityShadows()).thenReturn(true);
        assertFalse((boolean) Mixins.call(render, "impetus$disableVanillaEntityShadowsWithShaderShadows", true));
        // A pack leaving clouds alone keeps the player's setting
        pack("");
        clouds.renderDistanceChunks = 8;
        assertFalse(returned(clouds, "impetus$overrideCloudMode").isCancelled());
    }

    @Test
    void theBeaconBeamLeavesTheShadowMapAndWritesDepthOnRequest() {
        BeaconBeamDepthMixin beacon = Mixins.instance(BeaconBeamDepthMixin.class);
        Object[] render = {null, 0.0, 0.0, 0.0, 0.5F, -1, 1.0F};
        assertFalse(cancels(beacon, "impetus$noBeamInShadowPass", render));
        cancels(beacon, "impetus$beaconBeamDepthOn", render);
        cancels(beacon, "impetus$beaconBeamDepthOff", render);
        verify(TestGl.gl(), never()).glGetBoolean(anyInt());

        withPipeline();
        assertFalse(cancels(beacon, "impetus$noBeamInShadowPass", render));
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertTrue(cancels(beacon, "impetus$noBeamInShadowPass", render));
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        // beacon.beam.depth: depth writes on for the beam, then whatever vanilla had put back
        cancels(beacon, "impetus$beaconBeamDepthOn", render);
        verify(TestGl.gl(), never()).glGetBoolean(anyInt());
        when(pipeline.shouldWriteBeaconBeamToDepthBuffer()).thenReturn(true);
        cancels(beacon, "impetus$beaconBeamDepthOn", render);
        verify(TestGl.gl()).glGetBoolean(0x0B72);
        assertTrue((boolean) Mixins.get(beacon, "impetus$depthMaskOverridden"));
        cancels(beacon, "impetus$beaconBeamDepthOff", render);
        assertFalse((boolean) Mixins.get(beacon, "impetus$depthMaskOverridden"));
    }

    @Test
    void theArmKeepsBlendingOutsideTheHandStages() {
        RenderPlayerArmBlendMixin arm = Mixins.instance(RenderPlayerArmBlendMixin.class);
        // GlStateManager only reaches GL on a change, so blending starts off here
        GlStateManager.disableBlend();
        CapturedRenderingState.INSTANCE.setRenderStage(16);
        Mixins.call(arm, "impetus$keepArmUnblended");
        CapturedRenderingState.INSTANCE.setRenderStage(23);
        Mixins.call(arm, "impetus$keepArmUnblended");
        gl11.verify(() -> GL11.glEnable(GL11.GL_BLEND), never());
        CapturedRenderingState.INSTANCE.setRenderStage(0);
        Mixins.call(arm, "impetus$keepArmUnblended");
        gl11.verify(() -> GL11.glEnable(GL11.GL_BLEND));
    }

    @Test
    void screenshotsLeaveTextureStateClean() {
        returned(ScreenshotTextureStateMixin.class, "impetus$syncTextureUnitBeforeCapture", 854, 480, null);
        gl13.verify(() -> GL13.glActiveTexture(anyInt()), Mockito.atLeastOnce());
        returned(ScreenshotTextureStateMixin.class, "impetus$releaseFramebufferTexture", 854, 480, null);
        // The static-only mixins are still classes the game could construct
        for (Class<?> mixin : List.of(ScreenshotTextureStateMixin.class, GlStateManagerAlphaMixin.class, GlStateManagerColorMixin.class,
                LayerArmorBaseGlintMixin.class, OpenGlHelperLightmapClampMixin.class)) {
            assertNotNull(Mixins.construct(mixin));
        }
    }

    @Test
    void theSkyIsSplitIntoItsStagesAndToggles() {
        RenderGlobalMixin sky = Mixins.instance(RenderGlobalMixin.class);
        // Without a pack only the phase bookkeeping is skipped
        cancels(sky, "impetus$drawHorizon", 0.5F, 0);
        cancels(sky, "impetus$preCelestialRotate", 0.5F, 0);
        assertFalse(cancels(sky, "impetus$suppressSky", 0.5F, 0));
        withPipeline();
        cancels(sky, "impetus$drawHorizon", 0.5F, 0);
        verify(pipeline).drawSkyHorizon();
        // sunPathRotation turns the live sky, and a zero rotation leaves it alone
        cancels(sky, "impetus$preCelestialRotate", 0.5F, 0);
        gl11.verify(() -> GL11.glRotatef(anyFloat(), anyFloat(), anyFloat(), anyFloat()), never());
        CelestialUniforms.setSunPathRotation(30.0F);
        cancels(sky, "impetus$preCelestialRotate", 0.5F, 0);
        gl11.verify(() -> GL11.glRotatef(30.0F, 0.0F, 0.0F, 1.0F));
        cancels(sky, "impetus$beginSunMoon", 0.5F, 0);
        verify(pipeline).setPhase(ProgramId.SkyTextured, 4);
        cancels(sky, "impetus$beginMoon", 0.5F, 0);
        verify(pipeline).setPhase(ProgramId.SkyTextured, 5);
        cancels(sky, "impetus$endSunMoon", 0.5F, 0);
        verify(pipeline).setPhase(ProgramId.SkyBasic, 6);
        // Both cloud paths draw under gbuffers_clouds
        cancels(sky, "impetus$beginFastClouds", 0.5F, 0, 0.0, 0.0, 0.0);
        cancels(sky, "impetus$endFastClouds", 0.5F, 0, 0.0, 0.0, 0.0);
        cancels(sky, "impetus$beginFancyClouds", 0.5F, 0, 0.0, 0.0, 0.0);
        cancels(sky, "impetus$endFancyClouds", 0.5F, 0, 0.0, 0.0, 0.0);
        verify(pipeline, times(2)).setPhase(ProgramId.Clouds);
        verify(pipeline, times(2)).setPhase(null);

        // Celestial bodies a pack turns off draw with a transparent texture; stars skip their draw altogether
        TextureManager textures = client.getTextureManager();
        when(textures.getDynamicTextureLocation(eq("impetus_transparent"), any(DynamicTexture.class)))
                .thenReturn(new ResourceLocation("dynamic/impetus_transparent_1"));
        Statics.set(RenderGlobalMixin.class, "impetus$transparent", null);
        ResourceLocation sun = new ResourceLocation("textures/environment/sun.png");
        ResourceLocation moon = new ResourceLocation("textures/environment/moon_phases.png");
        ResourceLocation other = new ResourceLocation("textures/environment/end_sky.png");
        VertexBuffer stars = mock(VertexBuffer.class);
        pack("");
        Mixins.call(sky, "impetus$suppressCelestialBody", textures, sun);
        Mixins.call(sky, "impetus$suppressStarVbo", stars, GL11.GL_QUADS);
        Mixins.call(sky, "impetus$suppressStarList", 7);
        verify(textures).bindTexture(sun);
        verify(stars).drawArrays(GL11.GL_QUADS);
        gl11.verify(() -> GL11.glCallList(7));
        pack(String.join("\n", "sun = false", "moon = false", "stars = false", "sky = false"));
        Mixins.call(sky, "impetus$suppressCelestialBody", textures, sun);
        Mixins.call(sky, "impetus$suppressCelestialBody", textures, moon);
        Mixins.call(sky, "impetus$suppressCelestialBody", textures, other);
        Mixins.call(sky, "impetus$suppressStarVbo", stars, GL11.GL_QUADS);
        Mixins.call(sky, "impetus$suppressStarList", 7);
        verify(textures, times(2)).bindTexture(new ResourceLocation("dynamic/impetus_transparent_1"));
        verify(textures).bindTexture(other);
        verify(textures, times(1)).getDynamicTextureLocation(eq("impetus_transparent"), any(DynamicTexture.class));
        verify(stars, times(1)).drawArrays(anyInt());
        gl11.verify(() -> GL11.glCallList(7), times(1));
        assertTrue(cancels(sky, "impetus$suppressSky", 0.5F, 0));
        Statics.set(RenderGlobalMixin.class, "impetus$transparent", null);
    }

    @Test
    void terrainLayersHonourBackFacesAndSkipping() {
        RenderGlobalMixin terrain = Mixins.instance(RenderGlobalMixin.class);
        Object[] layer = {BlockRenderLayer.CUTOUT, 0.5, 0, null};
        returned(terrain, "impetus$applyBackFaceCulling", layer);
        assertFalse(returned(terrain, "impetus$skipTerrain", layer).isCancelled());
        withPipeline();
        when(pipeline.shouldCullBackFaces(anyInt())).thenReturn(true);
        returned(terrain, "impetus$applyBackFaceCulling", layer);
        returned(terrain, "impetus$restoreBackFaceCulling", layer);
        gl11.verify(() -> GL11.glDisable(GL11.GL_CULL_FACE), never());
        // backFace.cutout = true: culling off for that layer's draw and back on after
        when(pipeline.shouldCullBackFaces(BlockRenderLayer.CUTOUT.ordinal())).thenReturn(false);
        returned(terrain, "impetus$applyBackFaceCulling", layer);
        assertTrue((boolean) Mixins.get(terrain, "impetus$restoreCull"));
        returned(terrain, "impetus$restoreBackFaceCulling", layer);
        assertFalse((boolean) Mixins.get(terrain, "impetus$restoreCull"));
        // skipAllRendering draws no terrain at all
        assertFalse(returned(terrain, "impetus$skipTerrain", layer).isCancelled());
        when(pipeline.skipAllRendering()).thenReturn(true);
        assertEquals(0, returned(terrain, "impetus$skipTerrain", layer).getReturnValue());
    }

    @Test
    void theSelectionBoxUsesGbuffersLineOrWaitsForTheComposite() {
        RenderGlobalMixin outline = Mixins.instance(RenderGlobalMixin.class);
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        RayTraceResult target = new RayTraceResult(new Vec3d(0, 0, 0), net.minecraft.util.EnumFacing.UP);
        assertFalse(cancels(outline, "impetus$beginBlockOutline", player, target, 0, 0.5F));
        withPipeline();
        when(pipeline.hasDirectGbufferProgram(ProgramId.Line)).thenReturn(true);
        assertFalse(cancels(outline, "impetus$beginBlockOutline", player, target, 0, 0.5F));
        verify(pipeline).setPhase(ProgramId.Line);
        cancels(outline, "impetus$endBlockOutline", player, target, 0, 0.5F);
        verify(pipeline).setPhase(null);
        // Without gbuffers_line the draw is held until after the composite, and the replay itself draws as vanilla
        when(pipeline.hasDirectGbufferProgram(ProgramId.Line)).thenReturn(false);
        assertTrue(cancels(outline, "impetus$beginBlockOutline", player, target, 0, 0.5F));
        assertTrue((boolean) Statics.get(DeferredBlockOutline.class, "pending"));
        Statics.set(DeferredBlockOutline.class, "replaying", true);
        assertFalse(cancels(outline, "impetus$beginBlockOutline", player, target, 0, 0.5F));
    }

    @Test
    void theWorldRenderDrivesTheFramePipeline() {
        EntityRendererMixin renderer = Mixins.instance(EntityRendererMixin.class);
        Mixins.set(renderer, "mc", client);
        ItemRenderer hand = mock(ItemRenderer.class);
        Mixins.set(renderer, "itemRenderer", hand);
        Mixins.set(renderer, "renderHand", true);
        Mixins.set(renderer, "farPlaneDistance", 256.0F);
        List<Object[]> vanillaHand = new ArrayList<>();
        ShadowStubs.on(renderer, "renderHand", args -> {
            vanillaHand.add(args);
            return null;
        });
        ShadowStubs.on(renderer, "getFOVModifier", args -> 70.0F);
        EntityPlayerSP viewer = mock(EntityPlayerSP.class);
        when(client.getRenderViewEntity()).thenReturn(viewer);
        client.playerController = mock(PlayerControllerMP.class);
        client.effectRenderer = mock(ParticleManager.class);
        client.renderGlobal = mock(RenderGlobal.class);
        settings.viewBobbing = true;

        // No pack: nothing is driven, and the particle and hand redirects behave exactly as vanilla
        cancels(renderer, "impetus$onEnableLightmap");
        cancels(renderer, "impetus$beginShaderFrame", 0.5F, 0L);
        cancels(renderer, "impetus$captureRenderingState", 2, 0.5F, 0L);
        cancels(renderer, "impetus$beginTranslucents", 2, 0.5F, 0L);
        cancels(renderer, "impetus$compositeBeforeHand", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseWeather", 2, 0.5F, 0L);
        cancels(renderer, "impetus$finishShaderFrame", 0.5F, 0L);
        ParticleManager particles = client.effectRenderer;
        Mixins.call(renderer, "impetus$orderParticles", particles, viewer, 0.5F);
        verify(particles).renderParticles(viewer, 0.5F);
        Mixins.call(renderer, "impetus$skipPostCompositeShaderHand", renderer, 0.5F, 2);
        assertEquals(1, vanillaHand.size());
        // Cloud ordering follows the Extras preference without a pack
        ExtrasConfig extras = Extras.options();
        extras.render.cloudTranslucency = ExtrasConfig.CloudTranslucency.ALWAYS;
        assertEquals(Double.NEGATIVE_INFINITY, (double) Mixins.call(renderer, "impetus$cloudDrawSlot", 128.0));
        extras.render.cloudTranslucency = ExtrasConfig.CloudTranslucency.NEVER;
        assertEquals(Double.POSITIVE_INFINITY, (double) Mixins.call(renderer, "impetus$cloudDrawSlot", 128.0));
        extras.render.cloudTranslucency = ExtrasConfig.CloudTranslucency.DEFAULT;
        assertEquals(CloudPassState.cloudHeight(128.0F), (double) Mixins.call(renderer, "impetus$cloudDrawSlot", 128.0), 1e-6);

        pack("");
        withPipeline();
        // The frame opens on the pack's pipeline and the lightmap toggles swap the textured programs
        cancels(renderer, "impetus$beginShaderFrame", 0.5F, 0L);
        verify(pipeline).beginWorldRendering(0.5F);
        cancels(renderer, "impetus$onEnableLightmap");
        cancels(renderer, "impetus$onDisableLightmap");
        verify(pipeline).setLightmapEnabled(true);
        verify(pipeline).setLightmapEnabled(false);
        assertEquals(Double.NEGATIVE_INFINITY, (double) Mixins.call(renderer, "impetus$cloudDrawSlot", 128.0));
        // The frustum anchor captures matrices and fog colour, then renders the shadow map
        Mixins.set(renderer, "fogColorRed", 0.25F);
        Mixins.set(renderer, "fogColorGreen", 0.5F);
        Mixins.set(renderer, "fogColorBlue", 0.75F);
        cancels(renderer, "impetus$captureRenderingState", 2, 0.5F, 0L);
        assertEquals(new org.joml.Vector3f(0.25F, 0.5F, 0.75F), CapturedRenderingState.INSTANCE.getFogColor());
        verify(pipeline).captureRenderingState();
        verify(pipeline).renderShadowMap();
        // Each profiler section selects its gbuffer program
        cancels(renderer, "impetus$phaseSky", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseTerrain", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseEntities", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseBlockDamage", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseParticles", 2, 0.5F, 0L);
        cancels(renderer, "impetus$phaseRenderLast", 2, 0.5F, 0L);
        verify(pipeline).setPhase(ProgramId.SkyBasic);
        verify(pipeline).setPhase(ProgramId.Entities);
        verify(pipeline).setPhase(ProgramId.DamagedBlock);
        verify(pipeline).setPhase(ProgramId.Particles);
        verify(pipeline, times(2)).setPhase(null);
        // rain.depth turns depth writes back on for the weather
        // GlStateManager only reaches GL on a change, so the mask starts off here
        GlStateManager.depthMask(false);
        cancels(renderer, "impetus$phaseWeather", 2, 0.5F, 0L);
        gl11.verify(() -> GL11.glDepthMask(true), never());
        when(pipeline.shouldWriteRainAndSnowToDepthBuffer()).thenReturn(true);
        cancels(renderer, "impetus$phaseWeather", 2, 0.5F, 0L);
        gl11.verify(() -> GL11.glDepthMask(true));

        // At the translucent anchor: the solid hand draws first, then particles when the pack orders them before the deferred chain
        when(pipeline.beginHandRendering()).thenReturn(true);
        when(pipeline.getParticleOrdering()).thenReturn("before");
        cancels(renderer, "impetus$beginTranslucents", 2, 0.5F, 0L);
        verify(pipeline).beginHand();
        verify(hand).renderItemInFirstPerson(0.5F);
        verify(pipeline).endHandRendering();
        verify(client.getTextureManager()).bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        verify(particles, times(2)).renderParticles(viewer, 0.5F);
        verify(pipeline).beginTranslucents();
        // ...so vanilla's own particle draw is skipped once
        Mixins.call(renderer, "impetus$orderParticles", particles, viewer, 0.5F);
        verify(particles, times(2)).renderParticles(viewer, 0.5F);
        Mixins.call(renderer, "impetus$orderParticles", particles, viewer, 0.5F);
        verify(particles, times(3)).renderParticles(viewer, 0.5F);

        // At the hand anchor: the translucent hand, then the composite chain
        when(pipeline.beginHandTranslucentRendering()).thenReturn(true);
        cancels(renderer, "impetus$compositeBeforeHand", 2, 0.5F, 0L);
        verify(hand, times(2)).renderItemInFirstPerson(0.5F);
        verify(pipeline).finishWorldRendering();
        // vanilla's post-composite hand draws only the overlays, since the hand is already in the gbuffer
        Mixins.call(renderer, "impetus$skipPostCompositeShaderHand", renderer, 0.5F, 2);
        verify(hand).renderOverlays(0.5F);
        assertEquals(1, vanillaHand.size());
        Mixins.call(renderer, "impetus$skipPostCompositeShaderHand", renderer, 0.5F, 2);
        assertEquals(2, vanillaHand.size());
        cancels(renderer, "impetus$finishShaderFrame", 0.5F, 0L);
        verify(pipeline, times(2)).finishWorldRendering();

        // The shadow declarations' own bodies are stand-ins the game replaces with vanilla's members
        Mixins.call(renderer, "renderHand", 0.5F, 0);
        assertEquals(0.0F, (float) Mixins.call(renderer, "getFOVModifier", 0.5F, false));
        Mixins.call(renderer, "hurtCameraEffect", 0.5F);
        Mixins.call(renderer, "applyBobbing", 0.5F);
        Mixins.call(renderer, "enableLightmap");
        Mixins.call(renderer, "disableLightmap");
    }

    @Test
    void theShaderHandFollowsVanillasViewRules() {
        EntityRendererMixin renderer = Mixins.instance(EntityRendererMixin.class);
        Mixins.set(renderer, "mc", client);
        ItemRenderer hand = mock(ItemRenderer.class);
        Mixins.set(renderer, "itemRenderer", hand);
        Mixins.set(renderer, "farPlaneDistance", 256.0F);
        EntityPlayerSP viewer = mock(EntityPlayerSP.class);
        when(client.getRenderViewEntity()).thenReturn(viewer);
        client.playerController = mock(PlayerControllerMP.class);
        client.renderGlobal = mock(RenderGlobal.class);
        withPipeline();
        when(pipeline.beginHandRendering()).thenReturn(true);
        when(pipeline.beginHandTranslucentRendering()).thenReturn(true);
        when(pipeline.getParticleOrdering()).thenReturn("after");

        // The hand switched off, then a debug view, draw no shader hand
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        verify(pipeline, never()).beginHandRendering();
        Mixins.set(renderer, "renderHand", true);
        Mixins.set(renderer, "debugView", true);
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        Mixins.call(renderer, "impetus$skipPostCompositeShaderHand", renderer, 0.5F, 0);
        verify(hand, never()).renderItemInFirstPerson(anyFloat());
        verify(hand, never()).renderOverlays(anyFloat());
        Mixins.set(renderer, "debugView", false);

        // Asleep, in third person, or with the GUI hidden the hand stays away, anaglyph shifting each eye's projection
        when(viewer.isPlayerSleeping()).thenReturn(true);
        settings.anaglyph = true;
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        Mixins.call(renderer, "impetus$skipPostCompositeShaderHand", renderer, 0.5F, 0);
        when(viewer.isPlayerSleeping()).thenReturn(false);
        settings.thirdPersonView = 1;
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        settings.thirdPersonView = 0;
        settings.hideGUI = true;
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        settings.hideGUI = false;
        when(client.playerController.isSpectator()).thenReturn(true);
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        verify(hand, never()).renderItemInFirstPerson(anyFloat());
        verify(hand, never()).renderOverlays(anyFloat());
        gl11.verify(() -> GL11.glTranslatef(0.07F, 0.0F, 0.0F), Mockito.atLeastOnce());
        // A view entity that is not alive in the world, and particles ordered before with no view entity at all
        when(client.getRenderViewEntity()).thenReturn(null);
        when(pipeline.getParticleOrdering()).thenReturn("before");
        cancels(renderer, "impetus$beginTranslucents", 0, 0.5F, 0L);
        // The translucent hand is only drawn after a solid one
        Mixins.set(renderer, "impetus$shaderHandRendered", false);
        cancels(renderer, "impetus$compositeBeforeHand", 0, 0.5F, 0L);
        verify(pipeline, never()).beginHandTranslucentRendering();
    }
}
