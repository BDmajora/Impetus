package com.bdmajora.impetus.engine.impl.render.chunk.metrics;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderSectionMetricsTrackerTest {
    @Test
    void keepsOnlyTheSlowestSections() {
        RenderSectionMetricsTracker tracker = new RenderSectionMetricsTracker();
        RenderSection[] sections = new RenderSection[8];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new RenderSection(null, i, 0, 0);
            tracker.updateSectionBuildDuration(sections[i], (i + 1) * 10L);
        }
        assertEquals(5, tracker.getSlowestSections().size());
        assertFalse(tracker.getSlowestSections().contains(sections[0]));
        assertTrue(tracker.getSlowestSections().contains(sections[7]));
        tracker.updateSectionBuildDuration(sections[0], 1L);
        assertFalse(tracker.getSlowestSections().contains(sections[0]));
        tracker.updateSectionBuildDuration(sections[7], 5L);
        assertTrue(tracker.getSlowestSections().contains(sections[7]));
        tracker.removeSection(sections[7]);
        tracker.removeSection(sections[7]);
        assertEquals(4, tracker.getSlowestSections().size());
        assertThrows(UnsupportedOperationException.class, () -> tracker.getSlowestSections().clear());
        assertTrue(RenderSectionMetricsTracker.BY_BUILD_TIME.compare(sections[1], sections[2]) < 0);
    }
}
