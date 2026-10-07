#version 460
#extension GL_ARB_shading_language_include : enable
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_shader_buffer_load : require

#import <impetus:mesh/scene.glsl>
#import <impetus:mesh/vertex_format.glsl>

// Translucent terrain: plain interpolants, since this pass is small and keeps the texture's alpha for blending
layout(location = 0) out vec4 colour;

layout(location = 1) in Interpolants {
    vec2 uv;
    vec3 tint;
    float fogAmount;
};

layout(binding = 0) uniform sampler2D texDiffuse;

void main() {
    colour = texture(texDiffuse, uv);

    if (colour.a < alphaCutoffValue(uint(gl_PrimitiveID & 3))) {
        discard;
    }

    colour.rgb = mix(colour.rgb * tint, fogColour.rgb, fogAmount);
}
