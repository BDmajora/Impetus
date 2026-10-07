// Values of u_FogShape: 0 and 1 are Sodium's, 2 and 3 the Extras page's (ExtrasConfig.FogShape), with spherical as Impetus's baseline
const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;
const int FOG_SHAPE_RADIAL = 2;
const int FOG_SHAPE_PLANAR = 3;

vec4 _linearFog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogStart, float fogEnd) {
#ifdef USE_FOG
    if (fragDistance <= fogStart) {
        return fragColor;
    }
    float factor = fragDistance < fogEnd ? smoothstep(fogStart, fogEnd, fragDistance) : 1.0; // alpha value of fog is used as a weight
    vec3 blended = mix(fragColor.rgb, fogColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

float _linearFogValue(float fragDistance, float fogStart, float fogEnd) {
#ifdef USE_FOG
    if (fragDistance <= fogStart) {
        return 0.0;
    } else if (fragDistance >= fogEnd) {
        return 1.0;
    }

    return smoothstep(fogStart, fogEnd, fragDistance);
#else
    return 1.0;
#endif
}

vec4 _exp2Fog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogDensity) {
#ifdef USE_FOG
    float dist = fragDistance * fogDensity;
    float factor = clamp(1.0 / exp2(dist * dist), 0.0, 1.0);
    vec3 blended = mix(fogColor.rgb, fragColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

vec4 _expFog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogDensity) {
#ifdef USE_FOG
    float dist = fragDistance * fogDensity;
    float factor = clamp(1.0 / exp(dist), 0.0, 1.0);
    vec3 blended = mix(fogColor.rgb, fragColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
#else
    return fragColor;
#endif
}

float getFragDistance(int fogShape, vec3 position) {
    // Use the maximum of the horizontal and vertical distance to get cylindrical fog if fog shape is cylindrical
    if (fogShape == FOG_SHAPE_CYLINDRICAL) {
        return max(length(position.xz), abs(position.y));
    } else if (fogShape == FOG_SHAPE_RADIAL) {
        // Horizontal only, so looking straight up or down out of a fogged world shows clear sky
        return length(position.xz);
    } else {
        return length(position);
    }
}

// Planar fog needs the view-space depth, which the world-space position alone cannot give
float getFragDistance(int fogShape, vec3 position, float viewDepth) {
    if (fogShape == FOG_SHAPE_PLANAR) {
        return viewDepth;
    }
    return getFragDistance(fogShape, position);
}