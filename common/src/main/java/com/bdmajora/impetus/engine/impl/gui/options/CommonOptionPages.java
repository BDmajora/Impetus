package com.bdmajora.impetus.engine.impl.gui.options;

import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;

import java.util.Objects;

// Engine options shared by every platform.
public final class CommonOptionPages {
    // Prevents instantiation
    private CommonOptionPages() {
    }

    // Creates the option that selects the terrain renderer.
    public static OptionImpl<ImpetusGameOptions, ImpetusGameOptions.GraphicsApi> graphicsApi(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(ImpetusGameOptions.GraphicsApi.class, gameOpts)
                .setId(StandardOptions.Option.GRAPHICS_API.cast())
                .setName(TextComponent.translatable("options.graphicsApi"))
                .setTooltip(TextComponent.translatable("impetus.options.graphics_api.tooltip"))
                .setControl(o -> new CyclingControl<>(o, ImpetusGameOptions.GraphicsApi.class))
                .setImpact(OptionImpact.HIGH)
                // Greys the option out on a GPU without mesh shaders
                .setEnabledPredicate(MeshShaderSupport::isSupported)
                // Reads OpenGL on a GPU without mesh shaders
                .setBinding((opts, value) -> opts.graphicsApi = value,
                        opts -> MeshShaderSupport.isSupported() ? opts.graphicsApi : ImpetusGameOptions.GraphicsApi.OPENGL)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
    }

    // Creates the slider of the chunk builder thread count.
    public static OptionImpl<ImpetusGameOptions, Integer> chunkUpdateThreads(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(int.class, gameOpts)
                .setId(StandardOptions.Option.CHUNK_UPDATE_THREADS.cast())
                .setName(TextComponent.translatable("impetus.options.chunk_update_threads.name"))
                .setTooltip(TextComponent.translatable("impetus.options.chunk_update_threads.tooltip"))
                .setControl(o -> new SliderControl(o, 0, ChunkBuilder.getMaxThreadCount(), 1, ControlValueFormatter.translateQuantityOrDisabled("impetus.options.chunk_update_threads.value", "impetus.options.chunk_update_threads.default")))
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
    }

    // Creates the choice of when chunk updates wait for the next frame.
    public static OptionImpl<ImpetusGameOptions, ImpetusGameOptions.DeferChunkUpdatesMode> deferChunkUpdates(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(ImpetusGameOptions.DeferChunkUpdatesMode.class, gameOpts)
                .setId(StandardOptions.Option.DEFFER_CHUNK_UPDATES.cast())
                .setName(TextComponent.translatable("impetus.options.defer_chunk_updates.name"))
                .setTooltip(TextComponent.translatable("impetus.options.defer_chunk_updates.tooltip"))
                .setControl(o -> new CyclingControl<>(o, ImpetusGameOptions.DeferChunkUpdatesMode.class))
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> {
                    opts.performance.deferChunkUpdatesMode = value;
                    opts.performance.alwaysDeferChunkUpdates = value.defersVisible();
                    ImpetusRuntimeOptions.apply(opts);
                }, opts -> opts.performance.deferChunkUpdatesMode)
                .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                .build();
    }

    // Creates the switch that skips entities outside the view.
    public static OptionImpl<ImpetusGameOptions, Boolean> entityCulling(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(boolean.class, gameOpts)
                .setId(StandardOptions.Option.ENTITY_CULLING.cast())
                .setName(TextComponent.translatable("impetus.options.use_entity_culling.name"))
                .setTooltip(TextComponent.translatable("impetus.options.use_entity_culling.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.MEDIUM)
                .setBinding((opts, value) -> opts.performance.useEntityCulling = value, opts -> opts.performance.useEntityCulling)
                .build();
    }

    // Creates the switch that animates only textures on screen.
    public static OptionImpl<ImpetusGameOptions, Boolean> animateVisibleTextures(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(boolean.class, gameOpts)
                .setId(StandardOptions.Option.ANIMATE_VISIBLE_TEXTURES.cast())
                .setName(TextComponent.translatable("impetus.options.animate_only_visible_textures.name"))
                .setTooltip(TextComponent.translatable("impetus.options.animate_only_visible_textures.tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.HIGH)
                .setBinding((opts, value) -> opts.performance.animateOnlyVisibleTextures = value, opts -> opts.performance.animateOnlyVisibleTextures)
                .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                .build();
    }

    // Creates the choice of how the frame rate drops while the game sits idle.
    public static OptionImpl<ImpetusGameOptions, ImpetusGameOptions.InactivityFpsLimit> inactivityFpsLimit(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(ImpetusGameOptions.InactivityFpsLimit.class, gameOpts)
                .setId(StandardOptions.Option.INACTIVITY_FPS_LIMIT.cast())
                .setName(TextComponent.translatable("impetus.options.inactivity_fps_limit.name"))
                .setTooltip(TextComponent.translatable("impetus.options.inactivity_fps_limit.tooltip"))
                .setControl(o -> new CyclingControl<>(o, ImpetusGameOptions.InactivityFpsLimit.class))
                .setImpact(OptionImpact.LOW)
                .setBinding((opts, value) -> {
                    opts.performance.inactivityFpsLimit = value;
                    ImpetusRuntimeOptions.apply(opts);
                }, opts -> opts.performance.inactivityFpsLimit)
                .build();
    }

    // Creates the switch of the Impetus notification toasts.
    public static OptionImpl<ImpetusGameOptions, Boolean> showToasts(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(boolean.class, gameOpts)
                .setId(StandardOptions.Option.SHOW_TOASTS.cast())
                .setName(TextComponent.translatable("impetus.options.show_toasts.name"))
                .setTooltip(TextComponent.translatable("impetus.options.show_toasts.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.notifications.showToasts = value, opts -> opts.notifications.showToasts)
                .build();
    }

    // Creates the switch of the warnings about mods and resource packs that clash with Impetus.
    public static OptionImpl<ImpetusGameOptions, Boolean> incompatiblePackWarnings(ImpetusGameOptions gameOpts) {
        Objects.requireNonNull(gameOpts, "gameOpts");
        return OptionImpl.createBuilder(boolean.class, gameOpts)
                .setId(StandardOptions.Option.INCOMPATIBLE_PACK_WARNINGS.cast())
                .setName(TextComponent.translatable("impetus.options.incompatible_pack_warnings.name"))
                .setTooltip(TextComponent.translatable("impetus.options.incompatible_pack_warnings.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((opts, value) -> opts.advanced.disableIncompatibleModWarnings = !value, opts -> !opts.advanced.disableIncompatibleModWarnings)
                .build();
    }
}
