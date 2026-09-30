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
import com.bdmajora.extras.client.budget.RenderBudgetKeys;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.gui.FulgorStatsCommand;
import com.bdmajora.impetus.engine.impl.compat.checks.StartupChecks;
import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.impl.command.TogglePassCommand;
import com.bdmajora.impetus.impl.compat.ResourcePackScanner;
import com.bdmajora.impetus.impl.compat.modernui.MuiGuiScaleHook;
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
import net.minecraft.client.renderer.OpenGlHelper;
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
import org.lwjgl.opengl.GL15;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
        Runnable previousResetter = GLRenderDevice.VANILLA_STATE_RESETTER;
        boolean arbVbo = Statics.get(OpenGlHelper.class, "arbVbo");
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
             MockedStatic<DhCompat> dh = Mockito.mockStatic(DhCompat.class);
             MockedStatic<Umbra> umbra = Mockito.mockStatic(Umbra.class);
             MockedStatic<ResourcePackScanner> scanner = Mockito.mockStatic(ResourcePackScanner.class);
             MockedStatic<ClassLoaderCleaner> cleaner = Mockito.mockStatic(ClassLoaderCleaner.class);
             MockedStatic<Coarctatio> coarctatioHooks = Mockito.mockStatic(Coarctatio.class);
             MockedStatic<ImpetusToastRenderer> toasts = Mockito.mockStatic(ImpetusToastRenderer.class);
             MockedStatic<GL15> gl15 = Mockito.mockStatic(GL15.class)) {
            ImpetusVintage mod = new ImpetusVintage();
            // Construction: event handlers, the other modules' early hooks and the startup checks
            mod.onConstruct(mock(FMLConstructionEvent.class));
            assertEquals("1.2.3", ImpetusVintage.VERSION);
            verify(bus).register(mod);
            ctm.verify(CtmModelWrapping::register);
            fulgorHooks.verify(Fulgor::detectCompatibility);
            dh.verify(DhCompat::registerIrisAccessor);
            remapper.verify(RemapperCompactor::run);
            extras.verify(Extras::initialize);
            lights.verify(DynamicLights::initialize);
            checks.verify(StartupChecks::installCrashDialog);
            checks.verify(() -> StartupChecks.runAsync(any()));
            // Vanilla's buffer state is restored by unbinding the array buffer
            Statics.set(OpenGlHelper.class, "arbVbo", false);
            GLRenderDevice.VANILLA_STATE_RESETTER.run();
            gl15.verify(() -> GL15.glBindBuffer(anyInt(), Mockito.eq(0)));
            coarctatio.dynamicModels = false;
            mod.onConstruct(mock(FMLConstructionEvent.class));
            ctm.verify(CtmModelWrapping::register, times(1));

            // Init: commands (the pass toggle only in a dev environment), key bindings and the shader pack
            Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
            mod.onInit(mock(FMLInitializationEvent.class));
            verify(commands).registerCommand(any(TogglePassCommand.class));
            verify(commands).registerCommand(any(CoarctatioStatsCommand.class));
            verify(commands).registerCommand(any(FulgorStatsCommand.class));
            verify(commands).registerCommand(any(EquilibriumStatsCommand.class));
            lights.verify(DynamicLights::onClientInit);
            keys.verify(RenderBudgetKeys::register);
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
            mod.onRenderOverlay(all);
            mod.onRenderOverlay(hotbar);
            toasts.verify(() -> ImpetusToastRenderer.render(client, null), times(1));

            // The F3 screen gains the renderer's lines, the off-heap figure after vanilla's memory line, and each module's summary
            coarctatioHooks.when(Coarctatio::debugOverlayLine).thenReturn("Coarctatio: pools");
            fulgorHooks.when(Fulgor::debugOverlayLine).thenReturn("Fulgor: lighting");
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
            assertEquals("Coarctatio: pools", right.get(right.size() - 2));
            assertEquals("Fulgor: lighting", right.get(right.size() - 1));
            // Without renderer, memory line or module overlays only the header is added
            Mixins.set(global, "renderer", null);
            coarctatio.showDebugOverlay = false;
            fulgor.showDebugOverlay = false;
            right.clear();
            mod.onF3Text(text);
            assertEquals(2, right.size());
            assertSame(ImpetusVintage.logger(), ImpetusVintage.logger());
        } finally {
            Statics.set(MinecraftForge.class, "EVENT_BUS", previousBus);
            Statics.set(ClientCommandHandler.class, "instance", previousCommands);
            GLRenderDevice.VANILLA_STATE_RESETTER = previousResetter;
            Statics.set(OpenGlHelper.class, "arbVbo", arbVbo);
            Launch.blackboard.put("fml.deobfuscatedEnvironment", deobfuscated);
        }
    }

    // A pack serving the minecraft domain, or another, holding the given paths
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
            // No client, no pack list, or warnings turned off: nothing to scan
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
            // The same list is not scanned again, and ticks between scans only count down
            ResourcePackScanner.scanIfChanged(client);
            ResourcePackScanner.tick(client);
            notifications.verify(() -> ImpetusNotifications.warn(anyString(), any(String[].class)), times(1));
            // A changed list with nothing risky, then one with a single risky pack
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

    // Stands in for Modern UI's API class, which the hook finds by name when that mod is installed
    private static final class ModernUiApi {
        private static int calcGuiScales() {
            return 0x23;
        }
    }

    @Test
    void modernUisScaleMethodIsFoundByReflection() throws ReflectiveOperationException {
        new MuiGuiScaleHook();
        // The lookup's second stage: a candidate class answers with its calcGuiScales method, made callable, or with nothing
        Method lookup = MuiGuiScaleHook.class.getDeclaredMethod("lambda$static$1", Class.class);
        lookup.setAccessible(true);
        @SuppressWarnings("unchecked")
        Method found = ((Stream<Method>) lookup.invoke(null, ModernUiApi.class)).findFirst().orElseThrow();
        assertEquals(0x23, found.invoke(null));
        assertEquals(0, ((Stream<?>) lookup.invoke(null, String.class)).count());
    }

    @Test
    void theIrisApiAnswersFromUmbra() {
        IrisApi api = IrisApi.getInstance();
        assertFalse(api.isShaderPackInUse());
        assertFalse(api.isRenderingShadowPass());
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        try {
            assertTrue(api.isRenderingShadowPass());
        } finally {
            Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        }
        // The pack screen returns to the given screen, or to whatever is open when the caller's parent is not a screen
        GuiScreen parent = mock(GuiScreen.class);
        assertSame(parent, Mixins.get(api.openMainIrisScreenObj(parent), "parent"));
        GuiScreen open = mock(GuiScreen.class);
        client.currentScreen = open;
        Object screen = api.openMainIrisScreenObj("not a screen");
        assertInstanceOf(ShaderPackSelectScreen.class, screen);
        assertSame(open, Mixins.get(screen, "parent"));
    }
}
