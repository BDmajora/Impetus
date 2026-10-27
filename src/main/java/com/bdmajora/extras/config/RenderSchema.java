package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig.BakedEntitySettings;
import com.bdmajora.extras.ExtrasConfig.EntityModelSettings;
import com.bdmajora.extras.ExtrasConfig.GpuBoosterSettings;
import com.bdmajora.extras.ExtrasConfig.OcclusionSettings;
import com.bdmajora.extras.ExtrasConfig.RenderBudgetSettings;
import com.bdmajora.extras.ExtrasConfig.RenderSettings;

// Settings of world rendering and the render budget and the GPU booster and entity drawing
final class RenderSchema {
    // World render switches and fog and cloud tuning
    static final SettingGroup<RenderSettings> RENDER = SettingGroup.builder("render", c -> c.render, RenderSettings::new)
            .bool("fog", "Render atmospheric fog", s -> s.fog, (s, v) -> s.fog = v)
            .bool("lightUpdates", "Process client-side light updates", s -> s.lightUpdates, (s, v) -> s.lightUpdates = v)
            .bool("itemFrames", "Render item frames", s -> s.itemFrames, (s, v) -> s.itemFrames = v)
            .bool("armorStands", "Render armor stands", s -> s.armorStands, (s, v) -> s.armorStands = v)
            .bool("paintings", "Render paintings", s -> s.paintings, (s, v) -> s.paintings = v)
            .bool("pistons", "Render moving pistons", s -> s.pistons, (s, v) -> s.pistons = v)
            .bool("beacons", "Render beacon beams", s -> s.beacons, (s, v) -> s.beacons = v)
            .bool("limitBeaconBeamHeight", "Stop beacon beams at the world ceiling",
                    s -> s.limitBeaconBeamHeight, (s, v) -> s.limitBeaconBeamHeight = v)
            .bool("enchantingTableBooks", "Render the enchanting table's floating book",
                    s -> s.enchantingTableBooks, (s, v) -> s.enchantingTableBooks = v)
            .bool("playerNameTag", "Render player name tags", s -> s.playerNameTag, (s, v) -> s.playerNameTag = v)
            .bool("itemFrameNameTag", "Render item frame name tags", s -> s.itemFrameNameTag, (s, v) -> s.itemFrameNameTag = v)
            .bool("droppedItemsFancy", "Stack up to five models per dropped item pile instead of always drawing one",
                    s -> s.droppedItemsFancy, (s, v) -> s.droppedItemsFancy = v)
            .bool("preventShaders", "Block the vanilla post-processing shader pipeline",
                    s -> s.preventShaders, (s, v) -> s.preventShaders = v)
            .bool("profileEntityRendering", "Break entity and block-entity rendering out by type in the F3 profiler graph",
                    s -> s.profileEntityRendering, (s, v) -> s.profileEntityRendering = v)
            .integer("fogStart", 0, 200, "Fog start distance as a percentage of the fog end (100 = vanilla)",
                    s -> s.fogStart, (s, v) -> s.fogStart = v)
            .integer("fogDistance", 0, 32, "Fog distance in chunks (0 = follow render distance)",
                    s -> s.fogDistance, (s, v) -> s.fogDistance = v)
            .integer("cloudScale", RenderSettings.CLOUD_SCALE_MIN, RenderSettings.CLOUD_SCALE_MAX,
                    "Cloud scale in quarter steps (1 = 0.25x, 4 = vanilla, 16 = 4.00x)",
                    s -> s.cloudScale, (s, v) -> s.cloudScale = v)
            .integer("itemFrameLodDistance", 0, 256, "Distance in blocks past which framed items lose their side faces (0 = off)",
                    s -> s.itemFrameLodDistance, (s, v) -> s.itemFrameLodDistance = v)
            .choice("cloudTranslucency", "Cloud translucency mode (0 = Default, 1 = Always, 2 = Never)",
                    s -> s.cloudTranslucency, (s, v) -> s.cloudTranslucency = v)
            .choice("fogShape", "Terrain fog shape (0 = Vanilla, 1 = Cylindrical, 2 = Radial, 3 = Planar)",
                    s -> s.fogShape, (s, v) -> s.fogShape = v)
            .build();

