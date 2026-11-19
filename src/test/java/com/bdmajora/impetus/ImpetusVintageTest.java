package com.bdmajora.impetus;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.client.model.dynamic.compat.CtmModelWrapping;
import com.bdmajora.coarctatio.gui.CoarctatioStatsCommand;
import com.bdmajora.coarctatio.launch.ClassLoaderCleaner;
import com.bdmajora.coarctatio.launch.RemapperCompactor;
import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.equilibrium.gui.EquilibriumStatsCommand;
import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.culling.OcclusionCulling;
import com.bdmajora.extras.client.budget.RenderBudgetKeys;
import com.bdmajora.extras.client.zoom.LogicalZoom;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.gui.FulgorStatsCommand;
import com.bdmajora.impetus.engine.impl.compat.checks.StartupChecks;
import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.impl.command.TogglePassCommand;
import com.bdmajora.impetus.impl.compat.ResourcePackScanner;
import com.bdmajora.impetus.impl.compat.modernui.MuiGuiScaleHook;
import com.bdmajora.impetus.impl.gui.overlay.AutosaveIndicator;
import com.bdmajora.impetus.impl.gui.overlay.ImpetusToastRenderer;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.mixin.core.terrain.RenderGlobalMixin;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gui.modern.ShaderPackSelectScreen;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpetusVintageTest {
    private Minecraft client;
    private ImpetusGameOptions previousOptions;
    private ImpetusGameOptions options;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void settle() throws ClassNotFoundException {
        client = Mc.client();
        Class.forName(ImpetusVintage.class.getName());
        previousOptions = Statics.get(ImpetusVintage.class, "CONFIG");
        options = ImpetusGameOptions.defaults();
        Statics.set(ImpetusVintage.class, "CONFIG", options);
    }

    @AfterEach
    void restore() {
        Statics.set(ImpetusVintage.class, "CONFIG", previousOptions);
        Mixins.set(CoarctatioConfig.class, "instance", null);
        Mixins.set(FulgorConfig.class, "instance", null);
    }

    @Test
    void theModWiresItselfThroughEveryLoadingStage(@TempDir Path home) {
        Mc.forge(ImpetusVintage.MODID);
        ModContainer container = Loader.instance().getIndexedModList().get(ImpetusVintage.MODID);
        when(container.getVersion()).thenReturn("1.2.3");
        CoarctatioConfig coarctatio = Mc.uninitialized(CoarctatioConfig.class);
        coarctatio.dynamicModels = true;
        coarctatio.clearPoolsOnWorldLeave = true;
        coarctatio.showDebugOverlay = true;
        Mixins.set(CoarctatioConfig.class, "instance", coarctatio);
        FulgorConfig fulgor = Mc.uninitialized(FulgorConfig.class);
        fulgor.showDebugOverlay = true;
        Mixins.set(FulgorConfig.class, "instance", fulgor);
        Mixins.set(client, "gameDir", home.toFile());
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);

        EventBus previousBus = MinecraftForge.EVENT_BUS;
        EventBus bus = mock(EventBus.class);
        Statics.set(MinecraftForge.class, "EVENT_BUS", bus);
        ClientCommandHandler previousCommands = ClientCommandHandler.instance;
        ClientCommandHandler commands = mock(ClientCommandHandler.class);
        Statics.set(ClientCommandHandler.class, "instance", commands);
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
        }
        Object deobfuscated = Launch.blackboard.getOrDefault("fml.deobfuscatedEnvironment", false);
        try (MockedStatic<CtmModelWrapping> ctm = Mockito.mockStatic(CtmModelWrapping.class);
             MockedStatic<Fulgor> fulgorHooks = Mockito.mockStatic(Fulgor.class);
             MockedStatic<RemapperCompactor> remapper = Mockito.mockStatic(RemapperCompactor.class);
             MockedStatic<Extras> extras = Mockito.mockStatic(Extras.class);
             MockedStatic<DynamicLights> lights = Mockito.mockStatic(DynamicLights.class);
             MockedStatic<StartupChecks> checks = Mockito.mockStatic(StartupChecks.class);
             MockedStatic<GlContextInfo> glInfo = Mockito.mockStatic(GlContextInfo.class);
             MockedStatic<RenderBudgetKeys> keys = Mockito.mockStatic(RenderBudgetKeys.class);
             MockedStatic<LogicalZoom> zoom = Mockito.mockStatic(LogicalZoom.class);
             MockedStatic<com.bdmajora.impetus.umbra.gui.ShaderToggleKey> shaderKey = Mockito.mockStatic(com.bdmajora.impetus.umbra.gui.ShaderToggleKey.class);
             MockedStatic<DhCompat> dh = Mockito.mockStatic(DhCompat.class);
             MockedStatic<Umbra> umbra = Mockito.mockStatic(Umbra.class);
             MockedStatic<ResourcePackScanner> scanner = Mockito.mockStatic(ResourcePackScanner.class);
             MockedStatic<ClassLoaderCleaner> cleaner = Mockito.mockStatic(ClassLoaderCleaner.class);
             MockedStatic<Coarctatio> coarctatioHooks = Mockito.mockStatic(Coarctatio.class);
             MockedStatic<ImpetusToastRenderer> toasts = Mockito.mockStatic(ImpetusToastRenderer.class);
             MockedStatic<AutosaveIndicator> autosave = Mockito.mockStatic(AutosaveIndicator.class);
             MockedStatic<OcclusionCulling> occlusion = Mockito.mockStatic(OcclusionCulling.class)) {
            ImpetusVintage mod = new ImpetusVintage();
            // Constructs the mod.
            mod.onConstruct(mock(FMLConstructionEvent.class));
            assertEquals("1.2.3", ImpetusVintage.VERSION);
            verify(bus).register(mod);
            ctm.verify(CtmModelWrapping::register);
            fulgorHooks.verify(Fulgor::detectCompatibility);
            dh.verifyNoInteractions();
            remapper.verify(RemapperCompactor::run);
            extras.verify(Extras::initialize);
            lights.verify(DynamicLights::initialize);
            checks.verify(StartupChecks::installCrashDialog);
            checks.verify(() -> StartupChecks.runAsync(any()));
            coarctatio.dynamicModels = false;
            mod.onConstruct(mock(FMLConstructionEvent.class));
            ctm.verify(CtmModelWrapping::register, times(1));

            // Initialises the mod.
            Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
            mod.onInit(mock(FMLInitializationEvent.class));
            verify(commands).registerCommand(any(TogglePassCommand.class));
            verify(commands).registerCommand(any(CoarctatioStatsCommand.class));
            verify(commands).registerCommand(any(FulgorStatsCommand.class));
            verify(commands).registerCommand(any(EquilibriumStatsCommand.class));
            lights.verify(DynamicLights::onClientInit);
            keys.verify(RenderBudgetKeys::register);
            zoom.verify(LogicalZoom::register);
            shaderKey.verify(com.bdmajora.impetus.umbra.gui.ShaderToggleKey::register);
            dh.verify(DhCompat::run);
            umbra.verify(() -> Umbra.initialize(home));
            scanner.verify(() -> ResourcePackScanner.scanIfChanged(client));
            cleaner.verify(ClassLoaderCleaner::run);
            Launch.blackboard.put("fml.deobfuscatedEnvironment", false);
            mod.onInit(mock(FMLInitializationEvent.class));
            verify(commands, times(1)).registerCommand(any(TogglePassCommand.class));

            mod.onPostInit(mock(FMLPostInitializationEvent.class));
            fulgorHooks.verify(Fulgor::onPostInit);

            // Each frame's start rescans packs and brings the shader pipeline up to date
            mod.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.5F));
            mod.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0.5F));
            scanner.verify(() -> ResourcePackScanner.tick(client), times(1));
            umbra.verify(Umbra::updatePipeline, times(1));

            // Leaving a world clears the pools when asked to
            mod.onDisconnect(mock(FMLNetworkEvent.ClientDisconnectionFromServerEvent.class));
            coarctatio.clearPoolsOnWorldLeave = false;
            mod.onDisconnect(mock(FMLNetworkEvent.ClientDisconnectionFromServerEvent.class));
            coarctatioHooks.verify(Coarctatio::onWorldLeave, times(1));

            // Toasts draw over the whole HUD pass only
            RenderGameOverlayEvent.Post all = mock(RenderGameOverlayEvent.Post.class);
            when(all.getType()).thenReturn(RenderGameOverlayEvent.ElementType.ALL);
            RenderGameOverlayEvent.Post hotbar = mock(RenderGameOverlayEvent.Post.class);
            when(hotbar.getType()).thenReturn(RenderGameOverlayEvent.ElementType.HOTBAR);
            when(all.getPartialTicks()).thenReturn(0.25F);
            mod.onRenderOverlay(all);
            mod.onRenderOverlay(hotbar);
            toasts.verify(() -> ImpetusToastRenderer.render(client, null), times(1));
            autosave.verify(() -> AutosaveIndicator.render(client, null, 0.25F), times(1));

            // Ticks the autosave fade.
            mod.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
            mod.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            autosave.verify(() -> AutosaveIndicator.tick(false), times(1));
            when(client.getIntegratedServer()).thenReturn(mock(net.minecraft.server.integrated.IntegratedServer.class));
            mod.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            autosave.verify(() -> AutosaveIndicator.tick(true), times(1));
            occlusion.verify(() -> OcclusionCulling.tick(client), times(2));
            when(client.getIntegratedServer()).thenReturn(null);

            // Checks the F3 lines.
            coarctatioHooks.when(Coarctatio::debugOverlayLine).thenReturn("Coarctatio: pools");
            fulgorHooks.when(Fulgor::debugOverlayLine).thenReturn("Fulgor: lighting");
            ExtrasConfig extrasConfig = new ExtrasConfig();
            extras.when(Extras::options).thenReturn(extrasConfig);
            occlusion.when(OcclusionCulling::status).thenReturn("Occlusion: hidden");
            RenderGameOverlayEvent.Text text = mock(RenderGameOverlayEvent.Text.class);
            ArrayList<String> right = new ArrayList<>(List.of("Allocated: 10%"));
            when(text.getRight()).thenReturn(right);
            mod.onF3Text(text);
            assertEquals(List.of("Allocated: 10%"), right);
            settings.showDebugInfo = true;
            when(client.isReducedDebug()).thenReturn(true);
            mod.onF3Text(text);
            assertEquals(3, right.size());
            assertTrue(right.get(2).contains("Impetus") && right.get(2).contains("1.2.3"));
            when(client.isReducedDebug()).thenReturn(false);
            RenderGlobalMixin global = Mixins.instance(RenderGlobalMixin.class);
            ImpetusWorldRenderer renderer = mock(ImpetusWorldRenderer.class);
            when(renderer.getDebugStrings()).thenReturn(List.of("Viewport: 1 2 3"));
            Mixins.set(global, "renderer", renderer);
            Mixins.set(client, "renderGlobal", global);
            right.clear();
            right.add("Allocated: 10%");
            mod.onF3Text(text);
            assertTrue(right.get(1).startsWith("Off-Heap: +"), right.toString());
            assertTrue(right.contains("Viewport: 1 2 3"));
            assertEquals("Coarctatio: pools", right.get(right.size() - 3));
            assertEquals("Fulgor: lighting", right.get(right.size() - 2));
            assertEquals("Occlusion: hidden", right.get(right.size() - 1));
            // Checks the F3 lines without a renderer.
            Mixins.set(global, "renderer", null);
            coarctatio.showDebugOverlay = false;
            fulgor.showDebugOverlay = false;
            extrasConfig.occlusion.enabled = false;
            right.clear();
            mod.onF3Text(text);
            assertEquals(2, right.size());
            assertSame(ImpetusVintage.logger(), ImpetusVintage.logger());
        } finally {
            Statics.set(MinecraftForge.class, "EVENT_BUS", previousBus);
            Statics.set(ClientCommandHandler.class, "instance", previousCommands);
            Launch.blackboard.put("fml.deobfuscatedEnvironment", deobfuscated);
        }
    }

    // Returns a pack with the given domain and paths.
    private static IResourcePack pack(String domain, String... paths) {
        IResourcePack pack = mock(IResourcePack.class);
        when(pack.getResourceDomains()).thenReturn(Set.of(domain));
        Set<String> held = Set.of(paths);
        when(pack.resourceExists(any())).thenAnswer(invocation -> held.contains(invocation.<ResourceLocation>getArgument(0).getPath()));
        return pack;
    }

    private static ResourcePackRepository.Entry entry(String name, IResourcePack pack) {
        ResourcePackRepository.Entry entry = mock(ResourcePackRepository.Entry.class);
        when(entry.getResourcePackName()).thenReturn(name);
        when(entry.getResourcePack()).thenReturn(pack);
        return entry;
    }

    @Test
    void packsOverridingVanillaShadersAreReportedOncePerChange() {
        Mixins.set(ResourcePackScanner.class, "ticksUntilScan", 0);
        Mixins.set(ResourcePackScanner.class, "lastPackSignature", "");
        ResourcePackRepository repository = mock(ResourcePackRepository.class);
        IResourcePack brokenDomains = mock(IResourcePack.class);
        when(brokenDomains.getResourceDomains()).thenThrow(new IllegalStateException("closed"));
        IResourcePack brokenLookups = pack("minecraft");
        Mockito.doThrow(new IllegalStateException("closed")).when(brokenLookups).resourceExists(any());
        List<ResourcePackRepository.Entry> entries = List.of(
                entry("Blur", pack("minecraft", "shaders/program/blur.fsh")),
                entry("Art", pack("minecraft", "shaders/post/art.json")),
                entry("Notch", pack("minecraft", "shaders/program/notch.json")),
                entry("Wobble", pack("minecraft", "shaders/program/wobble.vsh")),
                entry("Modded", pack("othermod", "shaders/program/blur.fsh")),
                entry("Plain", pack("minecraft")),
                entry("Broken domains", brokenDomains),
                entry("Broken lookups", brokenLookups),
                entry(null, null));
        when(repository.getRepositoryEntries()).thenReturn(entries);
        try (MockedStatic<ImpetusNotifications> notifications = Mockito.mockStatic(ImpetusNotifications.class)) {
            // Checks the cases with nothing to scan.
            ResourcePackScanner.scanIfChanged(null);
            ResourcePackScanner.scanIfChanged(client);
            when(client.getResourcePackRepository()).thenReturn(repository);
            options.advanced.disableIncompatibleModWarnings = true;
            ResourcePackScanner.scanIfChanged(client);
            notifications.verifyNoInteractions();
            options.advanced.disableIncompatibleModWarnings = false;
            ResourcePackScanner.tick(client);
            notifications.verify(() -> ImpetusNotifications.warn("Resource pack compatibility",
                    "- Blur", "- Art", "- Notch", "...and 1 more", "Check the game log for details."));
            // Checks the same list is not scanned again.
            ResourcePackScanner.scanIfChanged(client);
            ResourcePackScanner.tick(client);
            notifications.verify(() -> ImpetusNotifications.warn(anyString(), any(String[].class)), times(1));
            // Scans a plain list and then a risky one.
            List<ResourcePackRepository.Entry> plain = List.of(entry("Plain", pack("minecraft")));
            when(repository.getRepositoryEntries()).thenReturn(plain);
            ResourcePackScanner.scanIfChanged(client);
            notifications.verify(() -> ImpetusNotifications.warn(anyString(), any(String[].class)), times(1));
            List<ResourcePackRepository.Entry> blur = List.of(entry("Blur", pack("minecraft", "shaders/program/blur.fsh")));
            when(repository.getRepositoryEntries()).thenReturn(blur);
            ResourcePackScanner.scanIfChanged(client);
            notifications.verify(() -> ImpetusNotifications.warn("Resource pack compatibility", "- Blur", "Check the game log for details."));
        } finally {
            Mixins.set(ResourcePackScanner.class, "lastPackSignature", "");
        }
    }

    // Stand-in for Modern UI's API class.
    private static final class ModernUiApi {
        private static int calcGuiScales() {
            return 0x23;
        }
    }

    @Test
    void modernUisScaleMethodIsFoundByReflection() throws ReflectiveOperationException {
        new MuiGuiScaleHook();
        // Checks the second lookup stage.
        Method lookup = MuiGuiScaleHook.class.getDeclaredMethod("lambda$static$1", Class.class);
        lookup.setAccessible(true);
        @SuppressWarnings("unchecked")
        Method found = ((Stream<Method>) lookup.invoke(null, ModernUiApi.class)).findFirst().orElseThrow();
        assertEquals(0x23, found.invoke(null));
        assertEquals(0, ((Stream<?>) lookup.invoke(null, String.class)).count());
    }

    @Test
    void theIrisApiAnswersFromUmbra() {
        assertTrue(IrisApi.class.isInterface(), "Mods compiled against Iris use invokeinterface");
        IrisApi api = IrisApi.getInstance();
        assertSame(api, IrisApi.getInstance());
        assertEquals(0, api.getMinorApiRevision());
        assertEquals("options.umbra.shaderPackSelection", api.getMainScreenLanguageKey());
        assertFalse(api.isShaderPackInUse());
        assertFalse(api.isRenderingShadowPass());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        try {
            assertTrue(api.isRenderingShadowPass());
        } finally {
            Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        }
        // Checks the screen the pack screen returns to.
        GuiScreen parent = mock(GuiScreen.class);
        assertSame(parent, Mixins.get(api.openMainIrisScreenObj(parent), "parent"));
        GuiScreen open = mock(GuiScreen.class);
        client.currentScreen = open;
        Object screen = api.openMainIrisScreenObj("not a screen");
        assertInstanceOf(ShaderPackSelectScreen.class, screen);
        assertSame(open, Mixins.get(screen, "parent"));
    }

    @Test
    void theIrisApiConfigAppliesChangesThroughUmbra() {
        var config = IrisApi.getInstance().getConfig();
        assertSame(config, IrisApi.getInstance().getConfig());
        try (MockedStatic<Umbra> umbra = Mockito.mockStatic(Umbra.class)) {
            assertFalse(config.areShadersEnabled());
            umbra.when(Umbra::areShadersEnabled).thenReturn(true);
            assertTrue(config.areShadersEnabled());
            config.setShadersEnabledAndApply(false);
            config.setShadersEnabledAndApply(true);
            umbra.verify(() -> Umbra.setShadersEnabled(false));
            umbra.verify(() -> Umbra.setShadersEnabled(true));
        }
    }
}
