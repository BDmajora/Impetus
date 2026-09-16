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

// Turns Coarctatio's counters into megabytes; texture pixels and the class loader cache are MEASURED, the rest are ESTIMATES from counts times a per-object size, and the two are never conflated
public final class MemoryReport {
    // ---- object sizes, 64-bit JVM with compressed oops ----

    // String: 16 header + 4 value ref + 4 hash, plus its char[] at 16 header + 2 bytes per char, assuming a conservative 12-char average
    private static final long STRING_BYTES = 24 + 16 + 24;

    // int[28], one baked quad's vertex data: 16 header + 112 payload
    private static final long QUAD_ARRAY_BYTES = 128;

    // ItemCameraTransforms plus eight ItemTransformVec3f of three Vector3f each, rounded well down from ~600
    private static final long CAMERA_TRANSFORMS_BYTES = 400;

    // A flattened multipart predicate plus its two arrays
    private static final long PREDICATE_BYTES = 64;

    // The DIFFERENCE, not the size: Guava's four-entry RegularImmutableMap is ~240 bytes and CoarctatioPropertyMap ~72
    private static final long PROPERTY_MAP_SAVED_BYTES = 168;

    // Per state, the ImmutableTable that packing removes outright; deliberately low since multi-valued property blocks are worth several times this
    private static final long STATE_TABLE_BYTES = 200;

    // Exact totals filled in by the releasing code; atomic because sprite and class loader figures accumulate off the main thread
    private static final AtomicLong SPRITE_BYTES = new AtomicLong();
    private static final AtomicLong CLASS_LOADER_BYTES = new AtomicLong();
    private static final AtomicLong PACKED_STATE_COUNT = new AtomicLong();
    private static final AtomicLong REMAPPER_ENTRIES = new AtomicLong();

    // One merged remapper map for a class extending a vanilla one: ImmutableMap entries for a few hundred members, well under the ~20 KB a TileEntity subclass carries
    private static final long REMAPPER_MAP_BYTES = 8192;

    private MemoryReport() {
    }

    // Measured: pixel arrays summed before release
    public static void recordSpriteBytes(long bytes) {
        SPRITE_BYTES.addAndGet(bytes);
    }

    // Measured: cache entries summed before release
    public static void recordClassLoaderBytes(long bytes) {
        CLASS_LOADER_BYTES.addAndGet(bytes);
    }

    // Estimated: count of states whose table was replaced by a packed index
    public static void recordPackedStates(long states) {
        PACKED_STATE_COUNT.addAndGet(states);
    }

    // Estimated: remapper cache entries rebuilt through the sharing map at construction; entries made afterwards are not counted
    public static void recordRemapperEntries(long entries) {
        REMAPPER_ENTRIES.addAndGet(entries);
    }

    // One line per feature plus a total, shared by the log dump and /coarctatio; features that saved nothing are skipped to keep the report short
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

    // Same arithmetic as lines() collapsed to one number for F3; kept separate so the overlay does not build strings every frame
    public static long totalBytes() {
        return (long) (ResourceLocationCaches.DOMAINS.shared() + ResourceLocationCaches.PATHS.shared()
                + ModelCaches.VARIANTS.shared() + StringPool.NBT_KEYS.shared()) * STRING_BYTES
                + ModelCaches.QUADS.shared() * QUAD_ARRAY_BYTES
                + TransformCaches.TRANSFORMS.shared() * CAMERA_TRANSFORMS_BYTES
                + ConditionCanonicalizer.sharedCount() * PREDICATE_BYTES
                + PACKED_STATE_COUNT.get() * STATE_TABLE_BYTES
                + CompactPropertyMaps.compacted() * PROPERTY_MAP_SAVED_BYTES
                + SPRITE_BYTES.get()
                + CLASS_LOADER_BYTES.get();
    }

    // Formats a byte count for display, dropping to KB below a megabyte so a small saving does not read "0.0 MB"
    public static String mib(long bytes) {
        return bytes >= 1024L * 1024L
                ? String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                : String.format("%.0f KB", bytes / 1024.0);
    }

    // max(0, count) guards a never-opened pool whose counter reads negative or garbage; show nothing saved rather than subtract from the total
    private static Line estimated(String name, long count, long each) {
        return new Line(name, Math.max(0, count) * each, false);
    }

    // A line flagged as a measurement rather than an estimate
    private static Line measured(String name, long bytes) {
        return new Line(name, bytes, true);
    }

    // One report row; exact is what decides whether it prints as "measured" or "estimated"
    private static final class Line {
        final String name;
        final long bytes;
        final boolean exact;

        Line(String name, long bytes, boolean exact) {
            this.name = name;
            this.bytes = bytes;
            this.exact = exact;
        }
    }

    // The single formatted total the F3 overlay shows
    public static String summary() {
        return mib(totalBytes());
    }
}
