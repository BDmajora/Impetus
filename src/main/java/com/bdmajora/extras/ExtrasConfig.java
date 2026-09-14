package com.bdmajora.extras;

import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.GpuBooster;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.extras.client.particle.ParticleTicker;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Every Extras setting, persisted to config/impetus-extras.cfg as a normal Forge Configuration since nothing is read during coremod setup; enum constant order is on-disk format, append only
public final class ExtrasConfig {
    private static final String CAT_ANIMATION = "animation";
    private static final String CAT_PARTICLE = "particle";
    private static final String CAT_PARTICLE_CLASSES = "particle_classes";
    private static final String CAT_DETAIL = "detail";
    private static final String CAT_RENDER = "render";
    private static final String CAT_EXTRA = "extra";
    private static final String CAT_RENDER_BUDGET = "render_budget";
    private static final String CAT_GPU_BOOSTER = "gpu_booster";
    private static final String CAT_ASYNC = "parallel_ticking";
    private static final String CAT_BAKED_ENTITIES = "baked_block_entities";
    private static final String CAT_NETWORK = "network";
    private static final String CAT_THREADS = "thread_scheduling";

    public final AnimationSettings animation = new AnimationSettings();
    public final ParticleSettings particle = new ParticleSettings();
    public final DetailSettings detail = new DetailSettings();
    public final RenderSettings render = new RenderSettings();
    public final ExtraSettings extra = new ExtraSettings();
    public final RenderBudgetSettings renderBudget = new RenderBudgetSettings();
    public final GpuBoosterSettings gpuBooster = new GpuBoosterSettings();
    public final AsyncSettings async = new AsyncSettings();
    public final BakedEntitySettings bakedEntities = new BakedEntitySettings();
    public final NetworkSettings network = new NetworkSettings();
    public final ThreadSettings threads = new ThreadSettings();

