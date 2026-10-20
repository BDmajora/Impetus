package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.Objects;

public interface ControlValueFormatter {
    // Auto at zero, else the number
    static ControlValueFormatter guiScale() {
        return (v) -> (v == 0) ? TextComponent.translatable("options.guiScale.auto") : TextComponent.literal(v + "x");
    }

    // Unlimited at the max, else the number
    static ControlValueFormatter fpsLimit() {
        return (v) -> (v == 260) ? TextComponent.translatable("options.framerateLimit.max") : TextComponent.translatable("options.framerate", v);
    }

    // Moody, Bright, or a percentage
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

    // Off at zero, else block count
    static ControlValueFormatter biomeBlend() {
        return (v) -> (v == 0) ? TextComponent.translatable("gui.none") : TextComponent.translatable("impetus.options.biome_blend.value", v);
    }

    TextComponent format(int value);

    // Lang key with the value as argument
    static ControlValueFormatter translateVariable(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        return (v) -> TextComponent.translatable(key, v);
    }

    // value%
    static ControlValueFormatter percentage() {
        return (v) -> TextComponent.literal(v + "%");
    }

    // valuex
    static ControlValueFormatter multiplier() {
        return (v) -> TextComponent.literal(v + "x");
    }

    // Disabled text at zero, else count with unit
    static ControlValueFormatter quantityOrDisabled(String name, String disableText) {
        Objects.requireNonNull(name, "Unit must not be null");
        Objects.requireNonNull(disableText, "Disabled text must not be null");
        return (v) -> TextComponent.literal(v == 0 ? disableText : v + " " + name);
    }

    // Plain number
    static ControlValueFormatter number() {
        return (v) -> TextComponent.literal(String.valueOf(v));
    }
}
