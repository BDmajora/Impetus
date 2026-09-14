package com.bdmajora.fulgor.gui;

import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Lighting page in Impetus' video options; except diagnostics and pause behaviour these are read by FulgorMixinPlugin before the window exists, hence REQUIRES_GAME_RESTART on most
public final class FulgorOptionPages {
    private static final String MOD_ID = "fulgor";

    private static final OptionStorage<FulgorConfig> STORAGE = new OptionStorage<FulgorConfig>() {
        // The options screen edits the live config directly
        @Override
        public FulgorConfig getData() {
            return FulgorConfig.get();
        }

        // Writes the file once the screen is dismissed
        @Override
        public void save() {
            FulgorConfig.get().save();
        }
    };

    private FulgorOptionPages() {
    }

    // Builds the page; most toggles need a restart since the mixin plugin already read them
    public static OptionPage lighting() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "engine"))
                .add(restartToggle("enabled",
                        "impetus.options.fulgor.enabled",
                        OptionImpact.HIGH,
                        (config, value) -> config.enabled = value,
                        config -> config.enabled))
                .add(restartToggle("deferred_light_updates",
                        "impetus.options.fulgor.deferred_updates",
                        OptionImpact.HIGH,
                        (config, value) -> config.deferredLightUpdates = value,
                        config -> config.deferredLightUpdates))
                .add(restartToggle("deduplicate_updates",
                        "impetus.options.fulgor.deduplicate_updates",
                        OptionImpact.HIGH,
                        (config, value) -> config.deduplicateUpdates = value,
                        config -> config.deduplicateUpdates))
                .add(restartToggle("cache_block_light_info",
                        "impetus.options.fulgor.block_light_info",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.cacheBlockLightInfo = value,
                        config -> config.cacheBlockLightInfo))
                .add(restartToggle("parallel_light_updates",
                        "impetus.options.fulgor.parallel_updates",
                        OptionImpact.HIGH,
                        (config, value) -> config.parallelLightUpdates = value,
                        config -> config.parallelLightUpdates))
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "correctness"))
                .add(restartToggle("fix_chunk_boundary_lighting",
                        "impetus.options.fulgor.chunk_boundaries",
                        OptionImpact.LOW,
                        (config, value) -> config.fixChunkBoundaryLighting = value,
                        config -> config.fixChunkBoundaryLighting))
                .add(restartToggle("send_non_trivial_section_light",
                        "impetus.options.fulgor.section_light",
                        OptionImpact.LOW,
                        (config, value) -> config.sendNonTrivialSectionLight = value,
                        config -> config.sendNonTrivialSectionLight))
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "client"))
                .add(restartToggle("optimize_render_light_updates",
                        "impetus.options.fulgor.render_updates",
                        OptionImpact.MEDIUM,
                        (config, value) -> config.optimizeRenderLightUpdates = value,
                        config -> config.optimizeRenderLightUpdates))
                // Read on every client tick, so this one genuinely applies without a restart.
                .add(liveToggle("skip_updates_while_paused",
                        "impetus.options.fulgor.skip_while_paused",
                        OptionImpact.LOW,
                        (config, value) -> config.skipUpdatesWhilePaused = value,
                        config -> config.skipUpdatesWhilePaused))
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(MOD_ID, "diagnostics"))
                .add(liveToggle("show_debug_overlay",
                        "impetus.options.fulgor.debug_overlay",
                        null,
                        (config, value) -> config.showDebugOverlay = value,
                        config -> config.showDebugOverlay))
                .add(liveToggle("warn_on_illegal_thread_access",
                        "impetus.options.fulgor.thread_warnings",
                        null,
                        (config, value) -> config.warnOnIllegalThreadAccess = value,
                        config -> config.warnOnIllegalThreadAccess))
                .build());

        return new OptionPage(
                OptionIdentifier.create(MOD_ID, "lighting"),
                TextComponent.translatable("impetus.options.pages.lighting"),
                ImmutableList.copyOf(groups));
    }

    private static OptionImpl<FulgorConfig, Boolean> restartToggle(
            String path, String langKey, OptionImpact impact,
            BiConsumer<FulgorConfig, Boolean> setter, Function<FulgorConfig, Boolean> getter) {
        return builder(path, langKey, impact, setter, getter)
                .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                .build();
    }

    private static OptionImpl<FulgorConfig, Boolean> liveToggle(
            String path, String langKey, OptionImpact impact,
            BiConsumer<FulgorConfig, Boolean> setter, Function<FulgorConfig, Boolean> getter) {
        return builder(path, langKey, impact, setter, getter).build();
    }

    private static OptionImpl.Builder<FulgorConfig, Boolean> builder(
            String path, String langKey, OptionImpact impact,
            BiConsumer<FulgorConfig, Boolean> setter, Function<FulgorConfig, Boolean> getter) {
        OptionImpl.Builder<FulgorConfig, Boolean> builder = OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(OptionIdentifier.create(MOD_ID, path, boolean.class))
                .setName(TextComponent.translatable(langKey + ".name"))
                .setTooltip(TextComponent.translatable(langKey + ".tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding(setter, getter);

        // The diagnostics have no performance impact worth labelling, and a badge would only suggest otherwise
        if (impact != null) {
            builder.setImpact(impact);
        }

        return builder;
    }
}
