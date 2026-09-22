package com.bdmajora.extras;

import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.booster.GpuBooster;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.extras.client.particle.ParticleTicker;
import com.bdmajora.impetus.impl.config.ConfigProperty;
import com.bdmajora.impetus.impl.gui.Localized;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
    private static final String CAT_OCCLUSION = "occlusion_culling";
    private static final String CAT_HUD = "hud";
    private static final String CAT_TEXT = "text";
    private static final String CAT_CLIENT_TICK = "client_tick";
    private static final String CAT_ENTITY_MODELS = "entity_models";
    private static final String CAT_LEAVES = "leaves";
    private static final String CAT_LOADING = "loading";

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
    public final OcclusionSettings occlusion = new OcclusionSettings();
    public final HudSettings hud = new HudSettings();
    public final TextSettings text = new TextSettings();
    public final ClientTickSettings clientTick = new ClientTickSettings();
    public final EntityModelSettings entityModels = new EntityModelSettings();
    public final LeafSettings leaves = new LeafSettings();
    public final LoadingSettings loading = new LoadingSettings();

    private final List<ConfigProperty> booleans = Arrays.asList(
            // --- Animations -------------------------------------------------------------------
            ConfigProperty.bool(CAT_ANIMATION, "animation", true, "Master switch for all texture animations",
                    v -> animation.all = v, () -> animation.all),
            ConfigProperty.bool(CAT_ANIMATION, "water", true, "Animate water textures",
                    v -> animation.water = v, () -> animation.water),
            ConfigProperty.bool(CAT_ANIMATION, "lava", true, "Animate lava textures",
                    v -> animation.lava = v, () -> animation.lava),
            ConfigProperty.bool(CAT_ANIMATION, "fire", true, "Animate fire textures",
                    v -> animation.fire = v, () -> animation.fire),
            ConfigProperty.bool(CAT_ANIMATION, "portal", true, "Animate nether portal textures",
                    v -> animation.portal = v, () -> animation.portal),
            ConfigProperty.bool(CAT_ANIMATION, "blockAnimations", true, "Animate other block textures",
                    v -> animation.blockAnimations = v, () -> animation.blockAnimations),
            // OptiFine splits these out of the general block-animation switch.
            ConfigProperty.bool(CAT_ANIMATION, "redstone", true, "Animate redstone textures",
                    v -> animation.redstone = v, () -> animation.redstone),
            ConfigProperty.bool(CAT_ANIMATION, "explosion", true, "Animate explosion textures",
                    v -> animation.explosion = v, () -> animation.explosion),
            ConfigProperty.bool(CAT_ANIMATION, "flame", true, "Animate flame textures",
                    v -> animation.flame = v, () -> animation.flame),
            ConfigProperty.bool(CAT_ANIMATION, "smoke", true, "Animate smoke textures",
                    v -> animation.smoke = v, () -> animation.smoke),
            ConfigProperty.bool(CAT_ANIMATION, "sculkSensor", true, "Animate sculk-sensor-style textures (modded only on 1.12.2)",
                    v -> animation.sculkSensor = v, () -> animation.sculkSensor),

            // --- Particles --------------------------------------------------------------------
            ConfigProperty.bool(CAT_PARTICLE, "particles", true, "Master switch for all particles",
                    v -> particle.all = v, () -> particle.all),
            ConfigProperty.bool(CAT_PARTICLE, "rainSplash", true, "Rain splash particles",
                    v -> particle.rainSplash = v, () -> particle.rainSplash),
            ConfigProperty.bool(CAT_PARTICLE, "blockBreak", true, "Block break particles",
                    v -> particle.blockBreak = v, () -> particle.blockBreak),
            ConfigProperty.bool(CAT_PARTICLE, "blockBreaking", true, "Block breaking (mining) particles",
                    v -> particle.blockBreaking = v, () -> particle.blockBreaking),
            ConfigProperty.bool(CAT_PARTICLE, "voidParticles", true, "Void particles near the world bottom",
                    v -> particle.voidParticles = v, () -> particle.voidParticles),
            ConfigProperty.bool(CAT_PARTICLE, "waterParticles", true, "Water drip, splash and suspended particles",
                    v -> particle.waterParticles = v, () -> particle.waterParticles),
            ConfigProperty.bool(CAT_PARTICLE, "portalParticles", true, "Nether portal particles",
                    v -> particle.portalParticles = v, () -> particle.portalParticles),
            ConfigProperty.bool(CAT_PARTICLE, "potionParticles", true, "Potion effect particles",
                    v -> particle.potionParticles = v, () -> particle.potionParticles),
            ConfigProperty.bool(CAT_PARTICLE, "drippingWaterLava", true, "Dripping water and lava particles",
                    v -> particle.drippingWaterLava = v, () -> particle.drippingWaterLava),
            ConfigProperty.bool(CAT_PARTICLE, "fireworkParticles", true, "Firework spark particles",
                    v -> particle.fireworkParticles = v, () -> particle.fireworkParticles),
            ConfigProperty.bool(CAT_PARTICLE, "parallelTick", true, "Tick particles across a worker pool instead of the client thread",
                    v -> particle.parallelTick = v, () -> particle.parallelTick),
            ConfigProperty.bool(CAT_PARTICLE, "parallelModded", false, "Also tick modded particle classes on the pool; off keeps them on the client thread",
                    v -> particle.parallelModded = v, () -> particle.parallelModded),
            ConfigProperty.bool(CAT_PARTICLE, "lightCache", true, "Sample each particle's light once per tick instead of once per frame",
                    v -> particle.lightCache = v, () -> particle.lightCache),
            ConfigProperty.bool(CAT_PARTICLE, "cullOffscreen", true, "Skip building quads for vanilla particles outside the view frustum",
                    v -> particle.cullOffscreen = v, () -> particle.cullOffscreen),
            ConfigProperty.bool(CAT_PARTICLE, "collisionCache", true, "Remember that a particle's block cell has nothing to collide with instead of sweeping it every tick",
                    v -> particle.collisionCache = v, () -> particle.collisionCache),

            // --- Details ----------------------------------------------------------------------
            ConfigProperty.bool(CAT_DETAIL, "sky", true, "Render the sky box",
                    v -> detail.sky = v, () -> detail.sky),
            ConfigProperty.bool(CAT_DETAIL, "stars", true, "Render stars",
                    v -> detail.stars = v, () -> detail.stars),
            ConfigProperty.bool(CAT_DETAIL, "sun", true, "Render the sun",
                    v -> detail.sun = v, () -> detail.sun),
            ConfigProperty.bool(CAT_DETAIL, "moon", true, "Render the moon",
                    v -> detail.moon = v, () -> detail.moon),
            ConfigProperty.bool(CAT_DETAIL, "rainSnow", true, "Render falling rain and snow",
                    v -> detail.rainSnow = v, () -> detail.rainSnow),
            ConfigProperty.bool(CAT_DETAIL, "biomeColors", true, "Biome-specific grass, foliage and water tint",
                    v -> detail.biomeColors = v, () -> detail.biomeColors),
            ConfigProperty.bool(CAT_DETAIL, "skyColors", true, "Biome-specific sky colour",
                    v -> detail.skyColors = v, () -> detail.skyColors),
            ConfigProperty.bool(CAT_DETAIL, "swampColors", true, "Swamp's darkened grass and foliage tint",
                    v -> detail.swampColors = v, () -> detail.swampColors),
            ConfigProperty.bool(CAT_DETAIL, "voidFog", true, "Void fog near the world bottom",
                    v -> detail.voidFog = v, () -> detail.voidFog),
            ConfigProperty.bool(CAT_DETAIL, "showCapes", true, "Render player capes",
                    v -> detail.showCapes = v, () -> detail.showCapes),
            ConfigProperty.bool(CAT_DETAIL, "heldItemTooltips", true, "Show the item name popup when switching hotbar slots",
                    v -> detail.heldItemTooltips = v, () -> detail.heldItemTooltips),

            // --- Render -----------------------------------------------------------------------
            ConfigProperty.bool(CAT_RENDER, "fog", true, "Render atmospheric fog",
                    v -> render.fog = v, () -> render.fog),
            ConfigProperty.bool(CAT_RENDER, "lightUpdates", true, "Process client-side light updates",
                    v -> render.lightUpdates = v, () -> render.lightUpdates),
            ConfigProperty.bool(CAT_RENDER, "itemFrames", true, "Render item frames",
                    v -> render.itemFrames = v, () -> render.itemFrames),
            ConfigProperty.bool(CAT_RENDER, "armorStands", true, "Render armor stands",
                    v -> render.armorStands = v, () -> render.armorStands),
            ConfigProperty.bool(CAT_RENDER, "paintings", true, "Render paintings",
                    v -> render.paintings = v, () -> render.paintings),
            ConfigProperty.bool(CAT_RENDER, "pistons", true, "Render moving pistons",
                    v -> render.pistons = v, () -> render.pistons),
            ConfigProperty.bool(CAT_RENDER, "beacons", true, "Render beacon beams",
                    v -> render.beacons = v, () -> render.beacons),
            ConfigProperty.bool(CAT_RENDER, "limitBeaconBeamHeight", false, "Stop beacon beams at the world ceiling",
                    v -> render.limitBeaconBeamHeight = v, () -> render.limitBeaconBeamHeight),
            ConfigProperty.bool(CAT_RENDER, "enchantingTableBooks", true, "Render the enchanting table's floating book",
                    v -> render.enchantingTableBooks = v, () -> render.enchantingTableBooks),
            ConfigProperty.bool(CAT_RENDER, "playerNameTag", true, "Render player name tags",
                    v -> render.playerNameTag = v, () -> render.playerNameTag),
            ConfigProperty.bool(CAT_RENDER, "itemFrameNameTag", true, "Render item frame name tags",
                    v -> render.itemFrameNameTag = v, () -> render.itemFrameNameTag),
            ConfigProperty.bool(CAT_RENDER, "droppedItemsFancy", true,
                    "Stack up to five models per dropped item pile instead of always drawing one",
                    v -> render.droppedItemsFancy = v, () -> render.droppedItemsFancy),
            ConfigProperty.bool(CAT_RENDER, "preventShaders", false, "Block the vanilla post-processing shader pipeline",
                    v -> render.preventShaders = v, () -> render.preventShaders),
            ConfigProperty.bool(CAT_RENDER, "profileEntityRendering", false,
                    "Break entity and block-entity rendering out by type in the F3 profiler graph",
                    v -> render.profileEntityRendering = v, () -> render.profileEntityRendering),

            // --- Extra ------------------------------------------------------------------------
            ConfigProperty.bool(CAT_EXTRA, "showFps", false, "Show the FPS overlay",
                    v -> extra.showFps = v, () -> extra.showFps),
            ConfigProperty.bool(CAT_EXTRA, "showFPSExtended", true, "Include average, 1% low and 0.1% low in the FPS overlay",
                    v -> extra.showFpsExtended = v, () -> extra.showFpsExtended),
            ConfigProperty.bool(CAT_EXTRA, "showCoords", false, "Show the coordinates overlay",
                    v -> extra.showCoords = v, () -> extra.showCoords),
            ConfigProperty.bool(CAT_EXTRA, "ignoreReducedDebugInfo", false,
                    "Show coordinates even when the server sets the reducedDebugInfo game rule",
                    v -> extra.ignoreReducedDebugInfo = v, () -> extra.ignoreReducedDebugInfo),
            ConfigProperty.bool(CAT_EXTRA, "steadyDebugHud", true, "Throttle how often the F3 overlay text is rebuilt",
                    v -> extra.steadyDebugHud = v, () -> extra.steadyDebugHud),
            ConfigProperty.bool(CAT_EXTRA, "useAdaptiveSync", false, "Use adaptive VSync (swap interval -1) when supported",
                    v -> extra.useAdaptiveSync = v, () -> extra.useAdaptiveSync),
            ConfigProperty.bool(CAT_EXTRA, "toasts", true, "Master switch for toast pop-ups",
                    v -> extra.toasts = v, () -> extra.toasts),
            ConfigProperty.bool(CAT_EXTRA, "toastAdvancement", true, "Advancement toasts",
                    v -> extra.toastAdvancement = v, () -> extra.toastAdvancement),
            ConfigProperty.bool(CAT_EXTRA, "toastRecipe", true, "Recipe unlock toasts",
                    v -> extra.toastRecipe = v, () -> extra.toastRecipe),
            ConfigProperty.bool(CAT_EXTRA, "toastTutorial", true, "Tutorial toasts",
                    v -> extra.toastTutorial = v, () -> extra.toastTutorial),
            ConfigProperty.bool(CAT_EXTRA, "toastSystem", true, "System toasts",
                    v -> extra.toastSystem = v, () -> extra.toastSystem),
            ConfigProperty.bool(CAT_EXTRA, "modNameTooltip", false, "Append the source mod's name to item tooltips",
                    v -> extra.modNameTooltip = v, () -> extra.modNameTooltip),
            ConfigProperty.bool(CAT_EXTRA, "paniniProjection", false, "Apply a Panini projection post-effect to widen the view",
                    v -> extra.paniniProjection = v, () -> extra.paniniProjection),
            // --- Render budget ----------------------------------------------------------------
            ConfigProperty.bool(CAT_RENDER_BUDGET, "enabled", false, "Adaptive render budgeting: skip distant idle mobs and thin cosmetic particles under frame pressure",
                    v -> renderBudget.enabled = v, () -> renderBudget.enabled),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "adaptive", true, "Tighten the budget a further step when frames run 20% or more over the profile target",
                    v -> renderBudget.adaptive = v, () -> renderBudget.adaptive),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "smartEntityCulling", true, "Skip rendering distant non-player living entities under pressure",
                    v -> renderBudget.smartEntityCulling = v, () -> renderBudget.smartEntityCulling),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "blockEntities", true, "Skip vanilla decorative block entity renderers (chests, signs, heads, banners, beds, shulker boxes) past the block entity distance under pressure",
                    v -> renderBudget.blockEntities = v, () -> renderBudget.blockEntities),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "itemFrames", true, "Skip plain item frames past the block entity distance under pressure; glowing, named and map frames are kept",
                    v -> renderBudget.itemFrames = v, () -> renderBudget.itemFrames),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "overlay", false, "Show the budget, frame pressure and skipped counts on the HUD",
                    v -> renderBudget.overlay = v, () -> renderBudget.overlay),
            ConfigProperty.bool(CAT_RENDER_BUDGET, "quickSetupShown", false, "Whether the one-time quick setup screen has been shown on a world join",
                    v -> renderBudget.quickSetupShown = v, () -> renderBudget.quickSetupShown),
            // --- GPU booster ------------------------------------------------------------------
            ConfigProperty.bool(CAT_GPU_BOOSTER, "enabled", false, "Master switch for the GPU Booster set: fast random, fast math and streamed vertex uploads",
                    v -> gpuBooster.enabled = v, () -> gpuBooster.enabled),
            ConfigProperty.bool(CAT_GPU_BOOSTER, "fastRandom", true, "Give entities and particles a cheaper random generator than java.util.Random",
                    v -> gpuBooster.fastRandom = v, () -> gpuBooster.fastRandom),
            ConfigProperty.bool(CAT_GPU_BOOSTER, "fastMath", true, "Cheaper angle wrapping and log2 in MathHelper",
                    v -> gpuBooster.fastMath = v, () -> gpuBooster.fastMath),
            ConfigProperty.bool(CAT_GPU_BOOSTER, "streamUploads", true, "Draw immediate-mode geometry through a streamed vertex buffer instead of client-side arrays",
                    v -> gpuBooster.streamUploads = v, () -> gpuBooster.streamUploads),
            // --- Parallel ticking -------------------------------------------------------------
            ConfigProperty.bool(CAT_ASYNC, "enabled", true, "Master switch for parallel server ticking (entities, random ticks, mob spawning across the worker pool); takes effect on the next launch",
                    v -> async.enabled = v, () -> async.enabled),
            ConfigProperty.bool(CAT_ASYNC, "entities", true, "Tick vanilla entities in parallel across the worker pool",
                    v -> async.entities = v, () -> async.entities),
            ConfigProperty.bool(CAT_ASYNC, "moddedEntities", false, "Also tick modded entities in parallel; off keeps them on the main thread",
                    v -> async.moddedEntities = v, () -> async.moddedEntities),
            ConfigProperty.bool(CAT_ASYNC, "randomTicks", true, "Run block random ticks (crop growth, fire spread, ...) in parallel",
                    v -> async.randomTicks = v, () -> async.randomTicks),
            ConfigProperty.bool(CAT_ASYNC, "spawning", true, "Evaluate natural mob spawn attempts in parallel per chunk",
                    v -> async.spawning = v, () -> async.spawning),
            // --- Baked block entities ---------------------------------------------------------
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "enabled", true, "Master switch: draw resting chests, ender chests, signs, beds and shulker boxes as terrain instead of through their block entity renderers",
                    v -> bakedEntities.enabled = v, () -> bakedEntities.enabled),
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "chests", true, "Bake chests and trapped chests, single and double",
                    v -> bakedEntities.chests = v, () -> bakedEntities.chests),
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "enderChests", true, "Bake ender chests",
                    v -> bakedEntities.enderChests = v, () -> bakedEntities.enderChests),
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "signs", true, "Bake sign boards and posts (text still comes from the renderer)",
                    v -> bakedEntities.signs = v, () -> bakedEntities.signs),
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "beds", true, "Bake beds",
                    v -> bakedEntities.beds = v, () -> bakedEntities.beds),
            ConfigProperty.bool(CAT_BAKED_ENTITIES, "shulkerBoxes", true, "Bake closed shulker boxes",
                    v -> bakedEntities.shulkerBoxes = v, () -> bakedEntities.shulkerBoxes),
            // --- Network ----------------------------------------------------------------------
            ConfigProperty.bool(CAT_NETWORK, "largePackets", true, "Lift vanilla's packet, payload, NBT, string and chunk data size caps (2 MB frames, 32 KB strings, 1 MB payloads)",
                    v -> network.largePackets = v, () -> network.largePackets),
            ConfigProperty.bool(CAT_NETWORK, "flushConsolidation", true, "Queue packets sent during a server tick and flush each connection once at the end of the tick instead of per packet",
                    v -> network.flushConsolidation = v, () -> network.flushConsolidation),
            ConfigProperty.bool(CAT_NETWORK, "fastVarInts", true, "Table-driven varint sizing and single-write varint encoding",
                    v -> network.fastVarInts = v, () -> network.fastVarInts),
            ConfigProperty.bool(CAT_NETWORK, "pooledCompression", true, "Compress and decompress packets without copying them into fresh arrays",
                    v -> network.pooledCompression = v, () -> network.pooledCompression),
            // --- Thread scheduling ------------------------------------------------------------
            ConfigProperty.bool(CAT_THREADS, "removeRenderYield", false, "Skip the Thread.yield() the render loop makes once per frame",
                    v -> threads.removeRenderYield = v, () -> threads.removeRenderYield),
            // --- Occlusion culling ------------------------------------------------------------
            ConfigProperty.bool(CAT_OCCLUSION, "enabled", true, "Master switch: skip drawing entities and block entities a worker thread has ray-cast as hidden behind blocks",
                    v -> occlusion.enabled = v, () -> occlusion.enabled),
            ConfigProperty.bool(CAT_OCCLUSION, "entities", true, "Cull hidden entities",
                    v -> occlusion.entities = v, () -> occlusion.entities),
            ConfigProperty.bool(CAT_OCCLUSION, "blockEntities", true, "Cull hidden block entities (chests, signs, ...)",
                    v -> occlusion.blockEntities = v, () -> occlusion.blockEntities),
            // --- HUD --------------------------------------------------------------------------
            ConfigProperty.bool(CAT_HUD, "cacheEnabled", false, "Draw the HUD into a framebuffer at a capped rate and composite it every frame; crosshair and vignette stay live",
                    v -> hud.cacheEnabled = v, () -> hud.cacheEnabled),
            // --- Text -------------------------------------------------------------------------
            ConfigProperty.bool(CAT_TEXT, "batchGlyphs", true, "Draw each string's glyphs in one batch instead of one immediate-mode quad per glyph",
                    v -> text.batchGlyphs = v, () -> text.batchGlyphs),
            // --- Client tick ------------------------------------------------------------------
            ConfigProperty.bool(CAT_CLIENT_TICK, "lightmapCaching", true, "Skip rebuilding and uploading the lightmap while nothing but the torch flicker has changed",
                    v -> clientTick.lightmapCaching = v, () -> clientTick.lightmapCaching),
            // --- Entity models ----------------------------------------------------------------
            ConfigProperty.bool(CAT_ENTITY_MODELS, "matrixTransforms", true, "Position each model box with one matrix multiply instead of up to six translate/rotate calls",
                    v -> entityModels.matrixTransforms = v, () -> entityModels.matrixTransforms),
            // --- Loading ----------------------------------------------------------------------
            ConfigProperty.bool(CAT_LOADING, "skipWorldLoadGc", true, "Skip the forced full garbage collection on world load and unload",
                    v -> loading.skipWorldLoadGc = v, () -> loading.skipWorldLoadGc),
            ConfigProperty.bool(CAT_LOADING, "smoothDimensionChange", true, "Skip the 'Loading terrain' screen on dimension change and respawn",
                    v -> loading.smoothDimensionChange = v, () -> loading.smoothDimensionChange),
            ConfigProperty.bool(CAT_LOADING, "releaseScreenshotBuffers", true, "Free the screenshot readback buffers after each screenshot instead of keeping them forever",
                    v -> loading.releaseScreenshotBuffers = v, () -> loading.releaseScreenshotBuffers),
            ConfigProperty.bool(CAT_LOADING, "asyncScreenshots", true, "Encode and write screenshots on a background thread",
                    v -> loading.asyncScreenshots = v, () -> loading.asyncScreenshots),
            ConfigProperty.bool(CAT_LOADING, "driverAtlasLimit", true, "Ask the driver for its maximum texture size instead of probing proxy uploads from 16384 down",
                    v -> loading.driverAtlasLimit = v, () -> loading.driverAtlasLimit)
    );

    private final List<ConfigProperty> integers = Arrays.asList(
            ConfigProperty.integer(CAT_DETAIL, "totalStars", DetailSettings.STARS_DEFAULT,
                    DetailSettings.STARS_MIN, DetailSettings.STARS_MAX, "Number of stars to generate",
                    v -> detail.totalStars = v, () -> detail.totalStars),
            ConfigProperty.integer(CAT_RENDER, "fogStart", 100, 0, 200,
                    "Fog start distance as a percentage of the fog end (100 = vanilla)",
                    v -> render.fogStart = v, () -> render.fogStart),
            ConfigProperty.integer(CAT_RENDER, "fogDistance", 0, 0, 32,
                    "Fog distance in chunks (0 = follow render distance)",
                    v -> render.fogDistance = v, () -> render.fogDistance),
            ConfigProperty.integer(CAT_RENDER, "cloudScale", RenderSettings.CLOUD_SCALE_VANILLA,
                    RenderSettings.CLOUD_SCALE_MIN, RenderSettings.CLOUD_SCALE_MAX,
                    "Cloud scale in quarter steps (1 = 0.25x, 4 = vanilla, 16 = 4.00x)",
                    v -> render.cloudScale = v, () -> render.cloudScale),
            ConfigProperty.integer(CAT_RENDER, "itemFrameLodDistance", 0, 0, 256,
                    "Distance in blocks past which framed items lose their side faces (0 = off)",
                    v -> render.itemFrameLodDistance = v, () -> render.itemFrameLodDistance),
            ConfigProperty.integer(CAT_EXTRA, "steadyDebugHudRefreshInterval",
                    ExtraSettings.STEADY_HUD_REFRESH_DEFAULT, ExtraSettings.STEADY_HUD_REFRESH_MIN,
                    ExtraSettings.STEADY_HUD_REFRESH_MAX, "F3 overlay rebuild interval in ticks",
                    v -> extra.steadyDebugHudRefreshInterval = v, () -> extra.steadyDebugHudRefreshInterval),
            ConfigProperty.integer(CAT_EXTRA, "paniniProjectionStrength", 25, 0, 100,
                    "Panini projection strength as a percentage",
                    v -> extra.paniniProjectionStrength = v, () -> extra.paniniProjectionStrength),
            ConfigProperty.integer(CAT_EXTRA, "autosaveInterval", ExtraSettings.AUTOSAVE_VANILLA_TICKS,
                    ExtraSettings.AUTOSAVE_MIN_TICKS, ExtraSettings.AUTOSAVE_MAX_TICKS,
                    "Singleplayer autosave interval in ticks (900 = vanilla)",
                    v -> extra.autosaveInterval = v, () -> extra.autosaveInterval),
            ConfigProperty.integer(CAT_RENDER_BUDGET, "particleBudget", RenderBudgetSettings.PARTICLE_BUDGET_DEFAULT,
                    RenderBudgetSettings.PARTICLE_BUDGET_MIN, RenderBudgetSettings.PARTICLE_BUDGET_MAX,
                    "Percentage of cosmetic particles allowed to spawn (100 = no particle budgeting)",
                    v -> renderBudget.particleBudget = v, () -> renderBudget.particleBudget),
            ConfigProperty.integer(CAT_RENDER_BUDGET, "entityCullDistance", RenderBudgetSettings.ENTITY_DISTANCE_DEFAULT,
                    RenderBudgetSettings.ENTITY_DISTANCE_MIN, RenderBudgetSettings.ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which idle living entities may be skipped under pressure",
                    v -> renderBudget.entityCullDistance = v, () -> renderBudget.entityCullDistance),
            ConfigProperty.integer(CAT_RENDER_BUDGET, "blockEntityDistance", RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_DEFAULT,
                    RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN, RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which decorative block entities and item frames may be skipped under pressure (vanilla stops most at 64 anyway)",
                    v -> renderBudget.blockEntityDistance = v, () -> renderBudget.blockEntityDistance),
            ConfigProperty.integer(CAT_ASYNC, "threads", 0, 0, AsyncSettings.THREADS_MAX,
                    "Worker threads for parallel ticking (0 = automatic, about three quarters of the cores)",
                    v -> async.threads = v, () -> async.threads),
            ConfigProperty.integer(CAT_NETWORK, "readTimeoutSeconds", NetworkSettings.READ_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a connection may go silent before it is dropped (vanilla 30)",
                    v -> network.readTimeoutSeconds = v, () -> network.readTimeoutSeconds),
            ConfigProperty.integer(CAT_NETWORK, "loginTimeoutSeconds", NetworkSettings.LOGIN_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to finish logging in (vanilla 30)",
                    v -> network.loginTimeoutSeconds = v, () -> network.loginTimeoutSeconds),
            ConfigProperty.integer(CAT_NETWORK, "keepAliveTimeoutSeconds", NetworkSettings.KEEP_ALIVE_TIMEOUT_DEFAULT, NetworkSettings.TIMEOUT_MIN, NetworkSettings.TIMEOUT_MAX,
                    "Seconds a client may take to answer a keep-alive (vanilla 15)",
                    v -> network.keepAliveTimeoutSeconds = v, () -> network.keepAliveTimeoutSeconds),
            ConfigProperty.integer(CAT_THREADS, "renderThreadPriority", ThreadSettings.PRIORITY_NORMAL, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the client (render) thread, 1-10 (5 = unchanged)",
                    v -> threads.renderThreadPriority = v, () -> threads.renderThreadPriority),
            ConfigProperty.integer(CAT_THREADS, "serverThreadPriority", ThreadSettings.PRIORITY_NORMAL, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the integrated server thread, 1-10 (5 = unchanged)",
                    v -> threads.serverThreadPriority = v, () -> threads.serverThreadPriority),
            ConfigProperty.integer(CAT_THREADS, "chunkBuilderPriority", ThreadSettings.CHUNK_BUILDER_DEFAULT, ThreadSettings.PRIORITY_MIN, ThreadSettings.PRIORITY_MAX,
                    "Java priority of the chunk builder threads, 1-10 (3 = unchanged)",
                    v -> threads.chunkBuilderPriority = v, () -> threads.chunkBuilderPriority),
            ConfigProperty.integer(CAT_OCCLUSION, "maxEntitySize", OcclusionSettings.MAX_ENTITY_SIZE_DEFAULT, 1, 32,
                    "Entities wider or taller than this many blocks are never culled",
                    v -> occlusion.maxEntitySize = v, () -> occlusion.maxEntitySize),
            ConfigProperty.integer(CAT_OCCLUSION, "maxBlockEntitySize", OcclusionSettings.MAX_BLOCK_ENTITY_SIZE_DEFAULT, 1, 32,
                    "Block entities whose render box exceeds this many blocks on any axis are never culled",
                    v -> occlusion.maxBlockEntitySize = v, () -> occlusion.maxBlockEntitySize),
            ConfigProperty.integer(CAT_OCCLUSION, "raycastSlack", OcclusionSettings.SLACK_DEFAULT, 0, 300,
                    "Hundredths of a block at the end of each ray that never occlude, so a target inside a block is not hidden by that block",
                    v -> occlusion.raycastSlack = v, () -> occlusion.raycastSlack),
            ConfigProperty.integer(CAT_HUD, "cacheFps", HudSettings.CACHE_FPS_DEFAULT, HudSettings.CACHE_FPS_MIN, HudSettings.CACHE_FPS_MAX,
                    "How many times per second the cached HUD is redrawn",
                    v -> hud.cacheFps = v, () -> hud.cacheFps),
            ConfigProperty.integer(CAT_LEAVES, "cullingDepth", LeafSettings.DEPTH_DEFAULT, LeafSettings.DEPTH_MIN, LeafSettings.DEPTH_MAX,
                    "Depth mode: how many solid blocks must lie behind a leaf face before it is dropped",
                    v -> leaves.cullingDepth = v, () -> leaves.cullingDepth)
    );

    private final List<ConfigProperty> enums = Arrays.asList(
            ConfigProperty.enumeration(CAT_RENDER, "cloudTranslucency", CloudTranslucency.values(), CloudTranslucency.DEFAULT,
                    "Cloud translucency mode (0 = Default, 1 = Always, 2 = Never)",
                    v -> render.cloudTranslucency = v, () -> render.cloudTranslucency),
            ConfigProperty.enumeration(CAT_RENDER, "fogShape", FogShape.values(), FogShape.VANILLA,
                    "Terrain fog shape (0 = Vanilla, 1 = Cylindrical, 2 = Radial, 3 = Planar)",
                    v -> render.fogShape = v, () -> render.fogShape),
            ConfigProperty.enumeration(CAT_EXTRA, "overlayCorner", OverlayCorner.values(), OverlayCorner.TOP_LEFT,
                    "Overlay corner (0 = Top Left, 1 = Top Right, 2 = Bottom Left, 3 = Bottom Right)",
                    v -> extra.overlayCorner = v, () -> extra.overlayCorner),
            ConfigProperty.enumeration(CAT_EXTRA, "textContrast", TextContrast.values(), TextContrast.SHADOW,
                    "Overlay text contrast (0 = None, 1 = Background, 2 = Shadow)",
                    v -> extra.textContrast = v, () -> extra.textContrast),
            ConfigProperty.enumeration(CAT_EXTRA, "timeOverride", TimeOverride.values(), TimeOverride.DEFAULT,
                    "Client-side time of day (0 = Default, 1 = Day only, 2 = Night only). Creative/cheats only.",
                    v -> extra.timeOverride = v, () -> extra.timeOverride),
            ConfigProperty.enumeration(CAT_EXTRA, "weatherOverride", WeatherOverride.values(), WeatherOverride.DEFAULT,
                    "Client-side weather (0 = Default, 1 = Clear, 2 = Rain, 3 = Thunder). Creative/cheats only.",
                    v -> extra.weatherOverride = v, () -> extra.weatherOverride),
            ConfigProperty.enumeration(CAT_RENDER_BUDGET, "profile", BudgetProfile.values(), BudgetProfile.BALANCED,
                    "Render budget profile (0 = Quality, 1 = Balanced, 2 = Performance)",
                    v -> renderBudget.profile = v, () -> renderBudget.profile),
            ConfigProperty.enumeration(CAT_LEAVES, "cullingMode", LeafCulling.values(), LeafCulling.DEFAULT,
                    "Fancy leaf face culling (0 = Default, 1 = Check surrounding, 2 = Depth)",
                    v -> leaves.cullingMode = v, () -> leaves.cullingMode)
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
        enums.forEach(property -> property.load(config));

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
        enums.forEach(property -> property.save(config));

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

    // Which interior leaf faces the mesher drops in fancy mode
    public enum LeafCulling implements Localized {
        DEFAULT("impetus.options.extras.leaf_culling.default"),
        CHECK("impetus.options.extras.leaf_culling.check"),
        DEPTH("impetus.options.extras.leaf_culling.depth");

        private final String key;

        LeafCulling(String key) {
            this.key = key;
        }

        // Lang key for the cycler label
        @Override
        public String translationKey() {
            return this.key;
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

    // Wire limits and timeouts (see network.NetworkLimits), after Packet Fixer, plus Krypton's flush consolidation, varint and compression paths; on by default since every cap here is one a large pack hits as a crash or a disconnect and the Krypton parts change nothing on the wire
    public static final class NetworkSettings {
        public static final int TIMEOUT_MIN = 5;
        public static final int TIMEOUT_MAX = 600;
        public static final int READ_TIMEOUT_DEFAULT = 120;
        public static final int LOGIN_TIMEOUT_DEFAULT = 120;
        public static final int KEEP_ALIVE_TIMEOUT_DEFAULT = 120;

        public boolean largePackets = true;
        public boolean flushConsolidation = true;
        public boolean fastVarInts = true;
        public boolean pooledCompression = true;
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

    // Occlusion culling of entities and block entities (see client.culling), after tr7zw's and Meldexun's Entity Culling; on by default since a hidden mob costs its full draw and a farm's worth of them is the difference between a playable base and not
    public static final class OcclusionSettings {
        public static final int MAX_ENTITY_SIZE_DEFAULT = 4;
        public static final int MAX_BLOCK_ENTITY_SIZE_DEFAULT = 4;
        public static final int SLACK_DEFAULT = 100;

        public boolean enabled = true;
        public boolean entities = true;
        public boolean blockEntities = true;
        public int maxEntitySize = MAX_ENTITY_SIZE_DEFAULT;
        public int maxBlockEntitySize = MAX_BLOCK_ENTITY_SIZE_DEFAULT;
        public int raycastSlack = SLACK_DEFAULT;
    }

    // HUD framebuffer caching (see client.hud.HudCache), the idea behind Exordium and Gnetum; off by default since mod overlays that animate per frame visibly step at the cache rate
    public static final class HudSettings {
        public static final int CACHE_FPS_MIN = 10;
        public static final int CACHE_FPS_DEFAULT = 30;
        public static final int CACHE_FPS_MAX = 120;

        public boolean cacheEnabled = false;
        public int cacheFps = CACHE_FPS_DEFAULT;
    }

    // Glyph batching (see client.text.GlyphBatch), after ImmediatelyFast; on by default, the output is pixel-identical
    public static final class TextSettings {
        public boolean batchGlyphs = true;
    }

    // Per-tick client work that can be skipped when its inputs have not changed, after BadOptimizations
    public static final class ClientTickSettings {
        public boolean lightmapCaching = true;
    }

    // Model box transforms as one matrix multiply, after Valkyrie; on by default
    public static final class EntityModelSettings {
        public boolean matrixTransforms = true;
    }

    // Fancy-leaf interior face culling, after More Culling and Cull Less Leaves; default mode changes nothing since any culling shows on a canopy's silhouette from some angle
    public static final class LeafSettings {
        public static final int DEPTH_MIN = 1;
        public static final int DEPTH_DEFAULT = 2;
        public static final int DEPTH_MAX = 4;

        public LeafCulling cullingMode = LeafCulling.DEFAULT;
        public int cullingDepth = DEPTH_DEFAULT;
    }

    // World load and misc client hitches, after VanillaFix, Chibi and Universal Tweaks; all on, none change what is drawn
    public static final class LoadingSettings {
        public boolean skipWorldLoadGc = true;
        public boolean smoothDimensionChange = true;
        public boolean releaseScreenshotBuffers = true;
        public boolean asyncScreenshots = true;
        public boolean driverAtlasLimit = true;
    }
}
