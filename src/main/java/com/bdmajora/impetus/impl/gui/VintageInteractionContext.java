package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;

// Interaction context of the 1.12 GUI.
public enum VintageInteractionContext implements InteractionContext {
    // The one instance.
    INSTANCE;

    // Returns whether a modifier key is held.
    @Override
    public boolean isSpecialKeyDown(SpecialKey key) {
        return switch (key) {
            case SHIFT -> GuiScreen.isShiftKeyDown();
            case CTRL -> GuiScreen.isCtrlKeyDown();
            case ALT -> GuiScreen.isAltKeyDown();
        };
    }

    // Plays the click sound.
    @Override
    public void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
