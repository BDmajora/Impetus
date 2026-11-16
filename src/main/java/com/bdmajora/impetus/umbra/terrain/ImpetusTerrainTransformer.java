package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewriter of pack shaders for Impetus terrain vertices.
public final class ImpetusTerrainTransformer {
    // Pattern of the version line.
    private static final Pattern VERSION = Pattern.compile("^\\s*#version[^\\n]*\\n", Pattern.MULTILINE);

    // Prevents instantiation
    private ImpetusTerrainTransformer() {
    }

    // Vertex prologue.
    private static final String VERTEX_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra terrain bridge (generated) ----",
            "in vec3 a_PosId;",
            "in vec4 a_Color;",
            "in vec2 a_TexCoord;",
            "in uint a_LightCoord;",
            "in vec4 iris_Normal;",
            "in vec4 iris_Tangent;",
            "in vec2 iris_MidTexCoord;",
            "in vec4 iris_BlockInfo;",
            "in vec4 iris_MidBlock;",
            "uniform mat4 u_ModelViewMatrix;",
            "uniform mat4 u_ProjectionMatrix;",
            "uniform vec3 u_RegionOffset;",
            "",
            "uvec3 _iris_relChunk(uint pos) { return (uvec3(pos) >> uvec3(5u,0u,2u)) & uvec3(7u,3u,7u); }",
            "vec3 _iris_drawTranslation(uint pos) { return vec3(_iris_relChunk(pos)) * 16.0; }",
            "// GLSL-120 shadow2D returned vec4; 330's texture() on a shadow sampler returns float. Wrap so .x/.z work.",
            "vec4 iris_shadow2D(sampler2DShadow s, vec3 p) { return vec4(texture(s, p)); }",
            "vec4 iris_shadow2DLod(sampler2DShadow s, vec3 p, float l) { return vec4(textureLod(s, p, l)); }",
            "",
            "vec4 iris_Vertex;",
            "vec4 iris_Color;",
            "vec4 iris_MultiTexCoord0;",
            "vec4 iris_MultiTexCoord1;",
            "vec4 iris_MultiTexCoord2 = vec4(0.0, 0.0, 0.0, 1.0);",
            "vec4 iris_MultiTexCoord3 = vec4(0.0, 0.0, 0.0, 1.0);",
            "vec4 iris_MidTexFull;",
            "vec4 iris_EntityFull;",
            "// The matrix built-ins alias the uniforms directly (as expressions, not uniform-initialized globals —",
            "// global initializers must be constant expressions in GLSL 330; drivers that accept them may evaluate",
            "// them before uniforms are loaded, collapsing every vertex to the origin).",
            "// gl_TextureMatrix[1] is vanilla's lightmap matrix (scale 1/256, translate 8/256): raw 0..240 lightmap",
            "// coords -> 0..1 UVs. The rest are identity (the block atlas uses untransformed coords).",
            "const mat4 iris_LightmapTextureMatrix = mat4(",
            "    vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0),",
            "    vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0));",
            "mat4 iris_TextureMatrix[8] = mat4[8](mat4(1.0), iris_LightmapTextureMatrix,",
            "    mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0));",
            "",
            "#define gl_Vertex iris_Vertex",
            "#define gl_Color iris_Color",
            "#define gl_MultiTexCoord0 iris_MultiTexCoord0",
            "#define gl_MultiTexCoord1 iris_MultiTexCoord1",
            "#define gl_MultiTexCoord2 iris_MultiTexCoord2",
            "#define gl_MultiTexCoord3 iris_MultiTexCoord3",
            "#define gl_Normal (iris_Normal.xyz)",
            "#define gl_ModelViewMatrix u_ModelViewMatrix",
            "#define gl_ProjectionMatrix u_ProjectionMatrix",
            "#define gl_ModelViewProjectionMatrix (u_ProjectionMatrix * u_ModelViewMatrix)",
            "#define gl_NormalMatrix (mat3(transpose(inverse(u_ModelViewMatrix))))",
            "#define gl_TextureMatrix iris_TextureMatrix",
            "#define ftransform() (u_ProjectionMatrix * (u_ModelViewMatrix * iris_Vertex))",
            "out float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "uniform vec4 iris_FogColor;",
            "uniform float iris_FogDensity;",
            "uniform float iris_FogStart;",
            "uniform float iris_FogEnd;",
            "out vec4 iris_TexCoordArr[4];",
            "#define gl_TexCoord iris_TexCoordArr",
            "// OptiFine packs rely on fixed-function GL_ALPHA_TEST for cutout transparency, but Impetus disables it",
            "// and discards in-shader instead; mirror its per-material cutoff (bits 1-2 of the material byte).",
            "const float[4] _UMBRA_ALPHA_CUTOFF = float[4](0.0, 0.1, 0.5, 1.0);",
            "flat out float iris_AlphaCutoff;",
            "// ---- end generated prologue ----",
            ""
    ) + "\n";

    // Returns the vertex main with the hoisted assignments.
    private static String vertexMain(String hoistedAssignments) {
        return "\nvoid main() {\n"
                + "    uint lightData = a_LightCoord;\n"
                + "    uint drawId = (lightData >> 8u) & 0xFFu;\n"
                + "    vec3 pos = a_PosId + u_RegionOffset + _iris_drawTranslation(drawId);\n"
                + "    iris_Vertex = vec4(pos, 1.0);\n"
                + "    iris_Color = a_Color;\n"
                + "    iris_MultiTexCoord0 = vec4(a_TexCoord, 0.0, 1.0);\n"
                + "    uint blockLight = (lightData >> 16u) & 0xFFu;\n"
                + "    uint skyLight = (lightData >> 24u) & 0xFFu;\n"
                + "    iris_MultiTexCoord1 = vec4(float(blockLight), float(skyLight), 0.0, 1.0);\n"
                + "    iris_MidTexFull = vec4(iris_MidTexCoord, 0.0, 1.0);\n"
                + "    iris_EntityFull = iris_BlockInfo;\n"
                + "    iris_AlphaCutoff = _UMBRA_ALPHA_CUTOFF[int((lightData >> 1u) & 3u)];\n"
                + hoistedAssignments
                + "    irisMain();\n"
                + "    if (any(isnan(gl_Position)) || any(isinf(gl_Position))) {\n"
                + "        gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * iris_Vertex;\n"
                + "    }\n"
                + "}\n";
    }

    // Returns the fragment output block.
    static String fragDataBlock() {
        return String.join("\n",
                "layout(location = 0) out vec4 iris_FragData[" + DrawBuffers.fragmentOutputArraySize() + "];",
                "#define gl_FragColor iris_FragData[0]",
                "#define gl_FragData iris_FragData",
                ""
        );
    }

    // Returns the alpha discard line of a snippet.
    static String alphaDiscard(String snippet) {
        return snippet == null ? "" : snippet;
    }

    // Fragment prologue.
    private static final String FRAGMENT_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra terrain bridge (generated) ----",
            "vec4 iris_shadow2D(sampler2DShadow s, vec3 p) { return vec4(texture(s, p)); }",
            "vec4 iris_shadow2DLod(sampler2DShadow s, vec3 p, float l) { return vec4(textureLod(s, p, l)); }",
            "const mat4 iris_LightmapTextureMatrix = mat4(",
            "    vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0),",
            "    vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0));",
            "mat4 iris_TextureMatrix[8] = mat4[8](mat4(1.0), iris_LightmapTextureMatrix,",
            "    mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0));",
            "#define gl_TextureMatrix iris_TextureMatrix",
            "in float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "uniform vec4 iris_FogColor;",
            "uniform float iris_FogDensity;",
            "uniform float iris_FogStart;",
            "uniform float iris_FogEnd;",
            "in vec4 iris_TexCoordArr[4];",
            "#define gl_TexCoord iris_TexCoordArr",
            "flat in float iris_AlphaCutoff;",
            "// ---- end generated prologue ----",
            ""
    ) + "\n";

    // Returns the chunk fade declarations of a pack source.
    static String chunkFadeDeclarations(String packSource, boolean shadow) {
        if (!packSource.contains("mc_chunkFade")) {
            return "";
        }
        if (shadow) {
            return "const float mc_chunkFade = -1.0;\n";
        }
        return "uniform float iris_ChunkAgesMs[256];\nuniform float iris_ChunkFadeInv;\nfloat mc_chunkFade;\n";
    }

    // Returns the chunk fade assignment of a pack source.
    static String chunkFadeAssignment(String packSource, boolean shadow) {
        if (shadow || !packSource.contains("mc_chunkFade")) {
            return "";
        }
        return "    mc_chunkFade = iris_ChunkFadeInv <= 0.0 ? 1.0 : clamp(iris_ChunkAgesMs[drawId] * iris_ChunkFadeInv, 0.0, 1.0);\n";
    }

    // Rewrites a vertex stage for terrain.
    public static String transformVertexShader(String source) {
        return transformVertexShader(source, false);
    }

    // Rewrites a vertex stage for terrain with tessellation.
    public static String transformVertexShader(String source, boolean shadow) {
        String body = stripVersion(source);
        body = renameMain(body);
        body = convertVaryings(body, "out");
        body = dropAttributeStorageQualifier(body);
        body = modernizeCommon(body);
        GlslGlobalInitHoister.Result hoist = GlslGlobalInitHoister.hoist(body);
        return VERTEX_PROLOGUE + attributeAdapterDefines(source) + chunkFadeDeclarations(source, shadow) + hoist.body
                + vertexMain(chunkFadeAssignment(source, shadow) + hoist.hoistedAssignments);
    }

    // Rewrites a fragment stage for terrain.
    public static String transformFragmentShader(String source) {
        return transformFragmentShader(source, DrawBuffers.DEFAULT);
    }

    // Rewrites a fragment stage with an alpha test.
    public static String transformFragmentShader(String source, int[] drawBuffers) {
        return transformFragmentShader(source, drawBuffers,
                "    if (iris_FragData[0].a < iris_AlphaCutoff) { discard; }\n");
    }

    // Rewrites a fragment stage with an alpha test and draw buffers.
    public static String transformFragmentShader(String source, int[] drawBuffers, String alphaTestSnippet) {
        String body = stripVersion(source);
        body = renameMain(body);
        body = convertVaryings(body, "in");
        body = modernizeCommon(body);
        body = DrawBuffers.rewriteFragmentOutputs(body, drawBuffers);
        GlslGlobalInitHoister.Result hoist = GlslGlobalInitHoister.hoist(body);
        String transformed = FRAGMENT_PROLOGUE + fragDataBlock() + hoist.body
                + "\nvoid main() {\n" + hoist.hoistedAssignments + "    irisMain();\n"
                + alphaDiscard(alphaTestSnippet)
                + "}\n";
        return transformed;
    }

    // Rewrites a modern vertex stage for terrain.
    public static String transformVertexShaderModern(String source) {
        return transformVertexShaderModern(source, false);
    }

    // Rewrites a modern vertex stage for terrain with tessellation.
    public static String transformVertexShaderModern(String source, boolean shadow) {
        String body = stripVersion(source);
        body = renameMain(body);
        body = dropAttributeStorageQualifier(body);
        body = rewriteFogParameters(body);
        body = ModernPackTransformer.rewriteUnsignedStrictness(body);
        return compatFor(VERTEX_PROLOGUE, source) + attributeAdapterDefines(source) + chunkFadeDeclarations(source, shadow) + body
                + vertexMain(chunkFadeAssignment(source, shadow));
    }

    // Rewrites a modern fragment stage for terrain.
    public static String transformFragmentShaderModern(String source) {
        return transformFragmentShaderModern(source, DrawBuffers.DEFAULT);
    }

    // Rewrites a modern fragment stage with an alpha test.
    public static String transformFragmentShaderModern(String source, int[] drawBuffers) {
        return transformFragmentShaderModern(source, drawBuffers,
                "    if (iris_FragData[0].a < iris_AlphaCutoff) { discard; }\n");
    }

    // Rewrites a modern fragment stage with an alpha test and draw buffers.
    public static String transformFragmentShaderModern(String source, int[] drawBuffers, String alphaTestSnippet) {
        String body = stripVersion(source);
        body = renameMain(body);
        body = rewriteFogParameters(body);
        body = ModernPackTransformer.rewriteUnsignedStrictness(body);
        body = DrawBuffers.rewriteFragmentOutputs(body, drawBuffers);
        boolean usesFragData = Pattern.compile("\\bgl_Frag(?:Data|Color)\\b").matcher(body).find();
        String transformed = compatFor(FRAGMENT_PROLOGUE, source)
                + (usesFragData ? fragDataBlock() : "")
                + body
                + (usesFragData
                        ? "\nvoid main() {\n    irisMain();\n"
                                + alphaDiscard(alphaTestSnippet)
                                + "}\n"
                        : "\nvoid main() {\n    irisMain();\n}\n");
        return transformed;
    }

    // Declared version.
    private static final Pattern DECLARED_VERSION = Pattern.compile("#version\\s+(\\d+)");

    // Interface block of the generated varyings.
    static final String VARYING_BLOCK = "IrisTerrainVaryings {\n    float iris_FogFragCoord;\n    vec4 iris_TexCoordArr[4];\n    float iris_AlphaCutoff;\n}";

    // Gl in tex coord.
    private static final Pattern GL_IN_TEX_COORD = Pattern.compile("gl_in\\s*\\[([^\\]]+)\\]\\s*\\.\\s*gl_TexCoord\\b");
    // Gl in fog.
    private static final Pattern GL_IN_FOG = Pattern.compile("gl_in\\s*\\[([^\\]]+)\\]\\s*\\.\\s*gl_FogFragCoord\\b");

    // Wraps the generated varyings in a block.
    public static String blockGeneratedVaryings(String transformed, boolean vertex) {
        String direction = vertex ? "out" : "in";
        String block = direction + " " + VARYING_BLOCK + ";";
        return transformed
                .replace(direction + " float iris_FogFragCoord;", block)
                .replace(direction + " vec4 iris_TexCoordArr[4];\n", "")
                .replace("flat " + direction + " float iris_AlphaCutoff;\n", "");
    }

    // Rewrites a geometry or tessellation stage for terrain.
    public static String transformAuxiliaryStage(String source, com.bdmajora.impetus.engine.impl.gl.shader.ShaderType stage) {
        String body = stripVersion(source);
        body = GL_IN_TEX_COORD.matcher(body).replaceAll("iris_varyingsIn[$1].iris_TexCoordArr");
        body = GL_IN_FOG.matcher(body).replaceAll("iris_varyingsIn[$1].iris_FogFragCoord");
        body = rewriteFogParameters(body);
        body = ModernPackTransformer.rewriteUnsignedStrictness(body);

        boolean writesTexCoord = body.contains("gl_TexCoord");
        boolean writesFog = body.contains("gl_FogFragCoord");

        StringBuilder prologue = new StringBuilder(compatFor("#version 330 core\n", source))
                .append("// ---- Impetus/Umbra terrain stage bridge (generated) ----\n")
                .append("uniform mat4 u_ModelViewMatrix;\n")
                .append("uniform mat4 u_ProjectionMatrix;\n")
                .append("#define gl_ModelViewMatrix u_ModelViewMatrix\n")
                .append("#define gl_ProjectionMatrix u_ProjectionMatrix\n")
                .append("#define gl_ModelViewProjectionMatrix (u_ProjectionMatrix * u_ModelViewMatrix)\n")
                .append("#define gl_NormalMatrix (mat3(transpose(inverse(u_ModelViewMatrix))))\n")
                .append("in ").append(VARYING_BLOCK).append(" iris_varyingsIn[];\n");

        String main;
        switch (stage) {
            case TESS_CTRL -> {
                prologue.append("out ").append(VARYING_BLOCK).append(" iris_varyingsOut[];\n");
                main = "\nvoid main() {\n    irisMain();\n"
                        + "    iris_varyingsOut[gl_InvocationID].iris_FogFragCoord = iris_varyingsIn[gl_InvocationID].iris_FogFragCoord;\n"
                        + "    iris_varyingsOut[gl_InvocationID].iris_TexCoordArr = iris_varyingsIn[gl_InvocationID].iris_TexCoordArr;\n"
                        + "    iris_varyingsOut[gl_InvocationID].iris_AlphaCutoff = iris_varyingsIn[gl_InvocationID].iris_AlphaCutoff;\n}\n";
            }
            case TESS_EVALUATE -> {
                prologue.append("out ").append(VARYING_BLOCK).append(";\n")
                        .append("#define gl_TexCoord iris_TexCoordArr\n")
                        .append("#define gl_FogFragCoord iris_FogFragCoord\n");
                main = "\nvoid main() {\n"
                        + (writesFog ? "" : "    iris_FogFragCoord = " + barycentric("iris_FogFragCoord") + ";\n")
                        + (writesTexCoord ? "" : "    for (int i = 0; i < 4; i++) { iris_TexCoordArr[i] = " + barycentric("iris_TexCoordArr[i]") + "; }\n")
                        + "    iris_AlphaCutoff = iris_varyingsIn[0].iris_AlphaCutoff;\n"
                        + "    irisMain();\n}\n";
            }
            default -> {
                prologue.append("out ").append(VARYING_BLOCK).append(";\n")
                        .append("#define gl_TexCoord iris_TexCoordArr\n")
                        .append("#define gl_FogFragCoord iris_FogFragCoord\n")
                        .append("void iris_passVaryings();\n")
                        .append("#define EmitVertex() { iris_passVaryings(); EmitVertex(); }\n");
                main = "\nvoid iris_passVaryings() {\n"
                        + (writesFog ? "" : "    iris_FogFragCoord = iris_varyingsIn[0].iris_FogFragCoord;\n")
                        + (writesTexCoord ? "" : "    iris_TexCoordArr = iris_varyingsIn[0].iris_TexCoordArr;\n")
                        + "    iris_AlphaCutoff = iris_varyingsIn[0].iris_AlphaCutoff;\n"
                        + "}\n\nvoid main() {\n    irisMain();\n}\n";
            }
        }

        prologue.append("// ---- end generated stage bridge ----\n");
        return prologue + renameMain(body) + main;
    }

    // Returns a barycentric interpolation of a member.
    private static String barycentric(String member) {
        return "gl_TessCoord.x * iris_varyingsIn[0]." + member
                + " + gl_TessCoord.y * iris_varyingsIn[1]." + member
                + " + gl_TessCoord.z * iris_varyingsIn[2]." + member;
    }

    // Compats the for.
    static String compatFor(String prologue, String packBody) {
        int version = 330;
        Matcher declared = DECLARED_VERSION.matcher(packBody);
        if (declared.find()) {
            version = Math.max(version, com.bdmajora.impetus.umbra.util.SafeInt.parse(declared.group(1)));
        }
        if (packBody.contains("imageStore") || packBody.contains("imageLoad")
                || packBody.contains("imageAtomic")) {
            version = Math.max(version, 430);
        }
        return prologue.replaceFirst("#version 330 core", "#version " + version + " compatibility");
    }

    // Special attribute decl.
    private static final Pattern SPECIAL_ATTRIBUTE_DECL = Pattern.compile(
            "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "(?:attribute|in)\\s+(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+"
                    + "(mc_Entity|mc_midTexCoord|at_tangent|at_midBlock)\\s*;");

    // Returns the defines that map attributes to engine names.
    private static String attributeAdapterDefines(String packSource) {
        java.util.Map<String, String> declaredTypes = new java.util.HashMap<>();
        Matcher decl = SPECIAL_ATTRIBUTE_DECL.matcher(packSource);
        while (decl.find()) {
            declaredTypes.putIfAbsent(decl.group(2), decl.group(1));
        }
        return "#define mc_Entity " + adaptTo(declaredTypes.get("mc_Entity"), "iris_EntityFull") + "\n"
                + "#define mc_midTexCoord " + adaptTo(declaredTypes.get("mc_midTexCoord"), "iris_MidTexFull") + "\n"
                + "#define at_tangent " + adaptTo(declaredTypes.get("at_tangent"), "iris_Tangent") + "\n"
                + "#define at_midBlock " + adaptTo(declaredTypes.get("at_midBlock"), "iris_MidBlock") + "\n";
    }

    // Adapts the to.
    private static String adaptTo(String declaredType, String vec4Global) {
        if (declaredType == null) {
            return vec4Global;
        }
        switch (declaredType) {
            case "vec3":
                return "(" + vec4Global + ".xyz)";
            case "vec2":
                return "(" + vec4Global + ".xy)";
            case "float":
                return "(" + vec4Global + ".x)";
            case "int":
                return "int(" + vec4Global + ".x)";
            case "uint":
                return "uint(max(" + vec4Global + ".x, 0.0))";
            case "ivec2":
                return "ivec2(" + vec4Global + ".xy)";
            default:
                return vec4Global;
        }
    }

    // Returns a source without its version line.
    static String stripVersion(String source) {
        return VERSION.matcher(source).replaceFirst("");
    }

    // Renames the main function.
    static String renameMain(String source) {
        return source.replaceAll("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)", "void irisMain()");
    }

    // Turns varying declarations into in or out declarations.
    static String convertVaryings(String source, String direction) {
        return source.replaceAll(
                "(?m)^(\\s*)((?:(?:invariant|flat|smooth|noperspective|centroid)\\s+)*)varying\\b",
                "$1$2" + direction);
    }

    // Removes attribute qualifiers.
    private static String dropAttributeStorageQualifier(String source) {
        source = source.replaceAll("(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?"
                + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                + "(?:attribute|in)\\s+(?:(?:lowp|mediump|highp)\\s+)?\\w+\\s+"
                + "(?:mc_Entity|mc_midTexCoord|at_tangent|at_midBlock)\\b"
                + "(?:\\s*=\\s*[^;]+)?\\s*;\\s*(?://.*)?$", "");
        source = source.replaceAll("(?m)^(\\s*)attribute\\s+(\\w+)\\s+(\\w+)\\s*;", "$1$2 $3 = $2(0.0);");
        return source.replaceAll("(?m)^(\\s*)attribute\\s+", "$1");
    }

    // Replaces legacy built ins with modern ones.
    static String modernizeCommon(String source) {
        source = rewriteFogParameters(source);
        source = ModernPackTransformer.rewriteUnsignedStrictness(source);
        source = source.replaceAll("\\btexture\\b", "gtexture");
        source = source.replaceAll("\\btexture2DLod\\b", "textureLod");
        source = source.replaceAll("\\btexture3DLod\\b", "textureLod");
        source = source.replaceAll("\\btexture2D\\b", "texture");
        source = source.replaceAll("\\btexture3D\\b", "texture");
        source = source.replaceAll("\\bshadow2DLod\\b", "iris_shadow2DLod");
        source = source.replaceAll("\\bshadow2D\\b", "iris_shadow2D");
        return source;
    }

    // Replaces fog struct reads with the fog uniforms.
    static String rewriteFogParameters(String source) {
        return FogParameters.rewrite(source);
    }
}
