// Decoders for MeshChunkVertex, the 16-byte terrain vertex.
//
//   x: [0..15] position x, [16..31] position y
//   y: [0..15] position z, [16..23] material bits, [24..31] block light
//   z: [0..23] colour, already multiplied by the shade factor on the CPU, [24..31] sky light
//   w: [0..15] u, [16..31] v
//
// Shade folded into the colour is what frees the fourth colour byte for sky light, and what makes this format
// four bytes narrower than the one the raster backend uses.

#define MODEL_SCALE   (32.0 / 65536.0)
#define MODEL_ORIGIN  8.0
#define COLOR_SCALE   (1.0 / 255.0)

vec3 decodeVertexPosition(Vertex v) {
    uvec3 packed = uvec3(
        (v.x >>  0) & 0xFFFFu,
        (v.x >> 16) & 0xFFFFu,
        (v.y >>  0) & 0xFFFFu
    );

    return (vec3(packed) * MODEL_SCALE) - MODEL_ORIGIN;
}

vec3 decodeVertexColour(Vertex v) {
    uvec3 packed = (uvec3(v.z) >> uvec3(0, 8, 16)) & uvec3(0xFFu);
    return vec3(packed) * COLOR_SCALE;
}

vec2 decodeVertexUV(Vertex v) {
    return vec2(v.w & 0xFFFFu, v.w >> 16) * (1.0 / float(TEXTURE_MAX_SCALE));
}

// The lightmap is sampled with CLAMP_TO_EDGE and the stored values are clamped away from 0 and 255, so the
// division by 256 never lands on the outermost texel.
vec2 decodeLightUV(Vertex v) {
    uvec2 light = uvec2(v.y >> 24, v.z >> 24) & uvec2(0xFFu);
    return vec2(light) / 256.0;
}

// Material bits, matching the raster backend's Material.bits():
//   [0..1] alpha cutoff selector (AlphaCutoffParameter: 0, 0.1, 0.5, 1.0), [2] mipmapped
bool hasMipmapping(Vertex v) {
    return ((v.y >> 16) & 4u) != 0u;
}

uint rawAlphaCutoff(Vertex v) {
    return uint((v.y >> 16) & 3u);
}

float alphaCutoffValue(uint selector) {
    return float[](0.0, 0.1, 0.5, 1.0)[selector];
}
