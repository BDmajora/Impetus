package com.bdmajora.extras;

import com.bdmajora.extras.config.ExtrasRuntime;
import com.bdmajora.extras.config.ExtrasSchema;
import com.bdmajora.impetus.impl.gui.Localized;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Objects;

// Every Extras setting group backed by a Forge configuration file that stores enum constants by ordinal
public final class ExtrasConfig {
    // Texture animation switches
    public final AnimationSettings animation = new AnimationSettings();
    // Particle switches and particle ticking options
    public final ParticleSettings particle = new ParticleSettings();
    // Sky and weather and colour detail switches
    public final DetailSettings detail = new DetailSettings();
    // World render switches and fog and cloud tuning
    public final RenderSettings render = new RenderSettings();
    // Overlay and toast and quality of life settings
    public final ExtraSettings extra = new ExtraSettings();
    // Adaptive render budget settings
    public final RenderBudgetSettings renderBudget = new RenderBudgetSettings();
    // GPU booster switches
    public final GpuBoosterSettings gpuBooster = new GpuBoosterSettings();
    // Parallel server ticking settings
    public final AsyncSettings async = new AsyncSettings();
    // Switches of block entities drawn as terrain
    public final BakedEntitySettings bakedEntities = new BakedEntitySettings();
    // Packet size limits and timeouts and Krypton switches
    public final NetworkSettings network = new NetworkSettings();
    // Thread priorities and the render loop yield switch
    public final ThreadSettings threads = new ThreadSettings();
    // Occlusion culling of entities and block entities
    public final OcclusionSettings occlusion = new OcclusionSettings();
    // HUD framebuffer cache settings
    public final HudSettings hud = new HudSettings();
    // Glyph batching switch
    public final TextSettings text = new TextSettings();
    // Client tick work skipped while its inputs stay unchanged
    public final ClientTickSettings clientTick = new ClientTickSettings();
    // Model box transform switch
    public final EntityModelSettings entityModels = new EntityModelSettings();
    // Fancy leaf face culling settings
    public final LeafSettings leaves = new LeafSettings();
    // World load and screenshot and texture atlas switches
    public final LoadingSettings loading = new LoadingSettings();
    // Hold to zoom settings
    public final ZoomSettings zoom = new ZoomSettings();

    // Forge configuration behind the settings or null for a detached config
    private Configuration config;

    // Reads the file into a new config and adds missing keys and falls back to unwritten defaults when the read fails
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

    // Writes every setting to the file and applies the live settings and does nothing for a detached config
    public void writeChanges() {
        Configuration file = config;
        if (file == null) {
            return;
        }
        ExtrasSchema.write(file, this);
        ExtrasRuntime.apply(this);
    }

    // Corner of the screen that anchors the FPS and coordinate overlay
    public enum OverlayCorner implements Localized {
        TOP_LEFT("impetus.options.extras.overlay_corner.top_left", false, false),
        TOP_RIGHT("impetus.options.extras.overlay_corner.top_right", false, true),
        BOTTOM_LEFT("impetus.options.extras.overlay_corner.bottom_left", true, false),
        BOTTOM_RIGHT("impetus.options.extras.overlay_corner.bottom_right", true, true);

        // Lang key of the cycler label
        private final String key;
        // Flag that marks a corner on the bottom edge
        private final boolean bottom;
        // Flag that marks a corner on the right edge
        private final boolean right;

        // Stores the lang key and the edges of the corner
        OverlayCorner(String key, boolean bottom, boolean right) {
            this.key = key;
            this.bottom = bottom;
            this.right = right;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }

        // Returns whether the corner sits on the bottom edge
        public boolean isBottom() {
            return this.bottom;
        }

        // Returns whether the corner sits on the right edge
        public boolean isRight() {
            return this.right;
        }
    }

