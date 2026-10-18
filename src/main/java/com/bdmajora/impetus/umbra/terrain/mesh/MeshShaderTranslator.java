package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderParser;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declaration;
import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declarator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewrites a pack terrain program, already lifted onto Impetus's vertex bridge by ImpetusTerrainTransformer, into the mesh-shader stages an Nvidium port ships: the vertex stage becomes a function each mesh lane runs for its vertex, its outputs per-thread globals copied into per-vertex arrays, and every varying gets a fixed location both stages agree on, worked out from the declarations alone so the files hold for any option set
public final class MeshShaderTranslator {
    // Bumped whenever ported files stop matching what the loader and the mesh pipeline expect, so stale ports are refused rather than mis-rendered
    public static final int PORT_FORMAT = 3;
    // The first line of every ported stage, after #version
    public static final String HEADER = "// Ported for Nvidium by Impetus, port format " + PORT_FORMAT;

    // Lanes per meshlet, one vertex each: eight quads, or four when the varyings would push the meshlet past the mesh output memory
    public static final int THREADS = 32;
    public static final int LIGHT_THREADS = 16;
    // Bytes of per-vertex output a 32-lane meshlet may use; NVIDIA gives a mesh workgroup 16 KB in all
    static final int OUTPUT_BUDGET = 14 * 1024;
    // Locations a fragment stage can take in at most
    static final int MAX_LOCATIONS = 32;

    // Names the bridge prologue declares on both sides; the loader pins the fragment side of these, since the bridge is added to the fragment stage at load
    public static final String ALPHA_CUTOFF = "iris_AlphaCutoff";
    public static final String FOG = "iris_FogFragCoord";
    public static final String TEX_COORDS = "iris_TexCoordArr";
    private static final String COLOR = "gl_Color";
    // gl_Color's stand-in on both sides
    private static final String COLOR_INPUT = "umbra_FrontColorIn";

    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+(\\d+)([^\\n]*)\\n?");
    private static final Pattern WRAPPER_MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");
    private static final Pattern INVARIANT_BUILTIN = Pattern.compile("(?m)^[ \\t]*invariant[ \\t]+gl_\\w+[ \\t]*;");
    private static final Pattern INTERPOLATION = Pattern.compile("\\b(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+");
    private static final Pattern LAYOUT = Pattern.compile("\\blayout\\s*\\([^)]*\\)\\s*");
    private static final Pattern ARRAY_DIMENSION = Pattern.compile("\\[\\s*(\\d*)\\s*]");
    // The loader's record of where the bridge varyings sit, written into the ported fragment stage
    private static final Pattern BRIDGE_DIRECTIVE = Pattern.compile("/\\*\\s*NVIDIUM_BRIDGE:([^*]*)\\*/");
    // The bridge's mc_chunkFade inputs: a per-region age array indexed by draw id and the fade rate, both uniforms on the raster path
    private static final Pattern CHUNK_FADE_UNIFORMS = Pattern.compile("\\buniform\\s+float\\s+iris_Chunk(?:AgesMs\\s*\\[[^\\]]*]|FadeInv)\\s*;");
    private static final Pattern CHUNK_AGE = Pattern.compile("\\biris_ChunkAgesMs\\s*\\[\\s*drawId\\s*]");
    private static final Pattern CHUNK_FADE_RATE = Pattern.compile("\\biris_ChunkFadeInv\\b");

    // Words GLSL 4.60 reserves that a pack written for 330 or earlier may use as plain names; renamed only below #version 400
    private static final Set<String> LATER_KEYWORDS = Set.of("sample", "patch", "subroutine", "precise", "coherent", "volatile",
            "restrict", "readonly", "writeonly", "resource", "noncoherent");
    // Keywords an older pack may still use as such through an extension, renamed only when it never enables one
    private static final Set<String> EXTENSION_KEYWORDS = Set.of("buffer", "shared");
    private static final Set<String> INTERPOLATION_QUALIFIERS = Set.of("flat", "smooth", "noperspective", "centroid", "sample");

    // Locations one value of each varying type fills
    private static final Map<String, Integer> TYPE_LOCATIONS = new HashMap<>();