    private final List<BooleanProperty> booleans = Arrays.asList(
            // --- Animations -------------------------------------------------------------------
            bool(CAT_ANIMATION, "animation", true, "Master switch for all texture animations",
                    v -> animation.all = v, () -> animation.all),
            bool(CAT_ANIMATION, "water", true, "Animate water textures",
                    v -> animation.water = v, () -> animation.water),
            bool(CAT_ANIMATION, "lava", true, "Animate lava textures",
                    v -> animation.lava = v, () -> animation.lava),
            bool(CAT_ANIMATION, "fire", true, "Animate fire textures",
                    v -> animation.fire = v, () -> animation.fire),
            bool(CAT_ANIMATION, "portal", true, "Animate nether portal textures",
                    v -> animation.portal = v, () -> animation.portal),
            bool(CAT_ANIMATION, "blockAnimations", true, "Animate other block textures",
                    v -> animation.blockAnimations = v, () -> animation.blockAnimations),
            // OptiFine splits these out of the general block-animation switch.
            bool(CAT_ANIMATION, "redstone", true, "Animate redstone textures",
                    v -> animation.redstone = v, () -> animation.redstone),
            bool(CAT_ANIMATION, "explosion", true, "Animate explosion textures",
                    v -> animation.explosion = v, () -> animation.explosion),
            bool(CAT_ANIMATION, "flame", true, "Animate flame textures",
                    v -> animation.flame = v, () -> animation.flame),
            bool(CAT_ANIMATION, "smoke", true, "Animate smoke textures",
                    v -> animation.smoke = v, () -> animation.smoke),
            bool(CAT_ANIMATION, "sculkSensor", true, "Animate sculk-sensor-style textures (modded only on 1.12.2)",
                    v -> animation.sculkSensor = v, () -> animation.sculkSensor),

            // --- Particles --------------------------------------------------------------------
            bool(CAT_PARTICLE, "particles", true, "Master switch for all particles",
                    v -> particle.all = v, () -> particle.all),
            bool(CAT_PARTICLE, "rainSplash", true, "Rain splash particles",
                    v -> particle.rainSplash = v, () -> particle.rainSplash),
            bool(CAT_PARTICLE, "blockBreak", true, "Block break particles",
                    v -> particle.blockBreak = v, () -> particle.blockBreak),
            bool(CAT_PARTICLE, "blockBreaking", true, "Block breaking (mining) particles",
                    v -> particle.blockBreaking = v, () -> particle.blockBreaking),
            bool(CAT_PARTICLE, "voidParticles", true, "Void particles near the world bottom",
                    v -> particle.voidParticles = v, () -> particle.voidParticles),
            bool(CAT_PARTICLE, "waterParticles", true, "Water drip, splash and suspended particles",
                    v -> particle.waterParticles = v, () -> particle.waterParticles),
            bool(CAT_PARTICLE, "portalParticles", true, "Nether portal particles",
                    v -> particle.portalParticles = v, () -> particle.portalParticles),
            bool(CAT_PARTICLE, "potionParticles", true, "Potion effect particles",
                    v -> particle.potionParticles = v, () -> particle.potionParticles),
            bool(CAT_PARTICLE, "drippingWaterLava", true, "Dripping water and lava particles",
                    v -> particle.drippingWaterLava = v, () -> particle.drippingWaterLava),
            bool(CAT_PARTICLE, "fireworkParticles", true, "Firework spark particles",
                    v -> particle.fireworkParticles = v, () -> particle.fireworkParticles),
            bool(CAT_PARTICLE, "parallelTick", true, "Tick particles across a worker pool instead of the client thread",
                    v -> particle.parallelTick = v, () -> particle.parallelTick),
            bool(CAT_PARTICLE, "parallelModded", false, "Also tick modded particle classes on the pool; off keeps them on the client thread",
                    v -> particle.parallelModded = v, () -> particle.parallelModded),
            bool(CAT_PARTICLE, "lightCache", true, "Sample each particle's light once per tick instead of once per frame",
                    v -> particle.lightCache = v, () -> particle.lightCache),
            bool(CAT_PARTICLE, "cullOffscreen", true, "Skip building quads for vanilla particles outside the view frustum",
                    v -> particle.cullOffscreen = v, () -> particle.cullOffscreen),
            bool(CAT_PARTICLE, "collisionCache", true, "Remember that a particle's block cell has nothing to collide with instead of sweeping it every tick",
                    v -> particle.collisionCache = v, () -> particle.collisionCache),

            // --- Details ----------------------------------------------------------------------
            bool(CAT_DETAIL, "sky", true, "Render the sky box",
                    v -> detail.sky = v, () -> detail.sky),
            bool(CAT_DETAIL, "stars", true, "Render stars",
                    v -> detail.stars = v, () -> detail.stars),
            bool(CAT_DETAIL, "sun", true, "Render the sun",
                    v -> detail.sun = v, () -> detail.sun),
            bool(CAT_DETAIL, "moon", true, "Render the moon",
                    v -> detail.moon = v, () -> detail.moon),
            bool(CAT_DETAIL, "rainSnow", true, "Render falling rain and snow",
                    v -> detail.rainSnow = v, () -> detail.rainSnow),
            bool(CAT_DETAIL, "biomeColors", true, "Biome-specific grass, foliage and water tint",
                    v -> detail.biomeColors = v, () -> detail.biomeColors),
            bool(CAT_DETAIL, "skyColors", true, "Biome-specific sky colour",
                    v -> detail.skyColors = v, () -> detail.skyColors),
            bool(CAT_DETAIL, "swampColors", true, "Swamp's darkened grass and foliage tint",
                    v -> detail.swampColors = v, () -> detail.swampColors),
            bool(CAT_DETAIL, "voidFog", true, "Void fog near the world bottom",
                    v -> detail.voidFog = v, () -> detail.voidFog),
            bool(CAT_DETAIL, "showCapes", true, "Render player capes",
                    v -> detail.showCapes = v, () -> detail.showCapes),
            bool(CAT_DETAIL, "heldItemTooltips", true, "Show the item name popup when switching hotbar slots",
                    v -> detail.heldItemTooltips = v, () -> detail.heldItemTooltips),

            // --- Render -----------------------------------------------------------------------
            bool(CAT_RENDER, "fog", true, "Render atmospheric fog",
                    v -> render.fog = v, () -> render.fog),
            bool(CAT_RENDER, "lightUpdates", true, "Process client-side light updates",
                    v -> render.lightUpdates = v, () -> render.lightUpdates),
            bool(CAT_RENDER, "itemFrames", true, "Render item frames",
                    v -> render.itemFrames = v, () -> render.itemFrames),
            bool(CAT_RENDER, "armorStands", true, "Render armor stands",
                    v -> render.armorStands = v, () -> render.armorStands),
            bool(CAT_RENDER, "paintings", true, "Render paintings",
                    v -> render.paintings = v, () -> render.paintings),
            bool(CAT_RENDER, "pistons", true, "Render moving pistons",
                    v -> render.pistons = v, () -> render.pistons),
            bool(CAT_RENDER, "beacons", true, "Render beacon beams",
                    v -> render.beacons = v, () -> render.beacons),
            bool(CAT_RENDER, "limitBeaconBeamHeight", false, "Stop beacon beams at the world ceiling",
                    v -> render.limitBeaconBeamHeight = v, () -> render.limitBeaconBeamHeight),
            bool(CAT_RENDER, "enchantingTableBooks", true, "Render the enchanting table's floating book",
                    v -> render.enchantingTableBooks = v, () -> render.enchantingTableBooks),
            bool(CAT_RENDER, "playerNameTag", true, "Render player name tags",
                    v -> render.playerNameTag = v, () -> render.playerNameTag),
            bool(CAT_RENDER, "itemFrameNameTag", true, "Render item frame name tags",
                    v -> render.itemFrameNameTag = v, () -> render.itemFrameNameTag),
            bool(CAT_RENDER, "droppedItemsFancy", true,
                    "Stack up to five models per dropped item pile instead of always drawing one",
                    v -> render.droppedItemsFancy = v, () -> render.droppedItemsFancy),
            bool(CAT_RENDER, "preventShaders", false, "Block the vanilla post-processing shader pipeline",
                    v -> render.preventShaders = v, () -> render.preventShaders),
            bool(CAT_RENDER, "profileEntityRendering", false,
                    "Break entity and block-entity rendering out by type in the F3 profiler graph",
                    v -> render.profileEntityRendering = v, () -> render.profileEntityRendering),

            // --- Extra ------------------------------------------------------------------------
            bool(CAT_EXTRA, "showFps", false, "Show the FPS overlay",
                    v -> extra.showFps = v, () -> extra.showFps),
            bool(CAT_EXTRA, "showFPSExtended", true, "Include average, 1% low and 0.1% low in the FPS overlay",
                    v -> extra.showFpsExtended = v, () -> extra.showFpsExtended),
            bool(CAT_EXTRA, "showCoords", false, "Show the coordinates overlay",
                    v -> extra.showCoords = v, () -> extra.showCoords),
            bool(CAT_EXTRA, "ignoreReducedDebugInfo", false,
                    "Show coordinates even when the server sets the reducedDebugInfo game rule",
                    v -> extra.ignoreReducedDebugInfo = v, () -> extra.ignoreReducedDebugInfo),
            bool(CAT_EXTRA, "steadyDebugHud", true, "Throttle how often the F3 overlay text is rebuilt",
                    v -> extra.steadyDebugHud = v, () -> extra.steadyDebugHud),
            bool(CAT_EXTRA, "useAdaptiveSync", false, "Use adaptive VSync (swap interval -1) when supported",
                    v -> extra.useAdaptiveSync = v, () -> extra.useAdaptiveSync),
            bool(CAT_EXTRA, "toasts", true, "Master switch for toast pop-ups",
                    v -> extra.toasts = v, () -> extra.toasts),
            bool(CAT_EXTRA, "toastAdvancement", true, "Advancement toasts",
                    v -> extra.toastAdvancement = v, () -> extra.toastAdvancement),
            bool(CAT_EXTRA, "toastRecipe", true, "Recipe unlock toasts",
                    v -> extra.toastRecipe = v, () -> extra.toastRecipe),
            bool(CAT_EXTRA, "toastTutorial", true, "Tutorial toasts",
                    v -> extra.toastTutorial = v, () -> extra.toastTutorial),
            bool(CAT_EXTRA, "toastSystem", true, "System toasts",
                    v -> extra.toastSystem = v, () -> extra.toastSystem),
            bool(CAT_EXTRA, "modNameTooltip", false, "Append the source mod's name to item tooltips",
                    v -> extra.modNameTooltip = v, () -> extra.modNameTooltip),
            bool(CAT_EXTRA, "paniniProjection", false, "Apply a Panini projection post-effect to widen the view",
                    v -> extra.paniniProjection = v, () -> extra.paniniProjection),
            // --- Render budget ----------------------------------------------------------------
            bool(CAT_RENDER_BUDGET, "enabled", false, "Adaptive render budgeting: skip distant idle mobs and thin cosmetic particles under frame pressure",
                    v -> renderBudget.enabled = v, () -> renderBudget.enabled),
            bool(CAT_RENDER_BUDGET, "adaptive", true, "Tighten the budget a further step when frames run 20% or more over the profile target",
                    v -> renderBudget.adaptive = v, () -> renderBudget.adaptive),
            bool(CAT_RENDER_BUDGET, "smartEntityCulling", true, "Skip rendering distant non-player living entities under pressure",
                    v -> renderBudget.smartEntityCulling = v, () -> renderBudget.smartEntityCulling),
            bool(CAT_RENDER_BUDGET, "blockEntities", true, "Skip vanilla decorative block entity renderers (chests, signs, heads, banners, beds, shulker boxes) past the block entity distance under pressure",
                    v -> renderBudget.blockEntities = v, () -> renderBudget.blockEntities),
            bool(CAT_RENDER_BUDGET, "itemFrames", true, "Skip plain item frames past the block entity distance under pressure; glowing, named and map frames are kept",
                    v -> renderBudget.itemFrames = v, () -> renderBudget.itemFrames),
            bool(CAT_RENDER_BUDGET, "overlay", false, "Show the budget, frame pressure and skipped counts on the HUD",
                    v -> renderBudget.overlay = v, () -> renderBudget.overlay),
            bool(CAT_RENDER_BUDGET, "quickSetupShown", false, "Whether the one-time quick setup screen has been shown on a world join",
                    v -> renderBudget.quickSetupShown = v, () -> renderBudget.quickSetupShown),
            // --- GPU booster ------------------------------------------------------------------
            bool(CAT_GPU_BOOSTER, "enabled", false, "Master switch for the GPU Booster set: fast random, fast math and streamed vertex uploads",
                    v -> gpuBooster.enabled = v, () -> gpuBooster.enabled),
            bool(CAT_GPU_BOOSTER, "fastRandom", true, "Give entities and particles a cheaper random generator than java.util.Random",
                    v -> gpuBooster.fastRandom = v, () -> gpuBooster.fastRandom),
            bool(CAT_GPU_BOOSTER, "fastMath", true, "Cheaper angle wrapping and log2 in MathHelper",
                    v -> gpuBooster.fastMath = v, () -> gpuBooster.fastMath),
            bool(CAT_GPU_BOOSTER, "streamUploads", true, "Draw immediate-mode geometry through a streamed vertex buffer instead of client-side arrays",
                    v -> gpuBooster.streamUploads = v, () -> gpuBooster.streamUploads),
            // --- Parallel ticking -------------------------------------------------------------
            bool(CAT_ASYNC, "enabled", true, "Master switch for parallel server ticking (entities, random ticks, mob spawning across the worker pool); takes effect on the next launch",
                    v -> async.enabled = v, () -> async.enabled),
            bool(CAT_ASYNC, "entities", true, "Tick vanilla entities in parallel across the worker pool",
                    v -> async.entities = v, () -> async.entities),
            bool(CAT_ASYNC, "moddedEntities", false, "Also tick modded entities in parallel; off keeps them on the main thread",
                    v -> async.moddedEntities = v, () -> async.moddedEntities),
            bool(CAT_ASYNC, "randomTicks", true, "Run block random ticks (crop growth, fire spread, ...) in parallel",
                    v -> async.randomTicks = v, () -> async.randomTicks),
            bool(CAT_ASYNC, "spawning", true, "Evaluate natural mob spawn attempts in parallel per chunk",
                    v -> async.spawning = v, () -> async.spawning),
            // --- Baked block entities ---------------------------------------------------------
            bool(CAT_BAKED_ENTITIES, "enabled", true, "Master switch: draw resting chests, ender chests, signs, beds and shulker boxes as terrain instead of through their block entity renderers",
                    v -> bakedEntities.enabled = v, () -> bakedEntities.enabled),
            bool(CAT_BAKED_ENTITIES, "chests", true, "Bake chests and trapped chests, single and double",
                    v -> bakedEntities.chests = v, () -> bakedEntities.chests),
            bool(CAT_BAKED_ENTITIES, "enderChests", true, "Bake ender chests",
                    v -> bakedEntities.enderChests = v, () -> bakedEntities.enderChests),
            bool(CAT_BAKED_ENTITIES, "signs", true, "Bake sign boards and posts (text still comes from the renderer)",
                    v -> bakedEntities.signs = v, () -> bakedEntities.signs),
            bool(CAT_BAKED_ENTITIES, "beds", true, "Bake beds",
                    v -> bakedEntities.beds = v, () -> bakedEntities.beds),
            bool(CAT_BAKED_ENTITIES, "shulkerBoxes", true, "Bake closed shulker boxes",
                    v -> bakedEntities.shulkerBoxes = v, () -> bakedEntities.shulkerBoxes),
            // --- Network ----------------------------------------------------------------------
            bool(CAT_NETWORK, "largePackets", true, "Lift vanilla's packet, payload, NBT, string and chunk data size caps (2 MB frames, 32 KB strings, 1 MB payloads)",
                    v -> network.largePackets = v, () -> network.largePackets),
            // --- Thread scheduling ------------------------------------------------------------
            bool(CAT_THREADS, "removeRenderYield", false, "Skip the Thread.yield() the render loop makes once per frame",
                    v -> threads.removeRenderYield = v, () -> threads.removeRenderYield)
    );

