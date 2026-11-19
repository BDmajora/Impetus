package com.bdmajora.impetus.engine.impl.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

// Options of the engine stored in a json file.
public class ImpetusGameOptions implements OptionStorage<ImpetusGameOptions> {
    // Logger of config problems.
    private static final Logger LOGGER = LogManager.getLogger();

    // Name of the default config file.
    private static final String DEFAULT_FILE_NAME = "impetus-options.json";

    // Quality options.
    public final QualitySettings quality = new QualitySettings();
    // Advanced options.
    public final AdvancedSettings advanced = new AdvancedSettings();
    // Performance options.
    public final PerformanceSettings performance = new PerformanceSettings();
    // Notification options.
    public final NotificationSettings notifications = new NotificationSettings();
    // Mesh terrain options.
    public final MeshTerrainSettings meshTerrain = new MeshTerrainSettings();

    // Index of the fullscreen resolution where zero is the desktop mode.
    public int fullscreenResolution = 0;

    // Fullscreen mode.
    public FullscreenMode fullscreenMode = FullscreenMode.OFF;

    // Terrain backend in use.
    public GraphicsApi graphicsApi = GraphicsApi.OPENGL;

    public static final int MIN_SIMULATION_DISTANCE = 5;
    public static final int MAX_SIMULATION_DISTANCE = 32;

    // Read by the integrated server while the client can change it.
    public volatile int simulationDistance = 10;

    public int getSimulationDistance() {
        return Math.clamp(this.simulationDistance, MIN_SIMULATION_DISTANCE, MAX_SIMULATION_DISTANCE);
    }

    public void setSimulationDistance(int distance) {
        this.simulationDistance = Math.clamp(distance, MIN_SIMULATION_DISTANCE, MAX_SIMULATION_DISTANCE);
    }

    // Read only state of the config.
    private boolean readOnly;

    // Path of the config file.
    private Path configPath;

    // Creates options with default values.
    public static ImpetusGameOptions defaults() {
        var options = new ImpetusGameOptions();
        options.configPath = getConfigPath(DEFAULT_FILE_NAME);

        return options;
    }

    // Returns the options themselves.
    @Override
    public ImpetusGameOptions getData() {
        return this;
    }

    // Writes the options to the config file.
    @Override
    public void save() {
        try {
            writeToDisk(this);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't save configuration changes", e);
        }

    }

    // Options that trade quality for speed.
    public static class PerformanceSettings {
        // Number of chunk builder threads where zero means automatic.
        public int chunkBuilderThreads = 0;
        // Whether chunk updates are always deferred.
        @SerializedName("always_defer_chunk_updates_v2")
        public boolean alwaysDeferChunkUpdates = true;

        // Whether only visible textures animate.
        public boolean animateOnlyVisibleTextures = true;
        // Whether hidden entities are culled.
        public boolean useEntityCulling = true;
        // Whether terrain hidden by fog is skipped.
        public boolean useFogOcclusion = true;
        // Whether hidden block faces are culled.
        public boolean useBlockFaceCulling = true;
        // Whether terrain uses the compact vertex format.
        public boolean useCompactVertexFormat = false;
        // Whether translucent faces are sorted.
        @SerializedName("use_translucent_face_sorting_v2")
        public boolean useTranslucentFaceSorting = true;
        // Whether quads move to cheaper render passes.
        public boolean useRenderPassOptimization = true;
        // Whether render passes are merged.
        public boolean useRenderPassConsolidation = true;
        // Whether clouds use the faster renderer.
        public boolean useFasterClouds = true;
        // Whether the GL context skips error checks.
        public boolean useNoErrorGLContext = true;
        // Whether terrain uses multi draw indirect.
        public boolean useMultiDrawIndirect = true;

        // Mode of asynchronous occlusion.
        public AsyncOcclusionMode asyncOcclusionMode = AsyncOcclusionMode.ONLY_SHADOW;

        // Whether hidden terrain is culled by a software rasterizer.
        public boolean useRasterOcclusionCulling = false;

        // Mode that defers chunk updates.
        public DeferChunkUpdatesMode deferChunkUpdatesMode = DeferChunkUpdatesMode.ONE_FRAME;
        // Frame rate limit while inactive.
        public InactivityFpsLimit inactivityFpsLimit = InactivityFpsLimit.AFK;
        // Mode of quad splitting.
        public QuadSplittingMode quadSplittingMode = QuadSplittingMode.ENABLED;
    }

    // Options for troubleshooting.
    public static class AdvancedSettings {
        // Whether memory use is traced.
        public boolean enableMemoryTracing = false;
        // Whether staging buffers use advanced mapping.
        public boolean useAdvancedStagingBuffers = true;
        // Whether warnings about incompatible mods are hidden.
        public boolean disableIncompatibleModWarnings = false;

        // Number of frames the CPU may run ahead.
        public int cpuRenderAheadLimit = 3;
    }

    // Options that change how the world looks.
    public static class QualitySettings {
        // Quality of weather.
        public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
        // Quality of leaves.
        public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;

        // Whether the vignette is drawn.
        public boolean enableVignette = true;

