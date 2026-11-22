package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.bdmajora.testing.Quads.*;
import static org.junit.jupiter.api.Assertions.*;

// Casts rays through generated sections and checks that every pair of front faces a ray crosses draws far before near.
class TranslucentOrderOracleTest {
    // Depth gap below which two hits count as the same surface.
    private static final float SAME_DEPTH = 1e-3f;

    @Test
    void glassCubesDrawFarBeforeNear() {
        check(glass(new Random(1), 0.08f, new ArrayList<>()));
    }

    @Test
    void sparseGlassPillarsDrawFarBeforeNear() {
        check(glass(new Random(2), 0.25f, new ArrayList<>()));
    }

    @Test
    void riverWaterDrawsFarBeforeNear() {
        check(river(new Random(3), new ArrayList<>(), false));
    }

    @Test
    void flowingRiverWaterDrawsFarBeforeNear() {
        check(river(new Random(4), new ArrayList<>(), true));
    }

    @Test
    void waterUnderGlassDrawsFarBeforeNear() {
        List<float[][]> quads = river(new Random(5), new ArrayList<>(), true);
        check(glass(new Random(6), 0.04f, quads));
    }

    @Test
    void theUnsortedPassDrawsOverlappingGlassOutOfOrder() {
        // Improved transparency off draws each facing range in meshing order
        float[][][] quads = glass(new Random(1), 0.08f, new ArrayList<>()).toArray(new float[0][][]);
        assertTrue(violations(quads, unsortedOrder(quads)).count > 0);
        float[][][] mixed = glass(new Random(6), 0.04f, river(new Random(5), new ArrayList<>(), true)).toArray(new float[0][][]);
        assertTrue(violations(mixed, unsortedOrder(mixed)).count > 0);
    }

    // Sorts the quads for sampled cameras and fails on any pair a ray proves out of order.
    private static void check(List<float[][]> quadList) {
        float[][][] quads = quadList.toArray(new float[0][][]);
        SortState state = record(quads).getSortState();
        Result result = violations(quads, (x, y, z) -> TranslucentSorter.order(state, x, y, z));
        assertEquals(0, result.count, state.getClass().getSimpleName() + " " + quads.length + " quads, first: " + result.first);
    }

    // Draw order of the quads for a camera, null meaning mesh order.
    @FunctionalInterface
    private interface Order {
        int[] at(float x, float y, float z);
    }

    // Number of out of order pairs and the first one found.
    private record Result(int count, String first) {}

    // Returns the order the unsorted pass draws: facing ranges one after another in meshing order.
    private static Order unsortedOrder(float[][][] quads) {
        QuadSet set = set(quads);
        int[] order = new int[quads.length];
        int n = 0;
        for (int facing = 0; facing < com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing.COUNT; facing++) {
            for (int q = 0; q < quads.length; q++) {
                if (set.facing(q).ordinal() == facing) order[n++] = q;
            }
        }
        assertEquals(quads.length, n);
        return (x, y, z) -> order;
    }

    // Counts the pairs of front faces that one ray crosses where the nearer face draws first.
    private static Result violations(float[][][] quads, Order orders) {
        Random random = new Random(42);
        int violations = 0;
        String first = null;

        for (int camera = 0; camera < 48; camera++) {
            float cx = random.nextFloat() * 36 - 10, cy = random.nextFloat() * 36 - 10, cz = random.nextFloat() * 36 - 10;
            int[] order = orders.at(cx, cy, cz);
            int[] position = new int[quads.length];
            for (int i = 0; i < quads.length; i++) {
                position[order == null ? i : order[i]] = i;
            }
            if (order != null) {
                assertEquals(quads.length, order.length);
            }

            for (int ray = 0; ray < 600; ray++) {
                float dx = random.nextFloat() * 16 - cx, dy = random.nextFloat() * 16 - cy, dz = random.nextFloat() * 16 - cz;
                List<float[]> hits = new ArrayList<>();
                for (int q = 0; q < quads.length; q++) {
                    float t = hit(quads[q], cx, cy, cz, dx, dy, dz);
                    if (t > 0) hits.add(new float[]{t, q});
                }
                for (float[] a : hits) {
                    for (float[] b : hits) {
                        // a is farther and must come first
                        if (a[0] > b[0] + SAME_DEPTH && position[(int) a[1]] > position[(int) b[1]]) {
                            violations++;
                            if (first == null) {
                                first = String.format("camera (%.2f %.2f %.2f) quad %d at %.3f drawn after nearer quad %d at %.3f",
                                        cx, cy, cz, (int) a[1], a[0], (int) b[1], b[0]);
                            }
                        }
                    }
                }
            }
        }

        return new Result(violations, first);
    }

