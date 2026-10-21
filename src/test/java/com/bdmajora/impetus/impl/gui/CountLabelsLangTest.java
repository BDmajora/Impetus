package com.bdmajora.impetus.impl.gui;

import net.minecraft.util.text.translation.LanguageMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CountLabelsLangTest {
    // Impetus's English lang file through the game's own parser, which also turns %d and %f into %s as the game does
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
        // Key, then what one and two of it read as
        String[][] counts = {
                {"impetus.options.biome_blend.value", "1 block", "2 blocks"},
                {"impetus.options.cpu_render_ahead_limit.value", "1 frame", "2 frames"},
                {"impetus.options.chunk_update_threads.value", "1 thread", "2 threads"},
                {"impetus.options.mesh_terrain.keep_distance.value", "1 chunk", "2 chunks"},
                {"impetus.options.extras.render.fog_distance.value", "1 chunk", "2 chunks"},
                {"impetus.options.extras.render.item_frame_lod.value", "1 block", "2 blocks"}};
        for (String[] count : counts) {
            assertEquals(count[1], read(lang, count[0] + ".one", 1));
            assertEquals(count[2], read(lang, count[0], 2));
            // Zero is plural in English, for the counts that show it rather than a disabled label
            assertEquals("0 " + count[2].substring(2), read(lang, count[0], 0));
        }
    }

    @Test
    void everyCountsZeroLabelIsTranslated() throws IOException {
        Map<String, String> lang = english();
        assertEquals("Default", lang.get("impetus.options.chunk_update_threads.default"));
        assertEquals("Default", lang.get("impetus.options.extras.render.fog_distance.default"));
        assertEquals("Off", lang.get("impetus.options.extras.render.item_frame_lod.off"));
    }

    @Test
    void noLabelHedgesItsPluralWithBracketedS() throws IOException {
        // A count takes a ".one" form for its singular instead
        english().forEach((key, value) -> assertFalse(value.contains("(s)"), key + "=" + value));
    }
}