    static {
        for (String scalar : new String[]{"float", "int", "uint", "bool", "double"}) {
            TYPE_LOCATIONS.put(scalar, 1);
        }
        for (String prefix : new String[]{"", "i", "u", "b"}) {
            for (int n = 2; n <= 4; n++) {
                TYPE_LOCATIONS.put(prefix + "vec" + n, 1);
            }
        }
        TYPE_LOCATIONS.put("dvec2", 1);
        TYPE_LOCATIONS.put("dvec3", 2);
        TYPE_LOCATIONS.put("dvec4", 2);
        for (int columns = 2; columns <= 4; columns++) {
            TYPE_LOCATIONS.put("mat" + columns, columns);
            for (int rows = 2; rows <= 4; rows++) {
                TYPE_LOCATIONS.put("mat" + columns + "x" + rows, columns);
            }
        }
    }

    // The vertex built-ins a mesh stage lacks, as globals the pack's code writes in their place
    private static final String BUILTINS = String.join("\n",
            "vec4 umbra_Position = vec4(0.0, 0.0, 0.0, 1.0);",
            "float umbra_PointSize = 1.0;",
            "vec4 umbra_ClipVertex = vec4(0.0);",
            "float umbra_ClipDistance[8];",
            "vec4 umbra_FrontColor = vec4(1.0);",
            "vec4 umbra_BackColor = vec4(1.0);",
            "uint umbra_VertexID = 0u;",
            "float umbra_SectionAgeMs = 0.0;",
            "float " + AtlasSampling.MIPPED + " = 1.0;",
            "#define gl_Position umbra_Position",
            "#define gl_PointSize umbra_PointSize",
            "#define gl_ClipVertex umbra_ClipVertex",
            "#define gl_ClipDistance umbra_ClipDistance",
            "#define gl_FrontColor umbra_FrontColor",
            "#define gl_BackColor umbra_BackColor",
            "#define gl_VertexID int(umbra_VertexID)",
            "#define gl_InstanceID 0",
            "#define gl_BaseVertex 0",
            "#define gl_BaseInstance 0",
            "#define gl_DrawID 0",
            "") + "\n";

    private static final String MESH_LAYOUT = String.join("\n",
            "layout(local_size_x = UMBRA_MESH_THREADS) in;",
            "layout(triangles, max_vertices = UMBRA_MESH_THREADS, max_primitives = UMBRA_MESH_THREADS / 2) out;",
            "") + "\n";

    private static final String UNDEFINE_BUILTINS = String.join("\n",
            "#undef gl_Position", "#undef gl_PointSize", "#undef gl_ClipVertex", "#undef gl_ClipDistance", "#undef gl_FrontColor",
            "#undef gl_BackColor", "#undef gl_VertexID", "#undef gl_InstanceID", "#undef gl_BaseVertex", "#undef gl_BaseInstance",
            "#undef gl_DrawID", "") + "\n";

    private static final String EXTENSIONS = String.join("\n",
            "#extension GL_NV_mesh_shader : require",
            "#extension GL_NV_gpu_shader5 : require",
            "#extension GL_NV_shader_buffer_load : require",
            "#extension GL_KHR_shader_subgroup_basic : require",
            "#extension GL_KHR_shader_subgroup_arithmetic : require",
            "#extension GL_KHR_shader_subgroup_clustered : require",
            "#extension GL_KHR_shader_subgroup_quad : require",
            "#extension GL_KHR_shader_subgroup_shuffle_relative : require",
            "") + "\n";

    private MeshShaderTranslator() {
    }

    // One varying both stages carry: its GLSL type and array suffix, first location, locations filled, and the fragment side's interpolation qualifiers
    public record Varying(String name, String type, String arraySuffix, int location, int locations, String interpolation) {
    }

    // One ported program: the mesh stage (both opaque and translucent forms, picked by UMBRA_TRANSLUCENT at load) and the fragment stage with its inputs pinned
    public record Port(String mesh, String fragment, List<Varying> varyings, int threads) {
    }

    // Thrown for a program shape the port cannot carry; that program keeps only its OpenGL stages
    public static final class UnsupportedProgramException extends RuntimeException {
        public UnsupportedProgramException(String message) {
            super(message);
        }
    }

    // Ports one program from its bridged vertex stage and the pack's own fragment stage
    public static Port port(String bridgedVertex, String fragment) {
        List<Varying> varyings = assignLocations(fragment);
        int threads = threadsFor(varyings);
        return new Port(meshShader(bridgedVertex, varyings, threads), fragmentShader(fragment, varyings), varyings, threads);
    }

