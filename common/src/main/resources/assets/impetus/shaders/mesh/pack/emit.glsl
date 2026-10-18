// Emits a quad's two triangles from the lane holding its first corner; lanes 4k..4k+3 are one quad's corners, so the corner positions are a quad broadcast away, and a subgroup prefix sum places the survivors without atomics
void umbra_emitQuad(uint lane, vec4 position, bool draw) {
    vec4 p0 = subgroupQuadBroadcast(position, 0u);
    vec4 p1 = subgroupQuadBroadcast(position, 1u);
    vec4 p2 = subgroupQuadBroadcast(position, 2u);
    vec4 p3 = subgroupQuadBroadcast(position, 3u);

    bool leader = draw && (lane & 3u) == 0u;
    bool first = leader && umbra_triangleVisible(p0, p1, p2, umbra_ScreenSize);
    bool second = leader && umbra_triangleVisible(p2, p3, p0, umbra_ScreenSize);

    uint count = uint(first) + uint(second);
    uint slot = subgroupExclusiveAdd(count);
    uint total = subgroupAdd(count);

    // The same (0,1,2) and (2,3,0) split the raster path's index buffer makes
    if (first) {
        gl_PrimitiveIndicesNV[slot * 3u] = lane;
        gl_PrimitiveIndicesNV[slot * 3u + 1u] = lane + 1u;
        gl_PrimitiveIndicesNV[slot * 3u + 2u] = lane + 2u;
        slot++;
    }
    if (second) {
        gl_PrimitiveIndicesNV[slot * 3u] = lane + 2u;
        gl_PrimitiveIndicesNV[slot * 3u + 1u] = lane + 3u;
        gl_PrimitiveIndicesNV[slot * 3u + 2u] = lane;
    }

    if (subgroupElect()) {
        gl_PrimitiveCountNV = total;
    }
}
