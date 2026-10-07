package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gl.blending.ProgramAlphaTest;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.impetus.umbra.uniforms.EyeBrightnessTracker;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraRenderingPipelineTest {
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;

    private Minecraft client;
    private GameSettings settings;
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<GL14> gl14;
    private int defaultTexUnit;
    private int lightmapTexUnit;
    private boolean framebufferSupported;
    private final int[] boundProgram = new int[1];

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void freshContext() {
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        settings.fboEnable = true;
        settings.gammaSetting = 0.5F;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 320;
        client.displayHeight = 240;
        when(client.getFramebuffer()).thenReturn(mock(Framebuffer.class));
        TextureManager textures = mock(TextureManager.class);
        ITextureObject atlas = mock(ITextureObject.class);
        when(atlas.getGlTextureId()).thenReturn(3);
        when(textures.getTexture(any())).thenReturn(atlas);
        when(client.getTextureManager()).thenReturn(textures);
        framebufferSupported = OpenGlHelper.framebufferSupported;
        OpenGlHelper.framebufferSupported = true;
        defaultTexUnit = OpenGlHelper.defaultTexUnit;
        lightmapTexUnit = OpenGlHelper.lightmapTexUnit;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE0 + 1;
        // The shadow pass enters the engine's managed scope, whose device must exist but start inactive
        Devices.active().makeInactive();
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        gl14 = Mockito.mockStatic(GL14.class);

        when(TestGl.gl().glGetInteger(0x8872)).thenReturn(48);
        when(TestGl.gl().glGetInteger(0x8824)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x8CDF)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x8D57)).thenReturn(8);
        when(TestGl.gl().glGetInteger(0x90DD)).thenReturn(16);
        when(TestGl.gl().glGetInteger(0x90DE)).thenReturn(1 << 24);
        when(TestGl.gl().supportsBufferBlending()).thenReturn(true);
        Mockito.doNothing().when(TestGl.gl()).glEnablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glDisablei(anyInt(), anyInt());
        Mockito.doNothing().when(TestGl.gl()).glBlendFuncSeparatei(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        // Every program declares every uniform and sampler it is offered, at location 1
        when(TestGl.gl().glGetUniformLocation(anyInt(), any(CharSequence.class))).thenReturn(1);
        // GL_CURRENT_PROGRAM answers whatever was last bound
        Mockito.doAnswer(invocation -> boundProgram[0] = invocation.getArgument(0)).when(TestGl.gl()).glUseProgram(anyInt());
        when(TestGl.gl().glGetInteger(GL_CURRENT_PROGRAM)).thenAnswer(invocation -> boundProgram[0]);
    }

    @AfterEach
    void restore() {
        gl14.close();
        gl13.close();
        gl11.close();
        OpenGlHelper.framebufferSupported = framebufferSupported;
        OpenGlHelper.defaultTexUnit = defaultTexUnit;
        OpenGlHelper.lightmapTexUnit = lightmapTexUnit;
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        ActiveCustomUniforms.clear();
        CustomTextureTransformer.setActivePatches(null);
        ShaderMacros.setPackLegacyPrograms(Collections.emptySet());
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.SRGB);
        EyeBrightnessTracker.setHalfLives(600.0F, 200.0F, 10.0F);
        CelestialUniforms.setSunPathRotation(0.0F);
        SystemTimeUniforms.COUNTER.reset();
        WorldRenderingSettings.setOldLighting(true);
        WorldRenderingSettings.setAmbientOcclusionLevel(1.0F);
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.setTickDelta(0);
        state.setRenderStage(0);
        state.setShadowModelView(new Matrix4f());
        state.setShadowProjection(new Matrix4f());
        state.setGbufferProjection(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
    }

    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static final String LEGACY_VERTEX = "#version 120\nvarying vec2 texcoord;\nvoid main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; }";

    // A pack using nearly every directive family the pipeline schedules
    private static ShaderPack fullPack() throws IOException {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, String.join("\n",
                "texture.noise = tex/noise.png",
                "texture.composite.colortex5 = tex/noise.png",
                "texture.gbuffers.gaux4 = tex/noise.png",
                "customTexture.extra = tex/noise.png",
                "image.voxels = voxel_sampler rgba_integer rgba8ui unsigned_int true false 16 16 16",
                "bufferObject.0 = 64",
                "bufferObject.1 = 16 true 0.5 0.5",
                "indirect.shadowcomp = 0 0",
                "indirect.deferred_a = 7 0",
                "indirect.broken = x",
                "size.buffer.colortex8 = 64 32",
                "flip.composite.colortex3 = true",
                "flip.composite1.colortex1 = false",
                "flip.deferred_pre.colortex4 = true",
                "flip.composite_pre.colortex6 = true",
                "scale.composite1 = 0.5 0.25 0.25",
                "blend.composite = SRC_ALPHA ONE_MINUS_SRC_ALPHA",
                "blend.gbuffers_textured = off",
                "alphaTest.gbuffers_textured = GREATER 0.1",
                "program.composite3.enabled = false",
                "allowConcurrentCompute = true",
                "rain.depth = true",
                "beacon.beam.depth = true",
                "frustum.culling = false",
                "occlusion.culling = false",
                "separateEntityDraws = true",
                "particles.ordering = before",
                "backFace.solid = false",
                "shadowMapResolution = 256",
                "shadowDistance = 64",
                "shadow.culling = reversed",
                "shadowLightBlockEntities = true",
                "shadowBlockEntities = false",
                "uniform.float.myUniform = 1.0",
                "iris.features.optional = HIGHER_SHADOWCOLOR TESSELLATION_SHADERS"));
        sources.put(path("/block.properties"), "block.10 = stone\nlayer.translucent = glass\n");
        sources.put(path("/setup.csh"), "#version 430\nlayout(local_size_x = 8, local_size_y = 8) in;\nconst ivec3 workGroups = ivec3(2, 2, 1);\nvoid main() {}");
        sources.put(path("/begin.vsh"), LEGACY_VERTEX);
        sources.put(path("/begin.fsh"), "#version 120\n/* DRAWBUFFERS:8 */\nconst bool colortex8MipmapEnabled = true;\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/prepare.vsh"), LEGACY_VERTEX);
        sources.put(path("/prepare.fsh"), "#version 120\n/* DRAWBUFFERS:9 */\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/prepare.csh"), "#version 430\nlayout(local_size_x = 4) in;\nconst int workGroupsX = 3;\nvoid main() {}");
        // The voxel volume is 16^3 at local 8x8x1: a declared 1x1x1 does not cover it and is widened
        sources.put(path("/shadowcomp.csh"), "#version 430\nlayout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\nconst ivec3 workGroups = ivec3(1, 1, 1);\nvoid main() { imageStore(colorimg4, ivec2(0), vec4(0)); imageStore(shadowcolorimg2, ivec2(0), vec4(0)); imageStore(shadowcolorimg6, ivec2(0), vec4(0)); }");
        sources.put(path("/shadowcomp_a.csh"), "#version 430\nlayout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\nvoid main() {}");
        sources.put(path("/shadowcomp1.vsh"), LEGACY_VERTEX);
        // shadowcolor2 exists under HIGHER_SHADOWCOLOR; 9 is past even Iris's eight and is dropped
        sources.put(path("/shadowcomp1.fsh"), "#version 120\n/* DRAWBUFFERS:029 */\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/deferred.vsh"), "#version 330 compatibility\nvoid main() { gl_Position = ftransform(); }");
        sources.put(path("/deferred.fsh"), "#version 330 compatibility\n/* RENDERTARGETS: 0,4 */\nlayout(location = 0) out vec4 color;\nlayout(location = 1) out vec4 aux;\nuniform sampler2D colortex4;\nvoid main() { color = vec4(1.0); aux = vec4(0.0); }");
        sources.put(path("/deferred_a.csh"), "#version 430\nlayout(local_size_x = 16, local_size_y = 16) in;\nconst vec2 workGroupsRender = vec2(0.5f, 0.5f);\nvoid main() { imageStore(colorimg4, ivec2(0), vec4(0)); }");
        sources.put(path("/deferred1.csh"), "#version 430\nlayout(local_size_x = 1) in;\nvoid main() {}");
        sources.put(path("/composite.vsh"), LEGACY_VERTEX);
        sources.put(path("/composite.fsh"), String.join("\n",
                "#version 120",
                "/* DRAWBUFFERS:03 */",
                "/* GAUX4FORMAT:RGB32F */",
                "/* GAUX4FORMAT:RGBA8 */",
                "const int colortex3Format = RGBA16F;",
                "const int gaux2Format = BOGUS;",
                "const int shadowcolor0Format = RGBA8;",
                "const int shadowcolor2Format = RGBA16F;",
                "const int shadowcolor3Format = R32UI;",
                "const int shadowcolor4Format = BOGUS;",
                "const bool shadowcolor2Clear = false;",
                "const vec4 shadowcolor3ClearColor = vec4(1.0, 0.0, 0.0, 1.0);",
                "const bool generateShadowColorMipmap = true;",
                "const bool shadowcolor1Mipmap = false;",
                "const bool shadowColor2Mipmap = true;",
                "const bool shadowcolor0Nearest = true;",
                "const bool shadowColor1Nearest = true;",
                "const bool shadowColor2MinMagNearest = true;",
                "const bool colortex3Clear = false;",
                "const bool colortex2Clear = false;",
                "const vec4 colortex3ClearColor = vec4(1.0, 0.5, 0.25f, 1.0);",
                "const vec4 colortex2ClearColor = vec4(0.5);",
                "const vec4 colortex7ClearColor = vec4(1.0, 2.0);",
                "const vec4 colortex6ClearColor = vec4(a, b, c, d);",
                "const float centerDepthHalflife = 2.0;",
                "const int noiseTextureResolution = 16;",
                "const float ambientOcclusionLevel = 0.5;",
                "const float wetnessHalflife = 300.0;",
                "const bool colortex2MipmapEnabled = true;",
                "const bool colortex3MipmapEnabled = false;",
                "const bool bogusMipmapEnabled = true;",
                "uniform sampler2D colortex9;",
                "uniform sampler2D gaux1;",
                "void main() { gl_FragData[0] = texture2D(colortex9, vec2(0.0)); }"));
        sources.put(path("/composite1.vsh"), LEGACY_VERTEX);
        sources.put(path("/composite1.fsh"), "#version 120\n/* DRAWBUFFERS:1 */\nvoid main() { imageStore(shadowcolorimg0, ivec2(0), vec4(0)); gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/composite3.vsh"), LEGACY_VERTEX);
        sources.put(path("/composite3.fsh"), "#version 120\nuniform sampler2D shadowcolor3;\nvoid main() { gl_FragColor = texture2D(shadowcolor3, vec2(0.0)); }");
        sources.put(path("/final.vsh"), LEGACY_VERTEX);
        sources.put(path("/final.fsh"), "#version 120\nuniform sampler2D colortex0;\nvoid main() { gl_FragColor = texture2D(colortex0, vec2(0.0)); }");
        for (String gbuffer : new String[] {"gbuffers_basic", "gbuffers_textured", "gbuffers_textured_lit", "gbuffers_skybasic",
                "gbuffers_entities", "gbuffers_entities_translucent", "gbuffers_spidereyes", "gbuffers_armor_glint",
                "gbuffers_hand", "gbuffers_hand_water"}) {
            sources.put(path("/" + gbuffer + ".vsh"), "#version 120\nattribute vec4 mc_Entity;\nvoid main() { gl_Position = ftransform(); }");
            sources.put(path("/" + gbuffer + ".fsh"), "#version 120\n/* DRAWBUFFERS:024 */\nuniform sampler2D gaux1;\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        }
        // A tessellated entity program: both stages compile, and immediate draws under it become patches
        sources.put(path("/gbuffers_entities.tcs"), "#version 400 compatibility\nlayout(vertices = 3) out;\nvoid main() {}");
        sources.put(path("/gbuffers_entities.tes"), "#version 400 compatibility\nlayout(triangles) in;\nvoid main() {}");
        sources.put(path("/gbuffers_terrain.vsh"), "#version 120\nvoid main() { gl_Position = ftransform(); }");
        sources.put(path("/gbuffers_terrain.fsh"), "#version 120\n/* DRAWBUFFERS:0245 */\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/gbuffers_water.vsh"), "#version 120\nvoid main() { gl_Position = ftransform(); }");
        sources.put(path("/gbuffers_water.fsh"), "#version 120\n/* DRAWBUFFERS:06 */\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/shadow.vsh"), String.join("\n",
                "#version 120",
                "#define SHADOWRES 1024",
                "#define SHADOWHPL 90.5",
                "#define SHADOWFOV 90.0",
                "const float sunPathRotation = 25.0;",
                "const bool shadowHardwareFiltering = true;",
                "const bool shadowtex0Mipmap = true;",
                "const bool shadowtexMipmap = true;",
                "const bool shadowtexNearest = true;",
                "const bool shadow1MinMagNearest = true;",
                "const float shadowDistanceRenderMul = 0.5;",
                "const float voxelDistance = 32.0;",
                "void main() { gl_Position = ftransform(); }"));
        sources.put(path("/shadow.gsh"), "#version 150\nvoid main() {}");
        sources.put(path("/shadow.fsh"), "#version 120\n/* DRAWBUFFERS:01 */\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/shadow_block.vsh"), "#version 120\nvoid main() { gl_Position = ftransform(); }");
        sources.put(path("/shadow_block.fsh"), "#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        return new ShaderPack(sources, Map.of(), Map.of(path("/tex/noise.png"), png()));
    }

    // The world the frame renders: a player in it, a pig and a dead cow nearby, a furnace with no renderer
    private void enterWorld() {
        WorldClient world = mock(WorldClient.class);
        // Every built-in uniform supplier runs, so the world answers what they read
        Mixins.set(world, "provider", mock(net.minecraft.world.WorldProvider.class));
        when(world.getBiome(any())).thenReturn(net.minecraft.init.Biomes.PLAINS);
        when(world.getBlockState(any())).thenReturn(net.minecraft.init.Blocks.AIR.getDefaultState());
        when(world.getSkyColor(any(), anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0.5, 0.6, 0.9));
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        player.posY = 70;
        when(player.getEyeHeight()).thenReturn(1.62F);
        when(player.getHeldItemMainhand()).thenReturn(net.minecraft.item.ItemStack.EMPTY);
        when(player.getHeldItemOffhand()).thenReturn(net.minecraft.item.ItemStack.EMPTY);
        when(player.getLook(anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0, 0, 1));
        when(player.getPositionEyes(anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0, 71.62, 0));
        EntityPig pig = mock(EntityPig.class);
        EntityPig dead = mock(EntityPig.class);
        dead.isDead = true;
        EntityPig far = mock(EntityPig.class);
        far.posX = 10_000;
        List<Entity> entities = new ArrayList<>(List.of(player, pig, dead, far));
        Mixins.set(world, "loadedEntityList", entities);
        TileEntityFurnace furnace = new TileEntityFurnace();
        furnace.setPos(new BlockPos(1, 64, 1));
        Mixins.set(world, "loadedTileEntityList", new ArrayList<TileEntity>(List.of(furnace)));
        when(world.getCombinedLight(any(), anyInt())).thenReturn(0x00F000A0);
        client.world = world;
        client.player = player;
        when(client.getRenderViewEntity()).thenReturn(player);
        when(client.getRenderManager()).thenReturn(mock(RenderManager.class));
        RenderGlobal renderGlobal = Mc.mock(RenderGlobal.class, SimpleWorldRenderer.Provider.class);
        ImpetusWorldRenderer worldRenderer = mock(ImpetusWorldRenderer.class);
        when(((SimpleWorldRenderer.Provider<?>) renderGlobal).impetus$getWorldRenderer()).thenAnswer(invocation -> worldRenderer);
        client.renderGlobal = renderGlobal;
    }

    @Test
    void aFullPackBuildsItsScheduleAndRendersAFrame() throws IOException {
        ShaderPack pack = fullPack();
        UmbraRenderingPipeline pipeline = new UmbraRenderingPipeline(pack);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);

        // Directive-derived switches
        assertTrue(pipeline.shouldWriteRainAndSnowToDepthBuffer());
        assertTrue(pipeline.shouldWriteBeaconBeamToDepthBuffer());
        assertTrue(pipeline.shouldDisableFrustumCulling());
        assertTrue(pipeline.shouldDisableOcclusionCulling());
        assertFalse(pipeline.skipAllRendering());
        assertTrue(pipeline.shouldSeparateEntityDraws());
        assertEquals("before", pipeline.getParticleOrdering());
        assertFalse(pipeline.shouldCullBackFaces(0));
        assertTrue(pipeline.shouldCullBackFaces(3));
        assertTrue(pipeline.shouldCullBackFaces(-1));
        assertTrue(pipeline.shouldCullBackFaces(9));
        assertTrue(pipeline.shouldDisableVanillaEntityShadows());
        assertNotNull(pipeline.getShadowRenderer());
        assertEquals(256, pipeline.getShadowRenderer().getResolution());
        assertNotNull(pipeline.getDhCompat());
        assertEquals(0.5F, WorldRenderingSettings.getAmbientOcclusionLevel());
        assertEquals(25.0F, CelestialUniforms.getSunPathRotation());

        // Nothing happens outside a frame
        assertFalse(pipeline.isWorldRenderingActive());
        pipeline.setPhase(ProgramId.Entities);
        pipeline.setLightmapEnabled(true);
        pipeline.refreshDynamicUniforms();
        pipeline.beginEyes();
        pipeline.endEyes();
        pipeline.beginArmorGlint();
        pipeline.endArmorGlint();
        pipeline.drawSkyHorizon();
        pipeline.onTerrainDraw(new int[] {0}, ProgramBlendState.empty());
        pipeline.afterTerrainDraw(1);
        pipeline.captureRenderingState();
        pipeline.renderShadowMap();
        pipeline.beginTranslucents();
        pipeline.beginHand();
        assertFalse(pipeline.beginHandRendering());
        pipeline.finishWorldRendering();
        assertFalse(pipeline.hasGbufferProgram(ProgramId.Entities));
        assertFalse(pipeline.hasDirectGbufferProgram(ProgramId.Entities));
        assertFalse(pipeline.isRenderingPostDeferredTranslucents());
        assertFalse(pipeline.isCameraPassActive());

        enterWorld();
        for (int frame = 0; frame < 2; frame++) {
            renderFrame(pipeline);
            // The second frame runs at a new size, rebuilding the screen-relative resources, with wide-gamut output on
            client.displayWidth = 400;
            client.displayHeight = 300;
            ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.DCI_P3);
        }
        verify(TestGl.gl(), Mockito.atLeastOnce()).glDispatchCompute(anyInt(), anyInt(), anyInt());
        verify(TestGl.gl(), Mockito.atLeastOnce()).glDispatchComputeIndirect(anyLong());
        // The shadow pass walks both lists through setupShadowTerrain, the player's walk with the player's view (unculled, this pack turns frustum culling off), never through the camera pass's entry
        ImpetusWorldRenderer worldRenderer = ImpetusWorldRenderer.instanceNullable();
        ArgumentCaptor<Viewport> playerView = ArgumentCaptor.forClass(Viewport.class);
        verify(worldRenderer, Mockito.atLeastOnce()).setupShadowTerrain(playerView.capture(), any(), any(), anyInt(), eq(false));
        verify(worldRenderer, never()).setupTerrain(any(), any(), anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());
        assertTrue(playerView.getValue().isBoxVisible(-1.0e6, -1.0e6, -1.0e6, -1.0e6 + 1, -1.0e6 + 1, -1.0e6 + 1));

        // A program that declared both shadow maps as plain sampler2D gets the raw-depth samplers
        when(TestGl.gl().glGetProgrami(77, 0x8B86)).thenReturn(2);
        when(TestGl.gl().glGetActiveUniform(eq(77), anyInt(), eq(256), any(IntBuffer.class))).thenAnswer(invocation -> {
            IntBuffer sizeType = invocation.getArgument(3);
            sizeType.put(1, 0x8B5E);
            return invocation.<Integer>getArgument(1) == 0 ? "shadowtex0" : "shadowtex1";
        });
        pipeline.applyShadowSamplerKinds(ShadowSamplerKinds.detect(77), false);
        UmbraShadowRenderer shadow = pipeline.getShadowRenderer();
        // Eight addressable shadowcolor buffers; only the touched ones exist, each in its own format
        assertEquals(8, shadow.getColorBufferCount());
        assertNotEquals(0, shadow.getColorTextureId(2));
        assertNotEquals(0, shadow.getColorTextureId(3));
        assertNotEquals(0, shadow.getColorTextureId(6));
        assertEquals(0, shadow.getColorTextureId(5));
        assertEquals(0, shadow.getColorTextureId(9));
        assertEquals(0x881A, shadow.getColorInternalFormat(2));
        verify(TestGl.gl(), Mockito.atLeastOnce()).glClearTexImage(anyInt(), eq(0), anyInt(), eq(GL11.GL_INT), any(java.nio.ByteBuffer.class));
        verify(TestGl.gl(), Mockito.atLeastOnce()).glAttachShader(anyInt(), anyInt());
        assertNotNull(shadow.getFramebuffer());
        assertNotNull(shadow.getCullingFrustum());
        assertNotNull(shadow.getShadowModelView());
        assertNotNull(shadow.getShadowProjection());
        pipeline.applyShadowSamplerKinds(null, true);
        pipeline.bindCustomImages();
        UmbraRenderingPipeline.assignSamplerUnitsToBoundProgram(5);
        pipeline.destroy();
        pipeline.destroy();
        // After teardown the pipeline is inert
        pipeline.beginWorldRendering(0.5F);
        assertFalse(pipeline.isWorldRenderingActive());
        assertFalse(pipeline.beginHandRendering());
        pipeline.applyShadowSamplerKinds(ShadowSamplerKinds.ALL_COMPARE, false);
        pipeline.bindCustomImages();
    }

    private void renderFrame(UmbraRenderingPipeline pipeline) {
        pipeline.beginWorldRendering(0.5F);
        assertTrue(pipeline.isWorldRenderingActive());
        assertTrue(pipeline.isCameraPassActive());
        pipeline.captureRenderingState();
        pipeline.renderShadowMap();
        pipeline.setPhase(ProgramId.SkyBasic);
        pipeline.drawSkyHorizon();
        pipeline.setPhase(ProgramId.SkyTextured);
        pipeline.setPhase(ProgramId.Clouds);
        pipeline.setPhase(ProgramId.Entities);
        assertEquals(ProgramId.Entities, pipeline.getCurrentPhase());
        assertTrue(pipeline.hasGbufferProgram(ProgramId.Entities));
        assertTrue(pipeline.hasDirectGbufferProgram(ProgramId.Entities));
        pipeline.refreshDynamicUniforms();
        pipeline.beginEyes();
        pipeline.endEyes();
        pipeline.beginArmorGlint();
        pipeline.endArmorGlint();
        pipeline.endArmorGlint();
        // The lightmap toggle swaps only the textured pair
        pipeline.setPhase(ProgramId.Textured);
        pipeline.setLightmapEnabled(true);
        assertEquals(ProgramId.TexturedLit, pipeline.getCurrentPhase());
        pipeline.setLightmapEnabled(true);
        pipeline.setLightmapEnabled(false);
        assertEquals(ProgramId.Textured, pipeline.getCurrentPhase());
        pipeline.setLightmapEnabled(false);
        // A phase no program covers draws fixed-function into colortex0
        pipeline.setPhase(ProgramId.DamagedBlock, 13);
        pipeline.setPhase(null);
        pipeline.refreshDynamicUniforms();
        pipeline.setPhase(ProgramId.Particles);
        boundProgram[0] = 0;
        pipeline.refreshDynamicUniforms();

        // Terrain draws redirect the gbuffer mask, including a target nothing attached
        pipeline.onTerrainDraw(new int[] {0, 2, 9}, ProgramBlendState.empty());
        pipeline.onTerrainDraw(new int[] {0, 6}, ProgramBlendState.from(ShaderProperties.parse("blend.w = ONE ONE"), "w"),
                ProgramAlphaTest.from(ShaderProperties.parse("alphaTest.w = GREATER 0.5"), "w"), true);
        pipeline.afterTerrainDraw(2);
        UmbraFramebuffer lod = pipeline.createDhFramebuffer(new int[] {0, 4}, false);
        pipeline.onDhLodDraw(lod, new int[] {0, 4}, ProgramBlendState.empty(), false);
        pipeline.onDhLodDraw(lod, new int[] {0, 4}, ProgramBlendState.empty(), true);
        pipeline.afterDhLodDraw(2);
        lod.destroy();
        when(TestGl.gl().glGetBoolean(GL11.GL_ALPHA_TEST)).thenReturn(true);
        UmbraFramebuffer translucentLod = pipeline.createDhFramebuffer(new int[] {0, -1}, true);
        pipeline.onDhLodDraw(translucentLod, new int[] {0}, ProgramBlendState.empty(), false);
        pipeline.afterDhLodDraw(1);
        translucentLod.destroy();

        pipeline.beginTranslucents();
        assertTrue(pipeline.isRenderingPostDeferredTranslucents());
        assertEquals(ProgramId.EntitiesTrans, pipeline.getTranslucentEntityPhase());
        pipeline.beginHand();
        assertTrue(pipeline.beginHandRendering());
        pipeline.endHandRendering();
        assertTrue(pipeline.beginHandTranslucentRendering());
        pipeline.endHandRendering();
        // The shadow pass never selects gbuffer phases
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        pipeline.setPhase(ProgramId.Entities);
        pipeline.refreshDynamicUniforms();
        pipeline.beginEyes();
        pipeline.endEyes();
        pipeline.beginArmorGlint();
        pipeline.afterTerrainDraw(1);
        assertFalse(pipeline.isCameraPassActive());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        pipeline.finishWorldRendering();
        assertFalse(pipeline.isWorldRenderingActive());
    }

    // No shadow pass, no deferred chain, no final program: colortex0 is blitted, prepare runs before the (absent) shadow map
    @Test
    void aMinimalPackFallsBackOnEveryOptionalStage() {
        Map<AbsolutePackPath, String> sources = new HashMap<>();
        sources.put(ShaderPack.PROPERTIES_PATH, String.join("\n",
                "prepareBeforeShadow = true",
                "iris.features.optional = SEPARATE_HARDWARE_SAMPLERS",
                "program.prepare1.enabled = false",
                "program.final.enabled = false"));
        sources.put(path("/prepare.csh"), "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}");
        sources.put(path("/prepare1.csh"), "#version 430\nlayout(local_size_x = 8) in;\nvoid main() {}");
        sources.put(path("/shadowcomp.vsh"), LEGACY_VERTEX);
        sources.put(path("/shadowcomp.fsh"), "#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/composite.vsh"), LEGACY_VERTEX);
        // colortex0 keeps last frame's contents and ends the chain flipped, so it is copied back after final
        sources.put(path("/composite.fsh"), "#version 120\n/* DRAWBUFFERS:0 */\nconst bool colortex0Clear = false;\nvoid main() { gl_FragData[0] = vec4(1.0); }");
        sources.put(path("/final.vsh"), LEGACY_VERTEX);
        sources.put(path("/final.fsh"), "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }");
        ShaderPack pack = new ShaderPack(sources);
        UmbraRenderingPipeline pipeline = new UmbraRenderingPipeline(pack);
        assertNull(pipeline.getShadowRenderer());
        assertFalse(pipeline.shouldDisableVanillaEntityShadows());
        assertEquals("mixed", pipeline.getParticleOrdering());
        OpenGlHelper.framebufferSupported = false;
        pipeline.beginWorldRendering(0.0F);
        pipeline.renderShadowMap();
        pipeline.beginTranslucents();
        assertFalse(pipeline.isRenderingPostDeferredTranslucents());
        assertEquals(ProgramId.Entities, pipeline.getTranslucentEntityPhase());
        // No hand program: fixed-function into colortex0
        assertTrue(pipeline.beginHandRendering());
        pipeline.endHandRendering();
        pipeline.applyShadowSamplerKinds(ShadowSamplerKinds.ALL_COMPARE, false);
        pipeline.finishWorldRendering();
        verify(TestGl.gl(), Mockito.atLeastOnce()).glBlitFramebuffer(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), eq(320), eq(240), anyInt(), anyInt());
        pipeline.destroy();
    }

    @Test
    void aPipelineThatCannotBuildReleasesWhatItMade() {
        ShaderPack pack = new ShaderPack(Map.of(path("/final.vsh"), LEGACY_VERTEX, path("/final.fsh"), "void main() {}"));
        when(TestGl.gl().glCheckFramebufferStatus(anyInt())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> new UmbraRenderingPipeline(pack));
        verify(TestGl.gl(), Mockito.atLeastOnce()).glDeleteSamplers(anyInt());
    }

    @Test
    void theShadowPassGivesThePlayerWalkTheCapturedPlayerView() {
        // Identity matrices make the view the clip cube around the feet, and an identity model-view adds no third-person offset
        Mixins.set(Umbra.class, "renderingPipeline", null);
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        float[] saved = new float[16];
        modelView.get(0, saved);
        modelView.put(0, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f());
        CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f());
        try {
            Viewport view = UmbraShadowRenderer.playerViewport(new Vector3d(100.5, 64, 100.5));
            assertEquals(6, view.getChunkCoord().x());
            assertEquals(4, view.getChunkCoord().y());
            assertTrue(view.isBoxVisible(100, 64, 100, 101, 65, 101));
            assertFalse(view.isBoxVisible(110, 64, 100, 111, 65, 101));
        } finally {
            modelView.put(0, saved);
        }
    }

    @Test
    void theShaderSourceHelpersFixWhatDriversReject() {
        assertEquals("#if 0\n#endif", UmbraRenderingPipeline.foldUncompilableConditionals("x", "#if 1.5 > 2.0\n#endif"));
        String modern = String.join("\n",
                "#version 430",
                "vec4 lightVolume;",
                "vec4 a = texture2DGradARB(tex, uv, dx, dy) + texture2DLodARB(tex, uv, 0.0);");
        String stabilized = UmbraRenderingPipeline.stabilizeShaderSource("x", modern);
        assertTrue(stabilized.contains("vec4 lightVolume = vec4(0.0);"), stabilized);
        assertTrue(stabilized.contains("textureGrad(tex, uv, dx, dy)"), stabilized);
        assertTrue(stabilized.contains("textureLod(tex, uv, 0.0)"), stabilized);
        // GLSL 120 keeps the ARB names, which only exist there
        String legacy = "#version 120\nvec4 a = texture2DLodARB(tex, uv, 0.0);";
        assertEquals(legacy, UmbraRenderingPipeline.stabilizeShaderSource("x", legacy));
        assertEquals("void main() {}", UmbraRenderingPipeline.stabilizeShaderSource("x", "void main() {}"));
        assertArrayEquals(new int[] {0, 3}, UmbraRenderingPipeline.sanitizeDrawBuffers("x", new int[] {0, 3, 99}));

        when(TestGl.gl().glGetError()).thenReturn(0x502, 0x500, 0, 0x501, 0);
        UmbraRenderingPipeline.drainGlError();
        UmbraRenderingPipeline.reportGlError("here");
        UmbraRenderingPipeline.reportGlError("there");
        UmbraRenderingPipeline.resetVanillaVertexArrayState();
        verify(TestGl.gl()).glBindVertexArray(0);
        verify(TestGl.gl(), never()).glDispatchCompute(anyInt(), anyInt(), anyInt());
    }
}
