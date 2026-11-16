package com.bdmajora.impetus.umbra;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.umbra.config.UmbraConfig;
import com.bdmajora.impetus.umbra.pipeline.UmbraPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPackLoader;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues;
import com.bdmajora.impetus.umbra.terrain.mesh.NvidiumPackPorter;

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
import java.util.function.BooleanSupplier;

// Entry point of the Umbra shader loader.
public final class Umbra {
    // Name of the module in logs.
    public static final String MODNAME = "Impetus/Umbra";
    // Logger of the module.
    private static final Logger LOGGER = LogManager.getLogger(MODNAME);

    // Config of the module.
    private static UmbraConfig config;
    // Loaded shader pack or null.
    private static ShaderPack currentPack;

    private static final Map<String, String> shaderPackOptionQueue = new HashMap<>();
    // Flag set when the pack options are reset on reload.
    private static boolean resetShaderPackOptions;

    // Active pipeline holder or null.
    private static UmbraPipeline pipeline;
    // Flag set when the pipeline must be rebuilt.
    private static boolean pipelineNeedsInit;

    // Pipeline of the loaded pack or null.
    private static UmbraRenderingPipeline renderingPipeline;
    // Flag set when building the pipeline failed.
    private static boolean renderingPipelineFailed;

    // Nvidium terrain.
    private static BooleanSupplier nvidiumTerrain = () -> ImpetusVintage.options().graphicsApi == ImpetusGameOptions.GraphicsApi.NVIDIUM
            && MeshShaderSupport.isSupported();
    // Flag set when the pack was loaded for mesh terrain.
    private static boolean loadedForNvidium;
    // Message about the mesh terrain port.
    private static String nvidiumPortNote = "";

    // Id of the current dimension.
    private static int dimensionId;
    // Name of the current dimension.
    private static String dimensionName;

    // Prevents instantiation
    private Umbra() {
    }

    // Returns the logger.
    public static Logger logger() {
        return LOGGER;
    }

    // Returns the config.
    public static UmbraConfig getConfig() {
        return config;
    }

    // Loads the config and prepares the shader pack state.
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

