// A 64-byte block of 32 packed quad indices. Each section's indices end where its vertices start.
struct ImpetusTranslucentOrderBlock {
    uvec4 words[4];
};

uvec2 impetus_translucentOrderAddress(uint vertexBlock, uint quadCount, uint drawIndex) {
    uint blocks = (quadCount + 31u) >> 5;
    uint slot = blocks * 32u - quadCount + drawIndex;
    return uvec2(vertexBlock - blocks + (slot >> 5), slot & 31u);
}

uint impetus_translucentQuad(ImpetusTranslucentOrderBlock blockData, uint slot) {
    uint pair = blockData.words[slot >> 3][(slot >> 1) & 3u];
    return (pair >> ((slot & 1u) << 4)) & 0xFFFFu;
}
