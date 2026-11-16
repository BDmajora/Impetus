package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Objects;
import java.util.function.Consumer;

// Index of the planes that cut the order of translucent geometry.
public final class CutPlaneIndex<T> {
    // Largest movement in buckets that is searched by bucket.
    static final int MAX_BUCKET_SPAN = 64;

    // Cuts and buckets of one owner.
    private record Registration<T>(float[][] cuts, int[] origin, long[][] buckets) {
    }

    // Owners by bucket on each axis.
    @SuppressWarnings("unchecked")
    private final Long2ObjectOpenHashMap<ReferenceOpenHashSet<T>>[] bucketsByAxis = new Long2ObjectOpenHashMap[] {
            new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>()
    };

    // Registration of each owner.
    private final Reference2ObjectOpenHashMap<T, Registration<T>> registrations = new Reference2ObjectOpenHashMap<>();

    // Owners reported by the running query.
    private final ReferenceOpenHashSet<T> reported = new ReferenceOpenHashSet<>();

    // Number of registered planes.
    private int planeCount;

    // Registers the cuts of an owner and replaces its earlier cuts.
    public void put(T owner, float[][] cutsByAxis, int originX, int originY, int originZ) {
        Objects.requireNonNull(owner, "owner");
        if (cutsByAxis.length != 3) {
            throw new IllegalArgumentException("Cuts are needed for three axes");
        }
        for (float[] cuts : cutsByAxis) {
            Objects.requireNonNull(cuts, "cuts");
        }
        remove(owner);

        if (cutsByAxis[0].length == 0 && cutsByAxis[1].length == 0 && cutsByAxis[2].length == 0) {
            return;
        }

        this.planeCount += cutsByAxis[0].length + cutsByAxis[1].length + cutsByAxis[2].length;

        int[] origin = { originX, originY, originZ };
        long[][] buckets = new long[3][];

        for (int axis = 0; axis < 3; axis++) {
            LongArrayList keys = new LongArrayList();
            long lastKey = 0;
            boolean first = true;

            for (float cut : cutsByAxis[axis]) {
                long key = bucketKey(cut, origin[axis]);
                if (first || key != lastKey) {
                    keys.add(key);
                    lastKey = key;
                    first = false;
                }
            }

            buckets[axis] = keys.toLongArray();

            for (long key : buckets[axis]) {
                this.bucketsByAxis[axis].computeIfAbsent(key, k -> new ReferenceOpenHashSet<>()).add(owner);
            }
        }

        this.registrations.put(owner, new Registration<>(cutsByAxis, origin, buckets));
    }

    // Removes an owner.
    public void remove(T owner) {
        Registration<T> registration = this.registrations.remove(owner);

        if (registration == null) {
            return;
        }

        this.planeCount -= registration.cuts[0].length + registration.cuts[1].length + registration.cuts[2].length;

        for (int axis = 0; axis < 3; axis++) {
            var map = this.bucketsByAxis[axis];

            for (long key : registration.buckets[axis]) {
                ReferenceOpenHashSet<T> owners = map.get(key);
                if (owners == null) continue;

                owners.remove(owner);

                if (owners.isEmpty()) {
                    map.remove(key);
                }
            }
        }
    }

    // Reports every owner with a plane between two positions.
    public void query(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, Consumer<T> out) {
        Objects.requireNonNull(out, "out");
        if (this.registrations.isEmpty()) {
            return;
        }

        try {
            queryAxis(0, fromX, toX, out);
            queryAxis(1, fromY, toY, out);
            queryAxis(2, fromZ, toZ, out);
        } finally {
            this.reported.clear();
        }
    }

    // Reports the owners with a plane between two positions on one axis.
    private void queryAxis(int axis, double from, double to, Consumer<T> out) {
        double lo = Math.min(from, to), hi = Math.max(from, to);

        if (!(lo < hi)) return;

        long loBucket = (long) Math.floor(lo);
        long hiBucket = (long) Math.floor(hi);

        if (!(hi - lo <= MAX_BUCKET_SPAN)) {
            var iterator = this.registrations.reference2ObjectEntrySet().fastIterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                considerCandidate(entry.getKey(), entry.getValue(), axis, lo, hi, out);
            }
            return;
        }

        var map = this.bucketsByAxis[axis];

        for (long bucket = loBucket; bucket <= hiBucket; bucket++) {
            ReferenceOpenHashSet<T> candidates = map.get(bucket);

            if (candidates == null) {
                continue;
            }

            for (T candidate : candidates) {
                considerCandidate(candidate, this.registrations.get(candidate), axis, lo, hi, out);
            }
        }
    }

    // Reports an owner when one of its planes lies between two positions.
    private void considerCandidate(T owner, Registration<T> registration, int axis, double lo, double hi, Consumer<T> out) {
        if (registration == null || this.reported.contains(owner)) {
            return;
        }

        double loLocal = lo - registration.origin[axis];
        double hiLocal = hi - registration.origin[axis];

        for (float cut : registration.cuts[axis]) {
            if (cut > loLocal && cut <= hiLocal) {
                this.reported.add(owner);
                out.accept(owner);
                return;
            }
        }
    }

    // Returns the bucket of a local coordinate.
    private static long bucketKey(float localCoord, int origin) {
        return (long) Math.floor((double) localCoord + (double) origin);
    }

    // Returns the number of owners.
    public int size() {
        return this.registrations.size();
    }

    // Returns the number of planes.
    public int planeCount() {
        return this.planeCount;
    }
}
