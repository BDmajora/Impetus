package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.resources.I18n;

// Contract of the localized.
public interface Localized {
    // Returns the translation key of the entry.
    String translationKey();

    // Returns the translated name.
    default String localizedName() {
        return I18n.format(this.translationKey());
    }
}
