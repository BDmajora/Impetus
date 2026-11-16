package com.bdmajora.impetus.engine.impl.gui.options;

import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests null refusals of the shared option pages.
class CommonOptionPagesNullTest {
    // Rejects null options and a null graphics option.
    @Test
    void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> CommonOptionPages.sortingGroup(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.graphicsApi(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.performance(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.meshTerrainGroup(null, null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.meshTerrainGroup(ImpetusGameOptions.defaults(), null));
    }

    // Formats the ends and the middle of the keep distance range.
    @Test
    void keepDistanceEnds() {
        assertNotNull(CommonOptionPages.formatKeepDistance(0));
        assertNotNull(CommonOptionPages.formatKeepDistance(1));
        assertNotNull(CommonOptionPages.formatKeepDistance(Integer.MAX_VALUE));
        assertNotNull(CommonOptionPages.formatKeepDistance(-1));
    }
}
