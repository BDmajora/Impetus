package com.bdmajora.impetus.engine.impl.render.chunk.metrics;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

// Tracks the sections with the longest build times.
public class RenderSectionMetricsTracker {
    // Order by build time.
    public static final Comparator<RenderSection> BY_BUILD_TIME = Comparator.comparingLong(RenderSection::getLastBuildDurationNanos);
    // Number of sections tracked.
    private static final int MAX_TRACKED_SECTIONS = 5;
    // Slowest sections with the fastest of them first.
    private final PriorityQueue<RenderSection> slowestSections = new PriorityQueue<>(BY_BUILD_TIME);
    // Sections in the queue by identity.
    private final ReferenceOpenHashSet<RenderSection> sectionsInHeap = new ReferenceOpenHashSet<>();

    // Adds a section to the tracked sections.
    private void addSection(RenderSection section) {
        slowestSections.add(section);
        sectionsInHeap.add(section);
    }

    // Stops tracking a section.
    public void removeSection(RenderSection section) {
        if (sectionsInHeap.remove(section)) {
            slowestSections.remove(section);
        }
    }

    // Stores the build time of a section and tracks it when it is among the slowest.
    public void updateSectionBuildDuration(RenderSection section, long duration) {
        removeSection(section);

        section.setLastBuildDurationNanos(duration);

        if (slowestSections.size() < MAX_TRACKED_SECTIONS) {
            addSection(section);
        } else if (duration > slowestSections.peek().getLastBuildDurationNanos()) {
            sectionsInHeap.remove(slowestSections.poll());
            addSection(section);
        }
    }

    // Returns a read only view of the slowest sections.
    public Collection<RenderSection> getSlowestSections() {
        return Collections.unmodifiableCollection(this.slowestSections);
    }
}
