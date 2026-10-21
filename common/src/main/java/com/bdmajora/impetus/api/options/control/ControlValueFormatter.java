package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.List;
import java.util.Objects;

// Turns a control's integer value into the text shown for it
public interface ControlValueFormatter {
    // Shows the auto label at zero and the scale as a multiplier otherwise
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? TextComponent.translatable("options.guiScale.auto") : TextComponent.literal(v + "x");
    }

    // Shows the unlimited label for every limit the game does not apply, as vanilla limits only below 260 and loads options.txt unclamped and both LWJGL2's and lwjgly's sync skip zero or less, and the frame rate otherwise
    static ControlValueFormatter fpsLimit() {
        return (v) -> (v >= 260 || v <= 0) ? TextComponent.translatable("options.framerateLimit.max") : TextComponent.translatable("options.framerate", v);
    }

    // Shows the minimum label at zero and the maximum label at 100 and a percentage otherwise
    static ControlValueFormatter brightness() {
        return (v) -> {
            if (v == 0) {
                return TextComponent.translatable("options.gamma.min");
            } else if (v == 100) {
                return TextComponent.translatable("options.gamma.max");
            } else {
                return TextComponent.literal(v + "%");
            }
        };
    }

    // Shows the none label at zero and the blend radius in blocks otherwise
    static ControlValueFormatter biomeBlend() {
        return translateQuantityOrDisabled("impetus.options.biome_blend.value", "gui.none");
    }

    // Returns the text shown for the value
    TextComponent format(int value);

    // Shows the translation for the key with the value as its argument
    static ControlValueFormatter translateVariable(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        return (v) -> TextComponent.translatable(key, v);
    }

    // Shows the translation for the key with the value as its argument, or the key's ".one" form at one and minus one, where English drops the plural, when that form is translated
    static ControlValueFormatter translateQuantity(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        // Built once here; the draw context shows the first key in a list that has a translation, so a language without the ".one" form falls back to the plain one
        List<String> one = List.of(key + ".one", key);
        List<String> other = List.of(key);
        return (v) -> TextComponent.translatable(v == 1 || v == -1 ? one : other, v);
    }

    // Shows the translation for the disabled key at zero and the quantity for the key otherwise
    static ControlValueFormatter translateQuantityOrDisabled(String key, String disabledKey) {
        ControlValueFormatter quantity = translateQuantity(key);
        Objects.requireNonNull(disabledKey, "Disabled key must not be null");
        // Built once here, so every zero shares one immutable label
        TextComponent disabled = TextComponent.translatable(disabledKey);
        return (v) -> v == 0 ? disabled : quantity.format(v);
    }

    // Shows the value followed by a percent sign
    static ControlValueFormatter percentage() {
        return (v) -> TextComponent.literal(v + "%");
    }

    // Shows the value followed by a multiplication sign
    static ControlValueFormatter multiplier() {
        return (v) -> TextComponent.literal(v + "x");
    }

    // Shows the disabled text at zero and the value with its unit otherwise, as literal text that neither translates nor pluralises, so units that are words belong in translateQuantityOrDisabled
    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        Objects.requireNonNull(name, "Unit must not be null");
        Objects.requireNonNull(disableText, "Disabled text must not be null");
        // Built once here, a blank unit giving the value alone so no trailing space shifts the right-aligned text, and every zero sharing one immutable label
        String suffix = name.isBlank() ? "" : " " + name;
        TextComponent disabled = TextComponent.literal(disableText);
        return (v) -> v == 0 ? disabled : TextComponent.literal(v + suffix);
    }

    // Shows the value as a plain number
    static ControlValueFormatter number() {
        return (v) -> TextComponent.literal(String.valueOf(v));
    }
}