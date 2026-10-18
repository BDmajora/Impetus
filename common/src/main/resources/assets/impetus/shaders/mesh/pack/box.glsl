// Section box against the frustum of the scene block's MVP, for the shadow pass, where no occlusion pass ran to say what is visible

// Whether the section's geometry box can reach the frustum: all eight corners outside one clip plane culls it
bool umbra_sectionInFrustum(ivec4 header, ivec3 relativeChunk) {
    vec3 low = vec3(relativeChunk << 4) + vec3(header.xyz & 0xF);
    vec3 high = low + vec3((header.xyz >> 4) & 0xF) + 1.0;
    vec3 below = vec3(0.0);
    vec3 above = vec3(0.0);

    for (int corner = 0; corner < 8; corner++) {
        vec3 point = vec3((corner & 1) != 0 ? high.x : low.x, (corner & 2) != 0 ? high.y : low.y, (corner & 4) != 0 ? high.z : low.z);
        vec4 clip = MVP * vec4(point, 1.0);
        below += vec3(lessThan(clip.xyz, -clip.www));
        above += vec3(greaterThan(clip.xyz, clip.www));
    }

    return all(lessThan(below, vec3(8.0))) && all(lessThan(above, vec3(8.0)));
}
