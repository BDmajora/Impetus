package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.pipeline.rendering.ShaderSourceFixes;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
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

// Compiler that patches and links pack programs.
public final class ShaderProgramCompiler {
    // Name of the hand lightmap uniform.
    public static final String HAND_LIGHTMAP_UNIFORM = "impetus_HandLightmap";

    // Prevents instantiation
    private ShaderProgramCompiler() {
    }

    // Patched stage sources and draw buffers of a program.
    public static final class PatchedSource {
        // Vertex stage source.
        public final String vertex;
        // Fragment stage source.
        public final String fragment;
        // Geometry stage source or null.
        public final String geometry;
        // Tessellation control stage source or null.
        public final String tessControl;
        // Tessellation evaluation stage source or null.
        public final String tessEval;
        // Draw buffers of the fragment stage.
        public final int[] drawBuffers;

        // Creates a patched source from its vertex and fragment and geometry and tess control and tess eval and draw buffers.
        PatchedSource(String vertex, String fragment, String geometry, String tessControl, String tessEval, int[] drawBuffers) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.geometry = geometry;
            this.tessControl = tessControl;
            this.tessEval = tessEval;
            this.drawBuffers = drawBuffers;
        }
    }

    // Returns the patched stages of a program.
    public static PatchedSource patchSource(String name, ProgramSource source, Map<String, String> defines) {
        if (source.getVertexSource().map(vertex -> vertex.contains("mc_chunkFade")).orElse(false) && !defines.containsKey("mc_chunkFade")) {
            defines = new java.util.LinkedHashMap<>(defines);
            defines.put("mc_chunkFade", "(-1.0)");
        }

        String vertexSource = source.getVertexSource().orElse(null);
        String fragmentSource = source.getFragmentSource().orElse(null);
        String geometrySource = source.getGeometrySource().orElse(null);
        String tessControlSource = source.getTessEvalSource().isPresent() ? source.getTessControlSource().orElse(null) : null;
        String tessEvalSource = tessControlSource != null ? source.getTessEvalSource().orElse(null) : null;

        if (vertexSource == null || fragmentSource == null) {
            throw new ProgramCreationException("Program '" + name + "' is missing a vertex or fragment stage");
        }

        vertexSource = CustomTextureTransformer.transform(name, vertexSource, TextureStage.GBUFFERS_AND_SHADOW);
        fragmentSource = CustomTextureTransformer.transform(name, fragmentSource, TextureStage.GBUFFERS_AND_SHADOW);
        geometrySource = CustomTextureTransformer.transform(name, geometrySource, TextureStage.GBUFFERS_AND_SHADOW);
        tessControlSource = CustomTextureTransformer.transform(name, tessControlSource, TextureStage.GBUFFERS_AND_SHADOW);
        tessEvalSource = CustomTextureTransformer.transform(name, tessEvalSource, TextureStage.GBUFFERS_AND_SHADOW);

        int[] drawBuffers = DrawBuffers.sanitize(
                DrawBuffers.parseActive(fragmentSource, defines), UmbraRenderTargets.MAX_COLOR_BUFFERS);
        if (ModernPackTransformer.isModernSource(fragmentSource)) {
            vertexSource = ModernPackTransformer.transform(vertexSource);
            fragmentSource = ModernPackTransformer.transform(fragmentSource);
            geometrySource = transformIfPresent(geometrySource, ModernPackTransformer::transform);
            tessControlSource = transformIfPresent(tessControlSource, ModernPackTransformer::transform);
            tessEvalSource = transformIfPresent(tessEvalSource, ModernPackTransformer::transform);
        }
        vertexSource = VanillaNameTransformer.transform(vertexSource);
        fragmentSource = VanillaNameTransformer.transform(fragmentSource);
        geometrySource = transformIfPresent(geometrySource, VanillaNameTransformer::transform);
        tessControlSource = transformIfPresent(tessControlSource, VanillaNameTransformer::transform);
        tessEvalSource = transformIfPresent(tessEvalSource, VanillaNameTransformer::transform);
        vertexSource = neutralizeUnfedVanillaAttributes(vertexSource);
        if (isFirstPersonHandProgram(name)) {
            vertexSource = injectHandLightmapBridge(vertexSource);
        }
        fragmentSource = DrawBuffers.rewriteFragmentOutputs(fragmentSource, drawBuffers);

        return new PatchedSource(
                ShaderSourceFixes.stabilize(name, applyDefines(vertexSource, defines)),
                ShaderSourceFixes.stabilize(name, applyDefines(fragmentSource, defines)),
                finishStage(name, geometrySource, defines),
                finishStage(name, tessControlSource, defines),
                finishStage(name, tessEvalSource, defines),
                drawBuffers);
    }

    // Transforms a stage that is present.
    private static String transformIfPresent(String stage, java.util.function.UnaryOperator<String> transform) {
        return stage == null ? null : transform.apply(stage);
    }

    // Applies the final fixes of a stage.
    private static String finishStage(String name, String stage, Map<String, String> defines) {
        return stage == null ? null : ShaderSourceFixes.stabilize(name, applyDefines(stage, defines));
    }

    // Compiles and links a program.
    public static UmbraProgram compile(String name, ProgramSource source, Map<String, String> defines) {
        PatchedSource patched = patchSource(name, source, defines);
        String processedVertex = patched.vertex;
        String processedFragment = patched.fragment;
        String geometrySource = patched.geometry;
        int[] drawBuffers = patched.drawBuffers;

        GlShader vertexShader = null;
        GlShader fragmentShader = null;
        GlShader geometryShader = null;
        GlShader tessControlShader = null;
        GlShader tessEvalShader = null;
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
            if (patched.tessEval != null) {
                tessControlShader = new GlShader(ShaderType.TESS_CONTROL, name + ".tcs", patched.tessControl);
                builder.attach(tessControlShader);
                tessEvalShader = new GlShader(ShaderType.TESS_EVALUATION, name + ".tes", patched.tessEval);
                builder.attach(tessEvalShader);
            }

            bindOptifineAttributes(builder, processedVertex);

            GlProgram program = builder.link();
            return new UmbraProgram(program, drawBuffers);
        } finally {
            if (vertexShader != null) {
                vertexShader.destroy();
            }
            if (fragmentShader != null) {
                fragmentShader.destroy();
            }
            if (geometryShader != null) {
                geometryShader.destroy();
            }
            if (tessControlShader != null) {
                tessControlShader.destroy();
            }
            if (tessEvalShader != null) {
                tessEvalShader.destroy();
            }
        }
    }

    // Binds the attribute locations that OptiFine packs expect.
    private static void bindOptifineAttributes(ProgramBuilder builder, String vertexSource) {
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

    // Returns the pattern of an attribute declaration.
    private static Pattern attributeDeclaration(String attributeName) {
        return Pattern.compile("\\b(?:attribute|in)\\s+\\w+\\s+" + attributeName + "\\b");
    }

    // Mc entity declaration.
    private static final Pattern MC_ENTITY_DECLARATION = attributeDeclaration(UmbraVertexAttributes.MC_ENTITY);
    // Mc mid tex coord declaration.
    private static final Pattern MC_MID_TEX_COORD_DECLARATION = attributeDeclaration(UmbraVertexAttributes.MC_MID_TEX_COORD);
    // At tangent declaration.
    private static final Pattern AT_TANGENT_DECLARATION = attributeDeclaration(UmbraVertexAttributes.AT_TANGENT);

    // At tangent input.
    private static final Pattern AT_TANGENT_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec4\\s+at_tangent\\s*;");
    // Mid tex float input.
    private static final Pattern MID_TEX_FLOAT_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+float\\s+mc_midTexCoord\\s*;");
    // Mid tex vec2 input.
    private static final Pattern MID_TEX_VEC2_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec2\\s+mc_midTexCoord\\s*;");
    // Mid tex vec3 input.
    private static final Pattern MID_TEX_VEC3_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec3\\s+mc_midTexCoord\\s*;");
    // Mid tex vec4 input.
    private static final Pattern MID_TEX_VEC4_INPUT = Pattern.compile("(?m)^\\s*(?:attribute|in)\\s+vec4\\s+mc_midTexCoord\\s*;");
    // At tangent fallback.
    private static final String AT_TANGENT_FALLBACK = "vec4 iris_tangentFallback() { "
            + "vec3 n = normalize(gl_Normal); "
            + "vec3 t = abs(n.y) < 0.99 ? cross(n, vec3(0.0, 1.0, 0.0)) : vec3(1.0, 0.0, 0.0); "
            + "return vec4(normalize(t), 1.0); }\n"
            + "#define at_tangent (iris_tangentFallback())";

    // Replaces vanilla attributes that no buffer feeds with constants.
    private static String neutralizeUnfedVanillaAttributes(String source) {
        source = AT_TANGENT_INPUT.matcher(source).replaceAll(Matcher.quoteReplacement(AT_TANGENT_FALLBACK));
        source = MID_TEX_FLOAT_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0.x");
        source = MID_TEX_VEC2_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0.xy");
        source = MID_TEX_VEC3_INPUT.matcher(source).replaceAll("#define mc_midTexCoord vec3(gl_MultiTexCoord0.xy, 0.0)");
        source = MID_TEX_VEC4_INPUT.matcher(source).replaceAll("#define mc_midTexCoord gl_MultiTexCoord0");
        return source;
    }

    // Returns whether a program draws the first person hand.
    private static boolean isFirstPersonHandProgram(String name) {
        return "gbuffers_hand".equals(name) || "gbuffers_hand_water".equals(name);
    }

    // Adds the hand lightmap uniform to a stage.
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

    // Applies the defines.
    private static String applyDefines(String source, Map<String, String> defines) {
        List<String> lines = new ArrayList<>(Arrays.asList(source.split("\n", -1)));
        List<String> processed = GlslPreprocessor.injectDefines(lines, defines);
        return String.join("\n", processed);
    }
}
