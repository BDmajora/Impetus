package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;

import java.util.regex.Pattern;

// Rewriter of pack shaders for Distant Horizons terrain.
public final class DhTerrainTransformer {
    // Prevents instantiation
    private DhTerrainTransformer() {
    }

    // Name of the LOD model view uniform.
    public static final String MODEL_VIEW = "umbra_dhModelView";
    // Name of the inverse LOD model view uniform.
    public static final String MODEL_VIEW_INVERSE = "umbra_dhModelViewInverse";
    // Name of the LOD projection uniform.
    public static final String PROJECTION = "umbra_dhProjection";
    // Name of the inverse LOD projection uniform.
    public static final String PROJECTION_INVERSE = "umbra_dhProjectionInverse";
    // Name of the LOD normal matrix uniform.
    public static final String NORMAL_MATRIX = "umbra_dhNormalMatrix";

    // Matrix aliases.
    private static final String MATRIX_ALIASES = String.join("\n",
            "uniform mat4 " + MODEL_VIEW + ";",
            "uniform mat4 " + MODEL_VIEW_INVERSE + ";",
            "uniform mat4 " + PROJECTION + ";",
            "uniform mat4 " + PROJECTION_INVERSE + ";",
            "uniform mat3 " + NORMAL_MATRIX + ";",
            "mat4 umbra_dhTextureMatrix[8] = mat4[8](mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0));",
            "#define gl_ModelViewMatrix " + MODEL_VIEW,
            "#define gl_ModelViewMatrixInverse " + MODEL_VIEW_INVERSE,
            "#define gl_ProjectionMatrix " + PROJECTION,
            "#define gl_ProjectionMatrixInverse " + PROJECTION_INVERSE,
            "#define gl_ModelViewProjectionMatrix (" + PROJECTION + " * " + MODEL_VIEW + ")",
            "#define gl_NormalMatrix " + NORMAL_MATRIX,
            "#define gl_TextureMatrix umbra_dhTextureMatrix",
            ""
    ) + "\n";

    // Fog uniforms.
    private static final String FOG_UNIFORMS = String.join("\n", FogParameters.DECLARATIONS) + "\n";

