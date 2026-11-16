package com.bdmajora.impetus.engine.impl.render.chunk.compile.executor;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

// Tracks the run times of chunk jobs by output type.
public class ChunkJobMetricsTracker {
    // Length of the observation window in nanoseconds.
    public static final long OBSERVATION_COUNT_TIME = TimeUnit.SECONDS.toNanos(1);

    // Average and largest and smallest duration of a job type.
    public record MetricStats(long avg, long max, long min) {
        // Returns the text of the values formatted by a function.
        public String toString(LongFunction<String> observationStringifier) {
            return "avg = " + observationStringifier.apply(avg)
                    + ", max = " + observationStringifier.apply(max)
                    + ", min = " + observationStringifier.apply(min);
        }

        // Returns the text of the raw values.
        @Override
        public @NotNull String toString() {
            return toString(String::valueOf);
        }
    }

    // Durations of one job type.
    public static class MetricsData {
        // Number of durations kept.
        private static final int MAX_OBSERVATIONS = 10000;

        // Weight of a new duration in the moving average.
        private static final double EMA_ALPHA = 0.05;

        // Kept durations.
        private final LongArrayList observations = new LongArrayList(MAX_OBSERVATIONS);
        // Position that the next duration replaces.
        private int nextInsertPoint = 0;

        // Number of durations in the last window.
        private int observationsInLastTimeInterval;
        // Number of durations in the current window.
        private int observationsInCurrentTimeInterval;

        // Moving average of the durations.
        private double emaNanos;
        // Whether a duration was collected.
        private boolean hasEma;

        // Adds a duration and replaces the oldest one when full.
        public void collect(long observation) {
            if (observations.size() < MAX_OBSERVATIONS) {
                observations.add(observation);
            } else {
                observations.set(nextInsertPoint++, observation);
                if (nextInsertPoint >= MAX_OBSERVATIONS) {
                    nextInsertPoint = 0;
                }
            }
            observationsInCurrentTimeInterval++;

            if (this.hasEma) {
                this.emaNanos += EMA_ALPHA * (observation - this.emaNanos);
            } else {
                this.emaNanos = observation;
                this.hasEma = true;
            }
        }

        // Starts a new window.
        public void flipInterval() {
            this.observationsInLastTimeInterval = this.observationsInCurrentTimeInterval;
            this.observationsInCurrentTimeInterval = 0;
        }

        // Returns the number of durations in the last window.
        public int getObservationsInLastTimeInterval() {
            return this.observationsInLastTimeInterval;
        }

        // Returns the moving average in nanoseconds or the fallback when nothing was collected.
        public double getAverageNanos(double fallback) {
            return this.hasEma ? this.emaNanos : fallback;
        }

        // Returns the average and largest and smallest kept duration.
        public MetricStats getStats() {
            int count = observations.size();
            if (count == 0) {
                return new MetricStats(0, 0, 0);
            }
            long sum = 0;
            boolean overflow = false;
            long quotient = 0;
            long remainder = 0;
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                long observation = observations.getLong(i);
                try {
                    sum = Math.addExact(sum, observation);
                } catch (ArithmeticException e) {
                    overflow = true;
                }
                quotient += observation / count;
                remainder += observation % count;
                min = Math.min(min, observation);
                max = Math.max(max, observation);
            }
            return new MetricStats(overflow ? quotient + remainder / count : sum / count, max, min);
        }
    }

    // Durations by output type.
    private final Reference2ReferenceMap<Class<? extends ChunkTaskOutput>, MetricsData> metricsByTask = new Reference2ReferenceArrayMap<>(2);

    // Time of the last window start.
    private long lastTimeIntervalFlip = System.nanoTime();

    // Starts a new window when the current one is over.
    public void tick() {
        long time = System.nanoTime();
        if ((time - lastTimeIntervalFlip) >= OBSERVATION_COUNT_TIME) {
            for (var data : metricsByTask.values()) {
                data.flipInterval();
            }
            lastTimeIntervalFlip = time;
        }
    }

    // Records the duration of a job under its output type and ignores negative durations.
    public void collectMetrics(ChunkJobResult.Success<? extends ChunkTaskOutput> successfulResult) {
        java.util.Objects.requireNonNull(successfulResult, "successfulResult");
        if (successfulResult.executionTimeNanos() < 0) {
            return;
        }
        var data = metricsByTask.computeIfAbsent(successfulResult.output().getClass(), $ -> new MetricsData());
        data.collect(successfulResult.executionTimeNanos());
    }

    // Returns the recent average duration of an output type or the fallback.
    public double getAverageExecutionNanos(Class<? extends ChunkTaskOutput> outputType, double fallback) {
        var data = metricsByTask.get(outputType);
        return data != null ? data.getAverageNanos(fallback) : fallback;
    }

    // Returns a read only view of the durations by output type.
    public Reference2ReferenceMap<Class<? extends ChunkTaskOutput>, MetricsData> getMetrics() {
        return Reference2ReferenceMaps.unmodifiable(metricsByTask);
    }
}