    private final List<IntProperty> integers = Arrays.asList(
            new IntProperty(CAT_DETAIL, "totalStars", DetailSettings.STARS_DEFAULT,
                    DetailSettings.STARS_MIN, DetailSettings.STARS_MAX, "Number of stars to generate",
                    v -> detail.totalStars = v, () -> detail.totalStars),
            new IntProperty(CAT_RENDER, "fogStart", 100, 0, 200,
                    "Fog start distance as a percentage of the fog end (100 = vanilla)",
                    v -> render.fogStart = v, () -> render.fogStart),
            new IntProperty(CAT_RENDER, "fogDistance", 0, 0, 32,
                    "Fog distance in chunks (0 = follow render distance)",
                    v -> render.fogDistance = v, () -> render.fogDistance),
            new IntProperty(CAT_RENDER, "cloudScale", RenderSettings.CLOUD_SCALE_VANILLA,
                    RenderSettings.CLOUD_SCALE_MIN, RenderSettings.CLOUD_SCALE_MAX,
                    "Cloud scale in quarter steps (1 = 0.25x, 4 = vanilla, 16 = 4.00x)",
                    v -> render.cloudScale = v, () -> render.cloudScale),
            new IntProperty(CAT_RENDER, "itemFrameLodDistance", 0, 0, 256,
                    "Distance in blocks past which framed items lose their side faces (0 = off)",
                    v -> render.itemFrameLodDistance = v, () -> render.itemFrameLodDistance),
            new IntProperty(CAT_EXTRA, "steadyDebugHudRefreshInterval",
                    ExtraSettings.STEADY_HUD_REFRESH_DEFAULT, ExtraSettings.STEADY_HUD_REFRESH_MIN,
                    ExtraSettings.STEADY_HUD_REFRESH_MAX, "F3 overlay rebuild interval in ticks",
                    v -> extra.steadyDebugHudRefreshInterval = v, () -> extra.steadyDebugHudRefreshInterval),
            new IntProperty(CAT_EXTRA, "paniniProjectionStrength", 25, 0, 100,
                    "Panini projection strength as a percentage",
                    v -> extra.paniniProjectionStrength = v, () -> extra.paniniProjectionStrength),
            new IntProperty(CAT_EXTRA, "autosaveInterval", ExtraSettings.AUTOSAVE_VANILLA_TICKS,
                    ExtraSettings.AUTOSAVE_MIN_TICKS, ExtraSettings.AUTOSAVE_MAX_TICKS,
                    "Singleplayer autosave interval in ticks (900 = vanilla)",
                    v -> extra.autosaveInterval = v, () -> extra.autosaveInterval),
            new IntProperty(CAT_RENDER_BUDGET, "particleBudget", RenderBudgetSettings.PARTICLE_BUDGET_DEFAULT,
                    RenderBudgetSettings.PARTICLE_BUDGET_MIN, RenderBudgetSettings.PARTICLE_BUDGET_MAX,
                    "Percentage of cosmetic particles allowed to spawn (100 = no particle budgeting)",
                    v -> renderBudget.particleBudget = v, () -> renderBudget.particleBudget),
            new IntProperty(CAT_RENDER_BUDGET, "entityCullDistance", RenderBudgetSettings.ENTITY_DISTANCE_DEFAULT,
                    RenderBudgetSettings.ENTITY_DISTANCE_MIN, RenderBudgetSettings.ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which idle living entities may be skipped under pressure",
                    v -> renderBudget.entityCullDistance = v, () -> renderBudget.entityCullDistance),
            new IntProperty(CAT_RENDER_BUDGET, "blockEntityDistance", RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_DEFAULT,
                    RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN, RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which decorative block entities and item frames may be skipped under pressure (vanilla stops most at 64 anyway)",
                    v -> renderBudget.blockEntityDistance = v, () -> renderBudget.blockEntityDistance),
            new IntProperty(CAT_ASYNC, "threads", 0, 0, AsyncSettings.THREADS_MAX,
                    "Worker threads for parallel ticking (0 = automatic, about three quarters of the cores)",
                    v -> async.threads = v, () -> async.threads),
            new IntProperty(CAT_NETWORK, "readTimeoutSeconds", NetworkSettings.READ_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a connection may go silent before it is dropped (vanilla 30)",
                    v -> network.readTimeoutSeconds = v, () -> network.readTimeoutSeconds),
            new IntProperty(CAT_NETWORK, "loginTimeoutSeconds", NetworkSettings.LOGIN_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to finish logging in (vanilla 30)",
                    v -> network.loginTimeoutSeconds = v, () -> network.loginTimeoutSeconds),
            new IntProperty(CAT_NETWORK, "keepAliveTimeoutSeconds", NetworkSettings.KEEP_ALIVE_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to answer a keep-alive (vanilla 15)",
                    v -> network.keepAliveTimeoutSeconds = v, () -> network.keepAliveTimeoutSeconds),
            new IntProperty(CAT_THREADS, "renderThreadPriority", ThreadSettings.PRIORITY_NORMAL, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the client (render) thread, 1-10 (5 = unchanged)",
                    v -> threads.renderThreadPriority = v, () -> threads.renderThreadPriority),
            new IntProperty(CAT_THREADS, "serverThreadPriority", ThreadSettings.PRIORITY_NORMAL, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the integrated server thread, 1-10 (5 = unchanged)",
                    v -> threads.serverThreadPriority = v, () -> threads.serverThreadPriority),
            new IntProperty(CAT_THREADS, "chunkBuilderPriority", ThreadSettings.CHUNK_BUILDER_DEFAULT, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the chunk builder threads, 1-10 (3 = unchanged)",
                    v -> threads.chunkBuilderPriority = v, () -> threads.chunkBuilderPriority)
    );

