package com.bdmajora.impetus.impl.gui;

import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import net.minecraft.util.text.translation.LanguageMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ImpactLabelsLangTest {
    // Impetus's English lang file through the game's own parser
    private static Map<String, String> english() throws IOException {
        try (InputStream in = ImpactLabelsLangTest.class.getResourceAsStream("/assets/impetus/lang/en_US.lang")) {
            assertNotNull(in, "en_US.lang is not on the test classpath");
            return LanguageMap.parseLangFile(in);
        }
    }

    // The lang key inside an impact's coloured label
    private static String keyOf(OptionImpact impact) {
        TextComponent inner = ((TextComponent.Styled) impact.getLocalizedName()).inner();
        return ((TextComponent.Translatable) inner).keys().getFirst();
    }

    @Test
    void everyImpactReadsAsItsWordInTheTooltipLine() throws IOException {
        Map<String, String> lang = english();
        Map<OptionImpact, String> words = Map.of(OptionImpact.LOW, "Low", OptionImpact.MEDIUM, "Medium", OptionImpact.HIGH, "High", OptionImpact.VARIES, "Varies");
        String line = lang.get("impetus.options.performance_impact_string");
        assertNotNull(line);
        for (OptionImpact impact : OptionImpact.values()) {
            assertEquals(words.get(impact), lang.get(keyOf(impact)), impact.name());
            assertEquals("Performance Impact: " + words.get(impact), String.format(line, lang.get(keyOf(impact))));
        }
    }

    @Test
    void noImpactKeyIsLeftWithoutAnImpact() throws IOException {
        Set<String> keys = Arrays.stream(OptionImpact.values()).map(ImpactLabelsLangTest::keyOf).collect(Collectors.toSet());
        Set<String> translated = english().keySet().stream().filter(key -> key.startsWith("impetus.option_impact.")).collect(Collectors.toSet());
        assertEquals(keys, translated);
    }
}