    // Fixed locations for everything the fragment stage may read, across every #if branch: the bridge's alpha cutoff, its fog and texture-coordinate arrays and the gl_Color stand-in when the fragment stage uses them, the pack's own inputs in declaration order, then the quad's mipmap flag when it samples the atlas
    public static List<Varying> assignLocations(String fragment) {
        Map<String, Varying> varyings = new LinkedHashMap<>();
        int[] next = {0};
        add(varyings, next, ALPHA_CUTOFF, "float", "", "flat ");
        if (uses(fragment, "gl_FogFragCoord") || uses(fragment, FOG)) {
            add(varyings, next, FOG, "float", "", "");
        }
        if (uses(fragment, "gl_TexCoord") || uses(fragment, TEX_COORDS)) {
            add(varyings, next, TEX_COORDS, "vec4", "[4]", "");
        }
        if (uses(fragment, COLOR)) {
            add(varyings, next, COLOR, "vec4", "", "");
        }

        for (Declaration declaration : GlslDeclarations.collect(fragment)) {
            if (!isInput(declaration)) {
                continue;
            }
            if (declaration.isBlock()) {
                throw new UnsupportedProgramException("fragment input block " + declaration.type());
            }
            StringBuilder interpolation = new StringBuilder();
            for (String qualifier : declaration.qualifiers()) {
                if (INTERPOLATION_QUALIFIERS.contains(qualifier)) {
                    interpolation.append(qualifier).append(' ');
                }
            }
            for (Declarator declarator : declaration.declarators()) {
                if (!varyings.containsKey(declarator.name())) {
                    add(varyings, next, declarator.name(), declaration.type(), declarator.arraySuffix(), interpolation.toString());
                }
            }
        }

        if (AtlasSampling.samplesAtlas(fragment)) {
            add(varyings, next, AtlasSampling.MIPPED, "float", "", "flat ");
        }

        if (next[0] > MAX_LOCATIONS) {
            throw new UnsupportedProgramException(next[0] + " varying locations");
        }
        return new ArrayList<>(varyings.values());
    }

    // Appends a varying at the next free location
    private static void add(Map<String, Varying> varyings, int[] next, String name, String type, String arraySuffix, String interpolation) {
        Integer perValue = TYPE_LOCATIONS.get(type);
        if (perValue == null) {
            throw new UnsupportedProgramException("varying " + name + " of type " + type);
        }
        int locations = perValue * arrayLength(name, arraySuffix);
        varyings.put(name, new Varying(name, type, arraySuffix, next[0], locations, interpolation));
        next[0] += locations;
    }

    // Elements in an array suffix, 1 for none; only literal sizes, since a location must not move with an option
    static int arrayLength(String name, String arraySuffix) {
        int length = 1;
        Matcher dimension = ARRAY_DIMENSION.matcher(arraySuffix);
        int consumed = 0;
        while (dimension.find()) {
            if (dimension.group(1).isEmpty()) {
                throw new UnsupportedProgramException("varying " + name + " has an unsized array");
            }
            length *= Integer.parseInt(dimension.group(1));
            consumed = dimension.end();
        }
        if (consumed != arraySuffix.length()) {
            throw new UnsupportedProgramException("varying " + name + " is sized by an expression");
        }
        return length;
    }

    // Lanes per meshlet for these varyings: a full warp unless the per-vertex outputs, counted over every branch, would not fit
    public static int threadsFor(List<Varying> varyings) {
        int locations = 1;
        for (Varying varying : varyings) {
            locations += varying.locations();
        }
        return THREADS * locations * 16 > OUTPUT_BUDGET ? LIGHT_THREADS : THREADS;
    }

