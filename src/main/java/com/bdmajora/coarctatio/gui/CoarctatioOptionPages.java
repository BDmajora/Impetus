package com.bdmajora.coarctatio.gui;

import java.util.function.Function;
import java.util.function.BiConsumer;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.impl.gui.ModuleOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// The Memory page in Impetus' video options, one OptionStorage writing straight through to the config; nearly everything is REQUIRES_GAME_RESTART since the mixin plugin reads the config before the window exists
public final class CoarctatioOptionPages {
    private static final String MOD_ID = "coarctatio";

    // Reads and writes the live config singleton so a toggle is immediately visible via CoarctatioConfig.get(); save() on every apply is fine for a seventeen-line file
    private static final OptionStorage<CoarctatioConfig> STORAGE = new OptionStorage<CoarctatioConfig>() {
        // The options screen edits the live config directly
        @Override
        public CoarctatioConfig getData() {
            return CoarctatioConfig.get();
        }

        // Writes the file once the screen is dismissed
        @Override
        public void save() {
            CoarctatioConfig.get().save();
        }
    };
    // Ids sit directly under the mod id and the lang keys are spelled out per option
    private static final ModuleOptions<CoarctatioConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "", "", STORAGE);

    private CoarctatioOptionPages() {
    }

    // Group ids that make up the loading-side page; everything else lands on the data page
    private static final Set<String> LOADING_PAGE_GROUPS = ImmutableSet.of("deduplication", "loading", "system");

    // The two pages: what happens while resources and models load, and what is kept smaller at runtime; one list of groups is built in on-screen order and split by id so each helper stays where it was
    public static List<OptionPage> pages() {
        List<OptionGroup> loading = new ArrayList<>();
        List<OptionGroup> data = new ArrayList<>();
        for (OptionGroup group : groups()) {
            (LOADING_PAGE_GROUPS.contains(group.getId().getPath()) ? loading : data).add(group);
        }
        List<OptionPage> pages = new ArrayList<>();
        // "page." keeps the ids clear of the group ids, which are the bare names split on above
        pages.add(new OptionPage(OptionIdentifier.create(MOD_ID, "page.loading"),
                TextComponent.translatable("impetus.options.pages.coarctatio.loading"), ImmutableList.copyOf(loading)));
        pages.add(new OptionPage(OptionIdentifier.create(MOD_ID, "page.data"),
                TextComponent.translatable("impetus.options.pages.coarctatio.data"), ImmutableList.copyOf(data)));
        return pages;
    }

    // A group heading from impetus.options.coarctatio.group.<id>
    private static TextComponent groupName(String id) {
        return TextComponent.translatable("impetus.options.coarctatio.group." + id);
    }

    // Every group in on-screen order
    private static List<OptionGroup> groups() {
        List<OptionGroup> groups = new ArrayList<>();

        // Interning passes over data the resource loader produces once at startup
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "deduplication"))
                .setName(groupName("deduplication"))
                .add(restartToggle("deduplicate_resource_locations",
                        "impetus.options.coarctatio.resource_locations",
                        OptionImpact.LOW,
                        (config, value) -> config.deduplicateResourceLocations = value,
                        config -> config.deduplicateResourceLocations))
                .add(restartToggle("deduplicate_model_variants",
                        "impetus.options.coarctatio.model_variants",
                        OptionImpact.LOW,
                        (config, value) -> config.deduplicateModelVariants = value,
                        config -> config.deduplicateModelVariants))
                .add(restartToggle("pool_quad_vertex_data",
                        "impetus.options.coarctatio.quad_vertex_data",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.poolQuadVertexData = value,
                        config -> config.poolQuadVertexData))
                .add(restartToggle("canonicalize_multipart_conditions",
                        "impetus.options.coarctatio.multipart_conditions",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.canonicalizeMultipartConditions = value,
                        config -> config.canonicalizeMultipartConditions))
                .build());

        // The block state representation itself — highest impact of the lot, since it touches every state object
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "block_states"))
                .setName(groupName("block_states"))
                .add(restartToggle("optimize_block_states",
                        "impetus.options.coarctatio.block_states",
                        OptionImpact.HIGH,
                        (config, value) -> config.optimizeBlockStates = value,
                        config -> config.optimizeBlockStates))
                .add(restartToggle("compact_state_properties",
                        "impetus.options.coarctatio.state_properties",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactStateProperties = value,
                        config -> config.compactStateProperties))
                .build());

        // Backing-collection swaps for NBT compounds and baked models
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "collections"))
                .setName(groupName("collections"))
                .add(restartToggle("compact_nbt_backing_map",
                        "impetus.options.coarctatio.nbt_backing_map",
                        OptionImpact.HIGH,
                        (config, value) -> config.compactNbtBackingMap = value,
                        config -> config.compactNbtBackingMap))
                // Consulted every time a compound is built, so it applies without restart and is spelled out instead of using restartToggle
                .add(OPTIONS.toggle("intern_nbt_keys", "impetus.options.coarctatio.nbt_keys",
                        (config, value) -> config.internNbtKeys = value, config -> config.internNbtKeys,
                        OptionImpact.MEDIUM, null, null))
                // Entry count below which a compound uses the array-backed map instead of a HashMap, read per compound; slider 0..64 in steps of 2, 0 meaning never
                .add(OPTIONS.builder("nbt_array_map_threshold", "impetus.options.coarctatio.nbt_threshold", int.class,
                                (config, value) -> config.nbtArrayMapThreshold = value, config -> config.nbtArrayMapThreshold,
                                null, null, null)
                        .setControl(option -> new SliderControl(option, 0, 64, 2, ControlValueFormatter.number()))
                        .build())
                .add(restartToggle("compact_baked_models",
                        "impetus.options.coarctatio.baked_models",
                        OptionImpact.LOW,
                        (config, value) -> config.compactBakedModels = value,
                        config -> config.compactBakedModels))
                .add(restartToggle("compact_model_graph",
                        "impetus.options.coarctatio.model_graph",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactModelGraph = value,
                        config -> config.compactModelGraph))
                .build());

        // Chunk storage: what gets dropped on load rather than kept resident
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "world"))
                .setName(groupName("world"))
                .add(restartToggle("strip_chunk_nbt",
                        "impetus.options.coarctatio.chunk_nbt",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.stripChunkNbt = value,
                        config -> config.stripChunkNbt))
                .add(restartToggle("drop_empty_chunk_sections",
                        "impetus.options.coarctatio.empty_sections",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.dropEmptyChunkSections = value,
                        config -> config.dropEmptyChunkSections))
                .build());

        // Everything else: loader caches, sprite and bake scratch data, search trees, pool lifetime
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "system"))
                .setName(groupName("system"))
                .add(restartToggle("weaken_class_loader_cache",
                        "impetus.options.coarctatio.class_loader_cache",
                        OptionImpact.HIGH,
                        (config, value) -> config.weakenClassLoaderCache = value,
                        config -> config.weakenClassLoaderCache))
                .add(restartToggle("release_sprite_data",
                        "impetus.options.coarctatio.sprite_data",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.releaseSpriteData = value,
                        config -> config.releaseSpriteData))
                .add(restartToggle("deduplicate_model_transforms",
                        "impetus.options.coarctatio.model_transforms",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.deduplicateModelTransforms = value,
                        config -> config.deduplicateModelTransforms))
                .add(restartToggle("release_bake_state",
                        "impetus.options.coarctatio.bake_state",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.releaseBakeState = value,
                        config -> config.releaseBakeState))
                .add(restartToggle("lazy_search_trees",
                        "impetus.options.coarctatio.search_trees",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.lazySearchTrees = value,
                        config -> config.lazySearchTrees))
                // Checked at world teardown, not at mixin-apply time, so no restart is needed
                .add(OPTIONS.toggle("clear_pools_on_world_leave", "impetus.options.coarctatio.clear_on_leave",
                        (config, value) -> config.clearPoolsOnWorldLeave = value, config -> config.clearPoolsOnWorldLeave,
                        OptionImpact.LOW, null, null))
                .add(restartToggle("compact_runtime_collections",
                        "impetus.options.coarctatio.runtime_collections",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactRuntimeCollections = value,
                        config -> config.compactRuntimeCollections))
                .build());

        // The model and resource loader: what is loaded when, and what the loader keeps
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "loading"))
                .setName(groupName("loading"))
                .add(restartToggle("dynamic_models",
                        "impetus.options.coarctatio.dynamic_models",
                        OptionImpact.HIGH,
                        (config, value) -> config.dynamicModels = value,
                        config -> config.dynamicModels))
                .add(restartToggle("dynamic_models_prebake_items",
                        "impetus.options.coarctatio.dynamic_models_prebake",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.dynamicModelsPrebakeItems = value,
                        config -> config.dynamicModelsPrebakeItems))
                .add(restartToggle("fast_item_layer_baking",
                        "impetus.options.coarctatio.fast_item_baking",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.fastItemLayerBaking = value,
                        config -> config.fastItemLayerBaking))
                .add(restartToggle("resource_existence_cache",
                        "impetus.options.coarctatio.resource_existence",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.resourceExistenceCache = value,
                        config -> config.resourceExistenceCache))
                .add(restartToggle("stackless_resource_exceptions",
                        "impetus.options.coarctatio.stackless_exceptions",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.stacklessResourceExceptions = value,
                        config -> config.stacklessResourceExceptions))
                .add(restartToggle("fast_atlas_stitching",
                        "impetus.options.coarctatio.atlas_stitching",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.fastAtlasStitching = value,
                        config -> config.fastAtlasStitching))
                .add(restartToggle("intern_loader_strings",
                        "impetus.options.coarctatio.loader_strings",
                        OptionImpact.LOW,
                        (config, value) -> config.internLoaderStrings = value,
                        config -> config.internLoaderStrings))
                .add(restartToggle("mod_scan_cache",
                        "impetus.options.coarctatio.mod_scan_cache",
                        OptionImpact.HIGH,
                        (config, value) -> config.modScanCache = value,
                        config -> config.modScanCache))
                .add(restartToggle("parallel_texture_load",
                        "impetus.options.coarctatio.parallel_texture_load",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.parallelTextureLoad = value,
                        config -> config.parallelTextureLoad))
                .add(restartToggle("compact_remapper_caches",
                        "impetus.options.coarctatio.remapper_caches",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactRemapperCaches = value,
                        config -> config.compactRemapperCaches))
                .add(restartToggle("skip_sound_debug_checks",
                        "impetus.options.coarctatio.sound_debug",
                        OptionImpact.LOW,
                        (config, value) -> config.skipSoundDebugChecks = value,
                        config -> config.skipSoundDebugChecks))
                .add(restartToggle("quiet_prefix_warnings",
                        "impetus.options.coarctatio.prefix_warnings",
                        OptionImpact.LOW,
                        (config, value) -> config.quietPrefixWarnings = value,
                        config -> config.quietPrefixWarnings))
                .add(restartToggle("plain_missing_models",
                        "impetus.options.coarctatio.missing_models",
                        OptionImpact.LOW,
                        (config, value) -> config.plainMissingModels = value,
                        config -> config.plainMissingModels))
                .add(restartToggle("canonicalize_model_parts",
                        "impetus.options.coarctatio.model_parts",
                        OptionImpact.LOW,
                        (config, value) -> config.canonicalizeModelParts = value,
                        config -> config.canonicalizeModelParts))
                .build());

        // Per-object caches and pools on the game's hot paths
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "runtime"))
                .setName(groupName("runtime"))
                .add(restartToggle("lazy_item_stack_capabilities",
                        "impetus.options.coarctatio.item_stack_capabilities",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.lazyItemStackCapabilities = value,
                        config -> config.lazyItemStackCapabilities))
                .add(restartToggle("primitive_ore_dictionary",
                        "impetus.options.coarctatio.ore_dictionary",
                        OptionImpact.LOW,
                        (config, value) -> config.primitiveOreDictionary = value,
                        config -> config.primitiveOreDictionary))
                .add(restartToggle("recycle_events",
                        "impetus.options.coarctatio.recycle_events",
                        OptionImpact.LOW,
                        (config, value) -> config.recycleEvents = value,
                        config -> config.recycleEvents))
                .add(restartToggle("pool_nbt_primitives",
                        "impetus.options.coarctatio.nbt_primitives",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.poolNbtPrimitives = value,
                        config -> config.poolNbtPrimitives))
                .add(restartToggle("cache_property_hashes",
                        "impetus.options.coarctatio.property_hashes",
                        OptionImpact.LOW,
                        (config, value) -> config.cachePropertyHashes = value,
                        config -> config.cachePropertyHashes))
                .add(restartToggle("cache_state_hashes",
                        "impetus.options.coarctatio.state_hashes",
                        OptionImpact.LOW,
                        (config, value) -> config.cacheStateHashes = value,
                        config -> config.cacheStateHashes))
                .add(restartToggle("soft_structure_templates",
                        "impetus.options.coarctatio.structure_templates",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.softStructureTemplates = value,
                        config -> config.softStructureTemplates))
                .build());

        // Read live every frame, so no restart flag and no impact; an F3 line costs nothing worth warning about
        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "diagnostics"))
                .setName(groupName("diagnostics"))
                .add(OPTIONS.toggle("show_debug_overlay", "impetus.options.coarctatio.debug_overlay",
                        (config, value) -> config.showDebugOverlay = value, config -> config.showDebugOverlay,
                        null, null, null))
                .build());

        return groups;
    }

    // Every mixin-gated switch is the same restart-flagged tickbox; langKey gets ".name"/".tooltip" appended, the setter writes on apply and the getter seeds the control and detects change
    private static OptionImpl<CoarctatioConfig, Boolean> restartToggle(
            String path,
            String langKey,
            OptionImpact impact,
            BiConsumer<CoarctatioConfig, Boolean> setter,
            Function<CoarctatioConfig, Boolean> getter) {
        return OPTIONS.toggle(path, langKey, setter, getter, impact, OptionFlag.REQUIRES_GAME_RESTART, null);
    }
}
