package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.coarctatio.dedup.StringPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Estimates the memory that Coarctatio saved.
public final class MemoryReport {

    // Estimated size of one string.
    private static final long STRING_BYTES = 24 + 16 + 24;

    // Count of compacted remapper entries.
    private static final AtomicLong REMAPPER_ENTRIES = new AtomicLong();

    // Estimated saving of one compacted remapper entry.
    private static final long REMAPPER_MAP_BYTES = 8192;

    // Prevents instantiation
    private MemoryReport() {
    }

    // Adds compacted remapper entries.
    public static void recordRemapperEntries(long entries) {
        REMAPPER_ENTRIES.addAndGet(entries);
    }

    // Returns the report as text lines.
    public static List<String> lines() {
        List<Line> entries = new ArrayList<>();

        entries.add(estimated("Resource names",
                (long) ResourceLocationCaches.DOMAINS.shared() + ResourceLocationCaches.PATHS.shared(),
                STRING_BYTES));
        entries.add(estimated("NBT keys", StringPool.NBT_KEYS.shared(), STRING_BYTES));
        entries.add(estimated("Remapper caches", REMAPPER_ENTRIES.get(), REMAPPER_MAP_BYTES));

        long total = 0;
        List<String> out = new ArrayList<>();
        out.add("Coarctatio memory saved");

        for (Line entry : entries) {
            if (entry.bytes <= 0) {
                continue;
            }
            total += entry.bytes;
            out.add(String.format("  %-22s %8s  estimated", entry.name, mib(entry.bytes)));
        }

        out.add(String.format("  %-22s %8s", "TOTAL", mib(total)));

        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        out.add(String.format("  %-22s %8s used of %s allocated, %s max",
                "Heap now", mib(used), mib(runtime.totalMemory()), mib(runtime.maxMemory())));

        return out;
    }

    // Returns the total estimated saving in bytes.
    public static long totalBytes() {
        return (long) (ResourceLocationCaches.DOMAINS.shared() + ResourceLocationCaches.PATHS.shared()
                + StringPool.NBT_KEYS.shared()) * STRING_BYTES
                + REMAPPER_ENTRIES.get() * REMAPPER_MAP_BYTES;
    }

    // Returns the size as kilobytes or megabytes.
    public static String mib(long bytes) {
        return bytes >= 1024L * 1024L
                ? String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                : String.format("%.0f KB", bytes / 1024.0);
    }

    // Returns a line estimated from a count.
    private static Line estimated(String name, long count, long each) {
        // Creates a line.
        return new Line(name, Math.max(0, count) * each);
    }

    // One line of the report.
    private static final class Line {
        // Label of the line.
        final String name;
        // Size of the line.
        final long bytes;

        Line(String name, long bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    // Returns the total saving as text.
    public static String summary() {
        return mib(totalBytes());
    }
}