    // The mesh stage, every template inlined so the file stands alone: the pack's vertex code with its interface turned into globals, each output fed from whichever #if branch declared it, then both the opaque and the translucent mesh mains behind UMBRA_TRANSLUCENT
    public static String meshShader(String bridgedVertex, List<Varying> varyings, int threads) {
        Matcher version = VERSION.matcher(bridgedVertex);
        if (!version.find()) {
            throw new UnsupportedProgramException("vertex stage without #version");
        }

        int declared = Integer.parseInt(version.group(1));
        boolean compatibility = version.group(2).contains("compatibility");

        Map<String, Varying> byName = new HashMap<>();
        for (Varying varying : varyings) {
            byName.put(varying.name(), varying);
        }

        String body = bridgedVertex.substring(0, version.start()) + bridgedVertex.substring(version.end());
        body = INVARIANT_BUILTIN.matcher(body).replaceAll("");
        // A mesh lane gets its section's age from the task stage and the fade rate from the scene block
        body = CHUNK_FADE_UNIFORMS.matcher(body).replaceAll("");
        body = CHUNK_AGE.matcher(body).replaceAll("umbra_SectionAgeMs");
        body = CHUNK_FADE_RATE.matcher(body).replaceAll("umbra_ChunkFadeInv");
        body = GlslDeclarations.rewrite(body, declaration -> vertexGlobal(declaration, byName));
        body = WRAPPER_MAIN.matcher(body).replaceAll("void umbra_vertexMain()");
        boolean extensions = body.contains("GL_ARB_shader_storage_buffer_object") || body.contains("GL_ARB_compute_shader");
        boolean rename = declared < 400;
        if (rename) {
            body = renameLaterKeywords(body, extensions);
        }

        StringBuilder out = new StringBuilder(body.length() + 16384);
        out.append("#version 460 ").append(compatibility ? "compatibility" : "core").append('\n');
        out.append(HEADER).append('\n');
        out.append(EXTENSIONS);
        out.append("#define UMBRA_MESH_THREADS ").append(threads).append('\n');
        // The output layout comes before any output, since the per-vertex arrays take their size from max_vertices
        out.append(MESH_LAYOUT);
        out.append("#ifdef UMBRA_TRANSLUCENT\n#define UMBRA_WRITABLE_TERRAIN 1\n#endif\n");
        out.append("#import <impetus:mesh/pack/scene.glsl>\n");
        out.append("#import <impetus:mesh/cull.glsl>\n");
        out.append(BUILTINS);
        out.append("// ---- pack vertex stage ----\n");
        out.append(body);
        out.append("\n// ---- end pack vertex stage ----\n");
        out.append(UNDEFINE_BUILTINS);
        // The copies name the pack's globals, so they take the same renames
        StringBuilder outputs = new StringBuilder();
        appendOutputs(outputs, varyings);
        out.append(rename ? renameLaterKeywords(outputs.toString(), extensions) : outputs);
        out.append("#import <impetus:mesh/pack/fetch.glsl>\n");
        out.append("#import <impetus:mesh/pack/emit.glsl>\n");
        out.append("#ifdef UMBRA_TRANSLUCENT\n#import <impetus:mesh/pack/translucent_main.glsl>\n#else\n#import <impetus:mesh/pack/opaque_main.glsl>\n#endif\n");
        return String.join("\n", ShaderParser.parseShader(out.toString(), ShaderLoader::getShaderSource)) + "\n";
    }

    // The fragment stage with every input pinned, the gl_Color stand-in, and the record of where the bridge varyings the loader adds belong
    public static String fragmentShader(String fragment, List<Varying> varyings) {
        Map<String, Varying> byName = new HashMap<>();
        StringBuilder bridge = new StringBuilder("/* NVIDIUM_BRIDGE:");
        for (Varying varying : varyings) {
            byName.put(varying.name(), varying);
            if (varying.name().startsWith("iris_")) {
                bridge.append(' ').append(varying.name()).append('=').append(varying.location());
            }
        }
        bridge.append(" */\n");

        String body = GlslDeclarations.rewrite(fragment, declaration -> fragmentInput(declaration, byName));
        Matcher declared = VERSION.matcher(body);
        // Block members match by name, so the fragment stage takes the same keyword renames as the mesh stage
        if (!declared.find() || Integer.parseInt(declared.group(1)) < 400) {
            body = renameLaterKeywords(body);
        }
        Varying mipped = byName.get(AtlasSampling.MIPPED);
        if (mipped != null) {
            body = AtlasSampling.rewrite(body);
        }
        Matcher version = VERSION.matcher(body);
        boolean versioned = version.find();

        StringBuilder header = new StringBuilder(HEADER).append('\n');
        // Locations on fragment inputs arrived in 4.10; below that they need separate_shader_objects
        if (!versioned || Integer.parseInt(version.group(1)) < 410) {
            header.append("#extension GL_ARB_separate_shader_objects : enable\n");
        }
        header.append(bridge);
        Varying color = byName.get(COLOR);
        if (color != null) {
            header.append(inputBlock(color.location(), "vec4 " + COLOR_INPUT)).append("\n#define gl_Color ").append(COLOR_INPUT).append('\n');
        }
        if (mipped != null) {
            header.append(inputBlock(mipped.location(), "flat float " + AtlasSampling.MIPPED)).append('\n').append(AtlasSampling.BIAS_DEFINE);
        }

        return versioned
                ? body.substring(0, version.end()) + header + body.substring(version.end())
                : header + body;
    }

