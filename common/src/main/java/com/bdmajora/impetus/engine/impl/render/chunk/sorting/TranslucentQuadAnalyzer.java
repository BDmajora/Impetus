package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.trigger.NormalPlanes;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.util.QuadUtil;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.BitSet;

public class TranslucentQuadAnalyzer {
    // X/Y/Z for each quad center
    private static final int EXPECTED_QUADS = 1000;
    // Cap on distinct quantized normals tracked; real terrain uses the six axis directions plus a few fluid slopes, and pathological modded geometry past this falls back to the coarse always-resort heuristic
    private static final int MAX_TRACKED_NORMALS = 16;
    private final FloatArrayList quadCenters = new FloatArrayList(EXPECTED_QUADS * 3);
    private final FloatArrayList quadNormals = new FloatArrayList(EXPECTED_QUADS * 3);
    private final Vector3f[] vertexPositions = new Vector3f[4];
    private final Vector3f currentNormal = new Vector3f();
    private final Vector3f globalNormal = new Vector3f();
    private final BitSet normalSigns = new BitSet(EXPECTED_QUADS);
    private static final BitSet EMPTY = new BitSet();
    // Linked map keeps registration order stable so identical meshes produce identical trigger data
    private final Int2ObjectLinkedOpenHashMap<PlaneAccumulator> planesByNormal = new Int2ObjectLinkedOpenHashMap<>();
    private boolean trackedNormalsOverflowed;
    private int currentVertex;
    private boolean hasDistinctNormals;

    private static final class PlaneAccumulator {
        final float nx, ny, nz;
        final FloatArrayList distances = new FloatArrayList();

        PlaneAccumulator(float nx, float ny, float nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        NormalPlanes build() {
            var values = this.distances.toFloatArray();
            Arrays.sort(values);

            // Deduplicate near-equal offsets; coplanar quads share one watched plane.
            int unique = 0;

            for (int i = 0; i < values.length; i++) {
                if (unique == 0 || values[i] - values[unique - 1] >= 1.0E-4f) {
                    values[unique++] = values[i];
                }
            }

            return new NormalPlanes(this.nx, this.ny, this.nz,
                    unique == values.length ? values : Arrays.copyOf(values, unique));
        }
    }

    public enum Level {
        // no sorting is required of the current section
        NONE,
        // sorting is required once during meshing
        STATIC,
        // sorting is required any time the camera moves
        DYNAMIC;

        public static final Level[] VALUES = values();

        // Whether the camera moving can change the correct order
        public boolean requiresDynamicSorting() {
            return this.ordinal() >= Level.DYNAMIC.ordinal();
        }
    }

    public TranslucentQuadAnalyzer() {
        for(int i = 0; i < 4; i++) {
            vertexPositions[i] = new Vector3f();
        }
    }

    // triggerPlanes holds the per-normal plane sets for DYNAMIC states, or null when too many distinct normals forced the caller onto coarse movement triggering
    public record SortState(Level level, float[] centers, float[] normals, int centersLength, BitSet normalSigns, Vector3f sharedNormal, NormalPlanes[] triggerPlanes) {
        public static final SortState NONE = new SortState(Level.NONE, null, null, 0, null, null, null);

        // Whether the camera moving can change the correct order
        public boolean requiresDynamicSorting() {
            return level.requiresDynamicSorting();
        }

        // Drops scratch data, keeping only what a re-sort needs
        public SortState compactForStorage() {
            if(this == NONE || requiresDynamicSorting()) {
                return this;
            } else {
                return new SortState(level, null, null, 0, null, null, null);
            }
        }

        // Null-safe compact
        public static SortState compacted(SortState state) {
            return state != null ? state.compactForStorage() : null;
        }
    }

    // Defensive copy
    private static BitSet cloneBits(BitSet bits) {
        if(bits.isEmpty()) {
            return EMPTY;
        } else {
            return (BitSet)bits.clone();
        }
    }

    // A single plane never needs re-sorting
    private boolean areAllQuadsOnSamePlane() {
        // With globalNormal (a, b, c), each quad's plane extension is ax + by + cz = d evaluated at its centre; if every quad yields the same d they share a plane and no sort is needed, otherwise a static sort is

        var centerArray = quadCenters.elements();

        float a = globalNormal.x, b = globalNormal.y, c = globalNormal.z;
        float d = a * centerArray[0] + b * centerArray[1] + c * centerArray[2];
        int nQuads = quadCenters.size() / 3;
        for(int quadIdx = 1; quadIdx < nQuads; quadIdx++) {
            int centerOff = quadIdx * 3;
            float candidateD = a * centerArray[centerOff + 0] + b * centerArray[centerOff + 1] + c * centerArray[centerOff + 2];
            if(Math.abs(candidateD - d) >= 1.0E-5F) {
                // Different planes
                return false;
            }
        }

        return true;
    }

