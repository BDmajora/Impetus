// Moving lights the CPU no longer bakes into meshes, relative to the terrain's render origin; TerrainDynamicLights uploads them and holds the same members and function for pack programs

#ifdef LEGACY
// GLSL 1.20 has no uniform blocks, and on such a context the meshes bake moving lights in instead
float impetus_dynamicLightLevel(vec3 position) {
    return 0.0;
}
#else
#if __VERSION__ >= 420
layout(std140, binding = 14) uniform ImpetusDynamicLights {
#else
layout(std140) uniform ImpetusDynamicLights {
#endif
    vec4 impetus_DynamicLights[64];
    ivec4 impetus_DynamicLightCount;
};

float impetus_dynamicLightLevel(vec3 position) {
    float level = 0.0;
    int count = min(impetus_DynamicLightCount.x, 64);
    for (int i = 0; i < count; i++) {
        vec4 light = impetus_DynamicLights[i];
        vec3 delta = position - light.xyz;
        float distanceSquared = dot(delta, delta);
        if (distanceSquared <= 60.0625) {
            level = max(level, (1.0 - sqrt(distanceSquared) / 7.75) * light.w);
        }
    }
    return min(level, 15.0);
}
#endif
