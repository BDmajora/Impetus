// The one uniform block every mesh phase shares, bound by GPU address so it survives program changes; mostly a table of raw device pointers
#define Vertex uvec4

// The translucent rasterizer swaps quads in place, so only it gets a writable view of the geometry arena
#ifdef WRITABLE_TERRAIN
#define TERRAIN_ACCESS restrict
#else
#define TERRAIN_ACCESS readonly restrict
#endif

// 32 bytes per section so the section rasterizer touches one cache line each
struct Section {
    // x: [0..3] minX [4..7] sizeX [8..31] chunk x; y: [0..3] minY [4..7] sizeY [8..16] chunk y [17] hidden [18..25] translucent draw slot; z: like x for z; w: first quad in the arena
    ivec4 header;
    // Eight uint16 quad counts: +X +Y +Z -X -Y -Z, unassigned, then the translucent quad count that precedes them
    ivec4 renderRanges;
};

struct Region {
    uint64_t a;
    uint64_t b;
};

// Extent in sections minus one, over the occupied part of the region only
ivec3 unpackRegionSize(Region region) {
    return ivec3((region.a >> 59) & 7, region.a >> 62, (region.a >> 56) & 7);
}

// Section coordinates of the occupied corner, each sign-extended out of its 24-bit slot
ivec3 unpackRegionPosition(Region region) {
    int x = int(int64_t(region.a << 16) >> 40);
    int y = (int(region.a) << 8) >> 8;
    int z = int(int64_t(region.b) >> 40);
    return ivec3(x, y, z);
}

// Highest live section index in the region
int unpackRegionCount(Region region) {
    return int((region.a >> 48) & 255);
}

// A zeroed header is an empty slot once the hidden bit and draw slot are masked off
bool sectionEmpty(ivec4 header) {
    header.y &= ~(0x1FF << 17);
    return header == ivec4(0);
}

// Chunk coordinates of a section, y sign-extended out of its 9 bits
ivec3 unpackSectionChunk(ivec4 header) {
    ivec3 chunk = ivec3(header.xyz) >> 8;
    chunk.y &= 0x1FF;
    chunk.y = (chunk.y << 23) >> 23;
    return chunk;
}

layout(std140, binding = 0) uniform SceneData {
    // Projection * modelview, translated by the camera's offset inside its own section
    mat4 MVP;
    ivec4 cameraChunk;
    // Camera offset within its section, negated
    vec4 subChunkOffset;
    vec4 fogColour;

    // This frame's visible region ids, stored right after this block in the same allocation
    readonly restrict uint16_t *regionIndices;
    readonly restrict Region *regionData;
    restrict Section *sectionData;
    // One byte per visible region and one per section, written by the occlusion fragment shaders
    restrict uint8_t *regionVisibility;
    restrict uint8_t *sectionVisibility;
    writeonly restrict uvec2 *terrainCommandBuffer;
    writeonly restrict uvec2 *translucencyCommandBuffer;
    // Regions the section sorter reorders this frame
    readonly restrict uint16_t *sortingRegionList;
    TERRAIN_ACCESS Vertex *terrainData;
    // Regions, sections, quads; only touched when a STATISTICS_* define is set
    uint32_t *statisticsBuffer;

    // Half the framebuffer size, so clip space maps to pixels with one multiply
    vec2 screenSize;

    float fogStart;
    float fogEnd;
    float fogDensity;
    // 0 none, 1 linear, 2 exponential squared
    int fogMode;
    // Values of include/fog.glsl's FOG_SHAPE_* constants
    int fogShape;

    uint16_t regionCount;
    uint8_t frameId;
};

// How much fog covers a point at a camera-relative position; viewDepth is clip w, the planar shape's distance
float computeFog(vec3 relative, float viewDepth) {
    if (fogMode == 0) {
        return 0.0;
    }

    float dist;
    if (fogShape == 1) {
        dist = max(length(relative.xz), abs(relative.y));
    } else if (fogShape == 2) {
        dist = length(relative.xz);
    } else if (fogShape == 3) {
        dist = viewDepth;
    } else {
        dist = length(relative);
    }

    if (fogMode == 2) {
        float scaled = dist * fogDensity;
        return 1.0 - clamp(1.0 / exp2(scaled * scaled), 0.0, 1.0) * fogColour.a;
    }
    if (fogMode == 3) {
        return 1.0 - clamp(1.0 / exp(dist * fogDensity), 0.0, 1.0) * fogColour.a;
    }

    if (dist <= fogStart) {
        return 0.0;
    }
    return (dist >= fogEnd ? 1.0 : smoothstep(fogStart, fogEnd, dist)) * fogColour.a;
}