        // Whether shading uses the quad normals.
        @SerializedName("use_quad_normals_for_shading_v2")
        public boolean useQuadNormalsForShading = true;

        // Milliseconds a new section takes to fade in.
        public int chunkFadeInDuration = 0;

        // Radius of the biome blend.
        public int legacyBiomeBlendRadius = 0;

        // Height of the clouds.
        public int cloudHeight = 128;
        // Distance of the clouds.
        public int cloudDistance = 64;
        // Radius of weather effects.
        public int weatherEffectRadius = 10;
        // Distance entities are drawn at.
        public int entityDistance = 100;

        // Filter of the block atlas.
        public PixelFilteringMode pixelFiltering = PixelFilteringMode.NEAREST;
        // Whether hidden fluid faces are culled.
        public boolean hiddenFluidCulling = true;
        // Whether fluid shapes are improved.
        public boolean improvedFluidShaping = false;
        // Whether entities sort by their closest point.
        public boolean closestPointEntitySort = false;

        // How blocks are drawn as fluidlogged when the server does not report it.
        public FluidloggingGuess inferredFluidlogging = FluidloggingGuess.TOUCHING;
    }

    // Settings of the mesh shader terrain backend.
    public static class MeshTerrainSettings {
        // Whether newly visible sections are drawn in a second pass.
        public boolean temporalCoherence = true;
        // Sorting of translucent geometry.
        public MeshTranslucencySorting translucencySorting = MeshTranslucencySorting.QUADS;
        // Whether the geometry budget follows the free video memory.
        public boolean automaticMemory = true;
        // Geometry budget in megabytes.
        public int maxGeometryMemory = 2048;
        // Chunks kept on the GPU past the render distance.
        public int regionKeepDistance = 0;
        // Level of statistics.
        public MeshStatistics statistics = MeshStatistics.NONE;
    }

    // Ways the mesh backend orders translucent geometry.
    public enum MeshTranslucencySorting implements TextProvider {
        NONE("impetus.options.mesh_terrain.sorting.none"),
        SECTIONS("impetus.options.mesh_terrain.sorting.sections"),
        QUADS("impetus.options.mesh_terrain.sorting.quads");

        // Display name.
        private final TextComponent name;

