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

// Mod entry point of Impetus.
@Mod(modid = ImpetusVintage.MODID, useMetadata = true, clientSideOnly = true, acceptableRemoteVersions = "*")
public class ImpetusVintage {
    // Mod id.
    public static final String MODID = "impetus";
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus");
    // Version read from the mod metadata.
    public static String VERSION;
    // Game options loaded at startup.
    private static final ImpetusGameOptions CONFIG = loadConfig();

    // Handles the construct.
    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        VERSION = Loader.instance().getIndexedModList().get(MODID).getVersion();
        MinecraftForge.EVENT_BUS.register(this);

        if (CoarctatioConfig.get().dynamicModels) {
            com.bdmajora.coarctatio.client.model.dynamic.compat.CtmModelWrapping.register();
        }

        Fulgor.detectCompatibility();

        com.bdmajora.impetus.umbra.compat.dh.DhCompat.registerIrisAccessor();

        com.bdmajora.coarctatio.launch.RemapperCompactor.run();

        com.bdmajora.coarctatio.launch.discovery.ModScanCache.save();

        com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.apply(CONFIG);

        Extras.initialize();

        DynamicLights.initialize();

        StartupChecks.installCrashDialog();
        StartupChecks.runAsync(GlContextInfo.capture());
    }

    // Handles the init.
    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        if ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            ClientCommandHandler.instance.registerCommand(new TogglePassCommand());
        }

        DynamicLights.onClientInit();

        com.bdmajora.extras.client.budget.RenderBudgetKeys.register();
        com.bdmajora.extras.client.zoom.LogicalZoom.register();

        ClientCommandHandler.instance.registerCommand(new CoarctatioStatsCommand());
        ClientCommandHandler.instance.registerCommand(new FulgorStatsCommand());
        ClientCommandHandler.instance.registerCommand(new EquilibriumStatsCommand());
        if (com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat.IS_LOADED) {
            ClientCommandHandler.instance.registerCommand(new com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedProbeCommand());
        }

        com.bdmajora.impetus.umbra.compat.dh.DhCompat.run();
        Umbra.initialize(PlatformUtil.getGameDir().toPath());
        ResourcePackScanner.scanIfChanged(Minecraft.getMinecraft());

        ClassLoaderCleaner.run();
    }

    // Handles the post init.
    @EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        Fulgor.onPostInit();
    }

    // Handles the render tick.
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            com.bdmajora.impetus.impl.platform.WindowModes.restoreOnce(Minecraft.getMinecraft(), CONFIG);
            ResourcePackScanner.tick(Minecraft.getMinecraft());
            Umbra.updatePipeline();
        }
    }

    // Handles the disconnect.
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (CoarctatioConfig.get().clearPoolsOnWorldLeave) {
            Coarctatio.onWorldLeave();
        }
    }

    // Handles the render overlay.
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            ImpetusToastRenderer.render(Minecraft.getMinecraft(), event.getResolution());
        }
    }

    // Handles the f3 text.
    @SubscribeEvent
    public void onF3Text(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }

        var strings = event.getRight();
        strings.add("");
        strings.add(String.format("%s%s Renderer (%s)", ChatFormatting.AQUA, "Impetus", VERSION));

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
    }

    // Returns the native memory string.
    private static String getNativeMemoryString() {
        return "Off-Heap: +" + MathUtil.toMib(getNativeMemoryUsage()) + "MB";
    }

    // Returns the native memory usage.
    private static long getNativeMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() + NativeBuffer.getTotalAllocated();
    }

    // Returns the logger.
    public static Logger logger() {
        return LOGGER;
    }

    // Reads the game options and returns defaults on failure.
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

    // Applies the runtime config.
    private static void applyRuntimeConfig(ImpetusGameOptions config) {
        NativeBuffer.ENABLE_MEMORY_TRACING = config.advanced.enableMemoryTracing;
        RenderRegionManager.USE_ADVANCED_STAGING_BUFFERS = config.advanced.useAdvancedStagingBuffers;
    }

    // Returns the options.
    public static ImpetusGameOptions options() {
        if (CONFIG == null) {
            throw new IllegalStateException("Config not yet available");
        } else {
            return CONFIG;
        }
    }
}
