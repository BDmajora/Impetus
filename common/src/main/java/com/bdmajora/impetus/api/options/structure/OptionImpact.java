package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;

import java.util.EnumSet;

public enum OptionImpact implements TextProvider {
    LOW(TextFormattingStyle.GREEN, "impetus.option_impact.low"),
    MEDIUM(TextFormattingStyle.YELLOW, "impetus.option_impact.medium"),
    HIGH(TextFormattingStyle.GOLD, "impetus.option_impact.high"),
    VARIES(TextFormattingStyle.WHITE, "impetus.option_impact.varies");

    // Unchangeable, so every label shares it
    private final TextComponent key;
    private final TextFormattingStyle colour;

    OptionImpact(TextFormattingStyle colour, String key) {
        this.key = TextComponent.translatable(key);
        this.colour = colour;
    }

    // Coloured impact label for the tooltip, a fresh one each call since a styled component's style set can be changed by whoever holds it
    @Override
    public TextComponent getLocalizedName() {
        return new TextComponent.Styled(this.key, EnumSet.of(this.colour));
    }
}