    // Returns the ray distance to the front side of a quad split like the index buffer or minus one.
    private static float hit(float[][] quad, float ox, float oy, float oz, float dx, float dy, float dz) {
        float a = triangle(quad[0], quad[1], quad[2], ox, oy, oz, dx, dy, dz);
        float b = triangle(quad[2], quad[3], quad[0], ox, oy, oz, dx, dy, dz);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    // Returns the ray distance to a counter clockwise triangle seen from its front or minus one.
    private static float triangle(float[] v0, float[] v1, float[] v2, float ox, float oy, float oz, float dx, float dy, float dz) {
        float e1x = v1[0] - v0[0], e1y = v1[1] - v0[1], e1z = v1[2] - v0[2];
        float e2x = v2[0] - v0[0], e2y = v2[1] - v0[1], e2z = v2[2] - v0[2];
        float px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        float det = e1x * px + e1y * py + e1z * pz;
        // Back faces are culled and edge-on faces cover nothing
        if (det < 1e-7f) return -1;
        float inv = 1 / det;
        float tx = ox - v0[0], ty = oy - v0[1], tz = oz - v0[2];
        float u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 1e-4f || u > 1 - 1e-4f) return -1;
        float qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        float v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < 1e-4f || u + v > 1 - 1e-4f) return -1;
        float t = (e2x * qx + e2y * qy + e2z * qz) * inv;
        return t > 1e-4f ? t : -1;
    }

    // Adds the faces of scattered glass cubes that touch no other cube.
    private static List<float[][]> glass(Random random, float density, List<float[][]> out) {
        boolean[][][] cells = new boolean[16][16][16];
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    cells[x][y][z] = random.nextFloat() < density;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (!cells[x][y][z]) continue;
                    if (!filled(cells, x + 1, y, z)) out.add(facingX(x + 1, y, y + 1, z, z + 1));
                    if (!filled(cells, x - 1, y, z)) out.add(flipped(facingX(x, y, y + 1, z, z + 1)));
                    if (!filled(cells, x, y + 1, z)) out.add(facingY(y + 1, x, x + 1, z, z + 1));
                    if (!filled(cells, x, y - 1, z)) out.add(flipped(facingY(y, x, x + 1, z, z + 1)));
                    if (!filled(cells, x, y, z + 1)) out.add(facingZ(z + 1, x, x + 1, y, y + 1));
                    if (!filled(cells, x, y, z - 1)) out.add(flipped(facingZ(z, x, x + 1, y, y + 1)));
                }
            }
        }
        return out;
    }

    private static boolean filled(boolean[][][] cells, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < 16 && y < 16 && z < 16 && cells[x][y][z];
    }

    // Adds a river over uneven ground the way the 1.12 fluid renderer meshes it.
    private static List<float[][]> river(Random random, List<float[][]> out, boolean flowing) {
        int[][] ground = new int[16][16];
        int[][] levels = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ground[x][z] = 4 + random.nextInt(8);
                // Flowing water steps down in places and leaves its side faces open
                levels[x][z] = flowing && random.nextInt(4) == 0 ? 8 : 9;
            }
        }
        // Corner heights shared by neighbouring cells like vanilla's averaged flow heights
        float[][] corner = new float[17][17];
        for (int x = 0; x <= 16; x++)
            for (int z = 0; z <= 16; z++)
                corner[x][z] = flowing ? 0.3f + random.nextFloat() * 0.589f : 0.8888889f;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int level = levels[x][z];
                for (int y = ground[x][z]; y < level; y++) {
                    boolean top = y == level - 1;
                    float h00 = top ? y + corner[x][z] : y + 1, h01 = top ? y + corner[x][z + 1] : y + 1;
                    float h11 = top ? y + corner[x + 1][z + 1] : y + 1, h10 = top ? y + corner[x + 1][z] : y + 1;
                    if (top) {
                        float[][] up = {{x, h00, z}, {x, h01, z + 1}, {x + 1, h11, z + 1}, {x + 1, h10, z}};
                        out.add(up);
                        out.add(flipped(up));
                    }
                    // Sides face air where the neighbour column has no water at this height
                    if (air(ground, levels, x + 1, z, y)) out.add(new float[][]{{x + 1, y, z}, {x + 1, h10, z}, {x + 1, h11, z + 1}, {x + 1, y, z + 1}});
                    if (air(ground, levels, x - 1, z, y)) out.add(new float[][]{{x, y, z + 1}, {x, h01, z + 1}, {x, h00, z}, {x, y, z}});
                    if (air(ground, levels, x, z + 1, y)) out.add(new float[][]{{x + 1, y, z + 1}, {x + 1, h11, z + 1}, {x, h01, z + 1}, {x, y, z + 1}});
                    if (air(ground, levels, x, z - 1, y)) out.add(new float[][]{{x, y, z}, {x, h00, z}, {x + 1, h10, z}, {x + 1, y, z}});
                }
            }
        }
        return out;
    }

    // Returns whether the cell beside a water block is air, treating the section border as open air.
    private static boolean air(int[][] ground, int[][] levels, int x, int z, int y) {
        if (x < 0 || z < 0 || x >= 16 || z >= 16) return true;
        return y >= ground[x][z] && y >= levels[x][z];
    }
}
