package com.bdmajora.extras;

import com.bdmajora.extras.config.ExtrasRuntime;
import com.bdmajora.extras.config.ExtrasSchema;
import com.bdmajora.impetus.impl.gui.Localized;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Objects;

// Settings of the Extras module.
public final class ExtrasConfig {
    // Group of the animation settings.
    public final AnimationSettings animation = new AnimationSettings();
    // Group of the particle settings.
    public final ParticleSettings particle = new ParticleSettings();
    // Group of the detail settings.
    public final DetailSettings detail = new DetailSettings();
    // Group of the render settings.
    public final RenderSettings render = new RenderSettings();
    // Group of the extra settings.
    public final ExtraSettings extra = new ExtraSettings();
    // Group of the render budget settings.
    public final RenderBudgetSettings renderBudget = new RenderBudgetSettings();
    // Group of the gpu booster settings.
    public final GpuBoosterSettings gpuBooster = new GpuBoosterSettings();
    // Group of the async settings.
    public final AsyncSettings async = new AsyncSettings();
    // Group of the baked entities settings.
    public final BakedEntitySettings bakedEntities = new BakedEntitySettings();
    // Group of the network settings.
    public final NetworkSettings network = new NetworkSettings();
    // Group of the threads settings.
    public final ThreadSettings threads = new ThreadSettings();
    // Group of the occlusion settings.
    public final OcclusionSettings occlusion = new OcclusionSettings();
    // Group of the hud settings.
    public final HudSettings hud = new HudSettings();
    // Group of the text settings.
    public final TextSettings text = new TextSettings();
    // Group of the client tick settings.
    public final ClientTickSettings clientTick = new ClientTickSettings();
    // Group of the entity models settings.
    public final EntityModelSettings entityModels = new EntityModelSettings();
    // Group of the leaves settings.
    public final LeafSettings leaves = new LeafSettings();
    // Group of the loading settings.
    public final LoadingSettings loading = new LoadingSettings();
    // Group of the zoom settings.
    public final ZoomSettings zoom = new ZoomSettings();

    // Forge configuration behind the settings or null for a detached config.
    private Configuration config;

    // Loads the value.
    public static ExtrasConfig load(File file) {
        Configuration config = new Configuration(Objects.requireNonNull(file, "file"));
        ExtrasConfig options = new ExtrasConfig();
        options.config = config;
        try {
            ExtrasSchema.read(config, options);
        } catch (RuntimeException e) {
            Extras.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            options = new ExtrasConfig();
            options.config = config;
        }
        ExtrasRuntime.install(options);
        ExtrasRuntime.apply(options);
        return options;
    }

    // Writes the changes.
    public void writeChanges() {
        Configuration file = config;
        if (file == null) {
            return;
        }
        ExtrasSchema.write(file, this);
        ExtrasRuntime.apply(this);
    }

    // Choices of the overlay corner setting.
    public enum OverlayCorner implements Localized {
        // Top left corner.
        TOP_LEFT("impetus.options.extras.overlay_corner.top_left", false, false),
        // Top right corner.
        TOP_RIGHT("impetus.options.extras.overlay_corner.top_right", false, true),
        // Bottom left corner.
        BOTTOM_LEFT("impetus.options.extras.overlay_corner.bottom_left", true, false),
        // Bottom right corner.
        BOTTOM_RIGHT("impetus.options.extras.overlay_corner.bottom_right", true, true);

        // Text of the key option.
        private final String key;
        // Switch of the bottom option.
        private final boolean bottom;
        // Switch of the right option.
        private final boolean right;

        // Creates an overlay corner from its key and bottom and right.
        OverlayCorner(String key, boolean bottom, boolean right) {
            this.key = key;
            this.bottom = bottom;
            this.right = right;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }

        // Returns whether the corner is at the bottom of the screen.
        public boolean isBottom() {
            return this.bottom;
        }

        // Returns whether the corner is at the right of the screen.
        public boolean isRight() {
            return this.right;
        }
    }