    private Configuration config;

    // Reads file, adding missing keys; a read failure yields defaults that are NOT written back, since overwriting an unparseable config would destroy the user's settings
    public static ExtrasConfig load(File file) {
        ExtrasConfig options = new ExtrasConfig();
        Configuration config = new Configuration(file);

        try {
            config.load();
            options.config = config;
            options.loadFrom(config);
            if (config.hasChanged()) {
                config.save();
            }
            GpuBooster.apply(options.gpuBooster);
            ParallelProcessor.install(options.async);
            ParallelProcessor.apply(options.async);
            ParticleTicker.apply(options.particle);
            BakedEntities.apply(options.bakedEntities);
            ThreadTuning.apply(options.threads);
            return options;
        } catch (Exception e) {
            Extras.LOGGER.error("Could not read {}, falling back to defaults", file, e);
            ExtrasConfig defaults = new ExtrasConfig();
            defaults.config = config;
            return defaults;
        }
    }

    // Reads every property, falling back to defaults for anything missing or out of range
    private void loadFrom(Configuration config) {
        booleans.forEach(property -> property.load(config));
        integers.forEach(property -> property.load(config));

        render.cloudTranslucency = readEnum(config, CAT_RENDER, "cloudTranslucency",
                CloudTranslucency.values(), CloudTranslucency.DEFAULT,
                "Cloud translucency mode (0 = Default, 1 = Always, 2 = Never)");
        render.fogShape = readEnum(config, CAT_RENDER, "fogShape",
                FogShape.values(), FogShape.VANILLA,
                "Terrain fog shape (0 = Vanilla, 1 = Cylindrical, 2 = Radial, 3 = Planar)");
        extra.overlayCorner = readEnum(config, CAT_EXTRA, "overlayCorner",
                OverlayCorner.values(), OverlayCorner.TOP_LEFT,
                "Overlay corner (0 = Top Left, 1 = Top Right, 2 = Bottom Left, 3 = Bottom Right)");
        extra.textContrast = readEnum(config, CAT_EXTRA, "textContrast",
                TextContrast.values(), TextContrast.SHADOW,
                "Overlay text contrast (0 = None, 1 = Background, 2 = Shadow)");
        extra.timeOverride = readEnum(config, CAT_EXTRA, "timeOverride",
                TimeOverride.values(), TimeOverride.DEFAULT,
                "Client-side time of day (0 = Default, 1 = Day only, 2 = Night only). Creative/cheats only.");
        extra.weatherOverride = readEnum(config, CAT_EXTRA, "weatherOverride",
                WeatherOverride.values(), WeatherOverride.DEFAULT,
                "Client-side weather (0 = Default, 1 = Clear, 2 = Rain, 3 = Thunder). Creative/cheats only.");
        renderBudget.profile = readEnum(config, CAT_RENDER_BUDGET, "profile",
                BudgetProfile.values(), BudgetProfile.BALANCED,
                "Render budget profile (0 = Quality, 1 = Balanced, 2 = Performance)");

        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.loadDisabledClasses(config.getStringList("disabledClasses", CAT_PARTICLE_CLASSES,
                new String[0], "Particle classes the user has switched off"));
        registry.loadDiscoveredClasses(config.getStringList("discoveredClasses", CAT_PARTICLE_CLASSES,
                new String[0], "Cache of discovered particle classes; rebuilt automatically"));
        async.synchronizedEntities = config.getStringList("synchronizedEntities", CAT_ASYNC,
                AsyncSettings.DEFAULT_SYNCHRONIZED, "Entity ids (or namespace:*) that always tick on the main thread");
    }

