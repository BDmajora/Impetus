package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;

// Tree of cut planes that orders translucent quads for any camera position.
public final class PartitionTree implements SortState.Resortable {

    // Root of the tree.
    private final Node root;
    // Number of quads.
    private final int quadCount;
    // Cut planes of each axis.
    private final float[][] cutPlanes;

    // Creates a tree.
    private PartitionTree(Node root, int quadCount, float[][] cutPlanes) {
        this.root = root;
        this.quadCount = quadCount;
        this.cutPlanes = cutPlanes;
    }

    // Largest group that is ordered once for every camera.
    static final int STATIC_LEAF_QUADS = 256;

    // Builds a tree for the quads or returns null when a group has no free cut.
    public static @Nullable PartitionTree build(QuadSet quads) {
        return build(quads, STATIC_LEAF_QUADS);
    }

    // Builds a tree with a limit for static groups.
    static @Nullable PartitionTree build(QuadSet quads, int staticLeafQuads) {
        int[] all = new int[quads.count()];
        for (int i = 0; i < all.length; i++) all[i] = i;

        FloatArrayList[] cuts = { new FloatArrayList(), new FloatArrayList(), new FloatArrayList() };
        Node root = partition(quads, all, 2, cuts, staticLeafQuads);
        if (root == null) return null;

        float[][] cutPlanes = new float[3][];
        for (int a = 0; a < 3; a++) {
            cutPlanes[a] = sortAndDedup(cuts[a]);
        }
        return new PartitionTree(root, quads.count(), cutPlanes);
    }

    // Returns the sorted cut planes of every tree without duplicates.
    public static float[][] mergeCutPlanes(Collection<PartitionTree> trees) {
        FloatArrayList[] merged = { new FloatArrayList(), new FloatArrayList(), new FloatArrayList() };

        for (PartitionTree tree : trees) {
            for (int axis = 0; axis < 3; axis++) {
                for (float cut : tree.cutPlanes(axis)) {
                    merged[axis].add(cut);
                }
            }
        }

        float[][] result = new float[3][];
        for (int axis = 0; axis < 3; axis++) {
            result[axis] = sortAndDedup(merged[axis]);
        }
        return result;
    }

    // Returns the sorted values without duplicates.
    private static float[] sortAndDedup(FloatArrayList floats) {
        float[] array = floats.toFloatArray();
        FloatArrays.quickSort(array);

        int n = 0;
        for (int i = 0; i < array.length; i++) {
            if (n == 0 || array[i] != array[n - 1]) {
                array[n++] = array[i];
            }
        }
        return FloatArrays.copy(array, 0, n);
    }

    // Builds the node of a group of quads.
    private static Node partition(QuadSet quads, int[] group, int parentAxis, FloatArrayList[] collectedCuts, int staticLeafQuads) {
        if (group.length <= 1) {
            return new Leaf(group);
        }

        int[] parallel = parallelOrder(quads, group);
        if (parallel != null) {
            return new Leaf(parallel);
        }

        boolean triedStatic = group.length <= staticLeafQuads;
        if (triedStatic) {
            int[] topological = TranslucentSorter.topologicalOrder(quads, group);
            if (topological != null) {
                return new Leaf(topological);
            }
        }

        int axis = -1;
        float[] planes = FloatArrays.EMPTY_ARRAY;
        for (int i = 0; i < 3; i++) {
            axis = (parentAxis + 1 + i) % 3;
            planes = cutsOn(quads, group, axis);
            if (planes.length > 0) break;
        }
        if (planes.length == 0) {
            int[] topological = !triedStatic && group.length <= TranslucentSorter.MAX_TOPO_QUADS ? TranslucentSorter.topologicalOrder(quads, group) : null;
            if (topological != null) {
                return new Leaf(topological);
            }
            return partitionIntersectors(quads, group, collectedCuts, staticLeafQuads);
        }

        Buckets buckets = bucket(quads, group, axis, planes);

        float[] carrying = planesWithQuads(planes, buckets.onPlane);
        if (carrying.length > 0 && carrying.length < planes.length && allParallel(quads, buckets.slabs)) {
            planes = carrying;
            buckets = bucket(quads, group, axis, planes);
        }

        IntArrayList[] onPlaneLists = buckets.onPlane, slabLists = buckets.slabs;
        int slabCount = planes.length + 1;

        Node[] slabs = new Node[slabCount];
        for (int i = 0; i < slabCount; i++) {
            int[] slabGroup = slabLists[i].toIntArray();
            if (slabGroup.length == 0) {
                slabs[i] = EMPTY;
                continue;
            }
            Node child = partition(quads, slabGroup, axis, collectedCuts, staticLeafQuads);
            if (child == null) return null;
            slabs[i] = child;
        }

        int[][] onPlane = new int[planes.length][];
        for (int i = 0; i < planes.length; i++) {
            onPlane[i] = onPlaneLists[i].toIntArray();
        }

        for (float plane : planes) {
            collectedCuts[axis].add(plane);
        }
        return new Split(axis, planes, slabs, onPlane);
    }