    // Loads the selected pack.
    public static synchronized void loadCurrentShaderpack() {
        unloadShaderpack();

        if (config == null || !config.isShaderPackEnabled()) {
            return;
        }

        String name = config.getShaderPackName();
        Path configTxt = config.getShaderpacksDirectory().resolve(name + ".txt");
        loadedForNvidium = nvidiumTerrain.getAsBoolean();
        Path packPath = loadedForNvidium ? nvidiumPort(config.getSelectedPackPath()) : config.getSelectedPackPath();

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

            MutableOptionValues effective = pack.getShaderPackOptions().getOptionValues().mutableCopy();
            Properties toSave = new Properties();
            effective.getBooleanValues().forEach((k, v) -> toSave.setProperty(k, Boolean.toString(v)));
            effective.getStringValues().forEach(toSave::setProperty);
            writeConfigProperties(configTxt, toSave);

            if (dimensionName != null) {
                pack.selectDimension(dimensionId, dimensionName);
            }
            currentPack = pack;
            pipelineNeedsInit = true;
        } catch (Exception e) {
            currentPack = null;
            LOGGER.error("Failed to load shader pack '" + name + "'; shaders disabled", e);
        }
    }

    // Handles the world changed.
    public static synchronized void onWorldChanged(int id, String name) {
        dimensionId = id;
        dimensionName = name;
        if (name != null && currentPack != null && currentPack.selectDimension(id, name)) {
            LOGGER.info("[Umbra] Dimension {} ({}) uses its own programs; rebuilding the pipeline", name, id);
            pipelineNeedsInit = true;
        }
    }

    // Saves option changes and reloads the pack.
    public static synchronized void queueShaderPackOptions(Map<String, String> changes) {
        shaderPackOptionQueue.putAll(changes);
        loadCurrentShaderpack();
    }

    // Clears the option values and reloads the pack.
    public static synchronized void resetShaderPackOptionsAndReload() {
        resetShaderPackOptions = true;
        loadCurrentShaderpack();
    }

    // Reads a properties file into a map.
    private static Map<String, String> readConfigProperties(Path path) {
        Map<String, String> result = new HashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(path)) {
            properties.load(is);
        } catch (IOException e) {
            LOGGER.warn("Failed to read shader option config {}; using defaults", path, e);
            return result;
        }
        properties.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    // Writes a properties file.
    private static void writeConfigProperties(Path path, Properties properties) {
        try (OutputStream os = Files.newOutputStream(path)) {
            properties.store(os, "This file stores overrides for the shader pack's default options.");
        } catch (IOException e) {
            LOGGER.warn("Failed to write shader option config {}", path, e);
        }
    }

    // Rebuilds the pipeline when needed.
    public static synchronized void updatePipeline() {
        if (!pipelineNeedsInit) {
            return;
        }
        if (currentPack == null) {
            pipelineNeedsInit = false;
            destroyPipelines();
        }
    }

    // Returns the pipeline for this frame.
    public static synchronized UmbraRenderingPipeline beginFrame() {
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

    // Returns the rendering pipeline.
    public static UmbraRenderingPipeline getRenderingPipeline() {
        return renderingPipeline;
    }

    // Deletes every pipeline.
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

    // Stores the phase.
    public static void setPhase(ProgramId phase) {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    // Stores the phase.
    public static void setPhase(ProgramId phase, int renderStage) {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase, renderStage);
        }
    }

    // Switches to the eyes program.
    public static void beginEyes() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.beginEyes();
        }
    }

    // Leaves the eyes program.
    public static void endEyes() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.endEyes();
        }
    }

    // Switches to the armor glint program.
    public static void beginArmorGlint() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.beginArmorGlint();
        }
    }

    // Leaves the armor glint program.
    public static void endArmorGlint() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.endArmorGlint();
        }
    }

    // Recomputes the uniforms that change each frame.
    public static void refreshDynamicUniforms() {
        UmbraRenderingPipeline pipeline = getRenderingPipeline();
        if (pipeline != null) {
            pipeline.refreshDynamicUniforms();
        }
    }

    // Returns the pipeline.
    public static UmbraPipeline getPipeline() {
        return pipeline;
    }

    // Unloads the pack and deletes its pipeline.
    public static synchronized void unloadShaderpack() {
        currentPack = null;
        pipelineNeedsInit = true;
    }

    // Returns whether shader pack in use.
    public static boolean isShaderPackInUse() {
        return currentPack != null && !renderingPipelineFailed;
    }

    // Returns the current pack.
    public static ShaderPack getCurrentPack() {
        return currentPack;
    }

    // Returns the selected pack name.
    public static String getSelectedPackName() {
        return config == null ? UmbraConfig.NO_PACK : config.getShaderPackName();
    }

    // Returns the names of the installed packs.
    public static List<String> listAvailablePacks() {
        return config == null ? java.util.Collections.emptyList() : config.listShaderpacks();
    }

    // Returns the mesh terrain port of a pack.
    private static Path nvidiumPort(Path original) {
        nvidiumPortNote = "";
        if (!Files.exists(original)) {
            return original;
        }
        Path target = config.portPath(config.getShaderPackName());
        try {
            Files.createDirectories(target.getParent());
            long start = System.nanoTime();
            NvidiumPackPorter.Result result = NvidiumPackPorter.ensure(original, target);
            if (!result.reused()) {
                LOGGER.info("[Umbra] Ported {} for Nvidium in {} ms: {} program(s){}", config.getShaderPackName(), (System.nanoTime() - start) / 1_000_000L,
                        result.ported().size(), result.skipped().isEmpty() ? "" : ", kept on OpenGL: " + String.join("; ", result.skipped()));
            }
            nvidiumPortNote = String.join("; ", result.skipped());
            return target;
        } catch (IOException | RuntimeException e) {
            nvidiumPortNote = "could not port the pack: " + e.getMessage();
            LOGGER.warn("[Umbra] Could not port {} for Nvidium; its terrain stays on OpenGL", config.getShaderPackName(), e);
            return original;
        }
    }

    // Returns the nvidium port note.
    public static String getNvidiumPortNote() {
        return nvidiumPortNote;
    }

    // Reloads the pack when the terrain backend changed and returns whether it did.
    public static synchronized boolean reloadIfTerrainBackendChanged() {
        if (config == null || !config.isShaderPackEnabled() || loadedForNvidium == nvidiumTerrain.getAsBoolean()) {
            return false;
        }
        loadCurrentShaderpack();
        return true;
    }

    // Stores the shaderpack and reload.
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