    // Choices of the leaf culling setting.
    public enum LeafCulling implements Localized {
        // Vanilla leaf culling.
        DEFAULT("impetus.options.extras.leaf_culling.default"),
        // Culls a leaf face toward another leaf when every other side is also covered.
        CHECK("impetus.options.extras.leaf_culling.check"),
        // Culls a leaf face toward another leaf unless air lies within the configured depth behind it.
        DEPTH("impetus.options.extras.leaf_culling.depth");

        // Text of the key option.
        private final String key;

        // Creates a leaf culling from its key.
        LeafCulling(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the text contrast setting.
    public enum TextContrast implements Localized {
        // Text drawn as is.
        NONE("impetus.options.extras.text_contrast.none"),
        // Text on a dark backing.
        BACKGROUND("impetus.options.extras.text_contrast.background"),
        // Text with a drop shadow.
        SHADOW("impetus.options.extras.text_contrast.shadow");

        // Text of the key option.
        private final String key;

        // Creates a text contrast from its key.
        TextContrast(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the cloud translucency setting.
    public enum CloudTranslucency implements Localized {
        // Vanilla's cloud draw order.
        DEFAULT("impetus.options.extras.cloud_translucency.default"),
        // Clouds always drawn after translucent terrain.
        ALWAYS("impetus.options.extras.cloud_translucency.always"),
        // Clouds always drawn before translucent terrain.
        NEVER("impetus.options.extras.cloud_translucency.never");

        // Text of the key option.
        private final String key;

        // Creates a cloud translucency from its key.
        CloudTranslucency(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the fog shape setting.
    public enum FogShape implements Localized {
        // Vanilla's fog by depth.
        VANILLA("impetus.options.extras.fog_shape.vanilla"),
        // Fog by horizontal distance.
        CYLINDRICAL("impetus.options.extras.fog_shape.cylindrical"),
        // Fog by spherical distance.
        RADIAL("impetus.options.extras.fog_shape.radial"),
        // Fog by distance along the view axis.
        PLANAR("impetus.options.extras.fog_shape.planar");

        // Text of the key option.
        private final String key;

        // Creates a fog shape from its key.
        FogShape(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }

        // Returns the fog shape index the terrain shaders switch on.
        public int shaderIndex() {
            return this.ordinal();
        }
    }

    // Choices of the time override setting.
    public enum TimeOverride implements Localized {
        // The world's own time.
        DEFAULT("impetus.options.extras.time.default"),
        // Always noon.
        DAY("impetus.options.extras.time.day"),
        // Always midnight.
        NIGHT("impetus.options.extras.time.night");

        // Text of the key option.
        private final String key;

        // Creates a time override from its key.
        TimeOverride(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the weather override setting.
    public enum WeatherOverride implements Localized {
        // The world's own weather.
        DEFAULT("impetus.options.extras.weather.default"),
        // Always clear.
        CLEAR("impetus.options.extras.weather.clear"),
        // Always raining.
        RAIN("impetus.options.extras.weather.rain"),
        // Always thundering.
        THUNDER("impetus.options.extras.weather.thunder");

        // Text of the key option.
        private final String key;

        // Creates a weather override from its key.
        WeatherOverride(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the budget profile setting.
    public enum BudgetProfile implements Localized {
        // Frame budget of 45 FPS.
        QUALITY("impetus.options.extras.budget_profile.quality", 22.2),
        // Frame budget of 60 FPS.
        BALANCED("impetus.options.extras.budget_profile.balanced", 16.7),
        // Frame budget of about 71 FPS.
        PERFORMANCE("impetus.options.extras.budget_profile.performance", 14.0);

        // Text of the key option.
        private final String key;
        // Value of the target frame millis option.
        public final double targetFrameMillis;

        // Creates a budget profile from its key and target frame millis.
        BudgetProfile(String key, double targetFrameMillis) {
            this.key = key;
            this.targetFrameMillis = targetFrameMillis;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the render budget row with Off first.
    public enum RenderBudgetMode implements Localized {
        // Budget off.
        OFF("options.off", null),
        // Performance profile.
        PERFORMANCE("impetus.options.extras.budget_profile.performance", BudgetProfile.PERFORMANCE),
        // Balanced profile.
        BALANCED("impetus.options.extras.budget_profile.balanced", BudgetProfile.BALANCED),
        // Quality profile.
        QUALITY("impetus.options.extras.budget_profile.quality", BudgetProfile.QUALITY);

        // Translation key of the entry.
        private final String key;
        // Profile the entry selects or null for off.
        public final BudgetProfile profile;

        // Creates a mode from its key and profile.
        RenderBudgetMode(String key, BudgetProfile profile) {
            this.key = key;
            this.profile = profile;
        }

        // Returns the mode the settings hold.
        public static RenderBudgetMode of(RenderBudgetSettings settings) {
            if (!settings.enabled) {
                return OFF;
            }
            return switch (settings.profile) {
                case PERFORMANCE -> PERFORMANCE;
                case BALANCED -> BALANCED;
                case QUALITY -> QUALITY;
            };
        }

        // Stores the mode in the settings and keeps the profile when turned off.
        public void applyTo(RenderBudgetSettings settings) {
            settings.enabled = this != OFF;
            if (this.profile != null) {
                settings.profile = this.profile;
            }
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Choices of the vertical sync setting.
    public enum VerticalSync implements Localized {
        // No vertical sync.
        OFF("options.off"),
        // Vertical sync.
        ON("options.on"),
        // Adaptive sync.
        ADAPTIVE("impetus.options.extras.vertical_sync.adaptive");

        // Text of the key option.
        private final String key;

        // Creates a vertical sync from its key.
        VerticalSync(String key) {
            this.key = key;
        }

        // Returns the translation key of the entry.
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Settings of the animation group.
    public static final class AnimationSettings {
        // Switch of the all option.
        public boolean all = true;
        // Switch of the water option.
        public boolean water = true;
        // Switch of the lava option.
        public boolean lava = true;
        // Switch of the fire option.
        public boolean fire = true;
        // Switch of the portal option.
        public boolean portal = true;
        // Switch of the block animations option.
        public boolean blockAnimations = true;
        // Switch of the redstone option.
        public boolean redstone = true;
        // Switch of the explosion option.
        public boolean explosion = true;
        // Switch of the flame option.
        public boolean flame = true;
        // Switch of the smoke option.
        public boolean smoke = true;
        // Switch of the sculk sensor option.
        public boolean sculkSensor = true;
    }

    // Settings of the particle group.
    public static final class ParticleSettings {
        // Switch of the all option.
        public boolean all = true;
        // Switch of the rain splash option.
        public boolean rainSplash = true;
        // Switch of the block break option.
        public boolean blockBreak = true;
        // Switch of the block breaking option.
        public boolean blockBreaking = true;
        // Switch of the void particles option.
        public boolean voidParticles = true;
        // Switch of the water particles option.
        public boolean waterParticles = true;
        // Switch of the portal particles option.
        public boolean portalParticles = true;
        // Switch of the potion particles option.
        public boolean potionParticles = true;
        // Switch of the dripping water lava option.
        public boolean drippingWaterLava = true;
        // Switch of the firework particles option.
        public boolean fireworkParticles = true;
        // Switch of the parallel tick option.
        public boolean parallelTick = true;
        // Switch of the parallel modded option.
        public boolean parallelModded = false;
        // Switch of the light cache option.
        public boolean lightCache = true;
        // Switch of the cull offscreen option.
        public boolean cullOffscreen = true;
        // Switch of the collision cache option.
        public boolean collisionCache = true;
    }

    // Settings of the detail group.
    public static final class DetailSettings {
        // Smallest value of the stars setting.
        public static final int STARS_MIN = 500;
        // Default value of the stars setting.
        public static final int STARS_DEFAULT = 1500;
        // Largest value of the stars setting.
        public static final int STARS_MAX = 32000;

        // Switch of the sky option.
        public boolean sky = true;
        // Switch of the stars option.
        public boolean stars = true;
        // Value of the total stars option.
        public int totalStars = STARS_DEFAULT;
        // Switch of the sun option.
        public boolean sun = true;
        // Switch of the moon option.
        public boolean moon = true;
        // Switch of the rain snow option.
        public boolean rainSnow = true;
        // Switch of the biome colors option.
        public boolean biomeColors = true;
        // Switch of the sky colors option.
        public boolean skyColors = true;
        // Switch of the swamp colors option.
        public boolean swampColors = true;
        // Switch of the void fog option.
        public boolean voidFog = true;
        // Switch of the show capes option.
        public boolean showCapes = true;
        // Switch of the held item tooltips option.
        public boolean heldItemTooltips = true;
    }

    // Settings of the render group.
    public static final class RenderSettings {
        // Smallest value of the cloud scale setting.
        public static final int CLOUD_SCALE_MIN = 1;
        // Vanilla's cloud cell scale.
        public static final int CLOUD_SCALE_VANILLA = 4;
        // Largest value of the cloud scale setting.
        public static final int CLOUD_SCALE_MAX = 16;

        // Switch of the fog option.
        public boolean fog = true;
        // Value of the fog start option.
        public int fogStart = 100;
        // Value of the fog distance option.
        public int fogDistance = 0;
        // Shape of the distance fog.
        public FogShape fogShape = FogShape.VANILLA;
        // Value of the cloud scale option.
        public int cloudScale = CLOUD_SCALE_VANILLA;
        // Whether clouds draw before or after translucent terrain.
        public CloudTranslucency cloudTranslucency = CloudTranslucency.DEFAULT;
        // Switch of the light updates option.
        public boolean lightUpdates = true;
        // Switch of the item frames option.
        public boolean itemFrames = true;
        // Value of the item frame lod distance option.
        public int itemFrameLodDistance = 0;
        // Switch of the item frame name tag option.
        public boolean itemFrameNameTag = true;
        // Switch of the armor stands option.
        public boolean armorStands = true;
        // Switch of the paintings option.
        public boolean paintings = true;
        // Switch of the pistons option.
        public boolean pistons = true;
        // Switch of the beacons option.
        public boolean beacons = true;
        // Switch of the limit beacon beam height option.
        public boolean limitBeaconBeamHeight = false;
        // Switch of the enchanting table books option.
        public boolean enchantingTableBooks = true;
        // Switch of the player name tag option.
        public boolean playerNameTag = true;
        // Switch of the dropped items fancy option.
        public boolean droppedItemsFancy = true;
        // Switch of the item model flicker fix option.
        public boolean itemModelFlickerFix = true;
        // Switch of the prevent shaders option.
        public boolean preventShaders = false;
        // Switch of the profile entity rendering option.
        public boolean profileEntityRendering = false;
    }

    // Settings of the extra group.
    public static final class ExtraSettings {
        // Smallest value of the steady hud refresh setting.
        public static final int STEADY_HUD_REFRESH_MIN = 1;
        // Default value of the steady hud refresh setting.
        public static final int STEADY_HUD_REFRESH_DEFAULT = 1;
        // Largest value of the steady hud refresh setting.
        public static final int STEADY_HUD_REFRESH_MAX = 20;
        // Vanilla's autosave interval of 45 seconds.
        public static final int AUTOSAVE_VANILLA_TICKS = 900;
        // Shortest autosave interval with 0 turning autosave off.
        public static final int AUTOSAVE_MIN_TICKS = 0;
        // Longest autosave interval of 30 minutes.
        public static final int AUTOSAVE_MAX_TICKS = 36000;

        // Switch of the show fps option.
        public boolean showFps = false;
        // Switch of the show fps extended option.
        public boolean showFpsExtended = true;
        // Switch of the show coords option.
        public boolean showCoords = false;
        // Switch of the ignore reduced debug info option.
        public boolean ignoreReducedDebugInfo = false;
        // Corner the overlay sits in.
        public OverlayCorner overlayCorner = OverlayCorner.TOP_LEFT;
        // How overlay text stays readable.
        public TextContrast textContrast = TextContrast.SHADOW;
        // Switch of the steady debug hud option.
        public boolean steadyDebugHud = true;
        // Value of the steady debug hud refresh interval option.
        public int steadyDebugHudRefreshInterval = STEADY_HUD_REFRESH_DEFAULT;
        // Switch of the use adaptive sync option.
        public boolean useAdaptiveSync = false;
        // Switch of the toasts option.
        public boolean toasts = true;
        // Switch of the toast advancement option.
        public boolean toastAdvancement = true;
        // Switch of the toast recipe option.
        public boolean toastRecipe = true;
        // Switch of the toast tutorial option.
        public boolean toastTutorial = true;
        // Switch of the toast system option.
        public boolean toastSystem = true;
        // Switch of the mod name tooltip option.
        public boolean modNameTooltip = false;
        // Switch of the panini projection option.
        public boolean paniniProjection = false;
        // Value of the panini projection strength option.
        public int paniniProjectionStrength = 25;
        // Time of day shown on the client.
        public TimeOverride timeOverride = TimeOverride.DEFAULT;
        // Weather shown on the client.
        public WeatherOverride weatherOverride = WeatherOverride.DEFAULT;
        // Value of the autosave interval option.
        public int autosaveInterval = AUTOSAVE_VANILLA_TICKS;
    }

    // Settings of the render budget group.
    public static final class RenderBudgetSettings {
        // Smallest value of the particle budget setting.
        public static final int PARTICLE_BUDGET_MIN = 0;
        // Default value of the particle budget setting.
        public static final int PARTICLE_BUDGET_DEFAULT = 65;
        // Largest value of the particle budget setting.
        public static final int PARTICLE_BUDGET_MAX = 100;
        // Smallest value of the entity distance setting.
        public static final int ENTITY_DISTANCE_MIN = 16;
        // Default value of the entity distance setting.
        public static final int ENTITY_DISTANCE_DEFAULT = 96;
        // Largest value of the entity distance setting.
        public static final int ENTITY_DISTANCE_MAX = 256;
        // Smallest value of the block entity distance setting.
        public static final int BLOCK_ENTITY_DISTANCE_MIN = 32;
        // Default value of the block entity distance setting.
        public static final int BLOCK_ENTITY_DISTANCE_DEFAULT = 48;
        // Largest value of the block entity distance setting.
        public static final int BLOCK_ENTITY_DISTANCE_MAX = 128;

        // Switch of the enabled option.
        public boolean enabled = false;
        // Frame budget the render budget controller aims for.
        public BudgetProfile profile = BudgetProfile.BALANCED;
        // Switch of the adaptive option.
        public boolean adaptive = false;
        // Value of the particle budget option.
        public int particleBudget = PARTICLE_BUDGET_DEFAULT;
        // Value of the entity cull distance option.
        public int entityCullDistance = ENTITY_DISTANCE_DEFAULT;
        // Switch of the smart entity culling option.
        public boolean smartEntityCulling = true;
        // Switch of the block entities option.
        public boolean blockEntities = true;
        // Value of the block entity distance option.
        public int blockEntityDistance = BLOCK_ENTITY_DISTANCE_DEFAULT;
        // Switch of the item frames option.
        public boolean itemFrames = true;
        // Switch of the overlay option.
        public boolean overlay = false;
    }

    // Settings of the gpu booster group.
    public static final class GpuBoosterSettings {
        // Switch of the enabled option.
        public boolean enabled = false;
        // Switch of the fast random option.
        public boolean fastRandom = true;
        // Switch of the fast math option.
        public boolean fastMath = true;
        // Switch of the stream uploads option.
        public boolean streamUploads = true;
        // Switch of the vertex format cache option.
        public boolean vertexFormatCache = true;
    }

    // Settings of the async group.
    public static final class AsyncSettings {
        // Largest value of the threads setting.
        public static final int THREADS_MAX = 64;
        // Entity ids that tick on the main thread in a fresh config.
        public static final String[] DEFAULT_SYNCHRONIZED = {
                "minecraft:tnt", "minecraft:item", "minecraft:xp_orb", "minecraft:hopper_minecart"
        };

        // Switch of the enabled option.
        public boolean enabled = true;
        // Value of the threads option.
        public int threads = 0;
        // Switch of the entities option.
        public boolean entities = true;
        // Switch of the modded entities option.
        public boolean moddedEntities = false;
        // Switch of the random ticks option.
        public boolean randomTicks = true;
        // Switch of the spawning option.
        public boolean spawning = true;
        // Text of the synchronized entities option.
        public String[] synchronizedEntities = DEFAULT_SYNCHRONIZED.clone();
    }

    // Settings of the baked entity group.
    public static final class BakedEntitySettings {
        // Switch of the enabled option.
        public boolean enabled = true;
        // Switch of the chests option.
        public boolean chests = true;
        // Switch of the ender chests option.
        public boolean enderChests = true;
        // Switch of the signs option.
        public boolean signs = true;
        // Switch of the beds option.
        public boolean beds = true;
        // Switch of the shulker boxes option.
        public boolean shulkerBoxes = true;
    }

    // Settings of the network group.
    public static final class NetworkSettings {
        // Smallest value of the timeout setting.
        public static final int TIMEOUT_MIN = 5;
        // Largest value of the timeout setting.
        public static final int TIMEOUT_MAX = 600;
        // Default value of the read timeout setting.
        public static final int READ_TIMEOUT_DEFAULT = 120;
        // Default value of the login timeout setting.
        public static final int LOGIN_TIMEOUT_DEFAULT = 120;
        // Default value of the keep alive timeout setting.
        public static final int KEEP_ALIVE_TIMEOUT_DEFAULT = 120;

        // Switch of the large packets option.
        public boolean largePackets = true;
        // Switch of the flush consolidation option.
        public boolean flushConsolidation = true;
        // Switch of the fast var ints option.
        public boolean fastVarInts = true;
        // Switch of the pooled compression option.
        public boolean pooledCompression = true;
        // Value of the read timeout seconds option.
        public int readTimeoutSeconds = READ_TIMEOUT_DEFAULT;
        // Value of the login timeout seconds option.
        public int loginTimeoutSeconds = LOGIN_TIMEOUT_DEFAULT;
        // Value of the keep alive timeout seconds option.
        public int keepAliveTimeoutSeconds = KEEP_ALIVE_TIMEOUT_DEFAULT;
    }

    // Settings of the thread group.
    public static final class ThreadSettings {
        // Smallest value of the priority setting.
        public static final int PRIORITY_MIN = Thread.MIN_PRIORITY;
        // Normal thread priority.
        public static final int PRIORITY_NORMAL = Thread.NORM_PRIORITY;
        // Largest value of the priority setting.
        public static final int PRIORITY_MAX = Thread.MAX_PRIORITY;
        // Default value of the chunk builder setting.
        public static final int CHUNK_BUILDER_DEFAULT = Thread.NORM_PRIORITY - 2;

        // Value of the render thread priority option.
        public int renderThreadPriority = PRIORITY_NORMAL;
        // Value of the server thread priority option.
        public int serverThreadPriority = PRIORITY_NORMAL;
        // Value of the chunk builder priority option.
        public int chunkBuilderPriority = CHUNK_BUILDER_DEFAULT;
        // Switch of the remove render yield option.
        public boolean removeRenderYield = false;
    }

    // Settings of the occlusion group.
    public static final class OcclusionSettings {
        // Default value of the max entity size setting.
        public static final int MAX_ENTITY_SIZE_DEFAULT = 16;
        // Default value of the max block entity size setting.
        public static final int MAX_BLOCK_ENTITY_SIZE_DEFAULT = 4;
        // Default value of the slack setting.
        public static final int SLACK_DEFAULT = 100;
        // Default value of the max distance setting in blocks.
        public static final int MAX_DISTANCE_DEFAULT = 256;
        // Default value of the hide delay setting in milliseconds.
        public static final int HIDE_DELAY_DEFAULT = 100;

        // Switch of the enabled option.
        public boolean enabled = true;
        // Switch of the entities option.
        public boolean entities = true;
        // Switch of the block entities option.
        public boolean blockEntities = true;
        // Value of the max entity size option.
        public int maxEntitySize = MAX_ENTITY_SIZE_DEFAULT;
        // Value of the max block entity size option.
        public int maxBlockEntitySize = MAX_BLOCK_ENTITY_SIZE_DEFAULT;
        // Value of the raycast slack option.
        public int raycastSlack = SLACK_DEFAULT;
        // Value of the max distance option.
        public int maxDistance = MAX_DISTANCE_DEFAULT;
        // Value of the hide delay option.
        public int hideDelay = HIDE_DELAY_DEFAULT;
        // Switch of the pause hidden armor stands option.
        public boolean pauseHidden = true;
        // Switch of the name tags through walls option.
        public boolean nameTagsThroughWalls = true;
    }

    // Settings of the hud group.
    public static final class HudSettings {
        // Smallest value of the cache fps setting.
        public static final int CACHE_FPS_MIN = 10;
        // Default value of the cache fps setting.
        public static final int CACHE_FPS_DEFAULT = 30;
        // Largest value of the cache fps setting.
        public static final int CACHE_FPS_MAX = 120;

        // Switch of the cache enabled option.
        public boolean cacheEnabled = false;
        // Value of the cache fps option.
        public int cacheFps = CACHE_FPS_DEFAULT;
    }

    // Settings of the text group.
    public static final class TextSettings {
        // Switch of the batch glyphs option.
        public boolean batchGlyphs = true;
    }

    // Settings of the client tick group.
    public static final class ClientTickSettings {
        // Switch of the lightmap caching option.
        public boolean lightmapCaching = true;
    }

    // Settings of the entity model group.
    public static final class EntityModelSettings {
        // Switch of the matrix transforms option.
        public boolean matrixTransforms = true;
    }

    // Settings of the leaf group.
    public static final class LeafSettings {
        // Smallest value of the depth setting.
        public static final int DEPTH_MIN = 1;
        // Default value of the depth setting.
        public static final int DEPTH_DEFAULT = 2;
        // Largest value of the depth setting.
        public static final int DEPTH_MAX = 4;

        // How leaves cull their faces.
        public LeafCulling cullingMode = LeafCulling.DEFAULT;
        // Value of the culling depth option.
        public int cullingDepth = DEPTH_DEFAULT;
    }

    // Settings of the loading group.
    public static final class LoadingSettings {
        // Switch of the skip world load gc option.
        public boolean skipWorldLoadGc = true;
        // Switch of the smooth dimension change option.
        public boolean smoothDimensionChange = true;
        // Switch of the release screenshot buffers option.
        public boolean releaseScreenshotBuffers = true;
        // Switch of the async screenshots option.
        public boolean asyncScreenshots = true;
        // Switch of the driver atlas limit option.
        public boolean driverAtlasLimit = true;
    }

    // Settings of the zoom group.
    public static final class ZoomSettings {
        // Divisor that turns the zoom level into a fraction of the view width.
        public static final int LEVEL_SCALE = 100;
        // Smallest value of the level setting.
        public static final int LEVEL_MIN = 5;
        // Default value of the level setting.
        public static final int LEVEL_DEFAULT = 23;
        // Largest value of the level setting.
        public static final int LEVEL_MAX = 90;

        // Switch of the enabled option.
        public boolean enabled = true;
        // Value of the level option.
        public int level = LEVEL_DEFAULT;
        // Switch of the smooth camera option.
        public boolean smoothCamera = true;
        // Switch of the hide hand option.
        public boolean hideHand = true;
    }
}
