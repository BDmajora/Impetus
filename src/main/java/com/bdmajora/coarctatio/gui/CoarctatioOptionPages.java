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

// Option pages of the memory features.
public final class CoarctatioOptionPages {
    // Identifier of the module.
    private static final String MOD_ID = "coarctatio";

    // Storage that reads and writes the configuration.
    private static final OptionStorage<CoarctatioConfig> STORAGE = new OptionStorage<CoarctatioConfig>() {
        // Returns the loaded configuration.
        @Override
        public CoarctatioConfig getData() {
            return CoarctatioConfig.get();
        }

        // Writes the configuration to its file.
        @Override
        public void save() {
            CoarctatioConfig.get().save();
        }
    };
    // Factory of the options of the module.
    private static final ModuleOptions<CoarctatioConfig> OPTIONS = new ModuleOptions<>(MOD_ID, "", "", STORAGE);

    // Prevents instantiation
    private CoarctatioOptionPages() {
    }

    // Groups shown on the loading page.
    private static final Set<String> LOADING_PAGE_GROUPS = ImmutableSet.of("deduplication", "loading", "system");

    // Returns the loading page and the data page.
    public static List<OptionPage> pages() {
        List<OptionGroup> loading = new ArrayList<>();
        List<OptionGroup> data = new ArrayList<>();
        for (OptionGroup group : groups()) {
            (LOADING_PAGE_GROUPS.contains(group.getId().getPath()) ? loading : data).add(group);
        }
        List<OptionPage> pages = new ArrayList<>();
        pages.add(new OptionPage(OptionIdentifier.create(MOD_ID, "page.loading"),
                TextComponent.translatable("impetus.options.pages.coarctatio.loading"), ImmutableList.copyOf(loading)));
        pages.add(new OptionPage(OptionIdentifier.create(MOD_ID, "page.data"),
                TextComponent.translatable("impetus.options.pages.coarctatio.data"), ImmutableList.copyOf(data)));
        return pages;
    }

    // Returns the translated name of a group.
    private static TextComponent groupName(String id) {
        return TextComponent.translatable("impetus.options.coarctatio.group." + id);
    }

    // Returns every option group.
    private static List<OptionGroup> groups() {
        List<OptionGroup> groups = new ArrayList<>();

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

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "collections"))
                .setName(groupName("collections"))
                .add(restartToggle("compact_nbt_backing_map",
                        "impetus.options.coarctatio.nbt_backing_map",
                        OptionImpact.HIGH,
                        (config, value) -> config.compactNbtBackingMap = value,
                        config -> config.compactNbtBackingMap))
                .add(OPTIONS.toggle("intern_nbt_keys", "impetus.options.coarctatio.nbt_keys",
                        (config, value) -> config.internNbtKeys = value, config -> config.internNbtKeys,
                        OptionImpact.MEDIUM, null, null))
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
                .add(OPTIONS.toggle("clear_pools_on_world_leave", "impetus.options.coarctatio.clear_on_leave",
                        (config, value) -> config.clearPoolsOnWorldLeave = value, config -> config.clearPoolsOnWorldLeave,
                        OptionImpact.LOW, null, null))
                .add(restartToggle("compact_runtime_collections",
                        "impetus.options.coarctatio.runtime_collections",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.compactRuntimeCollections = value,
                        config -> config.compactRuntimeCollections))
                .build());

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
                .add(restartToggle("parallel_model_load",
                        "impetus.options.coarctatio.parallel_model_load",
                        OptionImpact.HIGH,
                        (config, value) -> config.parallelModelLoad = value,
                        config -> config.parallelModelLoad))
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

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "diagnostics"))
                .setName(groupName("diagnostics"))
                .add(OPTIONS.toggle("show_debug_overlay", "impetus.options.coarctatio.debug_overlay",
                        (config, value) -> config.showDebugOverlay = value, config -> config.showDebugOverlay,
                        null, null, null))
                .build());

        return groups;
    }

    // Returns a toggle that needs a game restart.
    private static OptionImpl<CoarctatioConfig, Boolean> restartToggle(
            String path,
            String langKey,
            OptionImpact impact,
            BiConsumer<CoarctatioConfig, Boolean> setter,
            Function<CoarctatioConfig, Boolean> getter) {
        return OPTIONS.toggle(path, langKey, setter, getter, impact, OptionFlag.REQUIRES_GAME_RESTART, null);
    }
}
