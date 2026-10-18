// Decodes one UmbraMeshVertex into the bridge attributes the transformed pack vertex stage reads; in a mesh shader they are plain globals, filled here once per lane before the pack's code runs

// The vertex relative to the eye, for translucent sorting
vec3 umbra_EyeRelative;

void umbra_fetchVertex(uint quad, uint corner, vec3 sectionOrigin) {
    UmbraVertex v = umbra_TerrainData[(quad << 2) + corner];
    vec3 local = vec3(uvec3(v.a.x & 0xFFFFu, v.a.x >> 16, v.a.y & 0xFFFFu)) * (32.0 / 65536.0) - 8.0;

    umbra_EyeRelative = local + sectionOrigin + umbra_SubChunkOffset.xyz;
    // Measured from the model-view's own origin, as the raster path's region offset makes it
    a_PosId = local + sectionOrigin + umbra_RenderOffset.xyz;
    a_Color = unpackUnorm4x8(v.a.z);
    a_TexCoord = vec2(uvec2(v.a.w & 0xFFFFu, v.a.w >> 16)) * (1.0 / 65536.0);
    // Material in the low byte and the draw id left zero, so the generated decode adds no region translation
    a_LightCoord = ((v.a.y >> 16) & 0xFFu) | (((v.a.y >> 24) & 0xFFu) << 16) | ((v.b.x >> 24) << 24);
    // The material's mipmap bit, which the ported fragment stage's atlas lookups key on
    umbra_Mipped = float((v.a.y >> 16) & 1u);
    iris_Normal = vec4(unpackSnorm4x8(v.b.x).xyz, 0.0);
    iris_Tangent = unpackSnorm4x8(v.b.y);
    // The quad's mean UV is mc_midTexCoord, and the quad's four corners sit in this lane's cluster
    iris_MidTexCoord = subgroupClusteredAdd(a_TexCoord, 4u) * 0.25;
    iris_BlockInfo = vec4(float(int(v.b.z << 16) >> 16), float(int(v.b.z << 8) >> 24), float(v.b.z >> 24), 1.0);
    iris_MidBlock = vec4(ivec4(int(v.b.w << 24), int(v.b.w << 16), int(v.b.w << 8), int(v.b.w)) >> 24);
    umbra_VertexID = (quad << 2) + corner;
}
