package com.bdmajora.impetus.umbra.uniforms;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.uniforms.custom.CustomUniformInputs;
import com.bdmajora.impetus.umbra.uniforms.custom.CustomUniformValue;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiBossOverlay;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.FoodStats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UniformSurfaceTest {
    private Minecraft client;
    private GameSettings settings;
    private Object previousConfig;
    private boolean framebufferSupported;
    private Map<String, Supplier<CustomUniformValue>> table;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshClient() throws ClassNotFoundException {
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        settings.gammaSetting = 0.5F;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 800;
        client.displayHeight = 600;
        // Initialised first so its own config load cannot overwrite the one installed here
        Class.forName(ImpetusVintage.class.getName());
        previousConfig = Statics.get(ImpetusVintage.class, "CONFIG");
        Statics.set(ImpetusVintage.class, "CONFIG", ImpetusGameOptions.defaults());
        framebufferSupported = OpenGlHelper.framebufferSupported;
        CustomUniformInputs inputs = new CustomUniformInputs();
        CommonUniforms.addCommonUniforms(inputs);
        MatrixUniforms.addMatrixUniforms(inputs);
        table = Mixins.get(inputs, "inputs");
    }

    @AfterEach
    void restore() {
        Statics.set(ImpetusVintage.class, "CONFIG", previousConfig);
        OpenGlHelper.framebufferSupported = framebufferSupported;
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.setTickDelta(0);
        state.setShadowModelView(new Matrix4f());
        state.setShadowProjection(new Matrix4f());
        state.setGbufferProjection(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
        WorldRenderingSettings.setOldHandLight(true);
        WorldRenderingSettings.setDynamicHandLight(true);
        EyeBrightnessTracker.setHalfLives(600.0F, 200.0F, 10.0F);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        CelestialUniforms.setSunPathRotation(0.0F);
        SystemTimeUniforms.COUNTER.reset();
    }

    private CustomUniformValue resolve(String name) {
        return table.get(name).get();
    }

    // Every registered supplier, twice with a frame in between so the per-frame caches and the previous-frame matrices roll
    private Map<String, CustomUniformValue> resolveAll(MockedStatic<GL11> gl11) {
        Map<String, CustomUniformValue> values = new TreeMap<>();
        for (int pass = 0; pass < 2; pass++) {
            SystemTimeUniforms.COUNTER.beginFrame(System.nanoTime());
            table.forEach((name, supplier) -> values.put(name, supplier.get()));
        }
        return values;
    }

    @Test
    void withoutAWorldEveryUniformHasASafeDefault() {
        Map<String, CustomUniformValue> v;
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            v = resolveAll(gl11);
        }
        assertTrue(v.size() > 250, String.valueOf(v.size()));
        assertEquals(1.0F, v.get("hasSkylight").x());
        assertEquals(0.0F, v.get("hasCeiling").x());
        assertEquals(0.0F, v.get("ambientLight").x());
        assertEquals(256.0F, v.get("heightLimit").x());
        assertEquals(256.0F, v.get("logicalHeightLimit").x());
        assertEquals(-1.0F, v.get("biome").x());
        assertEquals(0.0F, v.get("biome_category").x());
        assertEquals(0.0F, v.get("temperature").x());
        assertEquals(128.0F, v.get("far").x());
        assertEquals(800.0F / 600.0F, v.get("aspectRatio").x(), 1.0e-6F);
        assertEquals(1.0F, v.get("isRightHanded").x());
        assertEquals(-1.0F, v.get("currentPlayerHealth").x());
        assertEquals(-1.0F, v.get("maxPlayerAir").x());
        assertEquals(0.0F, v.get("isSpectator").x());
        assertEquals("vec3(-256.0, -256.0, -256.0)", v.get("currentSelectedBlockPos").toString());
        assertEquals("vec4(0.0, 0.0, 0.0, 0.0)", v.get("lightningBoltPosition").toString());
        assertEquals("vec3(0.0, 0.0, 0.0)", v.get("endFlashPosition").toString());
        assertEquals(0.0F, v.get("fogMode").x());
        assertEquals(0.0F, v.get("fogDensity").x());
        assertEquals(102.4F, v.get("fogStart").x(), 1.0e-4F);
        assertEquals(128.0F, v.get("fogEnd").x());
        assertEquals(128.0F, v.get("dhFarPlane").x());
        assertEquals(0.05F, v.get("dhNearPlane").x());
        assertEquals(128.0F, v.get("dhRenderDistance").x());
        assertEquals(8.0F, v.get("vxRenderDistance").x());
        assertEquals(800.0F, v.get("viewWidth").x());
        assertEquals(600.0F, v.get("viewHeight").x());
        assertEquals(1.0F / 800.0F, v.get("pixelSizeX").x(), 1.0e-9F);
        assertEquals(0.5F, v.get("screenBrightness").x());
        assertEquals(0.0F, v.get("blendFunc").x());
        assertEquals((float) Math.PI, v.get("pi").x());
        assertEquals(20.0F, v.get("maxPlayerHunger").x());
        // A missing window reports a square and no texel size rather than dividing by zero
        client.displayWidth = 0;
        client.displayHeight = 0;
        assertEquals(1.0F, resolve("aspectRatio").x());
        assertEquals(0.0F, resolve("pixelSizeX").x());
        assertEquals(0.0F, resolve("pixelSizeY").x());
    }

    // A player standing in water in an End-like dimension, riding a boat, lit, hurt, burning and holding two lights
    private WorldClient world;
    private EntityPlayerSP player;
    private WorldProvider provider;

    private void enterWorld() {
        world = mock(WorldClient.class);
        provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(1);
        when(provider.doesXZShowFog(anyInt(), anyInt())).thenReturn(true);
        Mixins.set(world, "provider", provider);
        EntityLightningBolt bolt = Mc.uninitialized(EntityLightningBolt.class);
        bolt.posX = 10;
        EntityDragon dragon = Mc.uninitialized(EntityDragon.class);
        dragon.deathTicks = 5;
        Mixins.set(world, "loadedEntityList", new ArrayList<>(List.of(Mc.uninitialized(EntityBoat.class), bolt, dragon)));
        when(world.getBiome(any())).thenReturn(Biomes.SWAMPLAND);
        when(world.getRainStrength(anyFloat())).thenReturn(0.5F);
        when(world.getThunderStrength(anyFloat())).thenReturn(2.0F);
        when(world.getWorldTime()).thenReturn(30000L);
        when(world.getMoonPhase()).thenReturn(3);
        when(world.getTotalWorldTime()).thenReturn(100L);
        when(world.getSeaLevel()).thenReturn(63);
        when(world.getHeight()).thenReturn(256);
        when(world.getActualHeight()).thenReturn(128);
        when(world.getCelestialAngle(anyFloat())).thenReturn(0.8F);
        when(world.getSkyColor(any(), anyFloat())).thenReturn(new Vec3d(0.1, 0.2, 0.3));
        when(world.isBlockLoaded(any())).thenReturn(true);
        when(world.getBlockState(any())).thenReturn(Blocks.WATER.getDefaultState());

        player = mock(EntityPlayerSP.class);
        when(player.getPrimaryHand()).thenReturn(EnumHandSide.LEFT);
        when(player.isSneaking()).thenReturn(true);
        when(player.isSprinting()).thenReturn(true);
        player.hurtTime = 5;
        when(player.isInvisible()).thenReturn(true);
        when(player.isBurning()).thenReturn(true);
        player.onGround = true;
        player.posY = 3;
        when(player.isInWater()).thenReturn(true);
        when(player.isRiding()).thenReturn(true);
        when(player.isElytraFlying()).thenReturn(true);
        EntityBoat boat = mock(EntityBoat.class);
        when(boat.isInWater()).thenReturn(true);
        when(boat.getLook(anyFloat())).thenReturn(new Vec3d(0, 0, 1));
        when(player.getRidingEntity()).thenReturn(boat);
        when(player.getHealth()).thenReturn(10.0F);
        when(player.getMaxHealth()).thenReturn(20.0F);
        when(player.getFoodStats()).thenReturn(new FoodStats());
        when(player.getTotalArmorValue()).thenReturn(10);
        when(player.getAir()).thenReturn(150);
        when(player.getHeldItemMainhand()).thenReturn(new ItemStack(Blocks.TORCH));
        when(player.getHeldItemOffhand()).thenReturn(new ItemStack(Blocks.GLOWSTONE));
        when(player.isPotionActive(any())).thenReturn(true);
        when(player.getActivePotionEffect(MobEffects.NIGHT_VISION)).thenReturn(new PotionEffect(MobEffects.NIGHT_VISION, 100));
        when(player.getLook(anyFloat())).thenReturn(new Vec3d(1, 0, 0));
        when(player.getPositionEyes(anyFloat())).thenReturn(new Vec3d(0, 4.62, 0));
        when(player.getEyeHeight()).thenReturn(1.62F);
        when(player.getBrightnessForRender()).thenReturn(240 << 16 | 120);
        player.rotationYaw = 90;

        client.world = world;
        client.player = player;
        when(client.getRenderViewEntity()).thenReturn(player);
        PlayerControllerMP controller = mock(PlayerControllerMP.class);
        when(controller.isNotCreative()).thenReturn(true);
        client.playerController = controller;
        client.objectMouseOver = new RayTraceResult(new Vec3d(1, 2, 3), EnumFacing.UP, new BlockPos(1, 2, 3));
        GuiIngame gui = mock(GuiIngame.class);
        GuiBossOverlay bosses = mock(GuiBossOverlay.class);
        when(bosses.shouldCreateFog()).thenReturn(true);
        when(gui.getBossOverlay()).thenReturn(bosses);
        client.ingameGUI = gui;
        OpenGlHelper.framebufferSupported = true;
        settings.fboEnable = true;
        Framebuffer main = mock(Framebuffer.class);
        main.framebufferWidth = 1024;
        main.framebufferHeight = 512;
        when(client.getFramebuffer()).thenReturn(main);
        when(TestGl.gl().glGetInteger(GL11.GL_BLEND)).thenReturn(1);
        when(TestGl.gl().glGetInteger(GL11.GL_FOG)).thenReturn(1);
        when(TestGl.gl().glGetInteger(GL11.GL_FOG_MODE)).thenReturn(GL11.GL_EXP2);
        when(TestGl.gl().glGetFloat(0x0B62)).thenReturn(0.25F);
    }

    // The live fixed-function matrices read back as identity
    private static void identityReadback(MockedStatic<GL11> gl11) {
        gl11.when(() -> GL11.glGetFloat(anyInt(), any(FloatBuffer.class))).thenAnswer(invocation -> {
            FloatBuffer buffer = invocation.getArgument(1);
            new Matrix4f().get(buffer);
            return null;
        });
    }

    @Test
    void insideAWorldTheUniformsReadThePlayerAndCamera() {
        enterWorld();
        CapturedRenderingState.INSTANCE.setTickDelta(0.5F);
        CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f().translate(0, -1.62F, 0));
        Map<String, CustomUniformValue> v;
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            identityReadback(gl11);
            EyeBrightnessTracker.update();
            EyeBrightnessTracker.update();
            v = resolveAll(gl11);
        }
        assertEquals(0.0F, v.get("isRightHanded").x());
        for (String flag : new String[] {"is_sneaking", "is_sprinting", "is_hurt", "is_invisible", "is_burning", "is_on_ground",
                "feetInWater", "inSwimmingAnimation", "isRiding", "isElytraFlying", "vehicleInWater", "hasCeiling", "heavyFog"}) {
            assertEquals(1.0F, v.get(flag).x(), flag);
        }
        assertEquals(1.0F, v.get("isEyeInWater").x());
        assertEquals(0.5F, v.get("currentPlayerHealth").x());
        assertEquals(20.0F, v.get("maxPlayerHealth").x());
        assertEquals(1.0F, v.get("currentPlayerHunger").x());
        assertEquals(0.2F, v.get("currentPlayerArmor").x());
        assertEquals(0.5F, v.get("currentPlayerAir").x());
        assertEquals(300.0F, v.get("maxPlayerAir").x());
        assertEquals(1.0F, v.get("thunderStrength").x());
        assertEquals(6000.0F, v.get("worldTime").x());
        assertEquals(1.0F, v.get("worldDay").x());
        assertEquals(3.0F, v.get("moonPhase").x());
        assertEquals(63.0F, v.get("seaLevel").x());
        assertEquals(128.0F, v.get("logicalHeightLimit").x());
        assertEquals(0.1F, v.get("ambientLight").x());
        assertEquals(Biome.getIdForBiome(Biomes.SWAMPLAND), (int) v.get("biome").x());
        assertEquals(14.0F, v.get("biome_category").x());
        assertEquals(1.0F, v.get("biome_precipitation").x());
        // Swamp fog is the thick kind
        assertEquals(128.0F * 0.05F, v.get("fogStart").x(), 1.0e-4F);
        assertEquals(GL11.GL_EXP2, (int) v.get("fogMode").x());
        assertEquals(0.25F, v.get("fogDensity").x());
        assertEquals(1024.0F, v.get("viewWidth").x());
        assertEquals(512.0F, v.get("viewHeight").x());
        // The brighter offhand glowstone wins the hand light
        assertEquals(15.0F, v.get("heldBlockLightValue").x());
        assertEquals(15.0F, v.get("heldBlockLightValue2").x());
        assertEquals("vec3(1.0, 1.0, 1.0)", v.get("heldBlockLightColor").toString());
        assertEquals(1.0F, v.get("blindness").x());
        assertEquals(1.0F, v.get("blindFactor").x());
        assertEquals(0.0F, v.get("darknessFactor").x());
        assertTrue(v.get("nightVision").x() > 0.3F);
        assertEquals(1.0F, v.get("lightningBoltPosition").components[3]);
        assertNotEquals("vec3(0.0, 0.0, 0.0)", v.get("endFlashPosition").toString());
        assertNotEquals("vec3(-256.0, -256.0, -256.0)", v.get("currentSelectedBlockPos").toString());
        assertEquals(120.0F, EyeBrightnessTracker.getEyeBrightness().x);
        assertEquals(240.0F, EyeBrightnessTracker.getEyeBrightness().y);
        assertTrue(EyeBrightnessTracker.getWetness() > 0.0F);
    }

    @Test
    void theOtherBranchesOfTheWorldState() {
        enterWorld();
        when(provider.isSurfaceWorld()).thenReturn(true);
        when(provider.getDimension()).thenReturn(0);
        when(provider.doesXZShowFog(anyInt(), anyInt())).thenReturn(false);
        when(client.ingameGUI.getBossOverlay().shouldCreateFog()).thenReturn(false);
        when(world.getBlockState(any())).thenReturn(Blocks.LAVA.getDefaultState());
        when(world.getCelestialAngle(anyFloat())).thenReturn(0.5F);
        when(player.getActivePotionEffect(MobEffects.NIGHT_VISION)).thenReturn(new PotionEffect(MobEffects.NIGHT_VISION, 1000));
        when(player.getRidingEntity()).thenReturn(null);
        when(player.isPotionActive(any())).thenReturn(false);
        when(player.isPotionActive(MobEffects.NIGHT_VISION)).thenReturn(true);
        when(TestGl.gl().glGetFloat(0x0B62)).thenReturn(Float.NaN);
        client.playerController = null;
        WorldRenderingSettings.setOldHandLight(false);
        WorldRenderingSettings.setDynamicHandLight(false);
        EyeBrightnessTracker.setHalfLives(0.0F, 0.0F, 0.0F);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        // A singular projection inverts to identity instead of NaN
        CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f().zero());
        CapturedRenderingState.INSTANCE.setShadowProjection(new Matrix4f().zero());
        Map<String, CustomUniformValue> v;
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            EyeBrightnessTracker.update();
            v = resolveAll(gl11);
        }
        assertEquals(2.0F, v.get("isEyeInWater").x());
        assertEquals(0.0F, v.get("hasCeiling").x());
        assertEquals(1.0F, v.get("hasSkylight").x());
        assertEquals(0.0F, v.get("ambientLight").x());
        assertEquals(102.4F, v.get("fogStart").x(), 1.0e-4F);
        assertEquals(0.0F, v.get("fogDensity").x());
        assertEquals(1.0F, v.get("nightVision").x());
        assertEquals(0.0F, v.get("blindness").x());
        assertEquals(0.0F, v.get("heldBlockLightValue").x());
        assertEquals("vec3(0.0, 0.0, 0.0)", v.get("heldBlockLightColor2").toString());
        assertEquals(0.0F, v.get("vehicleId").x());
        assertEquals("vec3(0.0, 0.0, 0.0)", v.get("relativeVehiclePosition").toString());
        assertEquals(-1.0F, v.get("currentPlayerHealth").x());
        assertEquals(1.0F, v.get("gbufferProjectionInverse.0.0").x());
        assertEquals(1.0F, v.get("iris_ProjMatInverse.0.0").x());
        assertEquals("vec3(0.0, 0.0, 0.0)", v.get("endFlashPosition").toString());

        // Every vanilla biome falls into some category
        Set<Float> categories = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            when(world.getBiome(any())).thenReturn(biome);
            categories.add(resolve("biome_category").x());
            resolve("biome_precipitation");
        }
        assertTrue(categories.size() > 12, categories.toString());
        // A block the crosshair points at in an unloaded chunk, or no block at all, has no id
        when(world.isBlockLoaded(any())).thenReturn(false);
        assertEquals(0.0F, resolve("currentSelectedBlockId").x());
        client.objectMouseOver = new RayTraceResult(Mc.uninitialized(EntityBoat.class));
        assertEquals(0.0F, resolve("currentSelectedBlockId").x());
        assertEquals("vec3(-256.0, -256.0, -256.0)", resolve("currentSelectedBlockPos").toString());
    }

    @Test
    void theCameraTrackerRecentresFarFromTheOrigin() {
        enterWorld();
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        CameraUniforms.attach(notifier);
        int[] frames = new int[1];
        notifier.addListener(() -> frames[0]++);
        notifier.onNewFrame();
        player.posX = 40000;
        player.lastTickPosX = 40000;
        notifier.onNewFrame();
        assertEquals(2, frames[0]);
        // The shader-facing float is pulled back inside the walk range, the unshifted value is not
        assertTrue(Math.abs(CameraUniforms.getCurrentCameraPosition().x) < 30000.0, String.valueOf(CameraUniforms.getCurrentCameraPosition().x));
        assertEquals(40000.0, CameraUniforms.getCurrentCameraPositionUnshifted().x);
        assertEquals(40000.0, CameraUniforms.getCurrentRenderOriginUnshifted().x);
        assertEquals(40000, CameraUniforms.getCameraPositionInt(CameraUniforms.getCurrentCameraPositionUnshifted()).x);
        assertNotNull(CameraUniforms.getPreviousCameraPositionUnshifted());
        player.posX = 0;
        player.lastTickPosX = 0;
        notifier.onNewFrame();
        when(client.getRenderViewEntity()).thenReturn(null);
        notifier.onNewFrame();
        notifier.onNewFrame();
    }

    @Test
    void celestialDirectionsFollowTheSunPath() {
        enterWorld();
        CelestialUniforms.setSunPathRotation(30.0F);
        assertEquals(30.0F, CelestialUniforms.getSunPathRotation());
        // Morning casts from the rising sun, midnight from the moon overhead; either way the shadow light is up
        assertTrue(CelestialUniforms.getShadowLightPositionInWorldSpace().y > 0);
        when(world.getCelestialAngle(anyFloat())).thenReturn(0.5F);
        assertEquals(86.6F, CelestialUniforms.getShadowLightPositionInWorldSpace().y, 0.1F);
        assertEquals(0.25F, CelestialUniforms.getShadowAngle(), 1.0e-6F);
        assertEquals(100.0F, CelestialUniforms.getUpPosition().y, 1.0e-4F);
    }

    @Test
    void theCapturedStateRebasesOnTheCamera() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.setGbufferModelView(new Matrix4f().translate(0, -1.5F, 0));
        assertEquals(1.5, state.getCameraOffset().y, 1.0e-6);
        state.setGbufferModelView(new Matrix4f().zero());
        // A singular capture leaves the offset at zero
        assertEquals(0.0, state.getCameraOffset().y);
        assertEquals(1.5, state.getPreviousCameraOffset().y, 1.0e-6);
        state.setShadowModelView(new Matrix4f().translate(1, 0, 0));
        assertEquals(1.0F, state.getShadowModelView().m30());
        assertEquals(1.0F, state.getShadowModelViewCameraCentered().m30());
        state.setCameraPosition(1, 2, 3);
        assertEquals(2.0, state.getCameraPosition().y);
        state.setAtlasSize(512, 256);
        assertEquals(256, state.getAtlasSize().y);
        state.setFogColor(0.1F, 0.2F, 0.3F);
        assertEquals(0.3F, state.getFogColor().z);
        state.setColorModulator(1, 0.5F, 0.5F, 1);
        assertEquals(0.5F, state.getColorModulator().y);
        state.setColorModulator(1, 1, 1, 1);
        state.setEntityColor(1, 0, 0, 0.5F);
        assertEquals(0.5F, state.getEntityColor().w);
        state.resetEntityColor();
        assertEquals(0.0F, state.getEntityColor().w);
        state.setRenderStage(8);
        assertEquals(8, state.getRenderStage());
        state.setRenderStage(0);
        state.setCurrentAlphaTest(0.1F);
        assertEquals(0.1F, state.getCurrentAlphaTest());
        state.setCurrentAlphaTest(0);
        state.setCurrentRenderedBlockEntity(3);
        state.setCurrentRenderedEntity(4);
        state.setCurrentRenderedItem(5);
        assertEquals(3, state.getCurrentRenderedBlockEntity());
        assertEquals(4, state.getCurrentRenderedEntity());
        assertEquals(5, state.getCurrentRenderedItem());
        state.setCurrentRenderedBlockEntity(-1);
        state.setCurrentRenderedEntity(-1);
        state.setCurrentRenderedItem(-1);
        int reloads = state.getTextureReloadCount();
        state.incrementTextureReloadCount();
        assertEquals(reloads + 1, state.getTextureReloadCount());
    }

    @Test
    void theFrameClockWrapsAndResets() {
        SystemTimeUniforms.Timer timer = new SystemTimeUniforms.Timer();
        timer.beginFrame(0L);
        assertEquals(0.0F, timer.getLastFrameTime());
        timer.beginFrame(1_000_000_000L);
        assertEquals(1.0F, timer.getLastFrameTime());
        assertEquals(1.0F, timer.getFrameTimeCounter());
        // Past an hour the counter wraps to keep float precision
        timer.beginFrame(3_602_000_000_000L);
        assertTrue(timer.getFrameTimeCounter() < 3600.0F);
        assertEquals(3, timer.getFrameCounter());
        timer.reset();
        assertEquals(0, timer.getFrameCounter());
        assertEquals(0.0F, timer.getFrameTimeCounter());
    }

    @Test
    void theStaticHoldersCannotBeInstantiated() {
        for (Class<?> holder : new Class<?>[] {CommonUniforms.class, CelestialUniforms.class, CameraUniforms.class,
                EyeBrightnessTracker.class, MatrixUniforms.class, SystemTimeUniforms.class, CapturedRenderingState.class}) {
            assertNotNull(Mixins.construct(holder), holder.getName());
        }
        CommonUniforms.beginFrame();
    }
}