    // Quads on each plane and in each slab.
    private record Buckets(IntArrayList[] onPlane, IntArrayList[] slabs) {}

    // Sorts a group into the quads on each plane and in each slab.
    private static Buckets bucket(QuadSet quads, int[] group, int axis, float[] planes) {
        IntArrayList[] onPlane = new IntArrayList[planes.length];
        for (int i = 0; i < planes.length; i++) onPlane[i] = new IntArrayList();
        IntArrayList[] slabs = new IntArrayList[planes.length + 1];
        for (int i = 0; i <= planes.length; i++) slabs[i] = new IntArrayList();

        for (int quad : group) {
            float lo = quads.boundsMin(quad, axis), hi = quads.boundsMax(quad, axis);

            int slab = 0;
            while (slab < planes.length && planes[slab] <= lo + QuadSet.OVERLAP_EPSILON) slab++;

            if (hi - lo <= QuadSet.EPSILON && slab > 0 && Math.abs(planes[slab - 1] - lo) <= QuadSet.EPSILON) {
                onPlane[slab - 1].add(quad);
            } else {
                slabs[slab].add(quad);
            }
        }
        return new Buckets(onPlane, slabs);
    }

    // Returns the planes that hold quads.
    private static float[] planesWithQuads(float[] planes, IntArrayList[] onPlane) {
        FloatArrayList kept = new FloatArrayList(planes.length);
        for (int i = 0; i < planes.length; i++) {
            if (!onPlane[i].isEmpty()) kept.add(planes[i]);
        }
        return kept.toFloatArray();
    }

    // Returns whether the quads of every slab are parallel.
    private static boolean allParallel(QuadSet quads, IntArrayList[] slabs) {
        int reference = -1;
        for (IntArrayList slab : slabs) {
            for (int i = 0; i < slab.size(); i++) {
                int quad = slab.getInt(i);
                if (reference < 0) {
                    reference = quad;
                } else if (!quads.hasParallelNormal(quad, reference, quads.normalDot(quad, reference))) {
                    return false;
                }
            }
        }
        return true;
    }

    // Builds a node for a group that has no free cut.
    private static @Nullable Node partitionIntersectors(QuadSet quads, int[] group, FloatArrayList[] collectedCuts, int staticLeafQuads) {
        int n = group.length;
        if (n > TranslucentSorter.MAX_TOPO_QUADS) return null;

        int[] degree = new int[n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (intersects(quads, group[i], group[j])) {
                    degree[i]++;
                    degree[j]++;
                }
            }
        }

