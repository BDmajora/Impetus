package com.bdmajora.impetus.engine.impl.gui.frame.tab;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.theme.DefaultColors;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.engine.impl.gui.frame.AbstractFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.OptionPageFrame;
import com.bdmajora.impetus.engine.impl.gui.frame.ScrollableFrame;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Sidebar entry with the frame it shows.
@lombok.Builder(builderClassName = "Builder", setterPrefix = "set")
public record Tab<T extends AbstractFrame>(
        OptionIdentifier<Void> id,
        TextComponent title,
        Supplier<Boolean> onSelectFunction,
        Function<Dim2i, T> frameFunction,
        @Nullable OptionPage page,
        @Nullable Predicate<Option<?>> optionFilter,
        @Nullable AtomicReference<Integer> verticalScrollBarOffset
) {
    // Creates a builder.
    public static Tab.Builder<?> createBuilder() {
        return new Tab.Builder<>();
    }

    // Creates the content frame at the given size or returns null without a factory.
    public T createFrame(Dim2i dim) {
        return this.frameFunction != null ? this.frameFunction.apply(dim) : null;
    }

    // Creates a scrollable tab for one page.
    public static Tab<ScrollableFrame> from(OptionPage page, Predicate<Option<?>> optionFilter, AtomicReference<Integer> verticalScrollBarOffset) {
        Function<Dim2i, ScrollableFrame> frameFunction = dim2i -> ScrollableFrame
                .createBuilder()
                .setDimension(dim2i)
                .setFrame(OptionPageFrame
                        .createBuilder()
                        .setDimension(dim2i)
                        .setOptionPage(page)
                        .setOptionFilter(optionFilter)
                        .build())
                .setVerticalScrollBarOffset(verticalScrollBarOffset)
                .setScrollBarAccentColor(DefaultColors.getModAccentColor(page.getId().getModId()))
                .build();
        return Tab.<ScrollableFrame>builder()
                .setTitle(page.getName())
                .setId(page.getId())
                .setPage(page)
                .setOptionFilter(optionFilter)
                .setVerticalScrollBarOffset(verticalScrollBarOffset)
                .setFrameFunction(frameFunction)
                .build();
    }
}
