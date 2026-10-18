// scene.glsl's block under names no pack can collide with, for mesh shaders that carry a pack's own vertex code (packs declare fogStart, screenSize and Vertex themselves); field for field the same layout

#ifdef UMBRA_WRITABLE_TERRAIN
#define UMBRA_TERRAIN_ACCESS restrict
#else
#define UMBRA_TERRAIN_ACCESS readonly restrict
#endif

struct UmbraSection {
    ivec4 header;
    ivec4 renderRanges;
};

struct UmbraRegion {
    uint64_t a;
    uint64_t b;
};

// One UmbraMeshVertex: position, material and block light, colour, UV; then normal and sky light, tangent, mc_Entity, at_midBlock
struct UmbraVertex {
    uvec4 a;
    uvec4 b;
};

layout(std140, binding = 0) uniform UmbraSceneData {
    mat4 umbra_MVP;
    ivec4 umbra_CameraChunk;
    vec4 umbra_SubChunkOffset;
    vec4 umbra_RenderOffset;
    vec4 umbra_FogColour;

    readonly restrict uint16_t *umbra_RegionIndices;
    readonly restrict UmbraRegion *umbra_RegionData;
    restrict UmbraSection *umbra_SectionData;
    restrict uint8_t *umbra_RegionVisibility;
    restrict uint8_t *umbra_SectionVisibility;
    writeonly restrict uvec2 *umbra_TerrainCommands;
    writeonly restrict uvec2 *umbra_TranslucentCommands;
    readonly restrict uint16_t *umbra_SortingRegions;
    UMBRA_TERRAIN_ACCESS UmbraVertex *umbra_TerrainData;
    uint32_t *umbra_Statistics;
    readonly restrict uint32_t *umbra_SectionLoadTimes;

    vec2 umbra_ScreenSize;

    float umbra_FogStart;
    float umbra_FogEnd;
    float umbra_FogDensity;
    int umbra_FogMode;
    int umbra_FogShape;

    uint32_t umbra_TimeMs;
    float umbra_ChunkFadeInv;

    uint16_t umbra_RegionCount;
    uint8_t umbra_FrameId;
};