    // At load: turns the bridge inputs ImpetusTerrainTransformer declared in a transformed fragment stage into input blocks at the locations its port recorded, and lifts the stage to GLSL 1.50, the first with input blocks
    public static String pinBridgeInputs(String transformedFragment) {
        Matcher directive = BRIDGE_DIRECTIVE.matcher(transformedFragment);
        if (!directive.find()) {
            return transformedFragment;
        }
        String result = transformedFragment;
        for (String entry : directive.group(1).trim().split("\\s+")) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = entry.substring(0, equals);
            int location = Integer.parseInt(entry.substring(equals + 1));
            Matcher input = Pattern.compile("(?m)^([ \\t]*)((?:flat[ \\t]+)?)in[ \\t]+(\\w+)[ \\t]+(" + Pattern.quote(name) + "\\b[^;]*);").matcher(result);
            if (input.find()) {
                result = result.substring(0, input.start()) + input.group(1) + inputBlock(location, input.group(2) + input.group(3) + ' ' + input.group(4))
                        + result.substring(input.end());
            }
        }
        Matcher version = VERSION.matcher(result);
        if (version.find() && Integer.parseInt(version.group(1)) < 150) {
            result = result.substring(0, version.start()) + "#version 150 compatibility\n" + result.substring(version.end());
        }
        return result;
    }

    // Whether a declaration is a stage input
    private static boolean isInput(Declaration declaration) {
        return declaration.storage().equals("in") || declaration.storage().equals("varying");
    }

    // Whether the source mentions an identifier as a whole word
    private static boolean uses(String source, String identifier) {
        return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\b").matcher(source).find();
    }

    // A vertex-stage global under the mesh stage: inputs become zeroed globals the fetch fills, outputs plain globals, and a fed output also declares its per-vertex array and marks itself fed inside the very #if branch it sits in
    static String vertexGlobal(Declaration declaration, Map<String, Varying> byName) {
        switch (declaration.storage()) {
            case "in", "attribute" -> {
                if (declaration.isBlock()) {
                    throw new UnsupportedProgramException("vertex input block " + declaration.type());
                }
                StringBuilder out = new StringBuilder();
                for (Declarator declarator : declaration.declarators()) {
                    out.append(precisionOf(declaration)).append(declaration.type()).append(' ').append(declarator.name()).append(declarator.arraySuffix());
                    if (declarator.arraySuffix().isEmpty()) {
                        out.append(" = ").append(declaration.type()).append("(0)");
                    }
                    out.append(';');
                }
                return out.toString();
            }
            case "out", "varying" -> {
                if (declaration.isBlock()) {
                    return outputBlock(declaration);
                }
                StringBuilder out = new StringBuilder();
                for (Declarator declarator : declaration.declarators()) {
                    out.append(precisionOf(declaration)).append(declaration.type()).append(' ').append(declarator.name())
                            .append(declarator.arraySuffix());
                    if (!declarator.initializer().isEmpty()) {
                        out.append(' ').append(declarator.initializer());
                    }
                    out.append(';');
                    Varying varying = byName.get(declarator.name());
                    if (varying != null) {
                        out.append("\n#define UMBRA_FED_").append(varying.location()).append(" 1\n")
                                .append(outputBlock(varying.location(), varying.interpolation() + declaration.type() + ' ' + declarator.name()
                                        + declarator.arraySuffix()));
                    }
                }
                return out.toString();
            }
            default -> {
                return null;
            }
        }
    }

    // An output block as a struct-typed global, or loose globals when it has no instance name; gl_PerVertex redeclarations go entirely
    private static String outputBlock(Declaration declaration) {
        if (declaration.type().equals("gl_PerVertex")) {
            return "";
        }
        String members = LAYOUT.matcher(INTERPOLATION.matcher(declaration.blockBody()).replaceAll("")).replaceAll("");
        Declarator instance = declaration.declarators().getFirst();
        if (instance.name().isEmpty()) {
            return members;
        }
        String struct = "umbra_block_" + declaration.type();
        return "struct " + struct + " {" + members + "}; " + struct + ' ' + instance.name() + instance.arraySuffix() + ';';
    }

    // A fragment input declaration as one input block per variable at its location, any location the pack chose dropped since its numbering no longer applies
    static String fragmentInput(Declaration declaration, Map<String, Varying> byName) {
        if (!isInput(declaration) || declaration.isBlock()) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        for (Declarator declarator : declaration.declarators()) {
            StringBuilder member = new StringBuilder();
            for (String qualifier : declaration.qualifiers()) {
                member.append(qualifier).append(' ');
            }
            member.append(declaration.type()).append(' ').append(declarator.name()).append(declarator.arraySuffix());
            Varying varying = byName.get(declarator.name());
            out.append(varying != null ? inputBlock(varying.location(), member.toString()) : "in " + member + ";");
        }
        return out.toString();
    }

    // Copies each fed output into its per-vertex block; gl_Color's stand-in and the mipmap flag are always declared, since umbra_FrontColor and umbra_Mipped always exist
    private static void appendOutputs(StringBuilder out, List<Varying> varyings) {
        StringBuilder writes = new StringBuilder();
        for (Varying varying : varyings) {
            String output = "umbra_o" + varying.location() + "[lane].";
            if (varying.name().equals(COLOR)) {
                out.append(outputBlock(varying.location(), "vec4 " + COLOR_INPUT));
                writes.append("    ").append(output).append(COLOR_INPUT).append(" = umbra_FrontColor;\n");
            } else if (varying.name().equals(AtlasSampling.MIPPED)) {
                out.append(outputBlock(varying.location(), "flat float " + AtlasSampling.MIPPED));
                writes.append("    ").append(output).append(AtlasSampling.MIPPED).append(" = ").append(AtlasSampling.MIPPED).append(";\n");
            } else {
                writes.append("#ifdef UMBRA_FED_").append(varying.location()).append('\n')
                        .append("    ").append(output).append(varying.name()).append(" = ").append(varying.name()).append(";\n")
                        .append("#endif\n");
            }
        }
        out.append("void umbra_writeOutputs(uint lane) {\n").append(writes).append("}\n");
    }

    // One varying's per-vertex output: a block of its own at its location, since NVIDIA counts a loose per-vertex array as one location per vertex and its neighbours then overlap
    private static String outputBlock(int location, String member) {
        return "layout(location = " + location + ") out umbra_v" + location + " { " + member + "; } umbra_o" + location + "[];\n";
    }

    // The matching fragment input block, whose member keeps the varying's own name in the stage's global scope
    private static String inputBlock(int location, String member) {
        return "layout(location = " + location + ") in umbra_v" + location + " { " + member + "; };";
    }

    // Precision qualifiers survive onto a plain global, the rest of the stage interface does not
    private static String precisionOf(Declaration declaration) {
        StringBuilder out = new StringBuilder();
        for (String qualifier : declaration.qualifiers()) {
            if (qualifier.equals("highp") || qualifier.equals("mediump") || qualifier.equals("lowp")) {
                out.append(qualifier).append(' ');
            }
        }
        return out.toString();
    }

    // Renames identifiers that are keywords at 460 but were free names where the pack was written
    static String renameLaterKeywords(String body) {
        return renameLaterKeywords(body, body.contains("GL_ARB_shader_storage_buffer_object") || body.contains("GL_ARB_compute_shader"));
    }

    // The same, told whether the pack enables an extension that makes buffer and shared keywords already
    private static String renameLaterKeywords(String body, boolean extensions) {
        StringBuilder words = new StringBuilder();
        for (String word : LATER_KEYWORDS) {
            words.append(words.isEmpty() ? "" : "|").append(word);
        }
        if (!extensions) {
            for (String word : EXTENSION_KEYWORDS) {
                words.append('|').append(word);
            }
        }
        return Pattern.compile("\\b(" + words + ")\\b").matcher(body).replaceAll("umbra_$1");
    }
}
