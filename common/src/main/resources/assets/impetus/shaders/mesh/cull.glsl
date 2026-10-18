// Per-triangle rejection the mesh shaders run before emitting a primitive; every name is prefixed since Nvidium-ported pack mesh shaders include it beside a pack's own code, and halfScreen maps clip space to pixels

// Outside one clip plane at every corner; homogeneous, so it holds for corners behind the eye too
bool umbra_outsideFrustum(vec4 a, vec4 b, vec4 c) {
    vec3 low = max(max(a.xyz + a.www, b.xyz + b.www), c.xyz + c.www);
    vec3 high = max(max(a.www - a.xyz, b.www - b.xyz), c.www - c.xyz);
    return any(lessThan(low, vec3(0.0))) || any(lessThan(high, vec3(0.0)));
}

// Screen bounds that round to one pixel in either axis cover no pixel centre; only meaningful with every corner in front of the eye, since dividing by a negative w mirrors the corner
bool umbra_coversNoPixel(vec4 a, vec4 b, vec4 c, vec2 halfScreen) {
    if (min(min(a.w, b.w), c.w) <= 0.0) {
        return false;
    }
    vec2 pa = ((a.xy / a.w) + 1.0) * halfScreen;
    vec2 pb = ((b.xy / b.w) + 1.0) * halfScreen;
    vec2 pc = ((c.xy / c.w) + 1.0) * halfScreen;
    return any(equal(round(min(min(pa, pb), pc)), round(max(max(pa, pb), pc))));
}

// Whether a clip-space triangle can produce a fragment
bool umbra_triangleVisible(vec4 a, vec4 b, vec4 c, vec2 halfScreen) {
    return !umbra_outsideFrustum(a, b, c) && !umbra_coversNoPixel(a, b, c, halfScreen);
}
