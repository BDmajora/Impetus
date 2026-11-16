package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.util.sorting.MergeSort;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

// Chooses and computes the draw order of translucent quads.
public final class TranslucentSorter {
    // Distance of the imaginary camera along the shared normal.
    static final int FAKE_STATIC_CAMERA_OFFSET = 1000;

    // Dot product from which two normals count as parallel.
    private static final float PARALLEL_THRESHOLD = 0.98f;

    // Prevents instantiation
    private TranslucentSorter() {
    }

    // Returns the quads ordered from the farthest to the nearest.
    public static int[] sortByDistance(float[] centers, int quadCount, float camX, float camY, float camZ) {
        float[] distances = new float[quadCount];

        for (int quadIdx = 0; quadIdx < quadCount; quadIdx++) {
            int centerIdx = quadIdx * 3;

            float qX = centers[centerIdx] - camX;
            float qY = centers[centerIdx + 1] - camY;
            float qZ = centers[centerIdx + 2] - camZ;

            distances[quadIdx] = qX * qX + qY * qY + qZ * qZ;
        }

        return MergeSort.mergeSort(distances);
    }

    // Returns the quads ordered along a shared normal.
    public static int[] sortNormalRelative(float[] centers, int quadCount, float nx, float ny, float nz, BitSet flipped) {
        float camX = centers[0] + nx * FAKE_STATIC_CAMERA_OFFSET;
        float camY = centers[1] + ny * FAKE_STATIC_CAMERA_OFFSET;
        float camZ = centers[2] + nz * FAKE_STATIC_CAMERA_OFFSET;

        float[] distances = new float[quadCount];

        for (int quadIdx = 0; quadIdx < quadCount; quadIdx++) {
            int centerIdx = quadIdx * 3;

            float qX = centers[centerIdx] - camX;
            float qY = centers[centerIdx + 1] - camY;
            float qZ = centers[centerIdx + 2] - camZ;

            distances[quadIdx] = (nx * qX + ny * qY + nz * qZ) * (flipped.get(quadIdx) ? 1 : -1);
        }

        return MergeSort.mergeSort(distances);
    }

    // Returns the draw order of a state for a camera position or null.
    public static int @Nullable [] order(SortState state, float camX, float camY, float camZ) {
        if (state instanceof SortState.Static staticState) {
            return staticState.order();
        }

        if (state instanceof SortState.Dynamic dynamic) {
            return sortByDistance(dynamic.centers(), dynamic.quadCount(), camX, camY, camZ);
        }

        if (state instanceof PartitionTree tree) {
            return tree.order(camX, camY, camZ);
        }

        return null;
    }

    // Returns the cheapest sort state that orders the quads correctly.
    public static SortState analyze(QuadSet quads) {
        int quadCount = quads.count();

        if (quadCount == 0) {
            return SortState.NONE;
        }

        int reference = firstNonDegenerateQuad(quads);

        if (reference < 0) {
            return SortState.NONE;
        }

        if (allCoplanarWith(quads, reference)) {
            return SortState.NONE;
        }

        if (allOutwardOnBoundingBox(quads)) {
            return SortState.NONE;
        }

        float nx = quads.normalX(reference), ny = quads.normalY(reference), nz = quads.normalZ(reference);

        BitSet flipped = new BitSet(quadCount);
        boolean allParallel = true;

        for (int quadIdx = 0; quadIdx < quadCount; quadIdx++) {
            if (quads.isDegenerate(quadIdx)) continue;

            float dot = quads.normalDot(quadIdx, reference);

            if (Math.abs(dot) < PARALLEL_THRESHOLD) {
                allParallel = false;
                break;
            }

            if (dot < 0) {
                flipped.set(quadIdx);
            }
        }

        if (allParallel) {
            return new SortState.Static(sortNormalRelative(quads.centers(), quadCount, nx, ny, nz, flipped));
        }

        if (quadCount <= MAX_TOPO_QUADS) {
            int[] order = topologicalOrder(quads);

            if (order != null) {
                return new SortState.Static(order);
            }
        }

        PartitionTree tree = PartitionTree.build(quads);
        if (tree != null) return tree;

        return new SortState.Dynamic(quads.centers());
    }

    // Returns the first quad with a normal or minus one.
    private static int firstNonDegenerateQuad(QuadSet quads) {
        float[] normals = quads.normals();

        for (int quadIdx = 0; quadIdx < quads.count(); quadIdx++) {
            int normalOffset = quadIdx * 3;

            if (normals[normalOffset] != 0 || normals[normalOffset + 1] != 0 || normals[normalOffset + 2] != 0) {
                return quadIdx;
            }
        }

        return -1;
    }

    // Returns whether every quad lies in the plane of a quad.
    private static boolean allCoplanarWith(QuadSet quads, int reference) {
        for (int quadIdx = 0; quadIdx < quads.count(); quadIdx++) {
            if (!quads.isCoplanar(reference, quadIdx)) {
                return false;
            }
        }

        return true;
    }

    // Returns whether every quad lies on the bounding box and faces outward.
    private static boolean allOutwardOnBoundingBox(QuadSet quads) {
        QuadSet.Bounds box = quads.union();

        for (int quadIdx = 0; quadIdx < quads.count(); quadIdx++) {
            ModelQuadFacing facing = quads.facing(quadIdx);

            if (!facing.isDirection()) {
                return false;
            }

            float nx = facing.getStepX(), ny = facing.getStepY(), nz = facing.getStepZ();

            float quadMin = quads.minDot(quadIdx, nx, ny, nz);
            float quadMax = quads.maxDot(quadIdx, nx, ny, nz);

            if (Math.abs(quadMax - quadMin) > QuadSet.EPSILON) {
                return false;
            }

            if (Math.abs(quadMax - box.maxDot(nx, ny, nz)) > QuadSet.EPSILON) {
                return false;
            }
        }

        return true;
    }

    // Largest group that is ordered by dependencies.
    public static final int MAX_TOPO_QUADS = 2048;

    // Returns an order that does not depend on the camera or null when none exists.
    public static int @Nullable [] topologicalOrder(QuadSet quads) {
        int[] all = new int[quads.count()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return topologicalOrder(quads, all);
    }

    // Returns an order of a group that does not depend on the camera or null.
    static int @Nullable [] topologicalOrder(QuadSet quads, int[] group) {
        int n = group.length;

        byte[] state = new byte[n];
        int[] stack = new int[n], next = new int[n];
        int[] out = new int[n];
        int finished = 0;

        for (int root = n - 1; root >= 0; root--) {
            if (state[root] != 0) continue;

            int depth = 0;
            stack[0] = root;
            next[0] = n - 1;
            state[root] = 1;

            while (depth >= 0) {
                int current = stack[depth];
                int candidate = next[depth];

                while (candidate >= 0 && (state[candidate] == 2 || candidate == current
                        || !quads.isSeenThrough(group[current], group[candidate]))) {
                    candidate--;
                }

                if (candidate < 0) {
                    state[current] = 2;
                    out[n - 1 - finished++] = group[current];
                    depth--;
                    continue;
                }

                if (state[candidate] == 1) {
                    return null;
                }

                next[depth] = candidate - 1;
                depth++;
                stack[depth] = candidate;
                next[depth] = n - 1;
                state[candidate] = 1;
            }
        }

        return out;
    }
}
