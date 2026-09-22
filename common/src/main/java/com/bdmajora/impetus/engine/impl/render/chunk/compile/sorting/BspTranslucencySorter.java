package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import java.util.Arrays;

// Compact BSP ordering for translucent quads that deliberately does NOT split intersecting quads (terrain is mostly flat faces and centres pick a stable side); unusable normals fall back to centre-distance sorting
final class BspTranslucencySorter {
    private static final float PLANE_EPSILON = 1.0E-5f;
    private static final int MAX_BSP_QUADS = 2048;

    private BspTranslucencySorter() {
    }

    // Builds a BSP over the quad planes and walks it back-to-front from the camera
    static int[] sort(float[] centers, float[] normals, int quadCount, float cameraX, float cameraY, float cameraZ) {
        if (quadCount <= 0 || quadCount > MAX_BSP_QUADS || centers == null || normals == null
                || centers.length < quadCount * 3 || normals.length < quadCount * 3) {
            return null;
        }

        int[] left = new int[quadCount];
        int[] right = new int[quadCount];
        Arrays.fill(left, -1);
        Arrays.fill(right, -1);

        for (int quad = 0; quad < quadCount; quad++) {
            int normalOffset = quad * 3;
            float nx = normals[normalOffset];
            float ny = normals[normalOffset + 1];
            float nz = normals[normalOffset + 2];

            if (nx * nx + ny * ny + nz * nz < PLANE_EPSILON) {
                return null;
            }
        }

        for (int quad = 1; quad < quadCount; quad++) {
            insert(quad, centers, normals, left, right);
        }

        int[] order = new int[quadCount];
        int emitted = emit(centers, normals, left, right, cameraX, cameraY, cameraZ, order);

        return emitted == quadCount ? order : null;
    }

    // Walks down from the root (quad 0) and hangs the quad on the side of each plane its centre lies; iterative, since a run of coplanar quads degenerates the tree into a chain as deep as the quad count
    private static void insert(int quad, float[] centers, float[] normals, int[] left, int[] right) {
        int node = 0;
        while (true) {
            int[] side = signedDistanceToPlane(quad, node, centers, normals) < -PLANE_EPSILON ? left : right;
            int child = side[node];
            if (child == -1) {
                side[node] = quad;
                return;
            }
            node = child;
        }
    }

    // In-order walk emitting the far side of each plane before the node and the near side after, so the result is back-to-front from the camera; an explicit stack rather than recursion for the same reason insert is iterative. Returns how many quads were emitted
    private static int emit(float[] centers, float[] normals, int[] left, int[] right,
                            float cameraX, float cameraY, float cameraZ, int[] order) {
        int quadCount = order.length;
        // Each frame is a node whose far child has (state 1) or has not (state 0) been descended into yet
        int[] nodeStack = new int[quadCount];
        boolean[] farDone = new boolean[quadCount];
        int depth = 0;
        int cursor = 0;

        nodeStack[depth] = 0;
        farDone[depth] = false;
        depth++;

        while (depth > 0) {
            int node = nodeStack[depth - 1];
            boolean cameraInFront = signedDistanceToPlane(cameraX, cameraY, cameraZ, node, centers, normals) >= 0.0f;
            int far = cameraInFront ? left[node] : right[node];
            int near = cameraInFront ? right[node] : left[node];

            if (!farDone[depth - 1]) {
                farDone[depth - 1] = true;
                if (far != -1) {
                    nodeStack[depth] = far;
                    farDone[depth] = false;
                    depth++;
                    continue;
                }
            }

            // Far side finished: emit this node, then replace its frame with the near child so the stack never holds more than one path
            order[cursor++] = node;
            depth--;
            if (near != -1) {
                nodeStack[depth] = near;
                farDone[depth] = false;
                depth++;
            }
        }

        return cursor;
    }

    // Quad centre against another quad's plane
    private static float signedDistanceToPlane(int quad, int planeQuad, float[] centers, float[] normals) {
        int centerOffset = quad * 3;
        return signedDistanceToPlane(centers[centerOffset], centers[centerOffset + 1], centers[centerOffset + 2],
                planeQuad, centers, normals);
    }

    // Point against a quad's plane
    private static float signedDistanceToPlane(float x, float y, float z, int planeQuad, float[] centers, float[] normals) {
        int planeOffset = planeQuad * 3;

        float px = centers[planeOffset];
        float py = centers[planeOffset + 1];
        float pz = centers[planeOffset + 2];
        float nx = normals[planeOffset];
        float ny = normals[planeOffset + 1];
        float nz = normals[planeOffset + 2];

        return nx * (x - px) + ny * (y - py) + nz * (z - pz);
    }
}