    // Flushes every setting back to disk
    public void writeChanges() {
        if (config == null) {
            return;
        }

        booleans.forEach(property -> property.save(config));
        integers.forEach(property -> property.save(config));

        config.get(CAT_RENDER, "cloudTranslucency", 0).set(render.cloudTranslucency.ordinal());
        config.get(CAT_RENDER, "fogShape", 0).set(render.fogShape.ordinal());
        config.get(CAT_EXTRA, "overlayCorner", 0).set(extra.overlayCorner.ordinal());
        config.get(CAT_EXTRA, "textContrast", TextContrast.SHADOW.ordinal()).set(extra.textContrast.ordinal());
        config.get(CAT_EXTRA, "timeOverride", 0).set(extra.timeOverride.ordinal());
        config.get(CAT_EXTRA, "weatherOverride", 0).set(extra.weatherOverride.ordinal());
        config.get(CAT_RENDER_BUDGET, "profile", BudgetProfile.BALANCED.ordinal()).set(renderBudget.profile.ordinal());

        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        config.get(CAT_PARTICLE_CLASSES, "disabledClasses", new String[0])
                .set(registry.getDisabledClassesArray());
        config.get(CAT_PARTICLE_CLASSES, "discoveredClasses", new String[0])
                .set(registry.getDiscoveredClassesArray());
        config.get(CAT_ASYNC, "synchronizedEntities", AsyncSettings.DEFAULT_SYNCHRONIZED).set(async.synchronizedEntities);

        config.save();
        registry.markClean();
        GpuBooster.apply(gpuBooster);
        ParallelProcessor.apply(async);
        ParticleTicker.apply(particle);
        BakedEntities.apply(bakedEntities);
        ThreadTuning.apply(threads);
    }

