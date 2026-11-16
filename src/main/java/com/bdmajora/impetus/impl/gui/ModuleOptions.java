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

// Builder helpers for the options of one module that share an id prefix and a language prefix.
public final class ModuleOptions<C> {
    // Mod id of the options.
    private final String modId;
    // Prefix of every option path.
    private final String idPrefix;
    // Prefix of every language key.
    private final String lang;
    // Storage the options write to.
    private final OptionStorage<C> storage;

    // Creates helpers for a module.
    public ModuleOptions(String modId, String idPrefix, String lang, OptionStorage<C> storage) {
        this.modId = modId;
        this.idPrefix = idPrefix;
        this.lang = lang;
        this.storage = storage;
    }

    // Returns the storage of the module.
    public OptionStorage<C> storage() {
        return this.storage;
    }

    // Returns the id of a group.
    public OptionIdentifier<Void> group(String path) {
        return OptionIdentifier.create(this.modId, this.idPrefix + path);
    }

    // Returns the id of an option.
    public <T> OptionIdentifier<T> option(String path, Class<T> type) {
        return OptionIdentifier.create(this.modId, this.idPrefix + path, type);
    }

    // Returns the translated name of a key.
    public TextComponent name(String key) {
        return TextComponent.translatable(this.lang + key + ".name");
    }

    // Returns the translated tooltip of a key.
    public TextComponent tooltip(String key) {
        return TextComponent.translatable(this.lang + key + ".tooltip");
    }

    // Creates an option builder whose language key follows the path.
    public <T> OptionImpl.Builder<C, T> builder(String key, Class<T> type,
                                                BiConsumer<C, T> setter, Function<C, T> getter,
                                                OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, this.lang + key, type, setter, getter, impact, flag, enabled);
    }

    // Creates an option builder and applies the impact and flag and enabled rule when given.
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

    // Creates a tick box option whose language key follows the path.
    public OptionImpl<C, Boolean> toggle(String key, BiConsumer<C, Boolean> setter, Function<C, Boolean> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return toggle(key, this.lang + key, setter, getter, impact, flag, enabled);
    }

    // Creates a tick box option.
    public OptionImpl<C, Boolean> toggle(String path, String langKey, BiConsumer<C, Boolean> setter, Function<C, Boolean> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(path, langKey, boolean.class, setter, getter, impact, flag, enabled)
                .setControl(TickBoxControl::new)
                .build();
    }

    // Creates a slider option.
    public OptionImpl<C, Integer> slider(String key, int min, int max, int step, ControlValueFormatter formatter,
                                         BiConsumer<C, Integer> setter, Function<C, Integer> getter,
                                         OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, int.class, setter, getter, impact, flag, enabled)
                .setControl(opt -> new SliderControl(opt, min, max, step, formatter))
                .build();
    }

    // Creates a cycling option without an impact or flag.
    public <T extends Enum<T> & Localized> OptionImpl<C, T> cycling(String key, Class<T> type, T[] values,
                                                                    BiConsumer<C, T> setter, Function<C, T> getter,
                                                                    BooleanSupplier enabled) {
        return cycling(key, type, values, setter, getter, null, null, enabled);
    }

    // Creates a cycling option over the given constants.
    public <T extends Enum<T> & Localized> OptionImpl<C, T> cycling(String key, Class<T> type, T[] values,
                                                                    BiConsumer<C, T> setter, Function<C, T> getter,
                                                                    OptionImpact impact, OptionFlag flag, BooleanSupplier enabled) {
        return builder(key, type, setter, getter, impact, flag, enabled)
                .setControl(opt -> new CyclingControl<>(opt, values, localizedNames(values)))
                .build();
    }

    // Returns the translated name of each constant.
    public static TextComponent[] localizedNames(Localized[] values) {
        TextComponent[] names = new TextComponent[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = TextComponent.translatable(values[i].translationKey());
        }
        return names;
    }
}
