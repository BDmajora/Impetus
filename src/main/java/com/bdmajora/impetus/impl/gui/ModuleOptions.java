package com.bdmajora.impetus.impl.gui;

import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.SliderControl;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

// One module's option-building vocabulary: its storage, id namespace and lang prefix, so every module page builds its tickboxes, sliders and cyclers through the same few calls instead of its own copy of the builder chain
public final class ModuleOptions<C> {
    private final String modId;
    private final String idPrefix;
    private final String lang;
    private final OptionStorage<C> storage;

    // Ids become <modId>:<idPrefix><path>; names and tooltips resolve from <lang><key>.name / .tooltip
    public ModuleOptions(String modId, String idPrefix, String lang, OptionStorage<C> storage) {
        this.modId = modId;
        this.idPrefix = idPrefix;
        this.lang = lang;
        this.storage = storage;
    }

    public OptionStorage<C> storage() {
        return this.storage;
    }

    public OptionIdentifier<Void> group(String path) {
        return OptionIdentifier.create(this.modId, this.idPrefix + path);
    }

    public <T> OptionIdentifier<T> option(String path, Class<T> type) {
        return OptionIdentifier.create(this.modId, this.idPrefix + path, type);
    }

    public TextComponent name(String key) {
        return TextComponent.translatable(this.lang + key + ".name");
    }

    public TextComponent tooltip(String key) {
        return TextComponent.translatable(this.lang + key + ".tooltip");
    }

    // The common prefix of every option: id, name, tooltip and binding, with the optional impact, flag and enable gate applied only when given; key is both the id path and the lang key under the module's prefix
    public <T> OptionImpl.Builder<C, T> builder(String key, Class<T> type,
                                                BiConsumer<C, T> setter, Function<C, T> getter,
                                                OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, this.lang + key, type, setter, getter, impact, flag, enabled);
    }

    // As above for a module whose lang keys do not follow the id path; langKey gets ".name"/".tooltip" appended
    public <T> OptionImpl.Builder<C, T> builder(String path, String langKey, Class<T> type,
                                                BiConsumer<C, T> setter, Function<C, T> getter,
                                                OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        OptionImpl.Builder<C, T> builder = OptionImpl.createBuilder(type, this.storage)
                .setId(option(path, type))
                .setName(TextComponent.translatable(langKey + ".name"))
                .setTooltip(TextComponent.translatable(langKey + ".tooltip"))
                .setBinding(setter, getter);
        if (impact != null) {
            builder.setImpact(impact);
        }
        if (flag != null) {
            builder.setFlags(flag);
        }
        if (enabled != null) {
            builder.setEnabledPredicate(enabled);
        }
        return builder;
    }

    public OptionImpl<C, Boolean> toggle(String key, BiConsumer<C, Boolean> setter, Function<C, Boolean> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return toggle(key, this.lang + key, setter, getter, impact, flag, enabled);
    }

    public OptionImpl<C, Boolean> toggle(String path, String langKey, BiConsumer<C, Boolean> setter, Function<C, Boolean> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(path, langKey, boolean.class, setter, getter, impact, flag, enabled)
                .setControl(TickBoxControl::new)
                .build();
    }

    public OptionImpl<C, Integer> slider(String key, int min, int max, int step, ControlValueFormatter formatter,
                                         BiConsumer<C, Integer> setter, Function<C, Integer> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, int.class, setter, getter, impact, flag, enabled)
                .setControl(opt -> new SliderControl(opt, min, max, step, formatter))
                .build();
    }

    public <T extends Enum<T> & Localized> OptionImpl<C, T> cycling(String key, Class<T> type, T[] values,
                                                                    BiConsumer<C, T> setter, Function<C, T> getter,
                                                                    BooleanSupplier enabled) {
        return cycling(key, type, values, setter, getter, null, null, enabled);
    }

    public <T extends Enum<T> & Localized> OptionImpl<C, T> cycling(String key, Class<T> type, T[] values,
                                                                    BiConsumer<C, T> setter, Function<C, T> getter,
                                                                    OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, type, setter, getter, impact, flag, enabled)
                .setControl(opt -> new CyclingControl<>(opt, values, localizedNames(values)))
                .build();
    }

    // Display names for an enum cycler, resolved through the lang keys
    public static TextComponent[] localizedNames(Localized[] values) {
        TextComponent[] names = new TextComponent[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = TextComponent.translatable(values[i].translationKey());
        }
        return names;
    }
}