    private static <T extends Enum<T>> T readEnum(Configuration config, String category, String key,
                                                  T[] values, T fallback, String comment) {
        int ordinal = config.getInt(key, category, fallback.ordinal(), 0, values.length - 1, comment);
        return values[ordinal];
    }

    private BooleanProperty bool(String category, String key, boolean defaultValue, String comment,
                                 Consumer<Boolean> setter, Supplier<Boolean> getter) {
        return new BooleanProperty(category, key, defaultValue, comment, setter, getter);
    }

    // Enums, persisted by ordinal so append only

    // Where the FPS/coordinate overlay is anchored
    public enum OverlayCorner implements Localized {
        TOP_LEFT("impetus.options.extras.overlay_corner.top_left"),
        TOP_RIGHT("impetus.options.extras.overlay_corner.top_right"),
        BOTTOM_LEFT("impetus.options.extras.overlay_corner.bottom_left"),
        BOTTOM_RIGHT("impetus.options.extras.overlay_corner.bottom_right");

        private final String key;

        OverlayCorner(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }

        // Whether this corner sits on the bottom edge
        public boolean isBottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }

        // Whether this corner sits on the right edge
        public boolean isRight() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }
    }

    // How overlay text is made readable against the world behind it
    public enum TextContrast implements Localized {
        NONE("impetus.options.extras.text_contrast.none"),
        BACKGROUND("impetus.options.extras.text_contrast.background"),
        SHADOW("impetus.options.extras.text_contrast.shadow");

        private final String key;

        TextContrast(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // When clouds fade to translucent
    public enum CloudTranslucency implements Localized {
        DEFAULT("impetus.options.extras.cloud_translucency.default"),
        ALWAYS("impetus.options.extras.cloud_translucency.always"),
        NEVER("impetus.options.extras.cloud_translucency.never");

        private final String key;

        CloudTranslucency(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // The distance metric the terrain shader fogs by; Impetus already fogs spherically like entities, so VANILLA is spherical and RADIAL is the horizontal-only one (non-VANILLA means terrain and entities disagree); ordinals are fog.glsl's u_FogShape values
    public enum FogShape implements Localized {
        VANILLA("impetus.options.extras.fog_shape.vanilla"),
        CYLINDRICAL("impetus.options.extras.fog_shape.cylindrical"),
        RADIAL("impetus.options.extras.fog_shape.radial"),
        PLANAR("impetus.options.extras.fog_shape.planar");

        private final String key;

        FogShape(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }

        // The u_FogShape uniform value; see assets/impetus/shaders/include/fog.glsl
        public int shaderIndex() {
            return this.ordinal();
        }
    }

    // Client-side time-of-day lock
    public enum TimeOverride implements Localized {
        DEFAULT("impetus.options.extras.time.default"),
        DAY("impetus.options.extras.time.day"),
        NIGHT("impetus.options.extras.time.night");

        private final String key;

        TimeOverride(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Client-side weather lock
    public enum WeatherOverride implements Localized {
        DEFAULT("impetus.options.extras.weather.default"),
        CLEAR("impetus.options.extras.weather.clear"),
        RAIN("impetus.options.extras.weather.rain"),
        THUNDER("impetus.options.extras.weather.thunder");

        private final String key;

        WeatherOverride(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // How hard the render budget pushes; ordinal order is aggressiveness, and the target is the frame time the EMA is measured against (45 / 60 / ~71 FPS)
    public enum BudgetProfile implements Localized {
        QUALITY("impetus.options.extras.budget_profile.quality", 22.2),
        BALANCED("impetus.options.extras.budget_profile.balanced", 16.7),
        PERFORMANCE("impetus.options.extras.budget_profile.performance", 14.0);

        private final String key;
        public final double targetFrameMillis;

        BudgetProfile(String key, double targetFrameMillis) {
            this.key = key;
            this.targetFrameMillis = targetFrameMillis;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Vertical sync mode, folding adaptive sync in beside the vanilla on/off pair
    public enum VerticalSync implements Localized {
        OFF("options.off"),
        ON("options.on"),
        ADAPTIVE("impetus.options.extras.vertical_sync.adaptive");

        private final String key;

        VerticalSync(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
        }
    }

    // Something with a lang key; lets the option page build cycling controls generically
    public interface Localized {
        String translationKey();

        // Resolves the translation key for display
        default String localizedName() {
            return I18n.format(this.translationKey());
        }
    }

    // Setting groups

    // Texture animation switches; "all" gates every other field, and blockAnimations are what OptiFine breaks out separately
    public static final class AnimationSettings {
        public boolean all = true;
        public boolean water = true;
        public boolean lava = true;
        public boolean fire = true;
        public boolean portal = true;
        public boolean blockAnimations = true;
        public boolean redstone = true;
        public boolean explosion = true;
        public boolean flame = true;
        public boolean smoke = true;
        public boolean sculkSensor = true;
    }

    // Particle switches; "all" gates everything, named switches cover OptiFine/Sodium Extra effects, the rest go through ParticleClassRegistry's per-class toggles
    public static final class ParticleSettings {
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
        // Parallel ticking and the tick-time light sample (see client.particle.ParticleTicker), after AsyncParticles
        public boolean parallelTick = true;
        public boolean parallelModded = false;
        public boolean lightCache = true;
        public boolean cullOffscreen = true;
        public boolean collisionCache = true;
    }

    // Celestial and environmental detail switches
    public static final class DetailSettings {
        public static final int STARS_MIN = 500;
        public static final int STARS_DEFAULT = 1500;
        public static final int STARS_MAX = 32000;

        public boolean sky = true;
        public boolean stars = true;
        public int totalStars = STARS_DEFAULT;
        public boolean sun = true;
        public boolean moon = true;
        public boolean rainSnow = true;
        public boolean biomeColors = true;
        public boolean skyColors = true;
        public boolean swampColors = true;
        public boolean voidFog = true;
        public boolean showCapes = true;
        public boolean heldItemTooltips = true;
    }

    // World-render switches and fog/cloud tuning; cloud height/distance are absent since Impetus's Quality page owns those and two sliders for one value would fight
    public static final class RenderSettings {
        public static final int CLOUD_SCALE_MIN = 1;
        // The internal scale value that reproduces vanilla's 1.00x cloud size
        public static final int CLOUD_SCALE_VANILLA = 4;
        public static final int CLOUD_SCALE_MAX = 16;

        public boolean fog = true;
        public int fogStart = 100;
        public int fogDistance = 0;
        public FogShape fogShape = FogShape.VANILLA;
        public int cloudScale = CLOUD_SCALE_VANILLA;
        public CloudTranslucency cloudTranslucency = CloudTranslucency.DEFAULT;
        public boolean lightUpdates = true;
        public boolean itemFrames = true;
        public int itemFrameLodDistance = 0;
        public boolean itemFrameNameTag = true;
        public boolean armorStands = true;
        public boolean paintings = true;
        public boolean pistons = true;
        public boolean beacons = true;
        public boolean limitBeaconBeamHeight = false;
        public boolean enchantingTableBooks = true;
        public boolean playerNameTag = true;
        public boolean droppedItemsFancy = true;
        public boolean preventShaders = false;
        public boolean profileEntityRendering = false;
    }

    // Overlay, toast and quality-of-life settings
    public static final class ExtraSettings {
        public static final int STEADY_HUD_REFRESH_MIN = 1;
        public static final int STEADY_HUD_REFRESH_DEFAULT = 1;
        public static final int STEADY_HUD_REFRESH_MAX = 20;
        // MinecraftServer.tick autosaves every 900 ticks
        public static final int AUTOSAVE_VANILLA_TICKS = 900;
        public static final int AUTOSAVE_MIN_TICKS = 0;
        public static final int AUTOSAVE_MAX_TICKS = 36000;

        public boolean showFps = false;
        public boolean showFpsExtended = true;
        public boolean showCoords = false;
        public boolean ignoreReducedDebugInfo = false;
        public OverlayCorner overlayCorner = OverlayCorner.TOP_LEFT;
        public TextContrast textContrast = TextContrast.SHADOW;
        public boolean steadyDebugHud = true;
        public int steadyDebugHudRefreshInterval = STEADY_HUD_REFRESH_DEFAULT;
        public boolean useAdaptiveSync = false;
        public boolean toasts = true;
        public boolean toastAdvancement = true;
        public boolean toastRecipe = true;
        public boolean toastTutorial = true;
        public boolean toastSystem = true;
        public boolean modNameTooltip = false;
        public boolean paniniProjection = false;
        public int paniniProjectionStrength = 25;
        public TimeOverride timeOverride = TimeOverride.DEFAULT;
        public WeatherOverride weatherOverride = WeatherOverride.DEFAULT;
        public int autosaveInterval = AUTOSAVE_VANILLA_TICKS;
    }

    // Adaptive render budgeting (see client.budget.RenderBudgetController); off by default since it trades distant mobs and cosmetic particles for frame time, which is only a good trade on a machine that needs it
    public static final class RenderBudgetSettings {
        public static final int PARTICLE_BUDGET_MIN = 0;
        public static final int PARTICLE_BUDGET_DEFAULT = 65;
        public static final int PARTICLE_BUDGET_MAX = 100;
        public static final int ENTITY_DISTANCE_MIN = 16;
        public static final int ENTITY_DISTANCE_DEFAULT = 96;
        public static final int ENTITY_DISTANCE_MAX = 256;
        // Vanilla's dispatcher already drops most special renderers past 64 blocks, so the useful range sits below that
        public static final int BLOCK_ENTITY_DISTANCE_MIN = 32;
        public static final int BLOCK_ENTITY_DISTANCE_DEFAULT = 48;
        public static final int BLOCK_ENTITY_DISTANCE_MAX = 128;

        public boolean enabled = false;
        public BudgetProfile profile = BudgetProfile.BALANCED;
        public boolean adaptive = true;
        public int particleBudget = PARTICLE_BUDGET_DEFAULT;
        public int entityCullDistance = ENTITY_DISTANCE_DEFAULT;
        public boolean smartEntityCulling = true;
        public boolean blockEntities = true;
        public int blockEntityDistance = BLOCK_ENTITY_DISTANCE_DEFAULT;
        public boolean itemFrames = true;
        public boolean overlay = false;
        public boolean quickSetupShown = false;
    }

    // GPU Booster (see client.booster): the parts of Mr.Toad's GPUBooster that still have something to do on 1.12.2, since this renderer already owns buffer storage and vanilla's framebuffer already uses a depth renderbuffer; off by default like the render budget
    public static final class GpuBoosterSettings {
        public boolean enabled = false;
        public boolean fastRandom = true;
        public boolean fastMath = true;
        public boolean streamUploads = true;
    }

    // Parallel server ticking (see async.ParallelProcessor), after AxalotL's Async; on by default for vanilla entities, random ticks and spawning, with modded entities the one opt-in since their code was never written to share the world; the master needs a relaunch since the concurrent collections go in at world construction, everything else is live
    public static final class AsyncSettings {
        public static final int THREADS_MAX = 64;
        // TNT and drops interact with everything around them, hopper carts write into block entities, and orbs merge; all stay serial unless the user says otherwise
        public static final String[] DEFAULT_SYNCHRONIZED = {
                "minecraft:tnt", "minecraft:item", "minecraft:xp_orb", "minecraft:hopper_minecart"
        };

        public boolean enabled = true;
        public int threads = 0;
        public boolean entities = true;
        public boolean moddedEntities = false;
        public boolean randomTicks = true;
        public boolean spawning = true;
        public String[] synchronizedEntities = DEFAULT_SYNCHRONIZED.clone();
    }

    // Block entities drawn as terrain (see client.bakedentities), after Enhanced Block Entities; on by default since a resting chest drawn by the chunk mesh is strictly cheaper than one drawn by its renderer every frame
    public static final class BakedEntitySettings {
        public boolean enabled = true;
        public boolean chests = true;
        public boolean enderChests = true;
        public boolean signs = true;
        public boolean beds = true;
        public boolean shulkerBoxes = true;
    }

    // Wire limits and timeouts (see network.NetworkLimits), after Packet Fixer; on by default since every cap here is one a large pack hits as a crash or a disconnect
    public static final class NetworkSettings {
        public static final int TIMEOUT_MIN = 5;
        public static final int TIMEOUT_MAX = 600;
        public static final int READ_TIMEOUT_DEFAULT = 120;
        public static final int LOGIN_TIMEOUT_DEFAULT = 120;
        public static final int KEEP_ALIVE_TIMEOUT_DEFAULT = 120;

        public boolean largePackets = true;
        public int readTimeoutSeconds = READ_TIMEOUT_DEFAULT;
        public int loginTimeoutSeconds = LOGIN_TIMEOUT_DEFAULT;
        public int keepAliveTimeoutSeconds = KEEP_ALIVE_TIMEOUT_DEFAULT;
    }

    // Thread priorities and the render loop's yield (see client.ThreadTuning), after StutterFix; every default is what the game does already, so this is strictly opt-in
    public static final class ThreadSettings {
        public static final int PRIORITY_MIN = Thread.MIN_PRIORITY;
        public static final int PRIORITY_NORMAL = Thread.NORM_PRIORITY;
        public static final int PRIORITY_MAX = Thread.MAX_PRIORITY;
        // Impetus starts its chunk builders two below normal
        public static final int CHUNK_BUILDER_DEFAULT = Thread.NORM_PRIORITY - 2;

        public int renderThreadPriority = PRIORITY_NORMAL;
        public int serverThreadPriority = PRIORITY_NORMAL;
        public int chunkBuilderPriority = CHUNK_BUILDER_DEFAULT;
        public boolean removeRenderYield = false;
    }

    // Declarative property bindings

    // A boolean config entry bound to its in-memory field, so load and save cannot drift apart
    @Desugar
    private record BooleanProperty(String category, String key, boolean defaultValue, String comment,
                                   Consumer<Boolean> setter, Supplier<Boolean> getter) {
        void load(Configuration config) {
            setter.accept(config.getBoolean(key, category, defaultValue, comment));
        }

        void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }

    // As BooleanProperty, with an inclusive range Configuration clamps to on load
    @Desugar
    private record IntProperty(String category, String key, int defaultValue, int min, int max, String comment,
                               Consumer<Integer> setter, Supplier<Integer> getter) {
        void load(Configuration config) {
            setter.accept(config.getInt(key, category, defaultValue, min, max, comment));
        }

        void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }
}
