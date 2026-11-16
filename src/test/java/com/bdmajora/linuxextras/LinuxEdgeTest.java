package com.bdmajora.linuxextras;

import com.bdmajora.impetus.core.PropertiesConfig;
import com.bdmajora.impetus.impl.util.StringUtils;
import com.bdmajora.linuxextras.desktop.XdgOpen;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinuxEdgeTest {
    @Test
    void openRefusesNullBlankAndOptionLikeTargets() {
        assertFalse(XdgOpen.open(null));
        assertFalse(XdgOpen.open(""));
        assertFalse(XdgOpen.open("   "));
        assertFalse(XdgOpen.open("--help"));
        assertFalse(XdgOpen.open("-x"));
    }

    @Test
    void listFallbackMayBeNull() {
        PropertiesConfig config = new PropertiesConfig(LogManager.getLogger("test"), "none.cfg", "header");
        assertEquals(0, config.list("missing", null).length);
        assertEquals(2, config.list("missing", " a , b ,, ").length);
        assertEquals(7, config.integer("missing", 7, 0, 10));
        assertTrue(config.bool("missing", true));
    }

    @Test
    void stringDistanceHandlesEmptyAndEqualStrings() {
        assertEquals(0, StringUtils.levenshteinDistance("", ""));
        assertEquals(3, StringUtils.levenshteinDistance("", "abc"));
        assertEquals(3, StringUtils.levenshteinDistance("abc", ""));
        assertEquals(0, StringUtils.levenshteinDistance("same", "same"));
        assertEquals(1, StringUtils.levenshteinDistance("kitten", "sitten"));
        assertEquals(3, StringUtils.levenshteinDistance("kitten", "sitting"));
        assertThrows(NullPointerException.class, () -> StringUtils.levenshteinDistance(null, "a"));
        List<String> found = StringUtils.fuzzySearch(List.of("Fast Math", "Slow Walk", ""), "fast", 0, s -> s);
        assertEquals(List.of("Fast Math"), found);
        assertEquals(3, StringUtils.fuzzySearch(List.of("a", "b", "c"), "", 0, s -> s).size());
    }
}
