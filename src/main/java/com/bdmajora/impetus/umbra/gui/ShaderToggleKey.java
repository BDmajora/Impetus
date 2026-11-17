package com.bdmajora.impetus.umbra.gui;

import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

// Key that switches shaders off and back on with the pack used last.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class ShaderToggleKey {
    // Key that toggles shaders and is K by default.
    private static final KeyBinding TOGGLE = new KeyBinding("impetus.key.toggle_shaders", KeyConflictContext.IN_GAME, Keyboard.KEY_K, "impetus.key.category");

    // Prevents instantiation
    private ShaderToggleKey() {
    }

    // Registers the key.
    public static void register() {
        ClientRegistry.registerKeyBinding(TOGGLE);
    }

    // Toggles once per press.
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        while (TOGGLE.isPressed()) {
            toggle(Minecraft.getMinecraft());
        }
    }

    // Flips shaders and rebuilds the chunks when the state changed and reports the result above the hotbar.
    static Umbra.ToggleResult toggle(Minecraft client) {
        Umbra.ToggleResult result = Umbra.toggleShaders();
        if ((result == Umbra.ToggleResult.ENABLED || result == Umbra.ToggleResult.DISABLED) && client.world != null) {
            client.renderGlobal.loadRenderers();
        }
        if (client.ingameGUI != null) {
            client.ingameGUI.setOverlayMessage(message(result), false);
        }
        return result;
    }

    // Returns the action bar line of an outcome.
    static ITextComponent message(Umbra.ToggleResult result) {
        return switch (result) {
            case ENABLED -> new TextComponentTranslation("impetus.shaders.on", Umbra.getSelectedPackName());
            case DISABLED -> new TextComponentTranslation("impetus.shaders.off", Umbra.getLastPackName());
            case NO_PACK -> new TextComponentTranslation("impetus.shaders.none");
            case MISSING -> new TextComponentTranslation("impetus.shaders.missing", Umbra.getLastPackName());
            case FAILED -> new TextComponentTranslation("impetus.shaders.failed", Umbra.getLastPackName());
        };
    }
}
