package com.bdmajora.impetus.engine.impl.render.chunk;

import org.jetbrains.annotations.Nullable;

// Packs the state of a section into one long.
public final class PackedSectionMetadata {
    // Prevents instantiation
    private PackedSectionMetadata() {
    }

    // Bits of the visibility field.
    private static final int VISIBILITY_BITS = 46;
    // Mask of the visibility field.
    public static final long VISIBILITY_MASK = (1L << VISIBILITY_BITS) - 1;

    // Shift of the visual flags.
    private static final int VISUALS_FLAGS_SHIFT = 46;
    // Mask of the visual flags.
    private static final long VISUALS_FLAGS_MASK = 0b1111L;

    // Shift of the pending update.
    private static final int PENDING_UPDATE_SHIFT = 50;
    // Mask of the pending update.
    private static final long PENDING_UPDATE_MASK = 0b111L;

    // Bit of the build in flight flag.
    private static final int BUILD_IN_FLIGHT_BIT = 53;
    // Flag of a build in flight.
    private static final long BUILD_IN_FLIGHT_FLAG = 1L << BUILD_IN_FLIGHT_BIT;

    // Bit of the occluder data flag.
    private static final int HAS_OCCLUDER_DATA_BIT = 54;
    // Flag of a section with occluder data.
    private static final long HAS_OCCLUDER_DATA_FLAG = 1L << HAS_OCCLUDER_DATA_BIT;

    // Mask of the fields that change the graph search.
    public static final long GRAPH_INPUT_MASK = VISIBILITY_MASK | (VISUALS_FLAGS_MASK << VISUALS_FLAGS_SHIFT);

    // Returns the visibility field.
    public static long getVisibilityData(long packed) {
        return packed & VISIBILITY_MASK;
    }

    // Returns the metadata with a new visibility field.
    public static long withVisibilityData(long packed, long visibilityData) {
        return (packed & ~VISIBILITY_MASK) | (visibilityData & VISIBILITY_MASK);
    }

    // Returns the visual flags.
    public static int getVisualsFlags(long packed) {
        return (int) ((packed >>> VISUALS_FLAGS_SHIFT) & VISUALS_FLAGS_MASK);
    }

    // Returns the metadata with new visual flags.
    public static long withVisualsFlags(long packed, int flags) {
        return (packed & ~(VISUALS_FLAGS_MASK << VISUALS_FLAGS_SHIFT))
                | (((long) flags & VISUALS_FLAGS_MASK) << VISUALS_FLAGS_SHIFT);
    }

    // Returns the pending update or null.
    public static @Nullable ChunkUpdateType getPendingUpdate(long packed) {
        int encoded = (int) ((packed >>> PENDING_UPDATE_SHIFT) & PENDING_UPDATE_MASK);
        return encoded == 0 ? null : ChunkUpdateType.VALUES[encoded - 1];
    }

    // Returns the metadata with a new pending update.
    public static long withPendingUpdate(long packed, @Nullable ChunkUpdateType type) {
        long encoded = type == null ? 0L : (type.ordinal() + 1L);
        return (packed & ~(PENDING_UPDATE_MASK << PENDING_UPDATE_SHIFT))
                | (encoded << PENDING_UPDATE_SHIFT);
    }

    // Returns whether a build is in flight.
    public static boolean isBuildInFlight(long packed) {
        return (packed & BUILD_IN_FLIGHT_FLAG) != 0;
    }

    // Returns whether the section has occluder data.
    public static boolean hasOccluderData(long packed) {
        return (packed & HAS_OCCLUDER_DATA_FLAG) != 0;
    }

    // Returns the metadata with a new occluder data flag.
    public static long withHasOccluderData(long packed, boolean hasOccluderData) {
        return hasOccluderData ? (packed | HAS_OCCLUDER_DATA_FLAG) : (packed & ~HAS_OCCLUDER_DATA_FLAG);
    }

    // Shift of the compact metadata.
    private static final int COMPACT_META_SHIFT = VISUALS_FLAGS_SHIFT;
    // Fields of the compact metadata.
    private static final long COMPACT_META_FIELDS =
            (VISUALS_FLAGS_MASK << VISUALS_FLAGS_SHIFT)
                    | (PENDING_UPDATE_MASK << PENDING_UPDATE_SHIFT)
                    | BUILD_IN_FLIGHT_FLAG;
    // Mask of the compact metadata.
    private static final int COMPACT_META_MASK = (int) (COMPACT_META_FIELDS >>> COMPACT_META_SHIFT);
    // Shift of the pending update in the compact metadata.
    private static final int COMPACT_PENDING_SHIFT = PENDING_UPDATE_SHIFT - COMPACT_META_SHIFT;
    // Bit of the build flag in the compact metadata.
    private static final int COMPACT_BUILD_IN_FLIGHT_BIT = BUILD_IN_FLIGHT_BIT - COMPACT_META_SHIFT;

    // Returns the compact metadata.
    public static int toCompactMeta(long packed) {
        return (int) (packed >>> COMPACT_META_SHIFT) & COMPACT_META_MASK;
    }

    // Returns the visual flags of compact metadata.
    public static int getCompactVisualsFlags(int meta) {
        return meta & (int) VISUALS_FLAGS_MASK;
    }

    // Returns the pending update of compact metadata or null.
    public static @Nullable ChunkUpdateType getCompactPendingUpdate(int meta) {
        int encoded = (meta >>> COMPACT_PENDING_SHIFT) & (int) PENDING_UPDATE_MASK;
        return encoded == 0 ? null : ChunkUpdateType.VALUES[encoded - 1];
    }

    // Returns whether compact metadata has a build in flight.
    public static boolean isCompactBuildInFlight(int meta) {
        return (meta & (1 << COMPACT_BUILD_IN_FLIGHT_BIT)) != 0;
    }

    // Returns the metadata with a new build flag.
    public static long withBuildInFlight(long packed, boolean inFlight) {
        return inFlight ? (packed | BUILD_IN_FLIGHT_FLAG) : (packed & ~BUILD_IN_FLIGHT_FLAG);
    }
}
