package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureTransformer;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.terrain.VanillaNameTransformer;
import com.bdmajora.impetus.umbra.vertices.UmbraVertexAttributes;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Compiles a ProgramSource into a linked UmbraProgram (defines, patches, OptiFine attribute slots, link) for every immediate-mode gbuffer program; render thread only, and callers fall back to vanilla on exceptions
public final class ShaderProgramCompiler {
    public static final String HAND_LIGHTMAP_UNIFORM = "impetus_HandLightmap";

    private ShaderProgramCompiler() {
    }

    // The driver-visible source for one gbuffer/shadow program plus its dense draw-buffer routing; its own type produced by patchSource so the string patching is one reusable step
    public static final class PatchedSource {
        public final String vertex;
        public final String fragment;
        public final String geometry;
        public final int[] drawBuffers;

        PatchedSource(String vertex, String fragment, String geometry, int[] drawBuffers) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.geometry = geometry;
            this.drawBuffers = drawBuffers;
        }
    }

    // Applies defines and the name transforms without compiling, for inspection and caching
    public static PatchedSource patchSource(String name, ProgramSource source, Map<String, String> defines) {
        String vertexSource = source.getVertexSource().orElse(null);
        String fragmentSource = source.getFragmentSource().orElse(null);
        String geometrySource = source.getGeometrySource().orElse(null);

        if (vertexSource == null || fragmentSource == null) {
            throw new ProgramCreationException("Program '" + name + "' is missing a vertex or fragment stage");
        }

        // Raw texture.gbuffers.<sampler> directives: redirect the identifier to its minted customtexN name wherever the declared sampler type matches the directive's target (Umbra TextureTransformer)
        vertexSource = CustomTextureTransformer.transform(name, vertexSource, TextureStage.GBUFFERS_AND_SHADOW);
        fragmentSource = CustomTextureTransformer.transform(name, fragmentSource, TextureStage.GBUFFERS_AND_SHADOW);
        geometrySource = CustomTextureTransformer.transform(name, geometrySource, TextureStage.GBUFFERS_AND_SHADOW);

        // Modern single-source dual-stage packs (Complementary) need #version bumped to "330 compatibility" on the compat context like the fullscreen/terrain paths, or these phases fall back to vanilla (for gbuffers_clouds that is the "clouds move with the player" bug); GLSL-120 packs skip the bump but share the dense routing
        int[] drawBuffers = DrawBuffers.sanitize(
                DrawBuffers.parseActive(fragmentSource, defines), UmbraRenderTargets.MAX_COLOR_BUFFERS);
        if (ModernPackTransformer.isModernSource(fragmentSource)) {
            vertexSource = ModernPackTransformer.transform(vertexSource);
            fragmentSource = ModernPackTransformer.transform(fragmentSource);
            if (geometrySource != null) {
                geometrySource = ModernPackTransformer.transform(geometrySource);
            }
        }
        // Modern (1.17+) attribute/matrix names -> fixed-function built-ins, before the hand bridge so a hand program written against vaUV2 still goes through impetus_HandLightmap
        vertexSource = VanillaNameTransformer.transform(vertexSource);
        fragmentSource = VanillaNameTransformer.transform(fragmentSource);
        if (geometrySource != null) {
            geometrySource = VanillaNameTransformer.transform(geometrySource);
        }
        vertexSource = neutralizeUnfedVanillaAttributes(vertexSource);
        if (isFirstPersonHandProgram(name)) {
            vertexSource = injectHandLightmapBridge(vertexSource);
        }
        fragmentSource = DrawBuffers.rewriteFragmentOutputs(fragmentSource, drawBuffers);

        return new PatchedSource(
                UmbraRenderingPipeline.stabilizeShaderSource(name, applyDefines(vertexSource, defines)),
                UmbraRenderingPipeline.stabilizeShaderSource(name, applyDefines(fragmentSource, defines)),
                geometrySource == null
                        ? null
                        : UmbraRenderingPipeline.stabilizeShaderSource(name, applyDefines(geometrySource, defines)),
                drawBuffers);
    }

    // Patches, compiles, binds attributes and links; throws on any failure
    public static UmbraProgram compile(String name, ProgramSource source, Map<String, String> defines) {
        PatchedSource patched = patchSource(name, source, defines);
        String processedVertex = patched.vertex;
        String processedFragment = patched.fragment;
        String geometrySource = patched.geometry;
        int[] drawBuffers = patched.drawBuffers;

        GlShader vertexShader = null;
        GlShader fragmentShader = null;
        GlShader geometryShader = null;
        try {
            vertexShader = new GlShader(ShaderType.VERTEX, name + ".vsh", processedVertex);
            fragmentShader = new GlShader(ShaderType.FRAGMENT, name + ".fsh", processedFragment);

            ProgramBuilder builder = ProgramBuilder.begin(name)
                    .attach(vertexShader)
                    .attach(fragmentShader);

            if (geometrySource != null) {
                geometryShader = new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource);
                builder.attach(geometryShader);
            }

            bindOptifineAttributes(builder, processedVertex);

            GlProgram program = builder.link();
            return new UmbraProgram(program, drawBuffers);
        } finally {
            // The stage objects are no longer needed once the program is linked (or if linking failed).
            if (vertexShader != null) {
                vertexShader.destroy();
            }
            if (fragmentShader != null) {
                fragmentShader.destroy();
            }
            if (geometryShader != null) {
                geometryShader.destroy();
            }
        }
    }

    // Fixes mc_Entity, mc_midTexCoord and at_tangent to OptiFine's slots so the draw path matches
    private static void bindOptifineAttributes(ProgramBuilder builder, String vertexSource) {
        // Only bind slots for attributes the vertex shader actually declares, matching OptiFine's setupProgram.
        if (MC_ENTITY_DECLARATION.matcher(vertexSource).find()) {
            builder.bindAttributeLocation(UmbraVertexAttributes.MC_ENTITY_SLOT, UmbraVertexAttributes.MC_ENTITY);
        }
        if (MC_MID_TEX_COORD_DECLARATION.matcher(vertexSource).find()) {
            builder.bindAttributeLocation(UmbraVertexAttributes.MC_MID_TEX_COORD_SLOT, UmbraVertexAttributes.MC_MID_TEX_COORD);
        }
        if (AT_TANGENT_DECLARATION.matcher(vertexSource).find()) {
            builder.bindAttributeLocation(UmbraVertexAttributes.AT_TANGENT_SLOT, UmbraVertexAttributes.AT_TANGENT);
        }
    }

    // OptiFine's `attribute <type> <name>` scan, tolerant of both GLSL 120 `attribute` and 150 `in`; compiled once rather than per program, and find() replaces the old whole-source matches()
    private static Pattern attributeDeclaration(String attributeName) {
        return Pattern.compile("\\b(?:attribute|in)\\s+\\w+\\s+" + attributeName + "\\b");
    }

    private static final Pattern MC_ENTITY_DECLARATION = attributeDeclaration(UmbraVertexAttributes.MC_ENTITY);
    private static final Pattern MC_MID_TEX_COORD_DECLARATION = attributeDeclaration(UmbraVertexAttributes.MC_MID_TEX_COORD);
    private static final Pattern AT_TANGENT_DECLARATION = attributeDeclaration(UmbraVertexAttributes.AT_TANGENT);

    // The unfed attribute declarations and what each becomes, in order; see neutralizeUnfedVanillaAttributes
    private static final Pattern AT_TANGENT_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec4\\s+at_tangent\\s*;");
    private static final Pattern MID_TEX_FLOAT_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+float\\s+mc_midTexCoord\\s*;");
    private static final Pattern MID_TEX_VEC2_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec2\\s+mc_midTexCoord\\s*;");
    private static final Pattern MID_TEX_VEC3_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec3\\s+mc_midTexCoord\\s*;");
    private static final Pattern MID_TEX_VEC4_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec4\\s+mc_midTexCoord\\s*;");
    private static final String AT_TANGENT_FALLBACK = "vec4 iris_tangentFallback() { "
            + "vec3 n = normalize(gl_Normal); "
            + "vec3 t = abs(n.y) < 0.99 ? cross(n, vec3(0.0, 1.0, 0.0)) : vec3(1.0, 0.0, 0.0); "
            + "return vec4(normalize(t), 1.0); }\n"
            + "#define at_tangent (iris_tangentFallback())";

    // Immediate-mode programs never receive OptiFine's generic attributes (vanilla's draw path submits none), so at_tangent reads (0,0,0,1) and NaN-poisons the TBN, and mc_midTexCoord reads zero; both are rewritten to derive from what the fixed-function path does supply
    private static String neutralizeUnfedVanillaAttributes(String source) {
        source = AT_TANGENT_INPUT.matcher(source).replaceAll(Matcher.quoteReplacement(AT_TANGENT_FALLBACK));
        source = MID_TEX_FLOAT_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0.x");
        source = MID_TEX_VEC2_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0.xy");
        source = MID_TEX_VEC3_INPUT.matcher(source).replaceAll("#define mc_midTexCoord vec3(gl_MultiTexCoord0.xy, 0.0)");
        source = MID_TEX_VEC4_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0");
        return source;
    }

    // gbuffers_hand and gbuffers_hand_water get the hand-specific depth handling
    private static boolean isFirstPersonHandProgram(String name) {
        return "gbuffers_hand".equals(name) || "gbuffers_hand_water".equals(name);
    }

    // Replaces gl_MultiTexCoord1 with a uniform the hand renderer feeds, since vanilla lights held items through GL lighting and the fixed-function coordinate arrives ~0 (black hand); the declaration goes right after #version and its CONTIGUOUS #extension lines, not after the last #extension in the file, which Photon's includes put thousands of lines down
    private static String injectHandLightmapBridge(String source) {
        List<String> lines = new ArrayList<>(Arrays.asList(source.split("\n", -1)));
        int insertIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("#version")) {
                insertIndex = i + 1;
                break;
            }
        }
        if (insertIndex < 0) {
            lines.add(0, "#version " + GlslPreprocessor.DEFAULT_VERSION);
            insertIndex = 1;
        }
        while (insertIndex < lines.size()) {
            String line = lines.get(insertIndex).trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#extension")) {
                insertIndex++;
            } else {
                break;
            }
        }

        lines.add(insertIndex, "uniform vec2 " + HAND_LIGHTMAP_UNIFORM + ";");
        return String.join("\n", lines).replaceAll("\\bgl_MultiTexCoord1\\b",
                "vec4(" + HAND_LIGHTMAP_UNIFORM + ", 0.0, 1.0)");
    }

    // Injects the shared #define block after #version
    private static String applyDefines(String source, Map<String, String> defines) {
        List<String> lines = new ArrayList<>(Arrays.asList(source.split("\n", -1)));
        List<String> processed = GlslPreprocessor.injectDefines(lines, defines);
        return String.join("\n", processed);
    }
}