        // Creates a mode with a language key.
        MeshTranslucencySorting(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Levels of statistics counted by the mesh backend.
    public enum MeshStatistics implements TextProvider {
        NONE("impetus.options.mesh_terrain.statistics.none"),
        FRUSTUM("impetus.options.mesh_terrain.statistics.frustum"),
        REGIONS("impetus.options.mesh_terrain.statistics.regions"),
        SECTIONS("impetus.options.mesh_terrain.statistics.sections"),
        QUADS("impetus.options.mesh_terrain.statistics.quads");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        MeshStatistics(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Returns whether this level counts at least as much as another.
        public boolean includes(MeshStatistics level) {
            return this.ordinal() >= level.ordinal();
        }
    }

    // Options of notifications.
    public static class NotificationSettings {
        // Whether toasts are shown.
        public boolean showToasts = true;
        // Whether the autosave indicator is shown.
        public boolean autosaveIndicator = true;
        // Whether donation prompts are disabled.
        public boolean forceDisableDonationPrompts = false;

        // Whether the donation button was cleared.
        public boolean hasClearedDonationButton = false;
        // Whether the donation prompt was seen.
        public boolean hasSeenDonationPrompt = false;
    }

    // Quality levels of clouds and weather.
    public enum GraphicsQuality implements TextProvider {
        DEFAULT(List.of("options.gamma.default", "generator.default")),
        FANCY("options.clouds.fancy"),
        FAST("options.clouds.fast");

        // Display name.
        private final TextComponent name;

        // Creates a quality with a language key.
        GraphicsQuality(String name) {
            this.name = TextComponent.translatable(name);
        }

        // Creates a quality with fallback language keys.
        GraphicsQuality(List<String> names) {
            this.name = TextComponent.translatable(names);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Returns whether the quality is fancy for the global setting.
        public boolean isFancy(boolean fancyGraphics) {
            return (this == FANCY) || (this == DEFAULT && fancyGraphics);
        }
    }

    // Policies for deferring chunk mesh uploads.
    public enum DeferChunkUpdatesMode implements TextProvider {
        ZERO_FRAMES("impetus.options.defer_chunk_updates.zero"),
        ONE_FRAME("impetus.options.defer_chunk_updates.one"),
        ALWAYS("impetus.options.defer_chunk_updates.always");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        DeferChunkUpdatesMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Returns whether invisible sections are deferred.
        public boolean defersInvisible() {
            return this != ZERO_FRAMES;
        }

        // Returns whether visible sections are deferred.
        public boolean defersVisible() {
            return this == ALWAYS;
        }
    }

    // Frame rate limits while the window is idle.
    public enum InactivityFpsLimit implements TextProvider {
        NO_LIMIT("impetus.options.inactivity_fps_limit.no_limit"),
        AFK("impetus.options.inactivity_fps_limit.afk"),
        MINIMIZED("impetus.options.inactivity_fps_limit.minimized");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        InactivityFpsLimit(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Modes of quad splitting.
    public enum QuadSplittingMode implements TextProvider {
        DISABLED("impetus.options.quad_splitting.disabled"),
        ENABLED("impetus.options.quad_splitting.enabled");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        QuadSplittingMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }

        // Returns whether quad splitting is on.
        public boolean isEnabled() {
            return this == ENABLED;
        }
    }

    // Magnification filters of the block atlas.
    public enum PixelFilteringMode implements TextProvider {
        NEAREST("impetus.options.pixel_filtering.nearest"),
        LINEAR("impetus.options.pixel_filtering.linear");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        PixelFilteringMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Rules that decide when a block is drawn as holding fluid.
    public enum FluidloggingGuess implements TextProvider {
        OFF("impetus.options.inferred_fluidlogging.off", Integer.MAX_VALUE),
        SUBMERGED("impetus.options.inferred_fluidlogging.submerged", Integer.MAX_VALUE),
        SURROUNDED("impetus.options.inferred_fluidlogging.surrounded", 2),
        TOUCHING("impetus.options.inferred_fluidlogging.touching", 1);

        // Display name.
        private final TextComponent name;
        // Number of fluid sides that triggers the guess.
        public final int minSides;

        // Creates a rule with a language key and a side count.
        FluidloggingGuess(String key, int minSides) {
            this.name = TextComponent.translatable(key);
            this.minSides = minSides;
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Terrain backends.
    public enum GraphicsApi implements TextProvider {
        OPENGL("impetus.options.graphics_api.opengl"),
        NVIDIUM("impetus.options.graphics_api.nvidium");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        GraphicsApi(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Window modes.
    public enum FullscreenMode implements TextProvider {
        OFF("impetus.options.fullscreen_mode.off"),
        BORDERLESS("impetus.options.fullscreen_mode.borderless"),
        EXCLUSIVE("impetus.options.fullscreen_mode.exclusive");

        // Display name.
        private final TextComponent name;

        // Creates a constant with a language key.
        FullscreenMode(String key) {
            this.name = TextComponent.translatable(key);
        }

        // Returns the display name.
        @Override
        public TextComponent getLocalizedName() {
            return this.name;
        }
    }

    // Json reader and writer of the config.
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .create();

    // Loads the options from the default file.
    public static ImpetusGameOptions load() {
        return load(DEFAULT_FILE_NAME);
    }

    // Loads the options from a file and uses the defaults for a missing or unreadable or empty file.
    public static ImpetusGameOptions load(String name) {
        Objects.requireNonNull(name, "name");
        Path path = getConfigPath(name);
        ImpetusGameOptions config = null;
        boolean resaveConfig = true;

        if (Files.exists(path)) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ImpetusGameOptions.class);
            } catch (IOException e) {
                throw new RuntimeException("Could not parse config", e);
            } catch (JsonParseException e) {
                LOGGER.error("Could not parse config, will fallback to default settings", e);
                resaveConfig = false;
            }
        }

        ImpetusGameOptions defaults = new ImpetusGameOptions();

        if (config == null) {
            config = defaults;
        } else {
            restoreNulls(config, defaults);
        }

        config.configPath = path;

        // Clears the donation prompt override
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

    // Replaces every null field of the loaded options with the default and recurses into the settings groups.
    private static void restoreNulls(Object loaded, Object defaults) {
        for (Field field : loaded.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            Class<?> type = field.getType();

            if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || type.isPrimitive()) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(loaded);
                Object fallback = field.get(defaults);

                if (value == null) {
                    field.set(loaded, fallback);
                } else if (!type.isEnum() && type.getEnclosingClass() == ImpetusGameOptions.class && fallback != null) {
                    restoreNulls(value, fallback);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Could not validate option {}", field.getName(), e);
            }
        }
    }

    // Returns the path of a config file under the config directory.
    private static Path getConfigPath(String name) {
        return Paths.get("config", name);
    }

    // Writes the options to a temporary file and moves it over the config and rejects a read only config or a config without a file.
    public static void writeToDisk(ImpetusGameOptions config) throws IOException {
        Objects.requireNonNull(config, "config");

        if (config.isReadOnly()) {
            throw new IllegalStateException("Config file is read-only");
        }

        if (config.configPath == null) {
            throw new IllegalStateException("Config has no file");
        }

        Path dir = config.configPath.toAbsolutePath().getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }

        Path tempPath = config.configPath.resolveSibling(config.configPath.getFileName() + ".tmp");
        // Moved state of the temporary file.
        boolean moved = false;

        try {
            Files.writeString(tempPath, GSON.toJson(config));

            try {
                Files.move(tempPath, config.configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, config.configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempPath);
            }
        }
    }

    // Returns whether the config must not be written.
    public boolean isReadOnly() {
        return this.readOnly;
    }

    // Marks the config as read only.
    public void setReadOnly() {
        this.readOnly = true;
    }

    // Returns the name of the config file.
    public String getFileName() {
        if (this.configPath == null) {
            throw new IllegalStateException("Config has no file");
        }
        return this.configPath.getFileName().toString();
    }
}
