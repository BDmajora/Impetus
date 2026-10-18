// Opaque pack terrain: one lane per vertex and four per quad, so the pack's vertex code runs once per vertex as on the raster path; the translator declares the workgroup and output layout, umbra_writeOutputs and the thread count ahead of this

taskNV in UmbraTask {
    vec3 origin;
    uint baseOffset;
    uint quadCount;
    uvec4 rangeEndA;
    uvec4 rangeEndB;
    uvec4 rangeStartA;
    uvec4 rangeStartB;
    float ageMs;
} umbra_Task;

// A meshlet-wide quad index to its quad in the arena through the ranges the task shader kept, or uint(-1) past the end
uint umbra_resolveQuad(uint index) {
    if (index < umbra_Task.rangeEndA.x) { return umbra_Task.rangeStartA.x + index + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndA.y) { return umbra_Task.rangeStartA.y + (index - umbra_Task.rangeEndA.x) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndA.z) { return umbra_Task.rangeStartA.z + (index - umbra_Task.rangeEndA.y) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndA.w) { return umbra_Task.rangeStartA.w + (index - umbra_Task.rangeEndA.z) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndB.x) { return umbra_Task.rangeStartB.x + (index - umbra_Task.rangeEndA.w) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndB.y) { return umbra_Task.rangeStartB.y + (index - umbra_Task.rangeEndB.x) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndB.z) { return umbra_Task.rangeStartB.z + (index - umbra_Task.rangeEndB.y) + umbra_Task.baseOffset; }
    if (index < umbra_Task.rangeEndB.w) { return umbra_Task.rangeStartB.w + (index - umbra_Task.rangeEndB.z) + umbra_Task.baseOffset; }
    return uint(-1);
}

void main() {
    uint lane = gl_LocalInvocationID.x;

    if (lane == 0u) {
        gl_PrimitiveCountNV = 0u;
    }

    // Whole quads drop out together, which the quad operations below need
    uint quad = umbra_resolveQuad(gl_WorkGroupID.x * (UMBRA_MESH_THREADS / 4u) + (lane >> 2));

    if (quad == uint(-1)) {
        return;
    }

    umbra_fetchVertex(quad, lane & 3u, umbra_Task.origin);
    umbra_SectionAgeMs = umbra_Task.ageMs;
    umbra_vertexMain();

    gl_MeshVerticesNV[lane].gl_Position = umbra_Position;
    umbra_writeOutputs(lane);

    umbra_emitQuad(lane, umbra_Position, true);
}
