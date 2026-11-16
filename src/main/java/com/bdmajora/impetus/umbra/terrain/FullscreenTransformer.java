package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;

import java.util.regex.Pattern;

// Rewriter of pack shaders for fullscreen passes.
public final class FullscreenTransformer {
    // Pattern of the version line.
    private static final Pattern VERSION = Pattern.compile("^\\s*#version[^\\n]*\\n", Pattern.MULTILINE);

    // Prevents instantiation
    private FullscreenTransformer() {
    }

    // Vertex prologue.
    private static final String VERTEX_PROLOGUE = String.join("\n",
            "#version 330 core",
            "in vec4 a_Position;",
            "in vec2 a_TexCoord;",
            "vec4 iris_Vertex;",
            "vec4 iris_MultiTexCoord0;",
            "vec4 iris_MultiTexCoord1 = vec4(0.0, 0.0, 0.0, 1.0);",
            "vec4 iris_MultiTexCoord2 = vec4(0.0, 0.0, 0.0, 1.0);",
            "vec4 iris_MultiTexCoord3 = vec4(0.0, 0.0, 0.0, 1.0);",
            "vec4 iris_VertColor = vec4(1.0);",
            "vec3 iris_VertNormal = vec3(0.0, 0.0, 1.0);",
            "mat4 iris_Identity = mat4(1.0);",
            "mat3 iris_Identity3 = mat3(1.0);",
            "mat4 iris_FullscreenProj = mat4(vec4(2.0, 0.0, 0.0, 0.0), vec4(0.0, 2.0, 0.0, 0.0), vec4(0.0), vec4(-1.0, -1.0, 0.0, 1.0));",
            "#define gl_Vertex iris_Vertex",
            "#define gl_MultiTexCoord0 iris_MultiTexCoord0",
            "#define gl_MultiTexCoord1 iris_MultiTexCoord1",
            "#define gl_MultiTexCoord2 iris_MultiTexCoord2",
            "#define gl_MultiTexCoord3 iris_MultiTexCoord3",
            "#define gl_Color iris_VertColor",
            "#define gl_Normal iris_VertNormal",
            "#define gl_ModelViewProjectionMatrix iris_FullscreenProj",
            "#define gl_ModelViewMatrix iris_Identity",
            "#define gl_ProjectionMatrix iris_FullscreenProj",
            "#define gl_NormalMatrix iris_Identity3",
            "#define gl_TextureMatrix iris_TextureMatrixArray",
            "mat4 iris_TextureMatrixArray[8] = mat4[8](mat4(1.0),mat4(1.0),mat4(1.0),mat4(1.0),mat4(1.0),mat4(1.0),mat4(1.0),mat4(1.0));",
            "out vec4 iris_TexCoord[4];",
            "#define gl_TexCoord iris_TexCoord",
            "out float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "vec4 iris_shadow2D(sampler2DShadow s, vec3 p) { return vec4(texture(s, p)); }",
            "vec4 iris_shadow2DLod(sampler2DShadow s, vec3 p, float l) { return vec4(textureLod(s, p, l)); }",
            "#define ftransform() (iris_FullscreenProj * iris_Vertex)",
            ""
    ) + "\n";

    // Returns the vertex main with the hoisted assignments.
    private static String vertexMain(String hoistedAssignments) {
        return "\nvoid main() {\n"
                + "    iris_Vertex = a_Position;\n"
                + "    iris_MultiTexCoord0 = vec4(a_TexCoord, 0.0, 1.0);\n"
                + hoistedAssignments
                + "    irisMain();\n"
                + "}\n";
    }

    // Returns the declarations added before a fragment stage.
    private static String fragmentPrologue() {
        return String.join("\n",
            "#version 330 core",
            "layout(location = 0) out vec4 iris_FragData[" + DrawBuffers.fragmentOutputArraySize() + "];",
            "#define gl_FragColor iris_FragData[0]",
            "#define gl_FragData iris_FragData",
            "in vec4 iris_TexCoord[4];",
            "#define gl_TexCoord iris_TexCoord",
            "in float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "uniform vec4 iris_FogColor;",
            "uniform float iris_FogDensity;",
            "uniform float iris_FogStart;",
            "uniform float iris_FogEnd;",
            "vec4 iris_shadow2D(sampler2DShadow s, vec3 p) { return vec4(texture(s, p)); }",
            "vec4 iris_shadow2DLod(sampler2DShadow s, vec3 p, float l) { return vec4(textureLod(s, p, l)); }",
            ""
        ) + "\n";
    }

