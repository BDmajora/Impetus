package com.bdmajora.impetus.engine.impl.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ImpetusGameOptions implements OptionStorage<ImpetusGameOptions> {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String DEFAULT_FILE_NAME = "impetus-options.json";

    public final QualitySettings quality = new QualitySettings();
    public final AdvancedSettings advanced = new AdvancedSettings();
    public final PerformanceSettings performance = new PerformanceSettings();
    public final NotificationSettings notifications = new NotificationSettings();

    // Index into the monitor's available modes; 0 = current/desktop
    public int fullscreenResolution = 0;

    public FullscreenMode fullscreenMode = FullscreenMode.OFF;

    private boolean readOnly;

    private Path configPath;

    // Fresh defaults, used when no file exists
    public static ImpetusGameOptions defaults() {
        var options = new ImpetusGameOptions();
        options.configPath = getConfigPath(DEFAULT_FILE_NAME);

        return options;
    }

    // The options screen edits this object directly
    @Override
    public ImpetusGameOptions getData() {
        return this;
    }

    // Writes changes back to disk
    @Override
    public void save() {
        try {
            writeToDisk(this);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }

    }

    public static class PerformanceSettings {
        public int chunkBuilderThreads = 0;
        @SerializedName("always_defer_chunk_updates_v2") // this will reset the option in older configs
        public boolean alwaysDeferChunkUpdates = true;

        public boolean animateOnlyVisibleTextures = true;
        public boolean useEntityCulling = true;
        public boolean useFogOcclusion = true;
        public boolean useBlockFaceCulling = true;
        public boolean useCompactVertexFormat = false;
        @SerializedName("use_translucent_face_sorting_v2")
        public boolean useTranslucentFaceSorting = true;
        public boolean useRenderPassOptimization = true;
        public boolean useRenderPassConsolidation = true;
        public boolean useFasterClouds = true;
        public boolean useNoErrorGLContext = true;

        public AsyncOcclusionMode asyncOcclusionMode = AsyncOcclusionMode.ONLY_SHADOW;

        // Matches modern Sodium's Performance page.
        public DeferChunkUpdatesMode deferChunkUpdatesMode = DeferChunkUpdatesMode.ONE_FRAME;
        public InactivityFpsLimit inactivityFpsLimit = InactivityFpsLimit.AFK;
        public QuadSplittingMode quadSplittingMode = QuadSplittingMode.ENABLED;
    }

    public static class AdvancedSettings {
        public boolean enableMemoryTracing = false;
        public boolean useAdvancedStagingBuffers = true;
        public boolean disableIncompatibleModWarnings = false;

        public int cpuRenderAheadLimit = 3;
    }

    public static class QualitySettings {
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        public boolean enableVignette = true;

        @SerializedName("use_quad_normals_for_shading_v2")
        public boolean useQuadNormalsForShading = true;

        public int chunkFadeInDuration = 0;

        public int legacyBiomeBlendRadius = 0;

        public int cloudHeight = 128;
        public int cloudDistance = 64;
        public int weatherEffectRadius = 10;
        public int entityDistance = 100;

        // Matches modern Sodium's Quality page.
        public boolean improvedTransparency = false;
        public PixelFilteringMode pixelFiltering = PixelFilteringMode.NEAREST;
        public boolean hiddenFluidCulling = true;
        public boolean improvedFluidShaping = false;
        public boolean closestPointEntitySort = false;

        // Visual-only guess at fluidlogging on servers that cannot report it; see FluidloggingInference. Touching by default since pool and fountain rims, the common case, only touch water on one side
        public FluidloggingGuess inferredFluidlogging = FluidloggingGuess.TOUCHING;
    }

    public static class NotificationSettings {
        public boolean showToasts = true;
        public boolean autosaveIndicator = true;
        public boolean forceDisableDonationPrompts = false;

        public boolean hasClearedDonationButton = false;
        public boolean hasSeenDonationPrompt = false;
    }

    public enum GraphicsQuality implements TextProvider {
        DEFAULT(List.of("options.gamma.default", "generator.default")),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        private final TextComponent name;

        GraphicsQuality(String name) {
            this.name = TextComponent.translatable(name);
        }

        GraphicsQuality(List<String> names) {
            this.name = TextComponent.translatable(names);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Resolves DEFAULT against the global fancy toggle
        public boolean isFancy(boolean fancyGraphics) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphics);
        }
    }

    // Deferral policy for chunk mesh uploads, mirrors modern Sodium's DeferMode
    public enum DeferChunkUpdatesMode implements TextProvider {
        ZERO_FRAMES("impetus.options.defer_chunk_updates.zero"),
        ONE_FRAME("impetus.options.defer_chunk_updates.one"),
        ALWAYS("impetus.options.defer_chunk_updates.always");

        private final TextComponent name;

        DeferChunkUpdatesMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Whether a section not visible last frame should still be deferred
        public boolean defersInvisible() {
            return this != ZERO_FRAMES;
        }

        // Whether even visible sections should be deferred (never block the frame)
        public boolean defersVisible() {
            return this == ALWAYS;
        }
    }

    // Frame-rate limiting behavior while the window is unfocused/idle
    public enum InactivityFpsLimit implements TextProvider {
        NO_LIMIT("impetus.options.inactivity_fps_limit.no_limit"),
        AFK("impetus.options.inactivity_fps_limit.afk"),
        MINIMIZED("impetus.options.inactivity_fps_limit.minimized");

        private final TextComponent name;

        InactivityFpsLimit(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Terrain quad-splitting mode used by the translucency sorter
    public enum QuadSplittingMode implements TextProvider {
        DISABLED("impetus.options.quad_splitting.disabled"),
        ENABLED("impetus.options.quad_splitting.enabled");

        private final TextComponent name;

        QuadSplittingMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Anything but OFF
        public boolean isEnabled() {
            return this == ENABLED;
        }
    }

    // Magnification filtering applied to the block atlas (per-pixel look)
    public enum PixelFilteringMode implements TextProvider {
        NEAREST("impetus.options.pixel_filtering.nearest"),
        LINEAR("impetus.options.pixel_filtering.linear");

        private final TextComponent name;

        PixelFilteringMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Which neighbourhood makes a fluidloggable block render as holding the fluid next to it: a fluid directly above always counts once enabled, the side count is the number of horizontal fluid sources needed
    public enum FluidloggingGuess implements TextProvider {
        OFF("impetus.options.inferred_fluidlogging.off", Integer.MAX_VALUE),
        SUBMERGED("impetus.options.inferred_fluidlogging.submerged", Integer.MAX_VALUE),
        SURROUNDED("impetus.options.inferred_fluidlogging.surrounded", 2),
        TOUCHING("impetus.options.inferred_fluidlogging.touching", 1);

        private final TextComponent name;
        public final int minSides;

        FluidloggingGuess(String key, int minSides) {
            this.name = TextComponent.translatable(key);
            this.minSides = minSides;
        }

        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // On LWJGL2/1.12.2, EXCLUSIVE and BORDERLESS both map to the display's fullscreen mode; distinction kept for config parity and future backends
    public enum FullscreenMode implements TextProvider {
        OFF("impetus.options.fullscreen_mode.off"),
        BORDERLESS("impetus.options.fullscreen_mode.borderless"),
        EXCLUSIVE("impetus.options.fullscreen_mode.exclusive");

        private final TextComponent name;

        FullscreenMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Display name for the cycler
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Whether this window mode is fullscreen
        public boolean isFullscreen() {
            return this != OFF;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    // Loads the default file name
    public static ImpetusGameOptions load() {
        return load(DEFAULT_FILE_NAME);
    }

    // Reads the json, falling back to defaults and marking read-only if it cannot be parsed
    public static ImpetusGameOptions load(String name) {
        Path path = getConfigPath(name);
        ImpetusGameOptions config;
        boolean resaveConfig = true;

        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                config = GSON.fromJson(reader, ImpetusGameOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            } catch (JsonSyntaxException e) {
                LOGGER.error("Could not parse config, will fallback to default settings", e);
                config = new ImpetusGameOptions();
                resaveConfig = false;
            }
        } else {
            config = new ImpetusGameOptions();
        }

        config.configPath = path;

        // TODO Impetus: Remove the field completely in 0.4
        config.notifications.forceDisableDonationPrompts = false;

        if (resaveConfig) {
            try {
                writeToDisk(config);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't update config file", e);
            }
        }

        return config;
    }

    // Under the config directory
    private static Path getConfigPath(String name) {
        return Paths.get("config", name);
    }

    // Serialises to json, via a temp file so a crash mid-write cannot truncate the config
    public static void writeToDisk(ImpetusGameOptions config) throws IOException {
        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }

        Path dir = config.configPath.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        // Use a temporary location next to the config's final destination
        Path tempPath = config.configPath.resolveSibling(config.configPath.getFileName() + ".tmp");

        // Write the file to our temporary location
        Files.writeString(tempPath, GSON.toJson(config));

        // Atomically replace the old config file (if it exists) with the temporary file
        Files.move(tempPath, config.configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    // Set by the host when the config must not be written (a failed early startup); a file that merely failed to parse is rewritten with defaults on the next save
    public boolean isReadOnly() {
        return this.readOnly;
    }

    // Marks read-only
    public void setReadOnly() {
        this.readOnly = true;
    }

    // The file this was loaded from
    public String getFileName() {
        return this.configPath.getFileName().toString();
    }
}