    // Interior leaf faces the mesher drops in fancy mode
    public enum LeafCulling implements Localized {
        DEFAULT("impetus.options.extras.leaf_culling.default"),
        CHECK("impetus.options.extras.leaf_culling.check"),
        DEPTH("impetus.options.extras.leaf_culling.depth");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the mode
        LeafCulling(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Backdrop that keeps overlay text readable against the world
    public enum TextContrast implements Localized {
        NONE("impetus.options.extras.text_contrast.none"),
        BACKGROUND("impetus.options.extras.text_contrast.background"),
        SHADOW("impetus.options.extras.text_contrast.shadow");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the backdrop
        TextContrast(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Rule that decides when clouds turn translucent
    public enum CloudTranslucency implements Localized {
        DEFAULT("impetus.options.extras.cloud_translucency.default"),
        ALWAYS("impetus.options.extras.cloud_translucency.always"),
        NEVER("impetus.options.extras.cloud_translucency.never");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the rule
        CloudTranslucency(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Distance metric of terrain fog with VANILLA as spherical and RADIAL as horizontal only
    public enum FogShape implements Localized {
        VANILLA("impetus.options.extras.fog_shape.vanilla"),
        CYLINDRICAL("impetus.options.extras.fog_shape.cylindrical"),
        RADIAL("impetus.options.extras.fog_shape.radial"),
        PLANAR("impetus.options.extras.fog_shape.planar");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the shape
        FogShape(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }

        // Returns the u_FogShape uniform value of the shape as defined in the fog shader include
        public int shaderIndex() {
            return this.ordinal();
        }
    }

    // Client side time of day lock
    public enum TimeOverride implements Localized {
        DEFAULT("impetus.options.extras.time.default"),
        DAY("impetus.options.extras.time.day"),
        NIGHT("impetus.options.extras.time.night");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the lock
        TimeOverride(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Client side weather lock
    public enum WeatherOverride implements Localized {
        DEFAULT("impetus.options.extras.weather.default"),
        CLEAR("impetus.options.extras.weather.clear"),
        RAIN("impetus.options.extras.weather.rain"),
        THUNDER("impetus.options.extras.weather.thunder");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the lock
        WeatherOverride(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Render budget profile in rising order of aggressiveness with the frame time each one aims for
    public enum BudgetProfile implements Localized {
        QUALITY("impetus.options.extras.budget_profile.quality", 22.2),
        BALANCED("impetus.options.extras.budget_profile.balanced", 16.7),
        PERFORMANCE("impetus.options.extras.budget_profile.performance", 14.0);

        // Lang key of the cycler label
        private final String key;
        // Frame time in milliseconds that the frame time average is measured against
        public final double targetFrameMillis;

        // Stores the lang key and the target frame time of the profile
        BudgetProfile(String key, double targetFrameMillis) {
            this.key = key;
            this.targetFrameMillis = targetFrameMillis;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Vertical sync mode with adaptive sync beside the vanilla on and off pair
    public enum VerticalSync implements Localized {
        OFF("options.off"),
        ON("options.on"),
        ADAPTIVE("impetus.options.extras.vertical_sync.adaptive");

        // Lang key of the cycler label
        private final String key;

        // Stores the lang key of the mode
        VerticalSync(String key) {
            this.key = key;
        }

        // Returns the lang key of the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Texture animation switches where all gates every other switch
    public static final class AnimationSettings {
        // Master switch over every texture animation
        public boolean all = true;
        public boolean water = true;
        public boolean lava = true;
        public boolean fire = true;
        public boolean portal = true;
        // Switch of every block animation without a switch of its own
        public boolean blockAnimations = true;
        public boolean redstone = true;
        public boolean explosion = true;
        public boolean flame = true;
        public boolean smoke = true;
        // Switch of modded sculk sensor style animations
        public boolean sculkSensor = true;
    }

    // Particle switches and particle ticking options where all gates every particle
    public static final class ParticleSettings {
        // Master switch over every particle
        public boolean all = true;
        public boolean rainSplash = true;
        public boolean blockBreak = true;
        public boolean blockBreaking = true;
        public boolean voidParticles = true;
        public boolean waterParticles = true;
        public boolean portalParticles = true;
        public boolean potionParticles = true;
        public boolean drippingWaterLava = true;
        public boolean fireworkParticles = true;
        // Switch that ticks vanilla particles on the worker pool
        public boolean parallelTick = true;
        // Switch that also ticks modded particle classes on the worker pool
        public boolean parallelModded = false;
        // Switch that samples the light of each particle once per tick
        public boolean lightCache = true;
        // Switch that skips the quads of vanilla particles outside the view frustum
        public boolean cullOffscreen = true;
        // Switch that remembers block cells with nothing to collide with
        public boolean collisionCache = true;
    }

    // Celestial and environmental detail switches and the star count
    public static final class DetailSettings {
        // Fewest stars the star count allows
        public static final int STARS_MIN = 500;
        // Star count of a fresh config
        public static final int STARS_DEFAULT = 1500;
        // Most stars the star count allows
        public static final int STARS_MAX = 32000;

        public boolean sky = true;
        public boolean stars = true;
        // Number of stars generated for the sky
        public int totalStars = STARS_DEFAULT;
        public boolean sun = true;
        public boolean moon = true;
        public boolean rainSnow = true;
        // Switch of the biome tint of grass and foliage and water
        public boolean biomeColors = true;
        // Switch of the biome tint of the sky
        public boolean skyColors = true;
        // Switch of the darker swamp tint of grass and foliage
        public boolean swampColors = true;
        public boolean voidFog = true;
        public boolean showCapes = true;
        // Switch of the item name popup on a hotbar slot change
        public boolean heldItemTooltips = true;
    }

    // World render switches and fog and cloud tuning without cloud height or distance
    public static final class RenderSettings {
        // Smallest cloud scale in quarter steps
        public static final int CLOUD_SCALE_MIN = 1;
        // Cloud scale in quarter steps that matches the vanilla cloud size
        public static final int CLOUD_SCALE_VANILLA = 4;
        // Largest cloud scale in quarter steps
        public static final int CLOUD_SCALE_MAX = 16;

        public boolean fog = true;
        // Fog start as a percentage of the fog end
        public int fogStart = 100;
        // Fog distance in chunks where 0 follows the render distance
        public int fogDistance = 0;
        // Distance metric of terrain fog
        public FogShape fogShape = FogShape.VANILLA;
        // Cloud size in quarter steps of the vanilla size
        public int cloudScale = CLOUD_SCALE_VANILLA;
        // Rule that decides when clouds turn translucent
        public CloudTranslucency cloudTranslucency = CloudTranslucency.DEFAULT;
        // Switch of client side light updates
        public boolean lightUpdates = true;
        public boolean itemFrames = true;
        // Distance in blocks past which framed items lose their side faces where 0 turns it off
        public int itemFrameLodDistance = 0;
        public boolean itemFrameNameTag = true;
        public boolean armorStands = true;
        public boolean paintings = true;
        public boolean pistons = true;
        public boolean beacons = true;
        // Switch that stops beacon beams at the world ceiling
        public boolean limitBeaconBeamHeight = false;
        public boolean enchantingTableBooks = true;
        public boolean playerNameTag = true;
        // Switch that stacks up to five models per dropped item pile
        public boolean droppedItemsFancy = true;
        // Switch that blocks the vanilla post processing shaders
        public boolean preventShaders = false;
        // Switch that splits entity rendering by type in the F3 profiler
        public boolean profileEntityRendering = false;
    }

    // Overlay and toast and quality of life settings
    public static final class ExtraSettings {
        // Shortest F3 overlay rebuild interval in ticks
        public static final int STEADY_HUD_REFRESH_MIN = 1;
        // F3 overlay rebuild interval in ticks of a fresh config
        public static final int STEADY_HUD_REFRESH_DEFAULT = 1;
        // Longest F3 overlay rebuild interval in ticks
        public static final int STEADY_HUD_REFRESH_MAX = 20;
        // Autosave interval in ticks of the vanilla server
        public static final int AUTOSAVE_VANILLA_TICKS = 900;
        // Shortest autosave interval in ticks
        public static final int AUTOSAVE_MIN_TICKS = 0;
        // Longest autosave interval in ticks
        public static final int AUTOSAVE_MAX_TICKS = 36000;

        public boolean showFps = false;
        // Switch that adds the average and the low percentiles to the FPS overlay
        public boolean showFpsExtended = true;
        public boolean showCoords = false;
        // Switch that shows coordinates despite the reducedDebugInfo game rule
        public boolean ignoreReducedDebugInfo = false;
        // Corner of the screen that anchors the overlay
        public OverlayCorner overlayCorner = OverlayCorner.TOP_LEFT;
        // Backdrop of the overlay text
        public TextContrast textContrast = TextContrast.SHADOW;
        // Switch that throttles the F3 overlay text rebuild
        public boolean steadyDebugHud = true;
        // F3 overlay rebuild interval in ticks
        public int steadyDebugHudRefreshInterval = STEADY_HUD_REFRESH_DEFAULT;
        // Switch of adaptive vertical sync where the driver supports it
        public boolean useAdaptiveSync = false;
        // Master switch over every toast
        public boolean toasts = true;
        public boolean toastAdvancement = true;
        public boolean toastRecipe = true;
        public boolean toastTutorial = true;
        public boolean toastSystem = true;
        // Switch that appends the source mod name to item tooltips
        public boolean modNameTooltip = false;
        // Switch of the Panini projection post effect
        public boolean paniniProjection = false;
        // Panini projection strength as a percentage
        public int paniniProjectionStrength = 25;
        // Client side time of day lock
        public TimeOverride timeOverride = TimeOverride.DEFAULT;
        // Client side weather lock
        public WeatherOverride weatherOverride = WeatherOverride.DEFAULT;
        // Singleplayer autosave interval in ticks
        public int autosaveInterval = AUTOSAVE_VANILLA_TICKS;
    }

    // Adaptive render budget settings that trade distant mobs and cosmetic particles for frame time
    public static final class RenderBudgetSettings {
        // Smallest share of cosmetic particles as a percentage
        public static final int PARTICLE_BUDGET_MIN = 0;
        // Share of cosmetic particles as a percentage of a fresh config
        public static final int PARTICLE_BUDGET_DEFAULT = 65;
        // Largest share of cosmetic particles as a percentage
        public static final int PARTICLE_BUDGET_MAX = 100;
        // Shortest entity cull distance in blocks
        public static final int ENTITY_DISTANCE_MIN = 16;
        // Entity cull distance in blocks of a fresh config
        public static final int ENTITY_DISTANCE_DEFAULT = 96;
        // Longest entity cull distance in blocks
        public static final int ENTITY_DISTANCE_MAX = 256;
        // Shortest block entity skip distance in blocks
        public static final int BLOCK_ENTITY_DISTANCE_MIN = 32;
        // Block entity skip distance in blocks of a fresh config
        public static final int BLOCK_ENTITY_DISTANCE_DEFAULT = 48;
        // Longest block entity skip distance in blocks
        public static final int BLOCK_ENTITY_DISTANCE_MAX = 128;

        // Master switch of the render budget
        public boolean enabled = false;
        // Profile that sets the target frame time
        public BudgetProfile profile = BudgetProfile.BALANCED;
        // Switch that tightens the budget a further step under heavy frame pressure
        public boolean adaptive = true;
        // Share of cosmetic particles allowed to spawn as a percentage
        public int particleBudget = PARTICLE_BUDGET_DEFAULT;
        // Distance in blocks past which idle living entities may be skipped
        public int entityCullDistance = ENTITY_DISTANCE_DEFAULT;
        // Switch that skips distant living entities other than players under pressure
        public boolean smartEntityCulling = true;
        // Switch that skips distant decorative block entities under pressure
        public boolean blockEntities = true;
        // Distance in blocks past which decorative block entities and item frames may be skipped
        public int blockEntityDistance = BLOCK_ENTITY_DISTANCE_DEFAULT;
        // Switch that skips distant plain item frames under pressure
        public boolean itemFrames = true;
        // Switch of the HUD overlay of the budget state
        public boolean overlay = false;
    }

    // GPU booster switches where enabled gates every other switch
    public static final class GpuBoosterSettings {
        // Master switch over the GPU booster set
        public boolean enabled = false;
        // Switch of the entity and particle random generator
        public boolean fastRandom = true;
        // Switch of the MathHelper angle wrapping and log2 replacements
        public boolean fastMath = true;
        // Switch of streamed vertex buffer uploads for immediate mode geometry
        public boolean streamUploads = true;
        // Switch of the vertex array object cache per vertex format
        public boolean vertexFormatCache = true;
    }

    // Parallel server ticking settings where the master switch holds until the next launch
    public static final class AsyncSettings {
        // Most worker threads for parallel ticking
        public static final int THREADS_MAX = 64;
        // Entity ids that tick on the main thread in a fresh config
        public static final String[] DEFAULT_SYNCHRONIZED = {
                "minecraft:tnt", "minecraft:item", "minecraft:xp_orb", "minecraft:hopper_minecart"
        };

        // Master switch of parallel ticking that holds until the next launch
        public boolean enabled = true;
        // Worker thread count where 0 picks about three quarters of the cores
        public int threads = 0;
        // Switch that ticks vanilla entities in parallel
        public boolean entities = true;
        // Switch that also ticks modded entities in parallel
        public boolean moddedEntities = false;
        // Switch that runs block random ticks in parallel
        public boolean randomTicks = true;
        // Switch that evaluates natural spawn attempts in parallel
        public boolean spawning = true;
        // Entity ids or namespace wildcards that always tick on the main thread
        public String[] synchronizedEntities = DEFAULT_SYNCHRONIZED.clone();
    }

    // Switches of resting block entities drawn as terrain where enabled gates every other switch
    public static final class BakedEntitySettings {
        // Master switch over every baked block entity
        public boolean enabled = true;
        public boolean chests = true;
        public boolean enderChests = true;
        public boolean signs = true;
        public boolean beds = true;
        public boolean shulkerBoxes = true;
    }

    // Packet size limits and timeouts and Krypton switches
    public static final class NetworkSettings {
        // Shortest timeout in seconds
        public static final int TIMEOUT_MIN = 5;
        // Longest timeout in seconds
        public static final int TIMEOUT_MAX = 600;
        // Read timeout in seconds of a fresh config
        public static final int READ_TIMEOUT_DEFAULT = 120;
        // Login timeout in seconds of a fresh config
        public static final int LOGIN_TIMEOUT_DEFAULT = 120;
        // Keep alive timeout in seconds of a fresh config
        public static final int KEEP_ALIVE_TIMEOUT_DEFAULT = 120;

        // Switch that lifts the vanilla packet and string and NBT size caps
        public boolean largePackets = true;
        // Switch that flushes each connection once per server tick
        public boolean flushConsolidation = true;
        // Switch of table driven varint sizing and encoding
        public boolean fastVarInts = true;
        // Switch of packet compression without intermediate array copies
        public boolean pooledCompression = true;
        public int readTimeoutSeconds = READ_TIMEOUT_DEFAULT;
        public int loginTimeoutSeconds = LOGIN_TIMEOUT_DEFAULT;
        public int keepAliveTimeoutSeconds = KEEP_ALIVE_TIMEOUT_DEFAULT;
    }

    // Thread priorities and the render loop yield switch with game defaults in a fresh config
    public static final class ThreadSettings {
        // Lowest Java thread priority
        public static final int PRIORITY_MIN = Thread.MIN_PRIORITY;
        // Java thread priority the game threads start with
        public static final int PRIORITY_NORMAL = Thread.NORM_PRIORITY;
        // Highest Java thread priority
        public static final int PRIORITY_MAX = Thread.MAX_PRIORITY;
        // Priority Impetus gives its chunk builder threads
        public static final int CHUNK_BUILDER_DEFAULT = Thread.NORM_PRIORITY - 2;

        // Java priority of the client render thread
        public int renderThreadPriority = PRIORITY_NORMAL;
        // Java priority of the integrated server thread
        public int serverThreadPriority = PRIORITY_NORMAL;
        // Java priority of the chunk builder threads
        public int chunkBuilderPriority = CHUNK_BUILDER_DEFAULT;
        // Switch that skips the Thread.yield call of the render loop
        public boolean removeRenderYield = false;
    }

    // Occlusion culling of entities and block entities where enabled gates every other switch
    public static final class OcclusionSettings {
        // Largest culled entity size in blocks of a fresh config
        public static final int MAX_ENTITY_SIZE_DEFAULT = 4;
        // Largest culled block entity size in blocks of a fresh config
        public static final int MAX_BLOCK_ENTITY_SIZE_DEFAULT = 4;
        // Ray end slack in hundredths of a block of a fresh config
        public static final int SLACK_DEFAULT = 100;

        // Master switch of occlusion culling
        public boolean enabled = true;
        // Switch that culls hidden entities
        public boolean entities = true;
        // Switch that culls hidden block entities
        public boolean blockEntities = true;
        // Entity width or height in blocks above which an entity is never culled
        public int maxEntitySize = MAX_ENTITY_SIZE_DEFAULT;
        // Render box size in blocks above which a block entity is never culled
        public int maxBlockEntitySize = MAX_BLOCK_ENTITY_SIZE_DEFAULT;
        // Hundredths of a block at the end of each ray that never occlude
        public int raycastSlack = SLACK_DEFAULT;
    }

    // HUD framebuffer cache settings
    public static final class HudSettings {
        // Lowest HUD cache redraw rate per second
        public static final int CACHE_FPS_MIN = 10;
        // HUD cache redraw rate per second of a fresh config
        public static final int CACHE_FPS_DEFAULT = 30;
        // Highest HUD cache redraw rate per second
        public static final int CACHE_FPS_MAX = 120;

        // Switch that draws the HUD into a framebuffer at a capped rate
        public boolean cacheEnabled = false;
        // HUD cache redraws per second
        public int cacheFps = CACHE_FPS_DEFAULT;
    }

    // Glyph batching switch
    public static final class TextSettings {
        // Switch that draws the glyphs of each string in one batch
        public boolean batchGlyphs = true;
    }

    // Client tick work skipped while its inputs stay unchanged
    public static final class ClientTickSettings {
        // Switch that skips lightmap rebuilds while only the torch flicker changes
        public boolean lightmapCaching = true;
    }

    // Model box transform switch
    public static final class EntityModelSettings {
        // Switch that positions each model box with one matrix multiply
        public boolean matrixTransforms = true;
    }

    // Fancy leaf interior face culling settings
    public static final class LeafSettings {
        // Fewest solid blocks behind a leaf face in depth mode
        public static final int DEPTH_MIN = 1;
        // Solid blocks behind a leaf face in depth mode of a fresh config
        public static final int DEPTH_DEFAULT = 2;
        // Most solid blocks behind a leaf face in depth mode
        public static final int DEPTH_MAX = 4;

        // Interior leaf faces the mesher drops
        public LeafCulling cullingMode = LeafCulling.DEFAULT;
        // Solid blocks that must lie behind a leaf face before depth mode drops it
        public int cullingDepth = DEPTH_DEFAULT;
    }

    // World load and screenshot and texture atlas switches
    public static final class LoadingSettings {
        // Switch that skips the forced garbage collection on world load and unload
        public boolean skipWorldLoadGc = true;
        // Switch that skips the terrain loading screen on dimension change and respawn
        public boolean smoothDimensionChange = true;
        // Switch that frees the screenshot readback buffers after each screenshot
        public boolean releaseScreenshotBuffers = true;
        // Switch that encodes and writes screenshots on a background thread
        public boolean asyncScreenshots = true;
        // Switch that asks the driver for its largest texture size
        public boolean driverAtlasLimit = true;
    }

    // Hold to zoom settings
    public static final class ZoomSettings {
        // Divisor that turns the zoom level into a fraction of the view width
        public static final int LEVEL_SCALE = 100;
        // Smallest zoom level in hundredths of the view width
        public static final int LEVEL_MIN = 5;
        // Zoom level in hundredths of the view width that matches Logical Zoom
        public static final int LEVEL_DEFAULT = 23;
        // Largest zoom level in hundredths of the view width
        public static final int LEVEL_MAX = 90;

        // Switch of the zoom key
        public boolean enabled = true;
        // Share of the normal view width kept while zoomed in hundredths
        public int level = LEVEL_DEFAULT;
        // Switch that turns the cinematic camera on while zoomed
        public boolean smoothCamera = true;
        // Switch that hides the hand and held items while zoomed
        public boolean hideHand = true;
    }
}
