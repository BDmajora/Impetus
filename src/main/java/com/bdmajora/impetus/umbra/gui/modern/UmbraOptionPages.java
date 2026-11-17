package com.bdmajora.impetus.umbra.gui.modern;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ActionButtonControl;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.ReadOnlyStringControl;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.StandardOptions;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;

import java.lang.ref.WeakReference;
import java.util.OptionalInt;

// Builder of the Umbra pages of the video options screen.
public final class UmbraOptionPages {
    // Mod id of the pages.
    private static final String UMBRA_MOD_ID = "umbra";
    // Menu state behind the pages.
    private static final UmbraMenuState STATE = new UmbraMenuState();
    // Storage of the menu state.
    private static final OptionStorage<UmbraMenuState> STORAGE = () -> STATE;
    // Cached shadow distance label.
    private static ShadowDistanceLabel shadowDistance = new ShadowDistanceLabel(new WeakReference<>(null), "Default");

    // Prevents instantiation
    private UmbraOptionPages() {
    }

    // Returns the shader pack page.
    public static OptionPage shaderPacks(GuiScreen parent) {
        OptionGroup packs = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, "shader_packs"))
                .add(shadersSwitch())
                .add(openAction(
                        "open_shader_packs",
                        TextComponent.translatable("options.umbra.shaderPackSelection"),
                        TextComponent.translatable("options.umbra.shaderPackSelection.tooltip"),
                        () -> Minecraft.getMinecraft().displayGuiScreen(new ShaderPackSelectScreen(parent)),
                        true))
                .build();

        OptionGroup settings = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, "settings"))
                .add(OptionImpl.createBuilder(ColorSpaceConverter.ColorSpace.class, STORAGE)
                        .setId(OptionIdentifier.create(UMBRA_MOD_ID, "color_space", ColorSpaceConverter.ColorSpace.class))
                        .setName(TextComponent.translatable("options.umbra.colorSpace"))
                        .setTooltip(TextComponent.translatable("options.umbra.colorSpace.tooltip"))
                        .setControl(option -> new CyclingControl<>(option,
                                ColorSpaceConverter.ColorSpace.values(),
                                new TextComponent[] {
                                        TextComponent.literal("sRGB"),
                                        TextComponent.literal("DCI-P3"),
                                        TextComponent.literal("Display P3"),
                                        TextComponent.literal("Rec.2020"),
                                        TextComponent.literal("Adobe RGB") }))
                        .setBinding((state, value) -> {
                            ColorSpaceConverter.setColorSpace(value);
                            try {
                                Umbra.getConfig().save();
                            } catch (Exception ignored) {
                            }
                        }, state -> ColorSpaceConverter.getColorSpace())
                        .build())
                .add(OptionImpl.createBuilder(String.class, STORAGE)
                        .setId(OptionIdentifier.create(UMBRA_MOD_ID, "max_shadow_distance", String.class))
                        .setName(TextComponent.translatable("options.umbra.maxShadowDistance"))
                        .setTooltip(TextComponent.translatable("options.umbra.maxShadowDistance.tooltip"))
                        .setControl(ReadOnlyStringControl::new)
                        .setBinding((state, value) -> { }, state -> shadowDistanceLabel())
                        .setEnabled(false)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build();

        return new OptionPage(
                StandardOptions.Pages.SHADERS,
                TextComponent.translatable("options.umbra.shaderPackSelection"),
                ImmutableList.of(packs, settings));
    }

    // Returns the shaders on and off switch.
    private static OptionImpl<UmbraMenuState, Boolean> shadersSwitch() {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, "shaders_enabled", boolean.class))
                .setName(TextComponent.translatable("options.umbra.shaders"))
                .setTooltip(TextComponent.translatable("options.umbra.shaders.tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((state, value) -> Umbra.storeShadersEnabled(value), state -> Umbra.areShadersEnabled())
                .setEnabledPredicate(() -> Umbra.areShadersEnabled() || Umbra.canEnableShaders())
                .setFlags(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD)
                .build();
    }

    // Returns a button option that runs an action.
    private static OptionImpl<UmbraMenuState, Boolean> openAction(String path, TextComponent name, TextComponent tooltip,
                                                                // Action of the button.
                                                                Runnable action, boolean enabled) {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, path, boolean.class))
                .setName(name)
                .setTooltip(tooltip)
                .setControl(option -> new ActionButtonControl(option, action))
                .setBinding((state, value) -> { }, state -> false)
                .setEnabled(enabled)
                .build();
    }

    // Returns the label of the pack shadow distance.
    private static String shadowDistanceLabel() {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return "Default";
        }

        ShadowDistanceLabel label = shadowDistance;
        if (label.pack().get() != pack) {
            OptionalInt distance = pack.getProperties().getShadowDistance();
            label = shadowDistance = new ShadowDistanceLabel(new WeakReference<>(pack), distance.isPresent() ? distance.getAsInt() + " blocks" : "Default");
        }
        return label.text();
    }

    // Data of the shadow distance label.
    private record ShadowDistanceLabel(WeakReference<ShaderPack> pack, String text) {
    }

    // Values shown on the Umbra pages.
    private static class UmbraMenuState {
        // Returns the name of the color space.
        String colorSpace() {
            return "sRGB";
        }
    }
}
