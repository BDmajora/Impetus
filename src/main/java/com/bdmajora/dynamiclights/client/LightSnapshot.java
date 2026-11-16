package com.bdmajora.dynamiclights.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;

// Lights frozen at one update and indexed by the sections they can reach, read by every thread without locking.
final class LightSnapshot {
    // Snapshot with no lights.
    static final LightSnapshot EMPTY = new LightSnapshot(new DynamicLightSource[0], new double[0], 0,
            new Long2ObjectOpenHashMap<>());

    // Doubles stored per light: x, y, z and luminance.
    private static final int STRIDE = 4;

    // Every tracked source, lit or not, so updates can walk them without the engine's monitor.
    final DynamicLightSource[] sources;
    // Captured position and luminance of each lit source, STRIDE doubles per light.
    private final double[] lights;
    // Number of lit sources in lights.
    private final int count;
    // Section key to the offsets into lights of every light that reaches a block of that section.
    private final Long2ObjectOpenHashMap<int[]> sections;

    // Creates a snapshot from already captured arrays.
    private LightSnapshot(DynamicLightSource[] sources, double[] lights, int count,
                          Long2ObjectOpenHashMap<int[]> sections) {
        this.sources = sources;
        this.lights = lights;
        this.count = count;
        this.sections = sections;
    }

    // Captures the position and luminance of every source and indexes them by section.
    static LightSnapshot capture(Collection<DynamicLightSource> tracked) {
        if (tracked.isEmpty()) {
            return EMPTY;
        }

        DynamicLightSource[] sources = tracked.toArray(new DynamicLightSource[0]);
        double[] lights = new double[sources.length * STRIDE];
        Long2ObjectOpenHashMap<int[]> sections = new Long2ObjectOpenHashMap<>();
        long[] reached = new long[DynamicLightsEngine.MAX_REACHED_SECTIONS];
        int count = 0;

        for (DynamicLightSource source : sources) {
            int luminance = source.impetus$getLuminance();
            // A dark source adds nothing, matching maxDynamicLightLevel
            if (luminance <= 0) {
                continue;
            }

            double x = source.impetus$getDynamicLightX();
            double y = source.impetus$getDynamicLightY();
            double z = source.impetus$getDynamicLightZ();
            int offset = count++ * STRIDE;
            lights[offset] = x;
            lights[offset + 1] = y;
            lights[offset + 2] = z;
            lights[offset + 3] = luminance;

            // Full fade radius, so a lookup returns exactly the level the unindexed scan did
            int sectionCount = DynamicLightsEngine.reachedSections(x, y, z, DynamicLightsEngine.LOOKUP_RADIUS, 0, reached);
            for (int i = 0; i < sectionCount; i++) {
                int[] offsets = sections.get(reached[i]);
                sections.put(reached[i], append(offsets, offset));
            }
        }

        return new LightSnapshot(sources, lights, count, sections);
    }

    // Returns the array with the value added at the end, or a new one-element array for null.
    private static int[] append(int[] offsets, int value) {
        if (offsets == null) {
            return new int[] {value};
        }
        int[] grown = new int[offsets.length + 1];
        System.arraycopy(offsets, 0, grown, 0, offsets.length);
        grown[offsets.length] = value;
        return grown;
    }

    // Returns the strongest light level at the block, capped at fifteen.
    double levelAt(int x, int y, int z) {
        if (this.count == 0) {
            return 0.0D;
        }

        int[] offsets = this.sections.get(DynamicLightsEngine.packSection(x >> 4, y >> 4, z >> 4));
        if (offsets == null) {
            return 0.0D;
        }

        double[] lights = this.lights;
        double result = 0.0D;
        for (int offset : offsets) {
            result = DynamicLightsEngine.lightLevel(x, y, z, lights[offset], lights[offset + 1], lights[offset + 2],
                    (int) lights[offset + 3], result);
        }
        return Math.min(result, 15.0D);
    }

    // Returns the number of lit sources captured.
    int litCount() {
        return this.count;
    }
}
