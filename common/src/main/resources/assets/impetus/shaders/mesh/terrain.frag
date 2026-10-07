#version 460
#extension GL_ARB_shading_language_include : enable
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_shader_buffer_load : require
#extension GL_NV_fragment_shader_barycentric : require

#import <impetus:mesh/scene.glsl>
#import <impetus:mesh/vertex_format.glsl>

// Opaque terrain: only fog is interpolated; UV and shading are re-fetched by quad id and blended against the barycentrics, which keeps the mesh shader's output small
layout(location = 0) out vec4 colour;

layout(location = 1) in Interpolants {
    float fogAmount;
};

layout(binding = 0) uniform sampler2D texDiffuse;
layout(binding = 1) uniform sampler2D texLight;

// Vertex colour times lightmap per vertex, then interpolated, matching the raster path's shading
vec3 shadeVertex(Vertex v) {
    return decodeVertexColour(v) * texture(texLight, decodeLightUV(v)).rgb;
}

void main() {
    uint quad = uint(gl_PrimitiveID) >> 4;
    bool firstTriangle = ((gl_PrimitiveID >> 3) & 1) == 0;

    // The mesh shader emitted (0,1,2) and (2,3,0)
    uvec3 corners = firstTriangle ? uvec3(0, 1, 2) : uvec3(2, 3, 0);

    Vertex v0 = terrainData[(quad << 2) + corners.x];
    Vertex v1 = terrainData[(quad << 2) + corners.y];
    Vertex v2 = terrainData[(quad << 2) + corners.z];

    vec2 uv = gl_BaryCoordNV.x * decodeVertexUV(v0)
            + gl_BaryCoordNV.y * decodeVertexUV(v1)
            + gl_BaryCoordNV.z * decodeVertexUV(v2);

    // An unmipped material gets a large negative bias, which picks the base level without a second sampler
    colour = texture(texDiffuse, uv, float((gl_PrimitiveID >> 2) & 1) * -8.0);

    if (colour.a < alphaCutoffValue(uint(gl_PrimitiveID & 3))) {
        discard;
    }

    colour.a = 1.0;
    colour.rgb *= gl_BaryCoordNV.x * shadeVertex(v0)
                + gl_BaryCoordNV.y * shadeVertex(v1)
                + gl_BaryCoordNV.z * shadeVertex(v2);
    colour.rgb = mix(colour.rgb, fogColour.rgb, fogAmount);
}
