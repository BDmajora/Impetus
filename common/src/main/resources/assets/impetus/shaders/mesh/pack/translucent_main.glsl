// Translucent pack terrain in arena order, one lane per vertex; under SORT_QUADS each meshlet also swaps out-of-order neighbouring quads in the arena (the odd-even transposition sort Nvidium converges over frames), and the jiggle slot that belongs to the previous section never draws; the translator declares the workgroup and output layout ahead of this

taskNV in UmbraTask {
    vec3 origin;
    uint baseOffset;
    uint quadCount;
    uint jiggle;
    float ageMs;
} umbra_Task;

uniform int umbra_TaskMode;

#ifdef SORT_QUADS
// Swaps this lane's corner of a quad with the same corner of the next quad
void umbra_swapWithNext(uint quad, uint corner) {
    uint a = (quad << 2) + corner;
    uint b = a + 4u;
    UmbraVertex first = umbra_TerrainData[a];
    umbra_TerrainData[a] = umbra_TerrainData[b];
    umbra_TerrainData[b] = first;
}
#endif

void main() {
    uint lane = gl_LocalInvocationID.x;
    uint index = gl_WorkGroupID.x * (UMBRA_MESH_THREADS / 4u) + (lane >> 2);
    uint quad = umbra_Task.baseOffset + index;
    bool inSection = index < umbra_Task.quadCount;
    bool shown = inSection && index >= umbra_Task.jiggle;

    if (lane == 0u) {
        gl_PrimitiveCountNV = 0u;
    }

    // No early return: the sort below needs every lane at its barrier
    if (inSection) {
        umbra_fetchVertex(quad, lane & 3u, umbra_Task.origin);
        umbra_SectionAgeMs = umbra_Task.ageMs;
        umbra_vertexMain();
        gl_MeshVerticesNV[lane].gl_Position = umbra_Position;
        umbra_writeOutputs(lane);
    }

    umbra_emitQuad(lane, umbra_Position, shown);

#ifdef SORT_QUADS
    // Only the main pass sorts: the shadow pass has no eye to sort for
    if (umbra_TaskMode == 0) {
        vec3 centre = subgroupClusteredAdd(umbra_EyeRelative, 4u) * 0.25;
        float depth = shown ? dot(centre, centre) : -1.0;
        float next = subgroupShuffleDown(depth, 4u);

        // Every lane has read its corner before any is overwritten
        barrier();

        bool evenQuad = ((lane >> 2) & 1u) == 0u;
        if (evenQuad && depth > 0.0001 && next > 0.0001 && depth < next) {
            umbra_swapWithNext(quad, lane & 3u);
        }
    }
#endif
}
