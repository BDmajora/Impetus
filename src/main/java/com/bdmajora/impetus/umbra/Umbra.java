package com.bdmajora.impetus.umbra;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.config.UmbraConfig;
import com.bdmajora.impetus.umbra.pipeline.UmbraPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPackLoader;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

// Entry point and state holder for the shader layer: the parsed ShaderPack, compiled UmbraPipeline and per-frame UmbraRenderingPipeline, each with its own lifetime; nothing here throws, every failure leaves shaders off
public final class Umbra {
    public static final String MODNAME = "Impetus/Umbra";
    private static final Logger LOGGER = LogManager.getLogger(MODNAME);

    private static UmbraConfig config;
    private static ShaderPack currentPack;

    // Option values queued by the in-game menu, applied and merged into <pack>.txt on the next reload; queued because changing an option recompiles the pack, which only happens on the render thread
    private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
    // When set, the next reload discards every changed option value and takes the pack's own defaults
    private static boolean resetShaderPackOptions;

    // The compiled programs for the current pack, built lazily on the render thread since compiling needs a GL context
    private static UmbraPipeline pipeline;
    private static boolean pipelineNeedsInit;

    // The frame pipeline (render targets, gbuffer, composite/final chain), built at renderWorld HEAD rather than with the compile because it sizes targets from the current framebuffer
    private static UmbraRenderingPipeline renderingPipeline;
    // Latched when construction failed for the current pack, so the attempt is not repeated and re-logged every frame
    private static boolean renderingPipelineFailed;

    private Umbra() {
    }

    // The shader layer's logger
    public static Logger logger() {
        return LOGGER;
    }

    // Selected pack name and the shaderpacks directory
    public static UmbraConfig getConfig() {
        return config;
    }

    // Brings Umbra up against a game directory (optionsshaders.txt, shaderpacks/, parse the selected pack); never throws, since a broken pack must not stop the game starting
    public static void initialize(Path gameDirectory) {
        config = new UmbraConfig(gameDirectory);
        try {
            config.ensureShaderpacksDirectory();
            config.load();
        } catch (IOException e) {
            LOGGER.error("Failed to load Umbra configuration; shaders disabled", e);
            return;
        }

        if (config.isShaderPackEnabled()) {
            loadCurrentShaderpack();
        }
    }

    // Loads or reloads the pack the config names; existing state is cleared FIRST so a failed load cannot leave half the previous pack live
    public static synchronized void loadCurrentShaderpack() {
        unloadShaderpack();

        if (config == null || !config.isShaderPackEnabled()) {
            return;
        }

        Path packPath = config.getSelectedPackPath();
        String name = config.getShaderPackName();
        Path configTxt = config.getShaderpacksDirectory().resolve(name + ".txt");

        // Load the persisted changed option values, layer the queued in-game changes on top, then apply.
        Map<String, String> changedConfigs = readConfigProperties(configTxt);
        changedConfigs.putAll(shaderPackOptionQueue);
        shaderPackOptionQueue.clear();
        if (resetShaderPackOptions) {
            changedConfigs.clear();
        }
        resetShaderPackOptions = false;

        try {
            ShaderPack pack;
            if (Files.isDirectory(packPath)) {
                pack = ShaderPackLoader.loadFromDirectory(packPath, changedConfigs);
            } else if (Files.isRegularFile(packPath) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                pack = ShaderPackLoader.loadFromZip(packPath, changedConfigs);
            } else {
                LOGGER.warn("Selected shader pack '{}' was not found at {}; shaders disabled.", name, packPath);
                return;
            }

            // Persist the effective changed values (only options that differ from pack defaults are stored).
            MutableOptionValues effective = pack.getShaderPackOptions().getOptionValues().mutableCopy();
            Properties toSave = new Properties();
            effective.getBooleanValues().forEach((k, v) -> toSave.setProperty(k, Boolean.toString(v)));
            effective.getStringValues().forEach(toSave::setProperty);
            writeConfigProperties(configTxt, toSave);

            currentPack = pack;
            pipelineNeedsInit = true;
            List<String> programs = pack.getProgramSet().listDeclaredPrograms();
        } catch (Exception e) {
            currentPack = null;
            LOGGER.error("Failed to load shader pack '" + name + "'; shaders disabled", e);
        }
    }

    // Queues option changes from the in-game menu and reloads so they take effect and persist; keyed by option name, value "true"/"false" or the raw token
    public static synchronized void queueShaderPackOptions(Map<String, String> changes) {
        shaderPackOptionQueue.putAll(changes);
        loadCurrentShaderpack();
    }

    // Resets every changed option back to the pack's own defaults and reloads immediately
    public static synchronized void resetShaderPackOptionsAndReload() {
        resetShaderPackOptions = true;
        loadCurrentShaderpack();
    }