    // Classifies the section: none, static, or dynamic with trigger planes
    public SortState getSortState() {
        if (quadCenters.isEmpty()) {
            return SortState.NONE;
        }

        if (hasDistinctNormals) {
            // Must use dynamic sort, so clone everything the re-sort and trigger index need
            return new SortState(Level.DYNAMIC, quadCenters.toFloatArray(), quadNormals.toFloatArray(), quadCenters.size(), cloneBits(normalSigns), new Vector3f(globalNormal), buildTriggerPlanes());
        }

        // Same plane means no sorting, otherwise sort statically once to put them in the right order
        if (areAllQuadsOnSamePlane()) {
            return SortState.NONE;
        }

        // Just make a thin wrapper around our backing objects; compactForStorage drops them again before the state is kept
        return new SortState(Level.STATIC, quadCenters.elements(), null, quadCenters.size(), normalSigns, globalNormal, null);
    }

    // Resets for the next section
    public void clear() {
        quadCenters.clear();
        quadNormals.clear();
        currentVertex = 0;
        globalNormal.zero();
        normalSigns.clear();
        hasDistinctNormals = false;
        planesByNormal.clear();
        trackedNormalsOverflowed = false;
    }

    // Groups quads by normal; crossing any plane triggers a re-sort
    private NormalPlanes[] buildTriggerPlanes() {
        if (trackedNormalsOverflowed || planesByNormal.isEmpty()) {
            return null;
        }

        var planes = new NormalPlanes[planesByNormal.size()];
        int i = 0;

        for (var accumulator : planesByNormal.values()) {
            planes[i++] = accumulator.build();
        }

        return planes;
    }

    // Records a quad centre under its normal
    private void accumulatePlane(float centerX, float centerY, float centerZ) {
        if (trackedNormalsOverflowed) {
            return;
        }

        var key = NormalPlanes.quantize(currentNormal.x, currentNormal.y, currentNormal.z);
        var accumulator = planesByNormal.get(key);

        if (accumulator == null) {
            if (planesByNormal.size() >= MAX_TRACKED_NORMALS) {
                trackedNormalsOverflowed = true;
                planesByNormal.clear();
                return;
            }

            accumulator = new PlaneAccumulator(currentNormal.x, currentNormal.y, currentNormal.z);
            planesByNormal.put(key, accumulator);
        }

        // Use the group's representative normal so all offsets within a group live on one consistent axis.
        accumulator.distances.add(accumulator.nx * centerX + accumulator.ny * centerY + accumulator.nz * centerZ);
    }

    // Face normal of the captured quad from its vertices
    private void calculateNormal() {
        final Vector3f v0 = vertexPositions[0], v1 = vertexPositions[1], v2 = vertexPositions[2], v3 = vertexPositions[3];
        QuadUtil.faceNormal(v0.x, v0.y, v0.z, v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, v3.x, v3.y, v3.z, currentNormal);
    }

    // Stores the current quad's centre and normal
    private void captureQuad() {
        // The four positions in vertexPositions form a quad. Find its center
        float totalX = 0, totalY = 0, totalZ = 0;
        for (Vector3f vertex : vertexPositions) {
            totalX += vertex.x;
            totalY += vertex.y;
            totalZ += vertex.z;
        }

        float centerX = totalX * 0.25f, centerY = totalY * 0.25f, centerZ = totalZ * 0.25f;

        var centers = quadCenters;
        int currentQuadIndex = centers.size() / 3;
        centers.add(centerX);
        centers.add(centerY);
        centers.add(centerZ);

        // The normal is needed unconditionally: DYNAMIC sections register every quad's plane with the trigger index, not just those seen before the distinct-normal flag tripped
        calculateNormal();
        quadNormals.add(currentNormal.x);
        quadNormals.add(currentNormal.y);
        quadNormals.add(currentNormal.z);
        accumulatePlane(centerX, centerY, centerZ);

        if(!hasDistinctNormals) {
            if(globalNormal.x == 0 && globalNormal.y == 0 && globalNormal.z == 0) {
                // No normal has been tracked thus far, choose this one
                globalNormal.set(currentNormal);
            } else {
                float dotProduct = globalNormal.dot(currentNormal);
                // Only 1 and -1 truly imply a shared normal, but near-equal dot products are treated as shared so slightly slanted water at underwater lake edges counts as STATIC rather than DYNAMIC
                if (Math.abs(dotProduct) >= 0.98) {
                    if (dotProduct < 0) {
                        // Flag this quad as being flipped relative to the global normal
                        normalSigns.set(currentQuadIndex);
                    }
                } else {
                    hasDistinctNormals = true;
                }
            }
        }
    }

    // Accumulates one vertex; every fourth completes a quad
    public void capture(ChunkVertexEncoder.Vertex vertex) {
        int i = currentVertex;
        vertexPositions[i].set(vertex.x, vertex.y, vertex.z);
        i++;
        if(i == 4) {
            captureQuad();
            i = 0;
        }
        currentVertex = i;
    }
}
