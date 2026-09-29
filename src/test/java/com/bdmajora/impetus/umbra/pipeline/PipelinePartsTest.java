package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.BlendMode;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureFilteringData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelinePartsTest {
    private Minecraft client;
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private int defaultTexUnit;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // TextureUtil initialises under its own GL mock, so before this class installs one
        Mc.textures();
    }

    @BeforeEach
    void freshGl() {
        client = Mc.client();
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        defaultTexUnit = net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit;
        net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
    }

    @AfterEach
    void closeGl() {
        net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit = defaultTexUnit;
        gl13.close();
        gl11.close();
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.SRGB);
        DeferredBlockOutline.discard();
    }

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    private void installPack(String properties) {
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, properties)));
        Mixins.set(Umbra.class, "renderingPipeline", Mc.uninitialized(UmbraRenderingPipeline.class));
    }

    @Test
    void vanillaFeaturesFollowThePackOnlyWhileItRenders() {
        // No pack: everything vanilla draws
        assertTrue(VanillaFeatureToggles.shouldRenderSun());
        assertEquals(OptionalInt.empty(), VanillaFeatureToggles.getCloudMode());
        Mixins.set(Umbra.class, "currentPack", new ShaderPack(Map.of(ShaderPack.PROPERTIES_PATH, "sun = false")));
        // A pack without a rendering pipeline is not in charge either
        assertTrue(VanillaFeatureToggles.shouldRenderSun());
        assertEquals(OptionalInt.empty(), VanillaFeatureToggles.getCloudMode());
        installPack(String.join("\n", "sun = false", "moon = false", "stars = false", "sky = false", "vignette = false",
                "underwaterOverlay = false", "weather = false"));
        assertFalse(VanillaFeatureToggles.shouldRenderSun());
        assertFalse(VanillaFeatureToggles.shouldRenderMoon());
        assertFalse(VanillaFeatureToggles.shouldRenderStars());
        assertFalse(VanillaFeatureToggles.shouldRenderSky());
        assertFalse(VanillaFeatureToggles.shouldRenderVignette());
        assertFalse(VanillaFeatureToggles.shouldRenderUnderwaterOverlay());
        assertFalse(VanillaFeatureToggles.shouldRenderWeather());
        assertEquals(OptionalInt.empty(), VanillaFeatureToggles.getCloudMode());
        String[][] clouds = {{"off", "0"}, {"Fast", "1"}, {"FANCY", "2"}};
        for (String[] mode : clouds) {
            installPack("clouds = " + mode[0]);
            assertEquals(OptionalInt.of(Integer.parseInt(mode[1])), VanillaFeatureToggles.getCloudMode());
        }
        // An unknown mode is reported once and otherwise ignored
        installPack("clouds = puffy");
        assertEquals(OptionalInt.empty(), VanillaFeatureToggles.getCloudMode());
        assertEquals(OptionalInt.empty(), VanillaFeatureToggles.getCloudMode());
        assertNotNull(Mixins.construct(VanillaFeatureToggles.class));
    }

    @Test
    void shadowContentFollowsItsDirectives() {
        ShadowContentSettings defaults = ShadowContentSettings.defaults();
        assertTrue(defaults.shouldRenderTerrain() && defaults.shouldRenderTranslucent() && defaults.shouldRenderEntities()
                && defaults.shouldRenderBlockEntities() && defaults.shouldRenderAnyBlockEntities() && defaults.shouldRenderPlayer());
        assertFalse(defaults.shouldRenderLightBlockEntitiesOnly());
        assertEquals(ShadowContentSettings.Culling.ON, defaults.getCulling());
        assertTrue(defaults.toString().contains("culling=ON"));
        ShadowContentSettings lightsOnly = ShadowContentSettings.from(ShaderProperties.parse(String.join("\n",
                "shadowTerrain = false", "shadowTranslucent = false", "shadowEntities = false", "shadowBlockEntities = false",
                "shadowLightBlockEntities = true", "shadowPlayer = false", "shadow.culling = reversed")));
        assertFalse(lightsOnly.shouldRenderTerrain());
        assertTrue(lightsOnly.shouldRenderLightBlockEntitiesOnly());
        assertTrue(lightsOnly.shouldRenderAnyBlockEntities());
        assertEquals(ShadowContentSettings.Culling.REVERSED, lightsOnly.getCulling());
        assertEquals(ShadowContentSettings.Culling.OFF, ShadowContentSettings.from(ShaderProperties.parse("shadow.culling = false")).getCulling());
        assertEquals(ShadowContentSettings.Culling.OFF, ShadowContentSettings.from(ShaderProperties.parse("shadow.culling = off")).getCulling());
        assertEquals(ShadowContentSettings.Culling.ON, ShadowContentSettings.from(ShaderProperties.parse("shadow.culling = sideways")).getCulling());
        assertEquals(ShadowContentSettings.Culling.ON, ShadowContentSettings.from(ShaderProperties.parse("")).getCulling());
    }

    @Test
    void gbufferProgramsShareSourcesAndSkipWhatIsDisabled() {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        for (String program : new String[] {"gbuffers_basic", "gbuffers_textured", "gbuffers_spidereyes",
                "gbuffers_entities_translucent", "gbuffers_weather", "gbuffers_hand"}) {
            sources.put(path("/" + program + ".vsh"), "void main() {}");
            sources.put(path("/" + program + ".fsh"), "/* DRAWBUFFERS:04 */\nvoid main() {}");
        }
        sources.put(ShaderPack.PROPERTIES_PATH, "program.gbuffers_weather.enabled = false\nalphaTest.gbuffers_textured = GREATER 0.1");
        ShaderPack pack = new ShaderPack(sources);
        // The first program linked (gbuffers_basic, for the sky) fails, so every phase resolving to it stays vanilla
        when(TestGl.gl().glGetProgrami(anyInt(), eq(0x8B82))).thenReturn(0, 1);
        when(TestGl.gl().glGetUniformLocation(anyInt(), any(CharSequence.class))).thenAnswer(invocation -> {
            String name = invocation.getArgument(1).toString();
            return name.equals("gtexture") ? 4 : name.equals("impetus_HandLightmap") ? 7 : -1;
        });
        GbufferPrograms programs = new GbufferPrograms(pack, Map.of("colortex0", 8), Map.of("gaux4", 30));
        assertNull(programs.get(ProgramId.SkyBasic));
        assertNull(programs.get(ProgramId.Line));
        assertNull(programs.get(ProgramId.Weather));
        GbufferPrograms.Entry textured = programs.get(ProgramId.SkyTextured);
        assertSame(textured, programs.get(ProgramId.Clouds));
        assertFalse(programs.hasDirect(ProgramId.Clouds));
        assertTrue(programs.hasDirect(ProgramId.SpiderEyes));
        assertNotSame(textured, programs.get(ProgramId.SpiderEyes));
        assertTrue(programs.get(ProgramId.SpiderEyes).getBlendState().hasDirectives());
        assertTrue(textured.getAlphaTest().hasDirectives());
        assertArrayEquals(new int[] {0, 4}, textured.getDrawBuffers());
        assertSame(textured.drawBuffersReadOnly(), textured.drawBuffersReadOnly());
        assertNotNull(textured.getUniforms());
        assertNotNull(textured.getProgram());
        assertNotNull(textured.getShadowSamplerKinds());
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUniform1i(4, 0);
        textured.setHandLightmap(0.5F, 0.25F);
        verify(TestGl.gl(), Mockito.atLeastOnce()).glUniform2f(7, 0.5F, 0.25F);
        assertEquals(4, programs.entries().size());
        programs.destroy();
        assertTrue(programs.entries().isEmpty());
        assertNull(programs.get(ProgramId.SkyTextured));
        // An entry built without a draw-buffer list writes colortex0
        GbufferPrograms.Entry bare = new GbufferPrograms.Entry(null, null, null, null, null, -1, null);
        assertArrayEquals(new int[] {0}, bare.getDrawBuffers());
        bare.setHandLightmap(1, 1);
    }

    @Test
    void theWholePackCompilesUpFront() {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        for (String program : new String[] {"gbuffers_basic", "composite", "final"}) {
            sources.put(path("/" + program + ".vsh"), "void main() {}");
            sources.put(path("/" + program + ".fsh"), "void main() {}");
        }
        ShaderPack pack = new ShaderPack(sources);
        when(TestGl.gl().glGetProgrami(anyInt(), eq(0x8B82))).thenReturn(0, 1);
        UmbraPipeline pipeline = new UmbraPipeline(pack);
        assertEquals(2, pipeline.getCompiledProgramCount());
        pipeline.destroy();
        assertEquals(0, pipeline.getCompiledProgramCount());
        assertNull(pipeline.getProgram("final"));
        // GL info is best effort
        when(TestGl.gl().glGetInteger(0x821B)).thenThrow(new IllegalStateException("no context"));
        assertEquals(3, new UmbraPipeline(pack).getCompiledProgramCount());
    }

    @Test
    void wideGamutOutputRunsOnlyWhenSelected() {
        assertEquals(ColorSpaceConverter.ColorSpace.SRGB, ColorSpaceConverter.ColorSpace.byName(null));
        assertEquals(ColorSpaceConverter.ColorSpace.REC2020, ColorSpaceConverter.ColorSpace.byName(" rec2020 "));
        assertEquals(ColorSpaceConverter.ColorSpace.SRGB, ColorSpaceConverter.ColorSpace.byName("cmyk"));
        FullscreenQuadRenderer quad = new FullscreenQuadRenderer();
        ColorSpaceConverter converter = new ColorSpaceConverter();
        ColorSpaceConverter.setColorSpace(null);
        assertFalse(converter.isActive());
        converter.run(800, 600, quad);
        for (ColorSpaceConverter.ColorSpace space : ColorSpaceConverter.ColorSpace.values()) {
            ColorSpaceConverter.setColorSpace(space);
            assertEquals(space, ColorSpaceConverter.getColorSpace());
            converter.run(800, 600, quad);
            converter.run(800, 600, quad);
        }
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.DISPLAY_P3);
        converter.run(0, 600, quad);
        converter.run(1024, 768, quad);
        when(TestGl.gl().glGetUniformLocation(anyInt(), eq("u_Source"))).thenReturn(2);
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.ADOBE_RGB);
        converter.run(1024, 768, quad);
        verify(TestGl.gl()).glUniform1i(2, 0);
        verify(TestGl.gl(), Mockito.atLeast(8)).glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        // A failure disables the conversion rather than breaking presentation
        Mockito.doThrow(new IllegalStateException("lost")).when(TestGl.gl()).glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        converter.run(1024, 768, quad);
        assertFalse(converter.isActive());
        converter.destroy();
        converter.destroy();
        assertTrue(converter.isActive());
        quad.destroy();
        quad.destroy();
        verify(TestGl.gl(), times(1)).glDeleteVertexArrays(anyInt());
    }

    @Test
    void centreDepthIsReadBackAFrameLate() {
        CenterDepthSampler sampler = new CenterDepthSampler();
        sampler.destroy();
        // Nothing reads until a program asks for the uniform
        sampler.sample(5, 800, 600, 0.05F, 1.0F);
        verify(TestGl.gl(), never()).glReadPixels(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong());
        assertEquals(1.0F, CenterDepthSampler.getCenterDepthSmooth());
        sampler.sample(5, 0, 600, 0.05F, 1.0F);
        // Pixel-pack buffers: the first frame only issues, later frames map what the previous one read
        sampler.sample(5, 800, 600, 0.05F, 1.0F);
        verify(TestGl.gl()).glReadPixels(400, 300, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0L);
        assertEquals(1.0F, CenterDepthSampler.getCenterDepthSmooth());
        sampler.sample(5, 800, 600, 0.05F, 1.0F);
        assertEquals(0.0F, CenterDepthSampler.getCenterDepthSmooth());
        when(TestGl.gl().glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt()))
                .thenReturn(ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).putFloat(0, 1.0F));
        sampler.sample(5, 800, 600, 0.05F, 1.0F);
        float smoothing = CenterDepthSampler.getCenterDepthSmooth();
        assertTrue(smoothing > 0 && smoothing < 1, String.valueOf(smoothing));
        sampler.sample(5, 800, 600, 0.05F, 0.0F);
        assertEquals(1.0F, CenterDepthSampler.getCenterDepthSmooth());
        when(TestGl.gl().glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt())).thenReturn(null);
        sampler.sample(6, 800, 600, 0.05F, 1.0F);
        sampler.destroy();
        // A context without pixel-pack buffers reads synchronously
        ContextCapabilities caps = new ContextCapabilities();
        caps.OpenGL21 = false;
        caps.GL_ARB_pixel_buffer_object = false;
        GLContext.capabilities = caps;
        CenterDepthSampler sync = new CenterDepthSampler();
        CenterDepthSampler.getCenterDepthSmooth();
        sync.sample(5, 800, 600, 0.05F, 1.0F);
        sync.sample(5, 800, 600, 0.05F, 1.0F);
        verify(TestGl.gl(), times(2)).glReadPixels(eq(400), eq(300), eq(1), eq(1), eq(GL11.GL_DEPTH_COMPONENT), eq(GL11.GL_FLOAT), any(ByteBuffer.class));
        caps.OpenGL21 = true;
        caps.OpenGL30 = false;
        caps.GL_ARB_map_buffer_range = false;
        CenterDepthSampler noMap = new CenterDepthSampler();
        noMap.sample(5, 800, 600, 0.05F, 1.0F);
        noMap.destroy();
        sync.destroy();
    }

    @Test
    void theBlockOutlineIsReplayedAfterTheCompositeChain() {
        EntityPlayer player = mock(EntityPlayer.class);
        RayTraceResult hit = new RayTraceResult(Vec3d.ZERO, net.minecraft.util.EnumFacing.UP, net.minecraft.util.math.BlockPos.ORIGIN);
        DeferredBlockOutline.drawIfPending();
        DeferredBlockOutline.capture(player, hit, 0.5F);
        // Without a RenderGlobal there is nothing to replay into
        DeferredBlockOutline.drawIfPending();
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        boolean[] replaying = new boolean[1];
        Mockito.doAnswer(invocation -> replaying[0] = DeferredBlockOutline.isReplaying())
                .when(renderGlobal).drawSelectionBox(player, hit, 0, 0.5F);
        DeferredBlockOutline.capture(player, hit, 0.5F);
        DeferredBlockOutline.drawIfPending();
        assertTrue(replaying[0]);
        assertFalse(DeferredBlockOutline.isReplaying());
        Framebuffer framebuffer = mock(Framebuffer.class);
        when(client.getFramebuffer()).thenReturn(framebuffer);
        DeferredBlockOutline.capture(player, hit, 0.5F);
        DeferredBlockOutline.drawIfPending();
        verify(framebuffer).bindFramebuffer(false);
        // A capture without a target draws nothing
        DeferredBlockOutline.capture(player, null, 0.5F);
        DeferredBlockOutline.drawIfPending();
        verify(renderGlobal, times(2)).drawSelectionBox(player, hit, 0, 0.5F);
        assertNotNull(Mixins.construct(DeferredBlockOutline.class));
    }

    private static CustomImageDefinition image(String name, String value) {
        return CustomImageDefinition.parse(name, value);
    }

    @Test
    void customImagesGetUnitsAndStayZeroed() {
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(4);
        List<CustomImageDefinition> definitions = List.of(
                image("flat", "flat_sampler rgba rgba8 unsigned_byte true false 16 16"),
                image("voxel", "voxel_sampler rgba_integer rgba8ui unsigned_int false false 32 32 32"),
                image("screen", "screen_sampler rg rg16f half_float true true 0.5 0.5"),
                image("bogus", "bogus_sampler cmyk rgba8 unsigned_byte false false 1 1"),
                image("extra", "extra_sampler red r32f float false false 4 4"),
                image("over", "over_sampler rgb rgb8 byte false false 4 4"));
        CustomImageManager images = new CustomImageManager(definitions, 30, 31, 800, 600);
        assertFalse(images.isEmpty());
        assertEquals(4, images.getHardwareImageUnits());
        // The unsupported format is skipped, the last one runs out of image units, and "screen" and "extra" out of sampler units
        assertEquals(4, images.getNextAvailableImageUnit());
        Map<String, Integer> overrides = images.getUniformOverrides();
        assertEquals(0, overrides.get("flat"));
        assertEquals(30, overrides.get("flat_sampler"));
        assertEquals(31, overrides.get("voxel_sampler"));
        assertFalse(overrides.containsKey("screen_sampler"));
        assertFalse(overrides.containsKey("bogus"));
        assertArrayEquals(new int[] {32, 32, 32}, images.getFirst3DImageSize());
        verify(TestGl.gl()).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), anyInt(), eq(400), eq(300), eq(0), anyInt(), anyInt(), (ByteBuffer) eq(null));
        images.onResize(800, 600);
        images.onResize(1000, 500);
        verify(TestGl.gl()).glTexImage2D(eq(GL11.GL_TEXTURE_2D), eq(0), anyInt(), eq(500), eq(250), eq(0), anyInt(), anyInt(), (ByteBuffer) eq(null));
        images.clearAll();
        verify(TestGl.gl()).glMemoryBarrier(anyInt());
        images.bindAll();
        images.unbindAll();
        verify(TestGl.gl(), times(4)).glBindImageTexture(anyInt(), anyInt(), eq(0), eq(true), eq(0), anyInt(), anyInt());
        images.destroy();
        assertTrue(images.isEmpty());
        assertNull(images.getFirst3DImageSize());
        // Fixed-size images only: nothing to resize or clear; a driver reporting no units gets the declared maximum
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(0);
        CustomImageManager fixed = new CustomImageManager(List.of(image("f", "f_sampler rgba rgba8 unsigned_byte false false 2 2")), 30, 31, 800, 600);
        assertEquals(16, fixed.getHardwareImageUnits());
        fixed.onResize(10, 10);
        fixed.clearAll();
        verify(TestGl.gl(), times(1)).glMemoryBarrier(anyInt());
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void customTexturesOverrideTheirSamplerAndEveryAlias() throws Exception {
        ShaderPack pack = mock(ShaderPack.class);
        CustomTextureData.PngData png = new CustomTextureData.PngData(new TextureFilteringData(false, false), png());
        CustomTextureData.PngData broken = new CustomTextureData.PngData(new TextureFilteringData(false, false), new byte[] {1});
        when(pack.getCustomNoiseTexture()).thenReturn(png);
        Map<TextureStage, Map<String, CustomTextureData>> staged = new EnumMap<>(TextureStage.class);
        Map<String, CustomTextureData> composite = new LinkedHashMap<>();
        composite.put("gaux4", png);
        composite.put("mySampler", new CustomTextureData.ResourceData("minecraft", "textures/atlas/blocks.png"));
        composite.put("badPng", broken);
        composite.put("lightmapCopy", new CustomTextureData.LightmapMarker());
        staged.put(TextureStage.COMPOSITE_AND_FINAL, composite);
        when(pack.getCustomTextureDataMap()).thenReturn(staged);
        Map<String, CustomTextureData> global = new LinkedHashMap<>();
        global.put("volume", new CustomTextureData.RawData("TEXTURE_3D", "RGBA8", 2, 2, 2, "RGBA", "UNSIGNED_BYTE", new byte[32]));
        String[][] rawFormats = {{"R8", "RED", "BYTE"}, {"RG8", "RG", "SHORT"}, {"RGB8", "RGB", "UNSIGNED_SHORT"},
                {"R16F", "RED", "FLOAT"}, {"RGB16F", "RGB", "HALF_FLOAT"}, {"RGBA16F", "RGBA", "FLOAT"},
                {"R32F", "RED", "FLOAT"}, {"RGBA32F", "RGBA", "FLOAT"}};
        for (String[] format : rawFormats) {
            global.put("raw_" + format[0], new CustomTextureData.RawData("TEXTURE_2D", format[0], 1, 1, 1, format[1], format[2], new byte[16]));
        }
        global.put("badTarget", new CustomTextureData.RawData("TEXTURE_1D", "RGBA8", 1, 1, 1, "RGBA", "FLOAT", new byte[4]));
        global.put("badFormat", new CustomTextureData.RawData("TEXTURE_2D", "RGB10", 1, 1, 1, "RGBA", "FLOAT", new byte[4]));
        global.put("badPixel", new CustomTextureData.RawData("TEXTURE_2D", "RGBA8", 1, 1, 1, "BGRA", "FLOAT", new byte[4]));
        global.put("badType", new CustomTextureData.RawData("TEXTURE_2D", "RGBA8", 1, 1, 1, "RGBA", "DOUBLE", new byte[4]));
        global.put("overflow", png);
        when(pack.getUmbraCustomTextureDataMap()).thenReturn(global);
        Map<TextureStage, Map<String, Integer>> units = new EnumMap<>(TextureStage.class);
        units.put(TextureStage.COMPOSITE_AND_FINAL, Map.of("gaux4", 7, "colortex7", 7, "colortex0", 0));
        CustomTextureManager textures = new CustomTextureManager(pack, units, Map.of("gaux4", 7, "colortex7", 7), 24, 35);
        Map<String, CustomTextureManager.Override> overrides = textures.getOverrides(TextureStage.COMPOSITE_AND_FINAL);
        CustomTextureManager.Override gaux4 = overrides.get("gaux4");
        assertSame(gaux4, overrides.get("colortex7"));
        assertEquals(7, gaux4.colorTarget);
        assertEquals(24, gaux4.unit);
        assertEquals(-1, overrides.get("mySampler").colorTarget);
        assertFalse(overrides.containsKey("badPng"));
        assertFalse(overrides.containsKey("badFormat"));
        assertTrue(overrides.containsKey("volume"));
        // Twelve units run out before the last global texture
        assertFalse(overrides.containsKey("overflow"));
        assertEquals(36, textures.getNextAvailableUnit());
        assertTrue(textures.getOverrides(TextureStage.SETUP).containsKey("volume"));
        assertTrue(textures.getNoiseTextureId() > 0);
        assertFalse(textures.isEmpty());

        TextureManager manager = mock(TextureManager.class);
        when(client.getTextureManager()).thenReturn(manager);
        ITextureObject atlas = mock(ITextureObject.class);
        when(atlas.getGlTextureId()).thenReturn(55);
        when(manager.getTexture(new net.minecraft.util.ResourceLocation("minecraft", "textures/atlas/blocks"))).thenReturn(atlas);
        EntityRenderer renderer = Mc.uninitialized(EntityRenderer.class);
        DynamicTexture lightmap = mock(DynamicTexture.class);
        when(lightmap.getGlTextureId()).thenReturn(66);
        Mixins.set(renderer, "lightmapTexture", lightmap);
        Mixins.set(client, "entityRenderer", renderer);
        textures.bindAll();
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 55);
        verify(TestGl.gl()).glBindTexture(GL11.GL_TEXTURE_2D, 66);
        assertEquals(66, CustomTextureManager.getLightmapTextureId());
        // Without the lightmap or the resource the missing texture stands in
        Mixins.set(renderer, "lightmapTexture", null);
        when(manager.getTexture(any())).thenReturn(null);
        textures.bindAll();
        CustomTextureManager.getLightmapTextureId();
        textures.unbindAll();
        textures.destroy();
        assertTrue(textures.isEmpty());
        assertTrue(textures.getOverrides(TextureStage.COMPOSITE_AND_FINAL).isEmpty());

        // A noise texture that is not a pack PNG, or one that will not decode, keeps the generated noise
        when(pack.getCustomNoiseTexture()).thenReturn(new CustomTextureData.LightmapMarker());
        when(pack.getCustomTextureDataMap()).thenReturn(Map.of());
        when(pack.getUmbraCustomTextureDataMap()).thenReturn(Map.of());
        assertEquals(-1, new CustomTextureManager(pack, units, Map.of(), 24, 36).getNoiseTextureId());
        when(pack.getCustomNoiseTexture()).thenReturn(broken);
        assertEquals(-1, new CustomTextureManager(pack, units, Map.of(), 24, 36).getNoiseTextureId());
        when(pack.getCustomNoiseTexture()).thenReturn(null);
        assertTrue(new CustomTextureManager(pack, units, Map.of(), 24, 36).isEmpty());
    }
}