    // Loads the pack's saved option values
    private static Map<String, String> readConfigProperties(Path path) {
        Map<String, String> result = new HashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        Properties properties = new Properties();
        // NB: OptiFine specifies these config files as ISO-8859-1, which Properties.load already defaults to for byte streams
        try (InputStream is = Files.newInputStream(path)) {
            properties.load(is);
        } catch (IOException e) {
            LOGGER.warn("Failed to read shader option config {}; using defaults", path, e);
            return result;
        }
        properties.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    // Saves the pack's option values
    private static void writeConfigProperties(Path path, Properties properties) {
        try (OutputStream os = Files.newOutputStream(path)) {
            properties.store(os, "This file stores overrides for the shader pack's default options.");
        } catch (IOException e) {
            LOGGER.warn("Failed to write shader option config {}", path, e);
        }
    }

    // Render-thread hook building or rebuilding the compiled pipeline the first frame after a pack change; cheap no-op otherwise, needs a GL context, and a compile failure logs and leaves shaders off
    public static synchronized void updatePipeline() {
        if (!pipelineNeedsInit) {
            return;
        }
        // With the pack switched off entirely there is no renderWorld-driven rebuild, so tear down here; with a pack active, leave the flag for beginFrame() so teardown and rebuild happen together
        if (currentPack == null) {
            pipelineNeedsInit = false;
            destroyPipelines();
        }
    }

    // Render-thread hook for renderWorld HEAD: builds the frame pipeline on the first frame after a pack change and returns it; null when shaders are off OR the build failed, both meaning render vanilla
    public static synchronized UmbraRenderingPipeline beginFrame() {
        // Distant Horizons' rendering toggle: a flip reloads the pack (its define environment and LOD programs are baked at load), which lands in the rebuild below
        com.bdmajora.impetus.umbra.compat.dh.DhCompat.checkFrame();
        if (pipelineNeedsInit) {
            pipelineNeedsInit = false;
            destroyPipelines();
            renderingPipelineFailed = false;
        }
        if (currentPack == null || renderingPipelineFailed) {
            return null;
        }
        if (renderingPipeline == null) {
            try {
                renderingPipeline = new UmbraRenderingPipeline(currentPack);
            } catch (Exception e) {
                renderingPipelineFailed = true;
                LOGGER.error("Failed to build the Umbra rendering pipeline; shaders disabled for this pack", e);
            }
        }
        return renderingPipeline;
    }

    // The frame pipeline for mid-frame and end-of-frame hooks, or null when shaders are off; never BUILDS anything, so safe from any hook regardless of ordering
    public static UmbraRenderingPipeline getRenderingPipeline() {
        return renderingPipeline;
    }

    // Frees GL resources on pack unload; must run on the render thread
    private static void destroyPipelines() {
        if (pipeline != null) {
            pipeline.destroy();
            pipeline = null;
        }
        if (renderingPipeline != null) {
            renderingPipeline.destroy();
            renderingPipeline = null;
        }
    }

    // Phase switches from the vanilla render mixins; a no-op with no pack loaded
    public static void setPhase(ProgramId phase) {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    public static void setPhase(ProgramId phase, int renderStage) {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase, renderStage);
        }
    }

    // The glowing-eyes and enchantment-glint brackets from the entity layer mixins; no-ops with no pack loaded, so the mixins stay one line each
    public static void beginEyes() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.beginEyes();
        }
    }

    public static void endEyes() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.endEyes();
        }
    }

    public static void beginArmorGlint() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.beginArmorGlint();
        }
    }

    public static void endArmorGlint() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.endArmorGlint();
        }
    }

    // Re-uploads the per-object uniforms (entity/item/block-entity ids, entity colour) to the bound program; no-op with no pack loaded
    public static void refreshDynamicUniforms() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.refreshDynamicUniforms();
        }
    }

    // The compiled pipeline, or null when shaders are disabled or it has not been built yet this frame
    public static UmbraPipeline getPipeline() {
        return pipeline;
    }

    // Drops the active pack and everything built from it
    public static synchronized void unloadShaderpack() {
        currentPack = null;
        // GL teardown must run on the render thread; flag a rebuild so updatePipeline() disposes the pipeline there.
        pipelineNeedsInit = true;
    }

    // Whether a pack is parsed and active; this is what IrisApi reports to other mods, so it means "shaders are running", not "a pack is selected", and a pack whose pipeline failed to build is not running
    public static boolean isShaderPackInUse() {
        return currentPack != null && !renderingPipelineFailed;
    }

    // The parsed pack, or null when shaders are disabled
    public static ShaderPack getCurrentPack() {
        return currentPack;
    }

    // The selected pack's name or the "off" sentinel; selection is config state and exists even when nothing loaded
    public static String getSelectedPackName() {
        return config == null ? UmbraConfig.NO_PACK : config.getShaderPackName();
    }

    // Every pack found under shaderpacks/, for the selection screen
    public static List<String> listAvailablePacks() {
        return config == null ? java.util.Collections.emptyList() : config.listShaderpacks();
    }

    // Selects a pack by name (or NO_PACK to disable), persists to optionsshaders.txt and re-parses; does NO GL work so it is safe from the GUI thread, and updatePipeline rebuilds next frame
    public static synchronized void setShaderpackAndReload(String name) {
        if (config == null) {
            return;
        }
        config.setShaderPackName(name);
        try {
            config.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save shader selection to optionsshaders.txt", e);
        }
        loadCurrentShaderpack();
    }
}
