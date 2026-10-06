package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.impl.platform.GameWindow;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import com.bdmajora.extras.mixin.panini.ShaderGroupAccessor;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtrasClientTest {
    private ExtrasConfig config;
    private Minecraft client;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // Translations answer with their own keys, which is enough to see which lines were built
        Statics.set(I18n.class, "i18nLocale", new net.minecraft.client.resources.Locale());
    }

    @BeforeEach
    void freshClient() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        Mixins.set(client, "gameSettings", mock(GameSettings.class));
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        ItemFrameLodState.active = false;
    }

    @Test
    void adaptiveSyncIsReassertedOnlyWhenOnAndGivenUpOnFailure() {
        Object supported = Mixins.get(AdaptiveSync.class, "supported");
        try (MockedStatic<GameWindow> window = Mockito.mockStatic(GameWindow.class)) {
            // Support is the tear-control extension on either platform, resolved once
            Mixins.set(AdaptiveSync.class, "supported", null);
            window.when(() -> GameWindow.platformExtensionSupported("WGL_EXT_swap_control_tear")).thenReturn(true);
            assertTrue(AdaptiveSync.isSupported());
            window.when(() -> GameWindow.platformExtensionSupported("WGL_EXT_swap_control_tear")).thenReturn(false);
            assertTrue(AdaptiveSync.isSupported());
            Mixins.set(AdaptiveSync.class, "supported", null);
            assertFalse(AdaptiveSync.isSupported());
            // A query that throws (no context yet) reads as unsupported
            Mixins.set(AdaptiveSync.class, "supported", null);
            window.when(() -> GameWindow.platformExtensionSupported("GLX_EXT_swap_control_tear")).thenThrow(new IllegalStateException("no context"));
            assertFalse(AdaptiveSync.isSupported());

            // Off, nothing is re-asserted; on and supported, the adaptive interval is set
            AdaptiveSync.reapply();
            window.verify(() -> GameWindow.setSwapInterval(Mockito.anyInt()), Mockito.never());
            config.extra.useAdaptiveSync = true;
            Mixins.set(AdaptiveSync.class, "supported", true);
            AdaptiveSync.reapply();
            window.verify(() -> GameWindow.setSwapInterval(-1));
            assertEquals(ExtrasConfig.VerticalSync.ADAPTIVE, AdaptiveSync.current());

            // Applying a mode keeps vanilla's vsync flag in step and saves it
            AdaptiveSync.apply(ExtrasConfig.VerticalSync.ON);
            assertFalse(config.extra.useAdaptiveSync);
            assertTrue(client.gameSettings.enableVsync);
            window.verify(() -> GameWindow.setVsync(true));
            assertEquals(ExtrasConfig.VerticalSync.ON, AdaptiveSync.current());
            AdaptiveSync.apply(ExtrasConfig.VerticalSync.ADAPTIVE);
            assertTrue(config.extra.useAdaptiveSync);
            window.verify(() -> GameWindow.setSwapInterval(-1), Mockito.times(2));
            Mockito.verify(client.gameSettings, Mockito.times(2)).saveOptions();

            // A swap interval the driver refuses turns the mode off for good
            window.when(() -> GameWindow.setSwapInterval(-1)).thenThrow(new IllegalStateException("refused"));
            AdaptiveSync.reapply();
            assertFalse(config.extra.useAdaptiveSync);
            assertFalse((Boolean) Mixins.get(AdaptiveSync.class, "supported"));
            AdaptiveSync.apply(ExtrasConfig.VerticalSync.OFF);
            assertEquals(ExtrasConfig.VerticalSync.OFF, AdaptiveSync.current());
            assertNotNull(Mixins.construct(AdaptiveSync.class));
        } finally {
            Mixins.set(AdaptiveSync.class, "supported", supported);
        }
    }

    @Test
    void cloudsFollowTheScaleAndTheConfiguredHeight() {
        config.render.cloudScale = 8;
        assertEquals(24.0F, CloudPassState.cellSize());
        assertEquals(96.0F, CloudPassState.cloudHeight(96.0F));
        ImpetusGameOptions options = ImpetusGameOptions.defaults();
        options.quality.cloudHeight = 200;
        Object previous = Statics.get(ImpetusVintage.class, "CONFIG");
        Statics.set(ImpetusVintage.class, "CONFIG", options);
        try {
            Mixins.set(client, "world", mock(WorldClient.class));
            assertEquals(200.0F, CloudPassState.cloudHeight(96.0F));
        } finally {
            Statics.set(ImpetusVintage.class, "CONFIG", previous);
        }
        assertNotNull(Mixins.construct(CloudPassState.class));
    }

    @Test
    void theAtlasLimitComesFromTheDriverWhenAsked() {
        Mixins.set(DriverLimits.class, "maxTextureSize", -1);
        config.loading.driverAtlasLimit = false;
        assertEquals(-1, DriverLimits.maxTextureSize());
        config.loading.driverAtlasLimit = true;
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            // A driver that answers nothing falls back to vanilla's probe
            assertEquals(-1, DriverLimits.maxTextureSize());
            gl.when(() -> GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)).thenReturn(16384);
            assertEquals(16384, DriverLimits.maxTextureSize());
            // Asked once and remembered
            gl.when(() -> GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)).thenReturn(8192);
            assertEquals(16384, DriverLimits.maxTextureSize());
        } finally {
            Mixins.set(DriverLimits.class, "maxTextureSize", -1);
        }
        assertNotNull(Mixins.construct(DriverLimits.class));
    }

    @Test
    void gameplayFogIsBlindnessOrBeingSubmerged() {
        assertFalse(FogState.isGameplayFog());
        EntityLivingBase blind = mock(EntityLivingBase.class);
        when(blind.isPotionActive(MobEffects.BLINDNESS)).thenReturn(true);
        when(client.getRenderViewEntity()).thenReturn(blind);
        assertTrue(FogState.isGameplayFog());
        Entity swimming = mock(Entity.class);
        when(swimming.isInsideOfMaterial(Material.WATER)).thenReturn(true);
        when(client.getRenderViewEntity()).thenReturn(swimming);
        assertTrue(FogState.isGameplayFog());
        Entity dry = mock(Entity.class);
        when(client.getRenderViewEntity()).thenReturn(dry);
        assertFalse(FogState.isGameplayFog());
        assertNotNull(Mixins.construct(FogState.class));
    }

    @Test
    void farFramedItemsLoseTheirSides() {
        IBakedModel model = mock(IBakedModel.class);
        List<BakedQuad> quads = List.of(mock(BakedQuad.class));
        when(model.getQuads(any(), any(), Mockito.anyLong())).thenReturn(quads);
        when(model.isGui3d()).thenReturn(true);
        assertSame(quads, ItemFrameLodState.filterLodQuads(model, null, EnumFacing.EAST, 0L));
        ItemFrameLodState.active = true;
        assertSame(quads, ItemFrameLodState.filterLodQuads(model, null, null, 0L));
        assertSame(quads, ItemFrameLodState.filterLodQuads(model, null, EnumFacing.NORTH, 0L));
        assertTrue(ItemFrameLodState.filterLodQuads(model, null, EnumFacing.EAST, 0L).isEmpty());
        when(model.isGui3d()).thenReturn(false);
        assertSame(quads, ItemFrameLodState.filterLodQuads(model, null, EnumFacing.EAST, 0L));
        assertNotNull(Mixins.construct(ItemFrameLodState.class));
    }

    // A renderer class with a name, which is what a profiler section is called after
    static class PigRenderer {
    }

    @Test
    void entityRenderersGetTheirOwnProfilerSections() {
        WorldClient world = mock(WorldClient.class);
        Profiler profiler = new Profiler();
        profiler.profilingEnabled = true;
        profiler.startSection("root");
        Mixins.set(world, "profiler", profiler);
        PigRenderer renderer = new PigRenderer();
        // Off by default, and never for a missing world or renderer
        ProfilerHelper.startSection(world, renderer);
        ProfilerHelper.startSection(null, renderer);
        ProfilerHelper.startSection(world, null);
        assertEquals("root", profiler.getNameOfLastSection());
        config.render.profileEntityRendering = true;
        ProfilerHelper.startSection(world, renderer);
        assertEquals("root.PigRenderer", profiler.getNameOfLastSection());
        ProfilerHelper.endSection(world, renderer);
        assertEquals("root", profiler.getNameOfLastSection());
        // An anonymous renderer has no name to profile under
        Object anonymous = new Object() { };
        ProfilerHelper.startSection(world, anonymous);
        ProfilerHelper.endSection(world, anonymous);
        assertEquals("root", profiler.getNameOfLastSection());
        assertNotNull(Mixins.construct(ProfilerHelper.class));
    }

    @Test
    void threadPrioritiesFollowTheSettings() throws Exception {
        ExtrasConfig.ThreadSettings settings = new ExtrasConfig.ThreadSettings();
        settings.renderThreadPriority = 7;
        settings.serverThreadPriority = 3;
        settings.chunkBuilderPriority = 99;
        Object clientThread = Mixins.get(ThreadTuning.class, "clientThread");
        Thread server = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
                // Stopped by the test
            }
        });
        server.setDaemon(true);
        server.start();
        Thread render = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
                // Stopped by the test
            }
        });
        render.setDaemon(true);
        render.start();
        try {
            Mixins.set(ThreadTuning.class, "clientThread", render);
            ThreadTuning.onServerThreadStarted(server);
            ThreadTuning.apply(settings);
            assertEquals(7, render.getPriority());
            assertEquals(3, server.getPriority());
            // Clamped into the JVM's range
            assertEquals(Thread.MAX_PRIORITY, ChunkBuilder.WORKER_PRIORITY);
            // The client thread is found by asking from it
            Mixins.set(ThreadTuning.class, "clientThread", null);
            when(client.isCallingFromMinecraftThread()).thenReturn(true);
            ThreadTuning.apply(settings);
            assertSame(Thread.currentThread(), Mixins.get(ThreadTuning.class, "clientThread"));
            ThreadTuning.onServerThreadStopped();
            assertNull(Mixins.get(ThreadTuning.class, "serverThread"));
            // A dead thread is left alone
            server.interrupt();
            server.join();
            ThreadTuning.onServerThreadStarted(server);
            ThreadTuning.onServerThreadStopped();
        } finally {
            render.interrupt();
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
            Mixins.set(ThreadTuning.class, "clientThread", clientThread);
            ThreadTuning.apply(new ExtrasConfig.ThreadSettings());
        }
        assertNotNull(Mixins.construct(ThreadTuning.class));
    }

    @Test
    void theFrameCounterKeepsAFiveSecondWindowOfFrameTimes() {
        Mixins.set(FrameCounter.class, "lastFrameTime", 0L);
        Mixins.set(FrameCounter.class, "sampleCount", 0);
        Mixins.set(FrameCounter.class, "sampleHead", 0);
        Mixins.set(FrameCounter.class, "lastCacheTime", 0L);
        FrameCounter.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0.0F));
        // An empty window reads as zero
        FrameCounter.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.0F));
        assertEquals(0, FrameCounter.getAverageFps());
        // More frames than the ring holds grows it
        for (int i = 0; i < 600; i++) {
            FrameCounter.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.0F));
        }
        Mixins.set(FrameCounter.class, "lastCacheTime", 0L);
        FrameCounter.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.0F));
        assertTrue(FrameCounter.getAverageFps() > 0);
        assertTrue(FrameCounter.getOnePercentLowFps() > 0);
        assertTrue(FrameCounter.getPointOnePercentLowFps() > 0);
        assertTrue(Mixins.<long[]>get(FrameCounter.class, "sampleTimes").length > 512);
        // Samples older than the window are dropped, leaving only the frame just recorded
        long[] times = Mixins.get(FrameCounter.class, "sampleTimes");
        java.util.Arrays.fill(times, 1L);
        FrameCounter.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.0F));
        assertEquals(1, (int) Mixins.<Integer>get(FrameCounter.class, "sampleCount"));
        assertNotNull(Mixins.construct(FrameCounter.class));
    }

    private static ItemTooltipEvent tooltip(ItemStack stack, String... lines) {
        return new ItemTooltipEvent(stack, null, new ArrayList<>(List.of(lines)), ITooltipFlag.TooltipFlags.NORMAL);
    }

    @Test
    void theOwningModIsNamedUnderEveryTooltip() {
        ModContainer minecraft = mock(ModContainer.class);
        when(minecraft.getName()).thenReturn("Minecraft");
        Map<String, ModContainer> mods = new HashMap<>();
        mods.put("minecraft", minecraft);
        Mixins.set(Loader.instance(), "namedMods", mods);
        Mixins.set(Loader.instance(), "modController", mock(LoadController.class));

        config.extra.modNameTooltip = true;
        ItemTooltipEvent apple = tooltip(new ItemStack(Items.APPLE), "Apple");
        ModNameTooltipHandler.onItemTooltip(apple);
        assertEquals(2, apple.getToolTip().size());
        assertTrue(apple.getToolTip().get(1).endsWith("Minecraft"));
        // A line JEI already added is not repeated
        ModNameTooltipHandler.onItemTooltip(apple);
        assertEquals(2, apple.getToolTip().size());
        // Nothing for an empty stack or a mod nobody loaded
        ItemTooltipEvent empty = tooltip(ItemStack.EMPTY, "Air");
        ModNameTooltipHandler.onItemTooltip(empty);
        assertEquals(1, empty.getToolTip().size());
        mods.clear();
        ItemTooltipEvent orphan = tooltip(new ItemStack(Items.APPLE), "Apple");
        ModNameTooltipHandler.onItemTooltip(orphan);
        assertEquals(1, orphan.getToolTip().size());
        // Switched off, the tooltip is left alone
        config.extra.modNameTooltip = false;
        ModNameTooltipHandler.onItemTooltip(orphan);
        assertNotNull(Mixins.construct(ModNameTooltipHandler.class));
    }

    private static RenderGameOverlayEvent.Text overlay() {
        RenderGameOverlayEvent.Text event = mock(RenderGameOverlayEvent.Text.class);
        ScaledResolution resolution = mock(ScaledResolution.class);
        when(resolution.getScaledWidth()).thenReturn(320);
        when(resolution.getScaledHeight()).thenReturn(240);
        when(event.getResolution()).thenReturn(resolution);
        return event;
    }

    @Test
    void theOverlayListsWhatIsSwitchedOnInTheChosenCorner() {
        FontRenderer font = mock(FontRenderer.class);
        Mixins.set(client, "fontRenderer", font);
        EntityPlayer player = mock(net.minecraft.client.entity.EntityPlayerSP.class);
        Mixins.set(client, "player", player);

        // Nothing switched on draws nothing, and the debug screen or a hidden GUI owns the corner
        ExtrasHud.onRenderOverlay(overlay());
        verify(font, never()).drawStringWithShadow(anyString(), anyFloat(), anyFloat(), anyInt());
        client.gameSettings.showDebugInfo = true;
        ExtrasHud.onRenderOverlay(overlay());
        client.gameSettings.showDebugInfo = false;
        client.gameSettings.hideGUI = true;
        ExtrasHud.onRenderOverlay(overlay());
        client.gameSettings.hideGUI = false;

        config.extra.showFps = true;
        config.extra.showFpsExtended = true;
        config.extra.showCoords = true;
        config.render.lightUpdates = false;
        config.renderBudget.overlay = true;
        ExtrasHud.onRenderOverlay(overlay());
        verify(font, times(4)).drawStringWithShadow(anyString(), anyFloat(), anyFloat(), anyInt());

        // The budget readout, each corner and each contrast mode
        config.renderBudget.enabled = true;
        RenderBudgetController.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        client.gameSettings.reducedDebugInfo = true;
        for (ExtrasConfig.OverlayCorner corner : ExtrasConfig.OverlayCorner.values()) {
            config.extra.overlayCorner = corner;
            for (ExtrasConfig.TextContrast contrast : ExtrasConfig.TextContrast.values()) {
                config.extra.textContrast = contrast;
                try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
                    ExtrasHud.onRenderOverlay(overlay());
                }
            }
        }
        verify(font, Mockito.atLeastOnce()).drawString(anyString(), anyInt(), anyInt(), anyInt());
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        RenderBudgetController.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        ExtrasHud.onRenderOverlay(overlay());
        config.renderBudget.smartEntityCulling = false;
        RenderBudgetController.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        ExtrasHud.onRenderOverlay(overlay());
        Mixins.set(RenderBudgetController.class, "budget", com.bdmajora.extras.client.budget.RenderBudget.NEUTRAL);
        assertNotNull(Mixins.construct(ExtrasHud.class));
    }

    @Test
    void thePaniniPassRunsOnlyWhenEverythingItNeedsIsThere() {
        Mixins.set(PaniniProjection.class, "failed", false);
        Mixins.set(PaniniProjection.class, "shaderGroup", null);
        PaniniProjection.captureProjection(0.0F, 1.0F);
        PaniniProjection.captureProjection(2.0F, -4.0F);
        assertEquals(0.5F, (float) Mixins.<Float>get(PaniniProjection.class, "horizontalExtent"));
        assertEquals(0.25F, (float) Mixins.<Float>get(PaniniProjection.class, "verticalExtent"));

        // Off by default
        PaniniProjection.render(0.0F);
        config.extra.paniniProjection = true;
        config.extra.paniniProjectionStrength = 50;
        boolean shaders = OpenGlHelper.shadersSupported;
        OpenGlHelper.shadersSupported = true;
        try {
            // No world yet
            PaniniProjection.render(0.0F);
            Mixins.set(client, "world", mock(WorldClient.class));
            when(client.getRenderViewEntity()).thenReturn(mock(Entity.class));
            // A window with no area yet has nothing to draw into
            PaniniProjection.render(0.0F);
            // A chain that will not load is tried once and then left off
            Mixins.set(client, "displayWidth", 800);
            Mixins.set(client, "displayHeight", 600);
            PaniniProjection.render(0.0F);
            assertTrue((boolean) Mixins.<Boolean>get(PaniniProjection.class, "failed"));
            PaniniProjection.render(0.0F);
            Mixins.set(PaniniProjection.class, "failed", false);

            // A loaded chain gets its uniforms and draws, then hands the main framebuffer back
            ShaderGroup group = Mc.mock(ShaderGroup.class, ShaderGroupAccessor.class);
            Shader other = mock(Shader.class);
            when(other.getShaderManager()).thenReturn(mock(ShaderManager.class));
            Shader panini = mock(Shader.class);
            ShaderManager manager = mock(ShaderManager.class);
            ShaderUniform uniform = mock(ShaderUniform.class);
            when(manager.getShaderUniform("PaniniParams")).thenReturn(uniform);
            when(panini.getShaderManager()).thenReturn(manager);
            when(((ShaderGroupAccessor) group).impetus$getShaders()).thenReturn(List.of(other, panini));
            Framebuffer framebuffer = mock(Framebuffer.class);
            when(client.getFramebuffer()).thenReturn(framebuffer);
            Mixins.set(PaniniProjection.class, "shaderGroup", group);
            Mixins.set(PaniniProjection.class, "framebufferWidth", 800);
            Mixins.set(PaniniProjection.class, "framebufferHeight", 600);
            try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
                PaniniProjection.render(0.5F);
            }
            verify(uniform).set(0.5F, 0.5F, 0.25F, 0.0F);
            verify(group).render(0.5F);
            verify(framebuffer).bindFramebuffer(true);
            // A resize throws the old chain away before building a new one
            Mixins.set(client, "displayWidth", 1024);
            PaniniProjection.render(0.0F);
            verify(group).deleteShaderGroup();
            Mixins.set(PaniniProjection.class, "failed", false);

            // Switched off, the chain is released
            Mixins.set(PaniniProjection.class, "shaderGroup", group);
            config.extra.paniniProjection = false;
            PaniniProjection.render(0.0F);
            assertNull(Mixins.get(PaniniProjection.class, "shaderGroup"));
        } finally {
            OpenGlHelper.shadersSupported = shaders;
        }
        assertNotNull(Mixins.construct(PaniniProjection.class));
    }

    private static WorldServer creativeWorld(long time, boolean raining, boolean thundering) {
        WorldServer world = mock(WorldServer.class);
        WorldInfo info = mock(WorldInfo.class);
        when(info.getGameType()).thenReturn(GameType.CREATIVE);
        when(info.isRaining()).thenReturn(raining);
        when(info.isThundering()).thenReturn(thundering);
        when(world.getWorldInfo()).thenReturn(info);
        when(world.getWorldTime()).thenReturn(time);
        return world;
    }

    @Test
    void timeAndWeatherLocksNudgeTheWorldInCreative() {
        // Defaults leave the world alone
        WorldServer world = creativeWorld(500L, false, false);
        TimeWeatherOverride.apply(world);
        verify(world, never()).setWorldTime(Mockito.anyLong());

        config.extra.timeOverride = ExtrasConfig.TimeOverride.DAY;
        TimeWeatherOverride.apply(world);
        verify(world).setWorldTime(1001L);
        WorldServer evening = creativeWorld(12000L, false, false);
        TimeWeatherOverride.apply(evening);
        verify(evening).setWorldTime(24001L);
        WorldServer noon = creativeWorld(6000L, false, false);
        TimeWeatherOverride.apply(noon);
        verify(noon, never()).setWorldTime(Mockito.anyLong());

        config.extra.timeOverride = ExtrasConfig.TimeOverride.NIGHT;
        WorldServer morning = creativeWorld(24000L + 500L, false, false);
        TimeWeatherOverride.apply(morning);
        verify(morning).setWorldTime(24000L + 14001L);
        WorldServer dawn = creativeWorld(23000L, false, false);
        TimeWeatherOverride.apply(dawn);
        verify(dawn).setWorldTime(24000L + 14001L);
        WorldServer midnight = creativeWorld(18000L, false, false);
        TimeWeatherOverride.apply(midnight);
        verify(midnight, never()).setWorldTime(Mockito.anyLong());

        // Survival worlds are never touched
        WorldServer survival = creativeWorld(500L, false, false);
        when(survival.getWorldInfo().getGameType()).thenReturn(GameType.SURVIVAL);
        config.extra.weatherOverride = ExtrasConfig.WeatherOverride.RAIN;
        TimeWeatherOverride.apply(survival);
        verify(survival, never()).setWorldTime(Mockito.anyLong());
        verify(survival, never()).setRainStrength(anyFloat());

        // Weather is forced, and players are told when a server is running
        config.extra.timeOverride = ExtrasConfig.TimeOverride.DEFAULT;
        WorldServer clear = creativeWorld(0L, false, false);
        TimeWeatherOverride.apply(clear);
        verify(clear).setRainStrength(1.0F);
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players);
        WorldServer served = creativeWorld(0L, true, false);
        when(served.getMinecraftServer()).thenReturn(server);
        config.extra.weatherOverride = ExtrasConfig.WeatherOverride.THUNDER;
        TimeWeatherOverride.apply(served);
        verify(players, times(3)).sendPacketToAllPlayers(any(SPacketChangeGameState.class));
        // Already as asked, nothing to do
        WorldServer stormy = creativeWorld(0L, true, true);
        TimeWeatherOverride.apply(stormy);
        verify(stormy, never()).setRainStrength(anyFloat());
        config.extra.weatherOverride = ExtrasConfig.WeatherOverride.CLEAR;
        TimeWeatherOverride.apply(stormy);
        verify(stormy).setRainStrength(0.0F);
        assertNotNull(Mixins.construct(TimeWeatherOverride.class));
    }
}
