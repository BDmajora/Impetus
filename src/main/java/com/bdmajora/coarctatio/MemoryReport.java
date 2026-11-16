package com.bdmajora.coarctatio;

import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.coarctatio.dedup.ResourceLocationCaches;
import com.bdmajora.coarctatio.dedup.StringPool;
import com.bdmajora.coarctatio.dedup.TransformCaches;
import com.bdmajora.coarctatio.state.CompactPropertyMaps;
import com.bdmajora.coarctatio.state.ConditionCanonicalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Estimates the memory that Coarctatio saved.
public final class MemoryReport {

    // Estimated size of one string.
    private static final long STRING_BYTES = 24 + 16 + 24;

    // Estimated size of one quad array.
    private static final long QUAD_ARRAY_BYTES = 128;

    // Estimated size of one camera transform set.
    private static final long CAMERA_TRANSFORMS_BYTES = 400;

    // Estimated size of one predicate.
    private static final long PREDICATE_BYTES = 64;

    // Estimated saving of one compact property map.
    private static final long PROPERTY_MAP_SAVED_BYTES = 168;

    // Estimated saving of one packed state table row.
    private static final long STATE_TABLE_BYTES = 200;

    // Measured bytes of released texture data.
    private static final AtomicLong SPRITE_BYTES = new AtomicLong();
    // Measured bytes of weakened class loader entries.
    private static final AtomicLong CLASS_LOADER_BYTES = new AtomicLong();
    // Count of packed states.
    private static final AtomicLong PACKED_STATE_COUNT = new AtomicLong();
    // Count of compacted remapper entries.
    private static final AtomicLong REMAPPER_ENTRIES = new AtomicLong();

    // Estimated saving of one compacted remapper entry.
    private static final long REMAPPER_MAP_BYTES = 8192;

    // Prevents instantiation
    private MemoryReport() {
    }

    // Adds released texture bytes.
    public static void recordSpriteBytes(long bytes) {
        SPRITE_BYTES.addAndGet(bytes);
    }

    // Adds weakened class loader bytes.
    public static void recordClassLoaderBytes(long bytes) {
        CLASS_LOADER_BYTES.addAndGet(bytes);
    }

    // Adds packed states.
    public static void recordPackedStates(long states) {
        PACKED_STATE_COUNT.addAndGet(states);
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
        entries.add(estimated("Model variants", ModelCaches.VARIANTS.shared(), STRING_BYTES));
        entries.add(estimated("NBT keys", StringPool.NBT_KEYS.shared(), STRING_BYTES));
        entries.add(estimated("Quad vertex data", ModelCaches.QUADS.shared(), QUAD_ARRAY_BYTES));
        entries.add(estimated("Model transforms", TransformCaches.TRANSFORMS.shared(), CAMERA_TRANSFORMS_BYTES));
        entries.add(estimated("Multipart predicates", ConditionCanonicalizer.sharedCount(), PREDICATE_BYTES));
        entries.add(estimated("Block state tables", PACKED_STATE_COUNT.get(), STATE_TABLE_BYTES));
        entries.add(estimated("State property maps", CompactPropertyMaps.compacted(), PROPERTY_MAP_SAVED_BYTES));
        entries.add(measured("Texture pixel data", SPRITE_BYTES.get()));
        entries.add(measured("Class loader cache", CLASS_LOADER_BYTES.get()));
        entries.add(estimated("Remapper caches", REMAPPER_ENTRIES.get(), REMAPPER_MAP_BYTES));

        long total = 0;
        List<String> out = new ArrayList<>();
        out.add("Coarctatio memory saved");

        for (Line entry : entries) {
            if (entry.bytes <= 0) {
                continue;
            }
            total += entry.bytes;
            out.add(String.format("  %-22s %8s  %s", entry.name, mib(entry.bytes), entry.exact ? "measured" : "estimated"));
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
                + ModelCaches.VARIANTS.shared() + StringPool.NBT_KEYS.shared()) * STRING_BYTES
                + ModelCaches.QUADS.shared() * QUAD_ARRAY_BYTES
                + TransformCaches.TRANSFORMS.shared() * CAMERA_TRANSFORMS_BYTES
                + ConditionCanonicalizer.sharedCount() * PREDICATE_BYTES
                + PACKED_STATE_COUNT.get() * STATE_TABLE_BYTES
                + CompactPropertyMaps.compacted() * PROPERTY_MAP_SAVED_BYTES
                + SPRITE_BYTES.get()
                + CLASS_LOADER_BYTES.get()
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
        return new Line(name, Math.max(0, count) * each, false);
    }

    // Returns a line with a measured size.
    private static Line measured(String name, long bytes) {
        return new Line(name, bytes, true);
    }

    // One line of the report.
    private static final class Line {
        // Label of the line.
        final String name;
        // Size of the line.
        final long bytes;
        // Whether the size was measured.
        final boolean exact;

        Line(String name, long bytes, boolean exact) {
            this.name = name;
            this.bytes = bytes;
            this.exact = exact;
        }
    }

    // Returns the total saving as text.
    public static String summary() {
        return mib(totalBytes());
    }
}
