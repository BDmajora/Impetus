package com.bdmajora.impetus.engine.impl.gui.options;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests null refusals of the shared option builders.
class CommonOptionPagesNullTest {
    // Rejects null options in every builder.
    @Test
    void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> CommonOptionPages.graphicsApi(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.chunkUpdateThreads(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.deferChunkUpdates(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.entityCulling(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.animateVisibleTextures(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.inactivityFpsLimit(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.showToasts(null));
        assertThrows(NullPointerException.class, () -> CommonOptionPages.incompatiblePackWarnings(null));
    }

    // The holder of static builders cannot be created from outside.
    @Test
    void theConstructorIsPrivate() throws Exception {
        java.lang.reflect.Constructor<CommonOptionPages> constructor = CommonOptionPages.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
