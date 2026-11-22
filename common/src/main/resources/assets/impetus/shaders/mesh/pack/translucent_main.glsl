// Translucent pack terrain, one lane per vertex.

taskNV in UmbraTask {
    vec3 origin;
    uint baseOffset;
    uint quadCount;
    uint useSortedOrder;
    float ageMs;
} umbra_Task;

void main() {
    uint lane = gl_LocalInvocationID.x;
    uint index = gl_WorkGroupID.x * (UMBRA_MESH_THREADS / 4u) + (lane >> 2);
    bool inSection = index < umbra_Task.quadCount;

    if (lane == 0u) {
        gl_PrimitiveCountNV = 0u;
    }

    if (inSection) {
        uint base = umbra_Task.baseOffset;
        uint quad = base + index;
#ifdef SORT_QUADS
        if (umbra_Task.useSortedOrder != 0u) {
            // Four 32-byte vertices per quad.
            uvec2 address = impetus_translucentOrderAddress(base * 2u, umbra_Task.quadCount, index);
            quad = base + impetus_translucentQuad(umbra_TranslucentOrder[address.x], address.y);
        }
#endif
        umbra_fetchVertex(quad, lane & 3u, umbra_Task.origin);
        umbra_SectionAgeMs = umbra_Task.ageMs;
        umbra_vertexMain();
        gl_MeshVerticesNV[lane].gl_Position = umbra_Position;
        umbra_writeOutputs(lane);
    }

    umbra_emitQuad(lane, umbra_Position, inSection);
}
