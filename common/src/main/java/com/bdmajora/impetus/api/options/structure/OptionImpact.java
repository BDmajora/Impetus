package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;

import java.util.EnumSet;

// Performance impact of an option shown as a coloured label in its tooltip
public enum OptionImpact implements TextProvider {
    LOW(TextFormattingStyle.GREEN, "impetus.option_impact.low"),
    MEDIUM(TextFormattingStyle.YELLOW, "impetus.option_impact.medium"),
    HIGH(TextFormattingStyle.GOLD, "impetus.option_impact.high"),
    VARIES(TextFormattingStyle.WHITE, "impetus.option_impact.varies");

    // Translated label text shared by every label of this impact
    private final TextComponent key;
    // Colour applied to the label
    private final TextFormattingStyle colour;

    // Stores the label colour and creates the translated label text
    OptionImpact(TextFormattingStyle colour, String key) {
        this.key = TextComponent.translatable(key);
        this.colour = colour;
    }

    // Returns a new coloured label for the tooltip on every call
    @Override
    public TextComponent getLocalizedName() {
        return new TextComponent.Styled(this.key, EnumSet.of(this.colour));
    }
}