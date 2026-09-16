package com.bdmajora.impetus;

import com.mojang.realmsclient.gui.ChatFormatting;

import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.compat.checks.StartupChecks;
import com.bdmajora.impetus.engine.impl.compat.environment.GlContextInfo;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.gui.CoarctatioStatsCommand;
import com.bdmajora.coarctatio.launch.ClassLoaderCleaner;
import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.equilibrium.gui.EquilibriumStatsCommand;
import com.bdmajora.extras.Extras;
import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.gui.FulgorStatsCommand;
import com.bdmajora.impetus.impl.command.TogglePassCommand;
import com.bdmajora.impetus.impl.compat.ResourcePackScanner;
import com.bdmajora.impetus.impl.gui.overlay.ImpetusToastRenderer;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.util.PlatformUtil;
import com.bdmajora.impetus.umbra.Umbra;

@Mod(modid = ImpetusVintage.MODID, useMetadata = true, clientSideOnly = true, acceptableRemoteVersions = "*")
public class ImpetusVintage {
    public static final String MODID = "impetus";
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    public static String VERSION;
    private static final ImpetusGameOptions CONFIG = loadConfig();

    // Earliest Forge hook: loads config and registers the event handlers that need it
    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        GLRenderDevice.VANILLA_STATE_RESETTER = () -> OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        VERSION = Loader.instance().getIndexedModList().get(MODID).getVersion();
        MinecraftForge.EVENT_BUS.register(this);

        // Dynamic model loading bakes on demand, so CTM's per-model wrapping has to be listening before the first bake, which happens before init
        if (CoarctatioConfig.get().dynamicModels) {
            com.bdmajora.coarctatio.client.model.dynamic.compat.CtmModelWrapping.register();
        }

        // The lighting engine branches on these on its hot path and every world is constructed after this point; earlier, the mod list is not yet answerable
        Fulgor.detectCompatibility();

        // As early as a mod can run: the remapper has its mappings by now and most mod classes are still to be loaded through it
        com.bdmajora.coarctatio.launch.RemapperCompactor.run();

        // Seed the engine's hot-path option snapshot from the loaded config.
        com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.apply(CONFIG);

        // Load the Extras options now rather than from whichever mixin body runs first; they are read on the render/client thread, so the lazy path would do its first file read inside a frame
        Extras.initialize();

        // Same for Dynamic Lights, which also reads options from chunk-builder workers; only the config loads here, see DynamicLights#onClientInit for why the rest waits
        DynamicLights.initialize();

        // Platform compatibility: GL strings must be read on the client thread (which owns the context during FML construction); the adapter probe and overlay scan continue on a background thread
        StartupChecks.installCrashDialog();
        StartupChecks.runAsync(GlContextInfo.capture());
    }

    // Registers commands and the option pages once the game is up
    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        if ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            ClientCommandHandler.instance.registerCommand(new TogglePassCommand());
        }

        // Item light sources are read from resource packs here rather than at construction, because the item registry does not exist until now
        DynamicLights.onClientInit();

        // Key bindings must exist before the controls screen enumerates them
        com.bdmajora.extras.client.budget.RenderBudgetKeys.register();

        ClientCommandHandler.instance.registerCommand(new CoarctatioStatsCommand());
        ClientCommandHandler.instance.registerCommand(new FulgorStatsCommand());
        ClientCommandHandler.instance.registerCommand(new EquilibriumStatsCommand());

        // Phase 1: parse the selected shader pack only; no rendering changes here, and if no pack is selected or loading fails Impetus renders exactly as before
        Umbra.initialize(PlatformUtil.getGameDir().toPath());
        ResourcePackScanner.scanIfChanged(Minecraft.getMinecraft());

        // Runs here rather than earlier: every class a coremod will ask LaunchWrapper for has been transformed by now, so weakening its byte cache costs nothing and reclaims the largest block of startup memory
        ClassLoaderCleaner.run();
    }

    // Post-init is the earliest point every mod's blocks answer isSideSolid the way they will in-game
    @EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        Fulgor.onPostInit();
    }

    // Drives per-frame work that has no better home: toasts and the pack scanner
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        // Render thread with a live GL context: build/rebuild the Umbra pipeline the first frame after a pack change; no-op otherwise and safe with Umbra disabled
        if (event.phase == TickEvent.Phase.START) {
            ResourcePackScanner.tick(Minecraft.getMinecraft());
            Umbra.updatePipeline();
        }
    }

    // Tears down world-scoped renderer state
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        // Fires once on leaving a world or server (single-player included), the one unambiguous "the world is gone" signal on 1.12.2 since WorldEvent.Unload fires per dimension
        if (CoarctatioConfig.get().clearPoolsOnWorldLeave) {
            Coarctatio.onWorldLeave();
        }
    }

    // Draws the notification toast over the HUD
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            ImpetusToastRenderer.render(Minecraft.getMinecraft(), event.getResolution());
        }
    }

    // Adds the Impetus lines to the debug screen
    @SubscribeEvent
    public void onF3Text(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }

        var strings = event.getRight();
        strings.add("");
        strings.add(String.format("%s%s Renderer (%s)", ChatFormatting.AQUA, "Impetus", VERSION));

        // Impetus: Show a lot less with reduced debug info
        if (Minecraft.getMinecraft().isReducedDebug()) {
            return;
        }

        var renderer = ImpetusWorldRenderer.instanceNullable();

        if (renderer != null) {
            strings.addAll(renderer.getDebugStrings());
        }

        for (int i = 0; i < strings.size(); i++) {
            String str = strings.get(i);

            if (str.startsWith("Allocated:")) {
                strings.add(i + 1, getNativeMemoryString());

                break;
            }
        }

        if (CoarctatioConfig.get().showDebugOverlay) {
            strings.add(Coarctatio.debugOverlayLine());
        }

        if (FulgorConfig.get().showDebugOverlay) {
            strings.add(Fulgor.debugOverlayLine());
        }

        if (com.bdmajora.impetus.umbra.pipeline.LightShaftProbe.available()) {
            strings.addAll(com.bdmajora.impetus.umbra.pipeline.LightShaftProbe.INSTANCE.lines());
        }
    }

    // Off-heap usage formatted for the debug screen
    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    // Direct buffer bytes, from the JVM's buffer pool bean
    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }

    // The mod logger
    public static Logger logger() {
        return LOGGER;
    }

    // Loads or creates the options file
    private static ImpetusGameOptions loadConfig() {
        try {
            ImpetusGameOptions config = ImpetusGameOptions.load();
            applyRuntimeConfig(config);
            return config;
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");
            ImpetusGameOptions config = new ImpetusGameOptions();
            config.setReadOnly();
            applyRuntimeConfig(config);
            return config;
        }
    }

    // Pushes settings that vanilla caches, such as leaves quality
    private static void applyRuntimeConfig(ImpetusGameOptions config) {
        NativeBuffer.ENABLE_MEMORY_TRACING = config.advanced.enableMemoryTracing;
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = config.advanced.useAdvancedStagingBuffers;
    }

    // The live options object
    public static ImpetusGameOptions options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        } else {
            return CONFIG;
        }
    }
}
