package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;

import java.util.regex.Pattern;

// Lifts a pack's dh_terrain/dh_water/dh_shadow (and dh_generic) program onto Distant Horizons' LOD vertex format, the 1.12.2 analogue of Iris's DHTerrainTransformer/DHGenericTransformer: the pack keeps writing gl_Vertex/gl_Color/gl_Normal/gl_MultiTexCoord1 and reads dhMaterialId, and a generated _vert_init decodes DH's packed vertex (uvec4 vPosition, vec4 iris_color, uvec4 irisExtra) into those. The GLSL below is Iris's verbatim where it matters (light coord scaling, normal table, texture id packing) since packs are written against it
public final class DhTerrainTransformer {
    private DhTerrainTransformer() {
    }

    // The matrix uniforms DhLodRenderProgram fills per pass; named apart from MatrixUniforms' iris_* set, which carries the CAMERA matrices, so a program's uniform update cannot overwrite the LOD projection (DH near/far) or the shadow matrices
    public static final String MODEL_VIEW = "umbra_dhModelView";
    public static final String MODEL_VIEW_INVERSE = "umbra_dhModelViewInverse";
    public static final String PROJECTION = "umbra_dhProjection";
    public static final String PROJECTION_INVERSE = "umbra_dhProjectionInverse";
    public static final String NORMAL_MATRIX = "umbra_dhNormalMatrix";

    // Iris's DHTerrainTransformer: gl_TextureMatrix[0..1] become identity for DH programs, the lightmap coord is already 0..1
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

    // gl_Fog.* stand-ins are the same per-frame uniforms the terrain bridge uses (FogParameters), fed by CommonUniforms
    private static final String FOG_UNIFORMS = String.join("\n", FogParameters.DECLARATIONS) + "\n";

    // Vertex prologue for the LOD programs: DH's vertex attributes at the locations DhLodRenderProgram binds (0/1/2, DH's own VAO layout), the per-draw uniforms, and the gl_* aliases onto the decoded globals
    private static final String LOD_VERTEX_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons LOD bridge (generated) ----",
            "in uvec4 vPosition;",     // x, y, z block position in the buffer's section + meta (skylight/blocklight byte, micro offset byte)
            "in vec4 iris_color;",     // normalized RGBA
            "in uvec4 irisExtra;",     // x: DH material id, y: face normal index, zw: block texture tile id (little endian)
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

    // Vertex prologue for dh_generic (DH's beacon beams and clouds): unit-cube vertices placed by per-instance scale/translation attributes, at the locations DH's GlGenericObjectRenderer uses; the normal comes from the vertex index since a box has 4 vertices per face in DH's face order
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

    // The rest of the vertex-stage aliases, shared by both programs: DH has no texture coordinates, the lightmap coord is 0..1 in unit 1 (unit 2 aliases it like OptiFine 1.15+), higher units are empty
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

    // Fragment prologue for the LOD programs: the varyings the vertex prologue emits plus Iris's dh_hasTexture/dh_sampleTexture helpers over DH's block texture atlas (DISTANT_HORIZONS_TEXTURES)
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

    // Fragment prologue for dh_generic: no textures, the helpers answer "untextured" like Iris's DHGenericTransformer
    private static final String GENERIC_FRAGMENT_PROLOGUE = String.join("\n",
            "#version 330 core",
            "// ---- Impetus/Umbra Distant Horizons generic object bridge (generated) ----",
            "bool dh_hasTexture() { return false; }",
            "vec4 dh_sampleTexture() { return vec4(1.0); }",
            ""
    ) + "\n";

    // The fragment-stage aliases shared by both programs
    private static final String FRAGMENT_ALIASES = String.join("\n",
            "in float iris_FogFragCoord;",
            "#define gl_FogFragCoord iris_FogFragCoord",
            "in vec4 iris_TexCoordArr[4];",
            "#define gl_TexCoord iris_TexCoordArr",
            "// ---- end generated prologue ----",
            ""
    ) + "\n";

    private static final Pattern FRAG_DATA_USE = Pattern.compile("\\bgl_Frag(?:Data|Color)\\b");

    // The vertex stage, modern (#version 130+) or legacy source alike; every pack shipping dh_* programs is modern in practice, the legacy branch only keeps a GLSL-120 pack from failing outright
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

    // The fragment stage with gl_FragData routed to the program's DRAWBUFFERS; the alpha snippet is only the pack's own alphaTest.<program> directive, since Iris injects no default discard into DH programs
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
        // Packs writing gl_FragData/gl_FragColor get the generated output array; packs with named layout(location) outputs keep their declarations
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