    // Rewrites a vertex stage for a fullscreen pass.
    public static String transformVertexShader(String source) {
        String body = strip(source);
        body = renameMain(body);
        body = convertVaryings(body, "out");
        body = dropAttribute(body);
        body = modernize(body);
        GlslGlobalInitHoister.Result hoist = GlslGlobalInitHoister.hoist(body);
        return VERTEX_PROLOGUE + hoist.body + vertexMain(hoist.hoistedAssignments);
    }

    // Returns the outputs added after a fragment stage.
    private static String fragmentEpilogue(int[] drawBuffers) {
        StringBuilder out = new StringBuilder("\nvoid main() {\n    irisMain();\n");
        int slots = drawBuffers == null ? 1 : Math.max(1, drawBuffers.length);
        for (int slot = 0; slot < slots; slot++) {
            String target = "iris_FragData[" + slot + "]";
            out.append("    ").append(target).append(" = mix(").append(target)
                    .append(", vec4(0.0), isnan(").append(target).append("));\n");
            out.append("    ").append(target).append(" = mix(").append(target)
                    .append(", vec4(0.0), isinf(").append(target).append("));\n");
        }
        return out.append("}\n").toString();
    }

    // Rewrites a fragment stage with the default draw buffers.
    public static String transformFragmentShader(String source) {
        return transformFragmentShader(source, DrawBuffers.DEFAULT);
    }

    // Rewrites a fragment stage for its draw buffers.
    public static String transformFragmentShader(String source, int[] drawBuffers) {
        String body = strip(source);
        body = renameMain(body);
        body = convertVaryings(body, "in");
        body = modernize(body);
        body = DrawBuffers.rewriteFragmentOutputs(body, drawBuffers);
        GlslGlobalInitHoister.Result hoist = GlslGlobalInitHoister.hoist(body);
        String transformed = fragmentPrologue() + hoist.body
                + fragmentEpilogue(drawBuffers).replace("    irisMain();", hoist.hoistedAssignments + "    irisMain();");
        return transformed;
    }

    // Returns a source without its version line.
    private static String strip(String source) {
        return VERSION.matcher(source).replaceFirst("");
    }

    // Renames the main function.
    private static String renameMain(String source) {
        return source.replaceAll("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)", "void irisMain()");
    }

    // Turns varying declarations into in or out declarations.
    private static String convertVaryings(String source, String direction) {
        return source.replaceAll(
                "(?m)^(\\s*)((?:(?:invariant|flat|smooth|noperspective|centroid)\\s+)*)varying\\b",
                "$1$2" + direction);
    }

    // Removes attribute qualifiers.
    private static String dropAttribute(String source) {
        return source.replaceAll("(?m)^(\\s*)attribute\\s+", "$1");
    }

    // Replaces legacy built ins with modern ones.
    private static String modernize(String source) {
        source = rewriteLegacyProjectionProducts(source);
        source = rewriteFogParameters(source);
        source = source.replaceAll("\\btexture\\b", "gtexture");
        source = source.replaceAll("\\btexture2DLod\\b", "textureLod");
        source = source.replaceAll("\\btexture2D\\b", "texture");
        source = source.replaceAll("\\btexture3D\\b", "texture");
        source = source.replaceAll("\\bshadow2DLod\\b", "iris_shadow2DLod");
        source = source.replaceAll("\\bshadow2D\\b", "iris_shadow2D");
        return source;
    }

    // Rewrites legacy projection matrix products.
    private static String rewriteLegacyProjectionProducts(String source) {
        return source.replaceAll(
                "\\bvec4\\s*\\(([^;\\n]+)\\)\\s*\\*\\s*\\b(gbufferProjection|gbufferPreviousProjection|shadowProjection)\\b",
                "$2 * vec4($1)");
    }

    // Replaces fog struct reads with the fog uniforms.
    private static String rewriteFogParameters(String source) {
        return FogParameters.rewrite(source);
    }
}
