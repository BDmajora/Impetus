package com.bdmajora.impetus.impl.gui;

import net.minecraft.util.text.translation.LanguageMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CountLabelsLangTest {
    // Reads Impetus's English lang file through the game's parser.
    private static Map<String, String> english() throws IOException {
        try (InputStream in = CountLabelsLangTest.class.getResourceAsStream("/assets/impetus/lang/en_US.lang")) {
            assertNotNull(in, "en_US.lang is not on the test classpath");
            return LanguageMap.parseLangFile(in);
        }
    }

    // The key's translation filled in with the value
    private static String read(Map<String, String> lang, String key, int value) {
        String pattern = lang.get(key);
        assertNotNull(pattern, key + " is missing");
        return String.format(pattern, value);
    }

    @Test
    void everyCountLabelReadsSingularAtOneAndPluralOtherwise() throws IOException {
        Map<String, String> lang = english();
        // Keys and their singular and plural text.
        String[][] counts = {
                {"impetus.options.biome_blend.value", "1 block", "2 blocks"},
                {"impetus.options.chunk_update_threads.value", "1 thread", "2 threads"}};

        for (String[] count : counts) {
            assertEquals(count[1], read(lang, count[0] + ".one", 1));
            assertEquals(count[2], read(lang, count[0], 2));
            // Checks zero reads as plural.
            assertEquals("0 " + count[2].substring(2), read(lang, count[0], 0));
        }
    }

    @Test
    void everyCountsZeroLabelIsTranslated() throws IOException {
        Map<String, String> lang = english();
        assertEquals("Default", lang.get("impetus.options.chunk_update_threads.default"));
    }

    @Test
    void noLabelHedgesItsPluralWithBracketedS() throws IOException {
        // A count takes a ".one" form for its singular instead
        english().forEach((key, value) -> assertFalse(value.contains("(s)"), key + "=" + value));
    }
}
