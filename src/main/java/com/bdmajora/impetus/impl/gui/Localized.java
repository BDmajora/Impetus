package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.resources.I18n;

// Something with a lang key; lets the option pages build cycling controls generically over any module's enums
public interface Localized {
    String translationKey();

    // Resolves the translation key for display
    default String localizedName() {
        return I18n.format(this.translationKey());
    }
}
