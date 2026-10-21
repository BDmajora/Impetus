package com.bdmajora.impetus.umbra.gui.modern;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ActionButtonControl;
import com.bdmajora.impetus.api.options.control.CyclingControl;
import com.bdmajora.impetus.api.options.control.ReadOnlyStringControl;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;

import java.lang.ref.WeakReference;
import java.util.OptionalInt;

// Builds Umbra's page in Impetus' video options
public final class UmbraOptionPages {
    // Groups the page under the Umbra sidebar heading and picks its accent colour
    private static final String UMBRA_MOD_ID = "umbra";
    // Every option reads and writes its subsystem directly, so the storage object holds no state and exists only because OptionImpl requires one; save() is deliberately unimplemented
    private static final UmbraMenuState STATE = new UmbraMenuState();
    private static final OptionStorage<UmbraMenuState> STORAGE = () -> STATE;
    // Shadow distance label of the last pack it was made for, which starts empty so the first pack always makes one
    private static ShadowDistanceLabel shadowDistance = new ShadowDistanceLabel(new WeakReference<>(null), "Default");

    private UmbraOptionPages() {
    }

    // The whole Umbra tab, picker and pack-level settings on one page; the old two-page split earned nothing (TabFrame stacked them anyway and the second page's button opened the same screen), kept as two groups for the visual gap
    public static OptionPage shaderPacks(GuiScreen parent) {
        // The pack picker on its own, so it reads as the primary action rather than one row among the settings
        OptionGroup packs = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, "shader_packs"))
                .add(openAction(
                        "open_shader_packs",
                        TextComponent.translatable("options.umbra.shaderPackSelection"),
                        TextComponent.translatable("options.umbra.shaderPackSelection.tooltip"),
                        () -> Minecraft.getMinecraft().displayGuiScreen(new ShaderPackSelectScreen(parent)),
                        true))
                .build();

        // Everything that describes the loaded pack rather than which pack is loaded
        OptionGroup settings = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create(UMBRA_MOD_ID, "settings"))
                .add(OptionImpl.createBuilder(ColorSpaceConverter.ColorSpace.class, STORAGE)
                        .setId(OptionIdentifier.create(UMBRA_MOD_ID, "color_space", ColorSpaceConverter.ColorSpace.class))
                        .setName(TextComponent.translatable("options.umbra.colorSpace"))
                        .setTooltip(TextComponent.translatable("options.umbra.colorSpace.tooltip"))
                        // Display names supplied literally because the enum constant names are not what a user reads on a monitor spec sheet, and the array order must track the enum
                        .setControl(option -> new CyclingControl<>(option,
                                ColorSpaceConverter.ColorSpace.values(),
                                new TextComponent[] {
                                        TextComponent.literal("sRGB"),
                                        TextComponent.literal("DCI-P3"),
                                        TextComponent.literal("Display P3"),
                                        TextComponent.literal("Rec.2020"),
                                        TextComponent.literal("Adobe RGB") }))
                        // The converter is the source of truth, not STATE, so the getter reads back from it; the config save is best-effort and must not take the options screen down
                        .setBinding((state, value) -> {
                            ColorSpaceConverter.setColorSpace(value);
                            try {
                                Umbra.getConfig().save();
                            } catch (Exception ignored) {
                            }
                        }, state -> ColorSpaceConverter.getColorSpace())
                        .build())
                // Read-only: the loaded pack owns this number, so the row reports it; the no-op setter makes that explicit and setEnabled(false) greys it out
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
                OptionIdentifier.create(UMBRA_MOD_ID, "shader_packs"),
                TextComponent.translatable("options.umbra.shaderPackSelection"),
                ImmutableList.of(packs, settings));
    }

    // A row whose "control" is a button running an action; the boolean type parameter is a placeholder since OptionImpl needs a value type, reading constant false and writing nothing
    private static OptionImpl<UmbraMenuState, Boolean> openAction(String path, TextComponent name, TextComponent tooltip,
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

    // What the read-only shadow distance row shows: the pack's shadowDistance when declared, otherwise "Default", which also covers no pack loaded
    private static String shadowDistanceLabel() {
        ShaderPack pack = Umbra.getCurrentPack();
        if (pack == null) {
            return "Default";
        }

        // Made once per loaded pack, whose properties never change, so the row reads the same string every frame
        ShadowDistanceLabel label = shadowDistance;
        if (label.pack().get() != pack) {
            OptionalInt distance = pack.getProperties().getShadowDistance();
            label = shadowDistance = new ShadowDistanceLabel(new WeakReference<>(pack), distance.isPresent() ? distance.getAsInt() + " blocks" : "Default");
        }
        return label.text();
    }

    // A shadow distance label and the pack it was made for, held weakly so an unloaded pack is not kept alive by it
    private record ShadowDistanceLabel(WeakReference<ShaderPack> pack, String text) {
    }

    private static class UmbraMenuState {
        String colorSpace() {
            return "sRGB";
        }
    }
}