        boolean[] primary = new boolean[n];
        int count = 0;
        while (true) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (degree[i] > 0 && (best < 0 || degree[i] > degree[best])) best = i;
            }
            if (best < 0) break;
            primary[best] = true;
            count++;
            degree[best] = 0;
            for (int j = 0; j < n; j++) {
                if (!primary[j] && intersects(quads, group[best], group[j])) degree[j]--;
            }
        }
        if (count == 0 || count == n) return null;

        int[] first = new int[count], rest = new int[n - count];
        for (int i = 0, f = 0, r = 0; i < n; i++) {
            if (primary[i]) first[f++] = group[i]; else rest[r++] = group[i];
        }
        Node a = partition(quads, first, 2, collectedCuts, staticLeafQuads);
        Node b = a == null ? null : partition(quads, rest, 2, collectedCuts, staticLeafQuads);
        return b == null ? null : new Fixed(a, b);
    }

    // Returns whether two quads intersect.
    private static boolean intersects(QuadSet quads, int p, int q) {
        for (int axis = 0; axis < 3; axis++) {
            if (quads.boundsMin(p, axis) >= quads.boundsMax(q, axis) - QuadSet.OVERLAP_EPSILON
                    || quads.boundsMin(q, axis) >= quads.boundsMax(p, axis) - QuadSet.OVERLAP_EPSILON) return false;
        }
        return true;
    }

    // Returns the order of a group of parallel quads or null.
    private static int @Nullable [] parallelOrder(QuadSet quads, int[] group) {
        int reference = group[0];
        float nx = quads.normalX(reference), ny = quads.normalY(reference), nz = quads.normalZ(reference);
        BitSet flipped = new BitSet(group.length);
        for (int i = 0; i < group.length; i++) {
            int quad = group[i];
            float dot = quads.normalDot(quad, reference);
            if (!quads.hasParallelNormal(quad, reference, dot)) return null;

            if (dot < 0) {
                flipped.set(i);
            }
        }

        return TranslucentSorter.sortNormalRelative(quads.centers(), group, nx, ny, nz, flipped);
    }

    // Returns the free cut planes of a group on an axis.
    private static float[] cutsOn(QuadSet quads, int[] group, int axis) {
        int n = group.length;

        float[] starts = new float[n], ends = new float[n], points = new float[n];
        int startCount = 0, endCount = 0, pointCount = 0;

        for (int quad : group) {
            float lo = quads.boundsMin(quad, axis), hi = quads.boundsMax(quad, axis);

            if (hi - lo <= QuadSet.EPSILON) {
                points[pointCount++] = lo;
            } else {
                starts[startCount++] = lo;
                ends[endCount++] = hi;
            }
        }

        FloatArrays.quickSort(starts, 0, startCount);
        FloatArrays.quickSort(ends, 0, endCount);
        FloatArrays.quickSort(points, 0, pointCount);

        FloatArrayList cuts = new FloatArrayList();

        int nextStart = 0, nextEnd = 0, nextPoint = 0;
        int opened = 0;
        int below = 0;
        int sinceCut = 0;

        while (nextEnd < endCount || nextPoint < pointCount) {
            float plane = nextPoint == pointCount ? ends[nextEnd]
                    : nextEnd == endCount ? points[nextPoint]
                    : Math.min(ends[nextEnd], points[nextPoint]);

            while (nextEnd < endCount && ends[nextEnd] <= plane + QuadSet.OVERLAP_EPSILON) {
                nextEnd++;
                below++;
                sinceCut++;
            }

            int onPlane = 0;

            while (nextPoint < pointCount && points[nextPoint] <= plane + QuadSet.EPSILON) {
                nextPoint++;
                onPlane++;
            }

            while (nextStart < startCount && starts[nextStart] < plane - QuadSet.OVERLAP_EPSILON) {
                nextStart++;
                opened++;
            }

            int above = n - below - onPlane;
            boolean free = opened == nextEnd;
            boolean useful = (below > 0 ? 1 : 0) + (onPlane > 0 ? 1 : 0) + (above > 0 ? 1 : 0) >= 2;
            boolean separatesSomethingNew = onPlane > 0 || sinceCut > 0;

            if (free && useful && separatesSomethingNew) {
                cuts.add(plane);
                sinceCut = 0;
            }

            below += onPlane;
            sinceCut += onPlane;
        }

        return cuts.toFloatArray();
    }

    // Returns the cut planes of an axis.
    public float[] cutPlanes(int axis) {
        return cutPlanes[axis];
    }

    // Receiver of the quads of the draw order.
    public interface Sink {
        void accept(int[] quads);
    }

    // Returns the draw order for a camera position.
    public int @Nullable [] order(float camX, float camY, float camZ) {
        var sink = new ArraySink(new int[quadCount]);
        order(camX, camY, camZ, sink);
        return sink.order;
    }

    // Streams the draw order for a camera position.
    public void order(float camX, float camY, float camZ, Sink sink) {
        write(root, camX, camY, camZ, sink);
    }

    // Writes the order of a node.
    private static void write(Node node, float camX, float camY, float camZ, Sink sink) {
        if (node instanceof Leaf leaf) {
            sink.accept(leaf.quads);
            return;
        }

        if (node instanceof Fixed fixed) {
            write(fixed.first, camX, camY, camZ, sink);
            write(fixed.rest, camX, camY, camZ, sink);
            return;
        }

        Split split = (Split) node;
        float camCoord = switch (split.axis) {
            case 0 -> camX;
            case 1 -> camY;
            case 2 -> camZ;
            default -> throw new AssertionError();
        };

        int slab = 0;
        while (slab < split.planes.length && split.planes[slab] <= camCoord) slab++;

        for (int i = 0; i < slab; i++) {
            write(split.slabs[i], camX, camY, camZ, sink);
            sink.accept(split.onPlane[i]);
        }

        for (int i = split.slabs.length - 1; i > slab; i--) {
            write(split.slabs[i], camX, camY, camZ, sink);
            sink.accept(split.onPlane[i - 1]);
        }

        write(split.slabs[slab], camX, camY, camZ, sink);
    }

    // Sink that fills an array.
    private static final class ArraySink implements Sink {
        // Target array.
        private final int[] order;
        // Next position.
        private int pos;

        // Creates a sink.
        private ArraySink(int[] order) {
            this.order = order;
        }

        // Appends quads to the array.
        @Override
        public void accept(int[] quads) {
            System.arraycopy(quads, 0, order, pos, quads.length);
            pos += quads.length;
        }
    }

    // Returns whether a camera move crosses a cut plane.
    public boolean crossesCutPlane(double lastX, double lastY, double lastZ, double camX, double camY, double camZ) {
        return crossesOnAxis(0, lastX, camX) || crossesOnAxis(1, lastY, camY) || crossesOnAxis(2, lastZ, camZ);
    }

    // Returns whether a move crosses a cut plane of an axis.
    private boolean crossesOnAxis(int axis, double from, double to) {
        double lo = Math.min(from, to), hi = Math.max(from, to);

        for (float cut : cutPlanes[axis]) {
            if (cut > lo && cut <= hi) {
                return true;
            }
        }

        return false;
    }

    // Returns the debug position.
    @Override
    public int debugIndex() {
        return 2;
    }

    // Returns the number of quads.
    @Override
    public int quadCount() {
        return quadCount;
    }

    // Node without quads.
    private static final Node EMPTY = new Leaf(IntArrays.EMPTY_ARRAY);

    // Node of the tree.
    sealed interface Node permits Leaf, Split, Fixed {}
    // Node with a fixed order.
    record Leaf(int[] quads) implements Node {}
    // Node that splits space by planes.
    record Split(int axis, float[] planes, Node[] slabs, int[][] onPlane) implements Node {}
    // Node that draws one node and then another.
    record Fixed(Node first, Node rest) implements Node {}
}
