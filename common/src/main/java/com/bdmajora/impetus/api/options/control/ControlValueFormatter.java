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

    // Shows the unlimited label for zero or less and for 260 or more and the frame rate otherwise
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

    // Shows the translation for the key with the value as its argument and the singular form of the key at one and minus one when it is translated
    static ControlValueFormatter translateQuantity(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        // Key lists for singular and plural values with the plain key as the fallback
        List<String> one = List.of(key + ".one", key);
        List<String> other = List.of(key);
        return (v) -> TextComponent.translatable(v == 1 || v == -1 ? one : other, v);
    }

    // Shows the translation for the disabled key at zero and the quantity for the key otherwise
    static ControlValueFormatter translateQuantityOrDisabled(String key, String disabledKey) {
        ControlValueFormatter quantity = translateQuantity(key);
        Objects.requireNonNull(disabledKey, "Disabled key must not be null");
        // Shared label shown for every zero
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

    // Shows the disabled text at zero and the value with its unit otherwise as untranslated literal text
    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        Objects.requireNonNull(name, "Unit must not be null");
        Objects.requireNonNull(disableText, "Disabled text must not be null");
        // Omits the space before a blank unit and shares one label for every zero
        String suffix = name.isBlank() ? "" : " " + name;
        TextComponent disabled = TextComponent.literal(disableText);
        return (v) -> v == 0 ? disabled : TextComponent.literal(v + suffix);
    }

    // Shows the value as a plain number
    static ControlValueFormatter number() {
        return (v) -> TextComponent.literal(String.valueOf(v));
    }
}