    // Adaptive render budget switches and limits
    static final SettingGroup<RenderBudgetSettings> RENDER_BUDGET = SettingGroup.builder("render_budget", c -> c.renderBudget, RenderBudgetSettings::new)
            .bool("enabled", "Adaptive render budgeting: skip distant idle mobs and thin cosmetic particles under frame pressure",
                    s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("adaptive", "Tighten the budget a further step when frames run 20% or more over the profile target",
                    s -> s.adaptive, (s, v) -> s.adaptive = v)
            .bool("smartEntityCulling", "Skip rendering distant non-player living entities under pressure",
                    s -> s.smartEntityCulling, (s, v) -> s.smartEntityCulling = v)
            .bool("blockEntities", "Skip vanilla decorative block entity renderers (chests, signs, heads, banners, beds, shulker boxes) past the block entity distance under pressure",
                    s -> s.blockEntities, (s, v) -> s.blockEntities = v)
            .bool("itemFrames", "Skip plain item frames past the block entity distance under pressure; glowing, named and map frames are kept",
                    s -> s.itemFrames, (s, v) -> s.itemFrames = v)
            .bool("overlay", "Show the budget, frame pressure and skipped counts on the HUD",
                    s -> s.overlay, (s, v) -> s.overlay = v)
            .integer("particleBudget", RenderBudgetSettings.PARTICLE_BUDGET_MIN, RenderBudgetSettings.PARTICLE_BUDGET_MAX,
                    "Percentage of cosmetic particles allowed to spawn (100 = no particle budgeting)",
                    s -> s.particleBudget, (s, v) -> s.particleBudget = v)
            .integer("entityCullDistance", RenderBudgetSettings.ENTITY_DISTANCE_MIN, RenderBudgetSettings.ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which idle living entities may be skipped under pressure",
                    s -> s.entityCullDistance, (s, v) -> s.entityCullDistance = v)
            .integer("blockEntityDistance", RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MIN, RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_MAX,
                    "Distance in blocks past which decorative block entities and item frames may be skipped under pressure (vanilla stops most at 64 anyway)",
                    s -> s.blockEntityDistance, (s, v) -> s.blockEntityDistance = v)
            .choice("profile", "Render budget profile (0 = Quality, 1 = Balanced, 2 = Performance)",
                    s -> s.profile, (s, v) -> s.profile = v)
            .build();

    // GPU booster switches
    static final SettingGroup<GpuBoosterSettings> GPU_BOOSTER = SettingGroup.builder("gpu_booster", c -> c.gpuBooster, GpuBoosterSettings::new)
            .bool("enabled", "Master switch for the GPU Booster set: fast random, fast math, streamed vertex uploads and the vertex format cache",
                    s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("fastRandom", "Give entities and particles a cheaper random generator than java.util.Random",
                    s -> s.fastRandom, (s, v) -> s.fastRandom = v)
            .bool("fastMath", "Cheaper angle wrapping and log2 in MathHelper", s -> s.fastMath, (s, v) -> s.fastMath = v)
            .bool("streamUploads", "Draw immediate-mode geometry through a streamed vertex buffer instead of client-side arrays",
                    s -> s.streamUploads, (s, v) -> s.streamUploads = v)
            .bool("vertexFormatCache", "Keep one vertex array object per vertex format for streamed draws, so each draw binds it instead of re-specifying every attribute",
                    s -> s.vertexFormatCache, (s, v) -> s.vertexFormatCache = v)
            .build();

    // Switches of block entities drawn as terrain
    static final SettingGroup<BakedEntitySettings> BAKED_ENTITIES = SettingGroup.builder("baked_block_entities", c -> c.bakedEntities, BakedEntitySettings::new)
            .bool("enabled", "Master switch: draw resting chests, ender chests, signs, beds and shulker boxes as terrain instead of through their block entity renderers",
                    s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("chests", "Bake chests and trapped chests, single and double", s -> s.chests, (s, v) -> s.chests = v)
            .bool("enderChests", "Bake ender chests", s -> s.enderChests, (s, v) -> s.enderChests = v)
            .bool("signs", "Bake sign boards and posts (text still comes from the renderer)", s -> s.signs, (s, v) -> s.signs = v)
            .bool("beds", "Bake beds", s -> s.beds, (s, v) -> s.beds = v)
            .bool("shulkerBoxes", "Bake closed shulker boxes", s -> s.shulkerBoxes, (s, v) -> s.shulkerBoxes = v)
            .build();

    // Occlusion culling switches and size limits
    static final SettingGroup<OcclusionSettings> OCCLUSION = SettingGroup.builder("occlusion_culling", c -> c.occlusion, OcclusionSettings::new)
            .bool("enabled", "Master switch: skip drawing entities and block entities a worker thread has ray-cast as hidden behind blocks",
                    s -> s.enabled, (s, v) -> s.enabled = v)
            .bool("entities", "Cull hidden entities", s -> s.entities, (s, v) -> s.entities = v)
            .bool("blockEntities", "Cull hidden block entities (chests, signs, ...)", s -> s.blockEntities, (s, v) -> s.blockEntities = v)
            .integer("maxEntitySize", 1, 32, "Entities wider or taller than this many blocks are never culled",
                    s -> s.maxEntitySize, (s, v) -> s.maxEntitySize = v)
            .integer("maxBlockEntitySize", 1, 32, "Block entities whose render box exceeds this many blocks on any axis are never culled",
                    s -> s.maxBlockEntitySize, (s, v) -> s.maxBlockEntitySize = v)
            .integer("raycastSlack", 0, 300, "Hundredths of a block at the end of each ray that never occlude, so a target inside a block is not hidden by that block",
                    s -> s.raycastSlack, (s, v) -> s.raycastSlack = v)
            .build();

    // Model box transform switch
    static final SettingGroup<EntityModelSettings> ENTITY_MODELS = SettingGroup.builder("entity_models", c -> c.entityModels, EntityModelSettings::new)
            .bool("matrixTransforms", "Position each model box with one matrix multiply instead of up to six translate/rotate calls",
                    s -> s.matrixTransforms, (s, v) -> s.matrixTransforms = v)
            .build();

    // Prevents instantiation
    private RenderSchema() {
    }
}
