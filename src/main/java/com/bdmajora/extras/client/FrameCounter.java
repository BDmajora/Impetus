package com.bdmajora.extras.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;

// Measures the frame rate over a sliding window.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class FrameCounter {
    // Length of the sliding window.
    private static final long WINDOW_NS = 5_000_000_000L;
    // Time between recalculations.
    private static final long CACHE_INTERVAL_NS = 500_000_000L;

    // Time of each sample.
    private static long[] sampleTimes = new long[512];
    // Length of each frame.
    private static long[] sampleDeltas = new long[512];
    // Position of the oldest sample.
    private static int sampleHead;
    // Number of samples.
    private static int sampleCount;

    // Time of the previous frame.
    private static long lastFrameTime;
    // Time of the last recalculation.
    private static long lastCacheTime;

    // Average frame rate.
    private static int cachedAverageFps;
    // Frame rate of the slowest one percent.
    private static int cachedOnePercentLowFps;
    // Frame rate of the slowest tenth of a percent.
    private static int cachedPointOnePercentLowFps;

    // Prevents instantiation
    private FrameCounter() {
    }

    // Records the frame and drops old samples.
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        long now = System.nanoTime();

        if (lastFrameTime != 0) {
            long delta = now - lastFrameTime;
            if (delta > 0) {
                addSample(now, delta);
            }
        }
        lastFrameTime = now;

        while (sampleCount > 0 && now - sampleTimes[sampleHead] > WINDOW_NS) {
            sampleHead = (sampleHead + 1) % sampleTimes.length;
            sampleCount--;
        }

        if (now - lastCacheTime >= CACHE_INTERVAL_NS) {
            lastCacheTime = now;
            recalculate();
        }
    }

    // Returns the average frame rate.
    public static int getAverageFps() {
        return cachedAverageFps;
    }

    // Returns the frame rate of the slowest one percent.
    public static int getOnePercentLowFps() {
        return cachedOnePercentLowFps;
    }

    // Returns the frame rate of the slowest tenth of a percent.
    public static int getPointOnePercentLowFps() {
        return cachedPointOnePercentLowFps;
    }

    // Recomputes the frame rates.
    private static void recalculate() {
        int size = sampleCount;
        if (size == 0) {
            cachedAverageFps = 0;
            cachedOnePercentLowFps = 0;
            cachedPointOnePercentLowFps = 0;
            return;
        }

        long[] deltas = new long[size];
        long total = 0;
        for (int i = 0; i < size; i++) {
            long delta = sampleDeltas[(sampleHead + i) % sampleDeltas.length];
            deltas[i] = delta;
            total += delta;
        }

        Arrays.sort(deltas);

        double averageDelta = (double) total / size;
        cachedAverageFps = averageDelta > 0 ? (int) (1_000_000_000.0 / averageDelta) : 0;
        cachedOnePercentLowFps = percentileLow(deltas, 1.0);
        cachedPointOnePercentLowFps = percentileLow(deltas, 0.1);
    }

    // Stores a sample and grows the arrays when full.
    private static void addSample(long time, long delta) {
        if (sampleCount == sampleTimes.length) {
            int capacity = sampleTimes.length * 2;
            long[] times = new long[capacity];
            long[] newDeltas = new long[capacity];

            for (int i = 0; i < sampleCount; i++) {
                int source = (sampleHead + i) % sampleTimes.length;
                times[i] = sampleTimes[source];
                newDeltas[i] = sampleDeltas[source];
            }

            sampleTimes = times;
            sampleDeltas = newDeltas;
            sampleHead = 0;
        }

        int tail = (sampleHead + sampleCount) % sampleTimes.length;
        sampleTimes[tail] = time;
        sampleDeltas[tail] = delta;
        sampleCount++;
    }

    // Returns the frame rate of the slowest share of frames.
    private static int percentileLow(long[] ascendingDeltas, double percent) {
        int count = (int) Math.ceil(ascendingDeltas.length * (percent / 100.0));
        if (count == 0) {
            count = 1;
        }

        long sum = 0;
        for (int i = ascendingDeltas.length - count; i < ascendingDeltas.length; i++) {
            sum += ascendingDeltas[i];
        }

        double averageDelta = (double) sum / count;
        return averageDelta > 0 ? (int) (1_000_000_000.0 / averageDelta) : 0;
    }
}