    // Lod vertex prologue.
    private static final String LOD_VERTEX_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons LOD bridge (generated) ----",
            "in uvec4 vPosition;",
            "in vec4 iris_color;",
            "in uvec4 irisExtra;",
            "uniform vec3 modelOffset;",
            "uniform float mircoOffset;",
            "uniform float worldYOffset;",
            "vec3 _vert_position;",
            "vec2 _vert_tex_light_coord;",
            "int dhMaterialId;",
            "vec4 _vert_color;",
            "vec3 _vert_normal;",
            "out vec3 iris_vBlockPos;",
            "flat out uvec2 iris_TexId;",
            "const vec3 irisNormals[6] = vec3[](vec3(0,-1,0),vec3(0,1,0),vec3(0,0,-1),vec3(0,0,1),vec3(-1,0,0),vec3(1,0,0));",
            "void _vert_init() {",
            "    uint meta = vPosition.a;",
            "    uint mirco = (meta & 0xFF00u) >> 8u; // micro offset, a 2 bit value per axis: 0b00 none, 0b01 positive, 0b11 negative, packed 0b00zzyyxx",
            "    float mx = (mirco & 1u)!=0u ? mircoOffset : 0.0;",
            "    mx = (mirco & 2u)!=0u ? -mx : mx;",
            "    float my = (mirco & 4u)!=0u ? mircoOffset : 0.0;",
            "    my = (mirco & 8u)!=0u ? -my : my;",
            "    float mz = (mirco & 16u)!=0u ? mircoOffset : 0.0;",
            "    mz = (mirco & 32u)!=0u ? -mz : mz;",
            "    uint lights = meta & 0xFFu;",
            "    _vert_position = (vPosition.xyz + vec3(mx, 0, mz));",
            "    _vert_normal = irisNormals[irisExtra.y];",
            "    dhMaterialId = int(irisExtra.x);",
            "    _vert_tex_light_coord = vec2((float(lights/16u)+0.5) / 16.0, (mod(float(lights), 16.0)+0.5) / 16.0);",
            "    iris_vBlockPos = vec3(vPosition.xyz);",
            "    iris_TexId = uvec2(irisExtra.z | (irisExtra.w << 8u), irisExtra.y);",
            "    _vert_color = iris_color;",
            "}",
            "vec4 getVertexPosition() { return vec4(modelOffset + _vert_position, 1.0); }",
            "#define gl_Vertex getVertexPosition()",
            ""
    ) + "\n";

    // Generic vertex prologue.
    private static final String GENERIC_VERTEX_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons generic object bridge (generated) ----",
            "in vec3 vPosition;",
            "in vec4 iris_color;",
            "in vec3 aScale;",
            "in ivec3 aTranslateChunk;",
            "in vec3 aTranslateSubChunk;",
            "in int aMaterial;",
            "uniform ivec3 uOffsetChunk;",
            "uniform vec3 uOffsetSubChunk;",
            "uniform ivec3 uCameraPosChunk;",
            "uniform vec3 uCameraPosSubChunk;",
            "uniform int uSkyLight;",
            "uniform int uBlockLight;",
            "vec3 _vert_position;",
            "vec2 _vert_tex_light_coord;",
            "int dhMaterialId;",
            "vec4 _vert_color;",
            "vec3 _vert_normal;",
            "const vec3 irisNormals[6] = vec3[](vec3(0,0,-1),vec3(0,0,1),vec3(-1,0,0),vec3(1,0,0),vec3(0,-1,0),vec3(0,1,0));",
            "void _vert_init() {",
            "    vec3 trans = vec3(aTranslateChunk + uOffsetChunk - uCameraPosChunk) * 16.0;",
            "    trans += (aTranslateSubChunk + uOffsetSubChunk - uCameraPosSubChunk);",
            "    mat4 transform = mat4(",
            "        aScale.x, 0.0,      0.0,      0.0,",
            "        0.0,      aScale.y, 0.0,      0.0,",
            "        0.0,      0.0,      aScale.z, 0.0,",
            "        trans.x,  trans.y,  trans.z,  1.0",
            "    );",
            "    _vert_position = (transform * vec4(vPosition, 1.0)).xyz;",
            "    _vert_normal = irisNormals[int(floor(float(gl_VertexID) / 4))];",
            "    float blockLight = (float(uBlockLight)+0.5) / 16.0;",
            "    float skyLight = (float(uSkyLight)+0.5) / 16.0;",
            "    _vert_tex_light_coord = vec2(blockLight, skyLight);",
            "    dhMaterialId = aMaterial;",
            "    _vert_color = iris_color;",
            "}",
            "vec4 getVertexPosition() { return vec4(_vert_position, 1.0); }",
            "#define gl_Vertex getVertexPosition()",
            ""
    ) + "\n";

    // Vertex aliases.
    private static final String VERTEX_ALIASES = String.join("\n",
            "#define gl_Color _vert_color",
            "#define gl_Normal _vert_normal",
            "#define gl_MultiTexCoord0 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define gl_MultiTexCoord1 vec4(_vert_tex_light_coord, 0.0, 1.0)",
            "#define gl_MultiTexCoord2 vec4(_vert_tex_light_coord, 0.0, 1.0)",
            "#define gl_MultiTexCoord3 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define gl_MultiTexCoord4 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define gl_MultiTexCoord5 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define gl_MultiTexCoord6 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define gl_MultiTexCoord7 vec4(0.0, 0.0, 0.0, 1.0)",
            "#define ftransform() (" + PROJECTION + " * (" + MODEL_VIEW + " * getVertexPosition()))",
            "out float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "out vec4 iris_TexCoordArr[4];",
            "#define gl_TexCoord iris_TexCoordArr",
            "// ---- end generated prologue ----",
            ""
    ) + "\n";

    // Lod fragment prologue.
    private static final String LOD_FRAGMENT_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons LOD bridge (generated) ----",
            "in vec3 iris_vBlockPos;",
            "flat in uvec2 iris_TexId;",
            "uniform sampler2D dhBlockAtlas;",
            "bool dh_hasTexture() { return iris_TexId.x != 0u; }",
            "vec2 dh_blockFaceUv() {",
            "    vec3 pos = fract(iris_vBlockPos);",
            "    switch (iris_TexId.y)",
            "    {",
            "        case 0u: return vec2(pos.x, 1.0 - pos.z); // down",
            "        case 1u: return vec2(pos.x, pos.z); // up",
            "        case 2u: return vec2(1.0 - pos.x, 1.0 - pos.y); // north",
            "        case 3u: return vec2(pos.x, 1.0 - pos.y); // south",
            "        case 4u: return vec2(pos.z, 1.0 - pos.y); // west",
            "        default: return vec2(1.0 - pos.z, 1.0 - pos.y); // east",
            "    }",
            "}",
            "vec4 dh_sampleTexture() {",
            "    ivec2 atlasSize = textureSize(dhBlockAtlas, 0);",
            "    vec2 tileOrigin = vec2(float(iris_TexId.x % 256u), float(iris_TexId.x / 256u)) * 16.0;",
            "    vec2 uv = (tileOrigin + dh_blockFaceUv() * 16.0) / vec2(atlasSize);",
            "    return texture(dhBlockAtlas, uv);",
            "}",
            ""
    ) + "\n";

    // Generic fragment prologue.
    private static final String GENERIC_FRAGMENT_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons generic object bridge (generated) ----",
            "bool dh_hasTexture() { return false; }",
            "vec4 dh_sampleTexture() { return vec4(1.0); }",
            ""
    ) + "\n";

    // Fragment aliases.
    private static final String FRAGMENT_ALIASES = String.join("\n",
            "in float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "in vec4 iris_TexCoordArr[4];",
            "#define gl_TexCoord iris_TexCoordArr",
            "// ---- end generated prologue ----",
            ""
    ) + "\n";

    // Frag data use.
    private static final Pattern FRAG_DATA_USE = Pattern.compile("\\bgl_Frag(?:Data|Color)\\b");

    // Rewrites a vertex stage for LODs.
    public static String transformVertexShader(String source, boolean generic) {
        boolean modern = ModernPackTransformer.isModernSource(source);
        String body = ImpetusTerrainTransformer.stripVersion(source);
        body = ImpetusTerrainTransformer.renameMain(body);
        if (modern) {
            body = ImpetusTerrainTransformer.rewriteFogParameters(body);
            body = ModernPackTransformer.rewriteUnsignedStrictness(body);
        } else {
            body = ImpetusTerrainTransformer.convertVaryings(body, "out");
            body = ImpetusTerrainTransformer.modernizeCommon(body);
        }
        String prologue = (generic ? GENERIC_VERTEX_PROLOGUE : LOD_VERTEX_PROLOGUE)
                + MATRIX_ALIASES + FOG_UNIFORMS + VERTEX_ALIASES;
        return ImpetusTerrainTransformer.compatFor(prologue, source) + body
                + "\nvoid main() {\n    _vert_init();\n    irisMain();\n}\n";
    }

    // Rewrites a fragment stage for LODs.
    public static String transformFragmentShader(String source, int[] drawBuffers, String alphaTestSnippet,
                                                 boolean generic) {
        boolean modern = ModernPackTransformer.isModernSource(source);
        String body = ImpetusTerrainTransformer.stripVersion(source);
        body = ImpetusTerrainTransformer.renameMain(body);
        if (modern) {
            body = ImpetusTerrainTransformer.rewriteFogParameters(body);
            body = ModernPackTransformer.rewriteUnsignedStrictness(body);
        } else {
            body = ImpetusTerrainTransformer.convertVaryings(body, "in");
            body = ImpetusTerrainTransformer.modernizeCommon(body);
        }
        body = DrawBuffers.rewriteFragmentOutputs(body, drawBuffers == null ? DrawBuffers.DEFAULT : drawBuffers);
        boolean usesFragData = FRAG_DATA_USE.matcher(body).find();
        String prologue = (generic ? GENERIC_FRAGMENT_PROLOGUE : LOD_FRAGMENT_PROLOGUE)
                + MATRIX_ALIASES + FOG_UNIFORMS + FRAGMENT_ALIASES;
        return ImpetusTerrainTransformer.compatFor(prologue, source)
                + (usesFragData ? ImpetusTerrainTransformer.fragDataBlock() : "")
                + body
                + "\nvoid main() {\n    irisMain();\n"
                + (usesFragData ? ImpetusTerrainTransformer.alphaDiscard(alphaTestSnippet) : "")
                + "}\n";
    }
}
