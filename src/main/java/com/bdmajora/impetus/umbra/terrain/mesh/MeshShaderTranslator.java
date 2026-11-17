package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderParser;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declaration;
import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declarator;

import com.bdmajora.impetus.umbra.util.SafeInt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ports the vertex and fragment stages of a terrain program to an Nvidium mesh shader.
public final class MeshShaderTranslator {
    // Version of the port output.
    public static final int PORT_FORMAT = 4;
    // First line of every ported file.
    public static final String HEADER = "// Ported for Nvidium by Impetus, port format " + PORT_FORMAT;

    // Thread count of a mesh workgroup.
    public static final int THREADS = 32;
    // Thread count when the outputs exceed the budget.
    public static final int LIGHT_THREADS = 16;
    // Bytes of mesh outputs a workgroup may write.
    static final int OUTPUT_BUDGET = 14 * 1024;
    // Largest number of varying locations.
    static final int MAX_LOCATIONS = 32;

    // Name of the alpha cutoff varying.
    public static final String ALPHA_CUTOFF = "iris_AlphaCutoff";
    // Name of the fog coordinate varying.
    public static final String FOG = "iris_FogFragCoord";
    // Name of the texture coordinate array varying.
    public static final String TEX_COORDS = "iris_TexCoordArr";
    // Name of the vertex color builtin.
    private static final String COLOR = "gl_Color";
    // Name of the color input of the fragment stage.
    private static final String COLOR_INPUT = "umbra_FrontColorIn";

    // Pattern of the version line.
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+(\\d+)([^\\n]*)\\n?");
    // Pattern of the main function.
    private static final Pattern WRAPPER_MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");
    // Pattern of an invariant builtin declaration.
    private static final Pattern INVARIANT_BUILTIN = Pattern.compile("(?m)^[ \\t]*invariant[ \\t]+gl_\\w+[ \\t]*;");
    // Pattern of an interpolation qualifier.
    private static final Pattern INTERPOLATION = Pattern.compile("\\b(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+");
    // Pattern of a layout qualifier.
    private static final Pattern LAYOUT = Pattern.compile("\\blayout\\s*\\([^)]*\\)\\s*");
    // Pattern of one array dimension.
    private static final Pattern ARRAY_DIMENSION = Pattern.compile("\\[\\s*(\\d*)\\s*]");
    // Pattern of a bridge directive comment.
    private static final Pattern BRIDGE_DIRECTIVE = Pattern.compile("/\\*\\s*NVIDIUM_BRIDGE:([^*]*)\\*/");
    // Pattern of the chunk fade uniform declarations.
    private static final Pattern CHUNK_FADE_UNIFORMS = Pattern.compile("\\buniform\\s+float\\s+iris_Chunk(?:AgesMs\\s*\\[[^\\]]*]|FadeInv)\\s*;");
    // Pattern of a chunk age read.
    private static final Pattern CHUNK_AGE = Pattern.compile("\\biris_ChunkAgesMs\\s*\\[\\s*drawId\\s*]");
    // Pattern of the chunk fade rate.
    private static final Pattern CHUNK_FADE_RATE = Pattern.compile("\\biris_ChunkFadeInv\\b");

    // Keywords of later GLSL versions that are renamed.
    private static final Set<String> LATER_KEYWORDS = Set.of("sample", "patch", "subroutine", "precise", "coherent", "volatile",
            "restrict", "readonly", "writeonly", "resource", "noncoherent");
    // Keywords that are renamed unless their extensions are enabled.
    private static final Set<String> EXTENSION_KEYWORDS = Set.of("buffer", "shared");
    // Pattern of the later keywords.
    private static final Pattern LATER_ONLY = keywordPattern(LATER_KEYWORDS, Set.of());
    // Pattern of the later keywords and the extension keywords.
    private static final Pattern LATER_AND_EXTENSION = keywordPattern(LATER_KEYWORDS, EXTENSION_KEYWORDS);
    // Interpolation qualifiers.
    private static final Set<String> INTERPOLATION_QUALIFIERS = Set.of("flat", "smooth", "noperspective", "centroid", "sample");

    // Locations of one value of each varying type.
    private static final Map<String, Integer> TYPE_LOCATIONS = new HashMap<>();

    // Fills the location counts of the scalar and vector and matrix types
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

    // Stand ins of the vertex builtins.
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

    // Layout of the mesh workgroup.
    private static final String MESH_LAYOUT = String.join("\n",
            "layout(local_size_x = UMBRA_MESH_THREADS) in;",
            "layout(triangles, max_vertices = UMBRA_MESH_THREADS, max_primitives = UMBRA_MESH_THREADS / 2) out;",
            "") + "\n";

    // Removal of the builtin stand ins.
    private static final String UNDEFINE_BUILTINS = String.join("\n",
            "#undef gl_Position", "#undef gl_PointSize", "#undef gl_ClipVertex", "#undef gl_ClipDistance", "#undef gl_FrontColor",
            "#undef gl_BackColor", "#undef gl_VertexID", "#undef gl_InstanceID", "#undef gl_BaseVertex", "#undef gl_BaseInstance",
            "#undef gl_DrawID", "") + "\n";

    // Extensions a mesh shader needs.
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

    // Prevents instantiation
    private MeshShaderTranslator() {
    }

    // Varying with its type and locations.
    public record Varying(String name, String type, String arraySuffix, int location, int locations, String interpolation) {
    }

    // Ported stages with their varyings and thread count.
    public record Port(String mesh, String fragment, List<Varying> varyings, int threads) {
    }

    // Failure of a program the port cannot handle.
    public static final class UnsupportedProgramException extends RuntimeException {
        // Creates a failure with a reason.
        public UnsupportedProgramException(String message) {
            super(message);
        }
    }

    // Ports a bridged vertex stage and a fragment stage.
    public static Port port(String bridgedVertex, String fragment) {
        List<Varying> varyings = assignLocations(fragment);
        int threads = threadsFor(varyings);
        return new Port(meshShader(bridgedVertex, varyings, threads), fragmentShader(fragment, varyings), varyings, threads);
    }

    // Returns the varyings the fragment stage reads with their locations and rejects too many.
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

    // Adds a varying at the next location and rejects an unknown type or an overflow.
    private static void add(Map<String, Varying> varyings, int[] next, String name, String type, String arraySuffix, String interpolation) {
        Integer perValue = TYPE_LOCATIONS.get(type);
        if (perValue == null) {
            throw new UnsupportedProgramException("varying " + name + " of type " + type);
        }
        int locations = arrayLength(name, arraySuffix);
        // Rejects a varying whose locations overflow the running total
        try {
            locations = Math.multiplyExact(perValue, locations);
            int end = Math.addExact(next[0], locations);
            varyings.put(name, new Varying(name, type, arraySuffix, next[0], locations, interpolation));
            next[0] = end;
        } catch (ArithmeticException e) {
            throw new UnsupportedProgramException("varying " + name + " is too large");
        }
    }

    // Returns the element count of an array suffix and rejects an unsized or computed or overflowing array.
    static int arrayLength(String name, String arraySuffix) {
        int length = 1;
        Matcher dimension = ARRAY_DIMENSION.matcher(arraySuffix);
        int consumed = 0;
        while (dimension.find()) {
            if (dimension.group(1).isEmpty()) {
                throw new UnsupportedProgramException("varying " + name + " has an unsized array");
            }
            // Rejects an array whose location count overflows
            try {
                length = Math.multiplyExact(length, SafeInt.parse(dimension.group(1)));
            } catch (ArithmeticException e) {
                throw new UnsupportedProgramException("varying " + name + " is too large");
            }
            consumed = dimension.end();
        }
        if (consumed != arraySuffix.length()) {
            throw new UnsupportedProgramException("varying " + name + " is sized by an expression");
        }
        return length;
    }

    // Returns the thread count that keeps the outputs within the budget.
    public static int threadsFor(List<Varying> varyings) {
        int locations = 1;
        for (Varying varying : varyings) {
            locations += varying.locations();
        }
        return THREADS * locations * 16 > OUTPUT_BUDGET ? LIGHT_THREADS : THREADS;
    }

    // Returns the mesh shader of a bridged vertex stage.
    public static String meshShader(String bridgedVertex, List<Varying> varyings, int threads) {
        Matcher version = VERSION.matcher(bridgedVertex);
        if (!version.find()) {
            throw new UnsupportedProgramException("vertex stage without #version");
        }

        int declared = SafeInt.parse(version.group(1));
        boolean compatibility = version.group(2).contains("compatibility");

        Map<String, Varying> byName = new HashMap<>();
        for (Varying varying : varyings) {
            byName.put(varying.name(), varying);
        }

        String body = bridgedVertex.substring(0, version.start()) + bridgedVertex.substring(version.end());
        body = INVARIANT_BUILTIN.matcher(body).replaceAll("");
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
        out.append(MESH_LAYOUT);
        out.append("#ifdef UMBRA_TRANSLUCENT\n#define UMBRA_WRITABLE_TERRAIN 1\n#endif\n");
        out.append("#import <impetus:mesh/pack/scene.glsl>\n");
        out.append("#import <impetus:mesh/cull.glsl>\n");
        out.append(BUILTINS);
        out.append("// ---- pack vertex stage ----\n");
        out.append(body);
        out.append("\n// ---- end pack vertex stage ----\n");
        out.append(UNDEFINE_BUILTINS);
        StringBuilder outputs = new StringBuilder();
        appendOutputs(outputs, varyings);
        out.append(rename ? renameLaterKeywords(outputs.toString(), extensions) : outputs);
        out.append("#import <impetus:mesh/pack/fetch.glsl>\n");
        out.append("#import <impetus:mesh/pack/emit.glsl>\n");
        out.append("#ifdef UMBRA_TRANSLUCENT\n#import <impetus:mesh/pack/translucent_main.glsl>\n#else\n#import <impetus:mesh/pack/opaque_main.glsl>\n#endif\n");
        return String.join("\n", ShaderParser.parseShader(out.toString(), ShaderLoader::getShaderSource)) + "\n";
    }

    // Returns the fragment stage that reads the mesh outputs.
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
        if (!declared.find() || com.bdmajora.impetus.umbra.util.SafeInt.parse(declared.group(1)) < 400) {
            body = renameLaterKeywords(body);
        }
        Varying mipped = byName.get(AtlasSampling.MIPPED);
        if (mipped != null) {
            body = AtlasSampling.rewrite(body);
        }
        Matcher version = VERSION.matcher(body);
        boolean versioned = version.find();

        StringBuilder header = new StringBuilder(HEADER).append('\n');
        if (!versioned || com.bdmajora.impetus.umbra.util.SafeInt.parse(version.group(1)) < 410) {
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

    // Pins the inputs named by bridge directives to their locations.
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
        if (version.find() && com.bdmajora.impetus.umbra.util.SafeInt.parse(version.group(1)) < 150) {
            result = result.substring(0, version.start()) + "#version 150 compatibility\n" + result.substring(version.end());
        }
        return result;
    }

    // Returns whether a declaration is a stage input.
    private static boolean isInput(Declaration declaration) {
        return declaration.storage().equals("in") || declaration.storage().equals("varying");
    }

    // Returns whether a source uses an identifier.
    private static boolean uses(String source, String identifier) {
        return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\b").matcher(source).find();
    }

    // Returns the mesh shader form of a vertex global or null to keep it.
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

    // Returns the members of an output block as globals or a struct instance.
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

    // Returns the fragment form of an input or null to keep it.
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

    // Appends the output blocks and the function that writes them.
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

    // Returns an output block at a location.
    private static String outputBlock(int location, String member) {
        return "layout(location = " + location + ") out umbra_v" + location + " { " + member + "; } umbra_o" + location + "[];\n";
    }

    // Returns an input block at a location.
    private static String inputBlock(int location, String member) {
        return "layout(location = " + location + ") in umbra_v" + location + " { " + member + "; };";
    }

    // Returns the precision qualifiers of a declaration.
    private static String precisionOf(Declaration declaration) {
        StringBuilder out = new StringBuilder();
        for (String qualifier : declaration.qualifiers()) {
            if (qualifier.equals("highp") || qualifier.equals("mediump") || qualifier.equals("lowp")) {
                out.append(qualifier).append(' ');
            }
        }
        return out.toString();
    }

    // Renames the later keywords of a body.
    static String renameLaterKeywords(String body) {
        return renameLaterKeywords(body, body.contains("GL_ARB_shader_storage_buffer_object") || body.contains("GL_ARB_compute_shader"));
    }

    // Renames the later keywords and the extension keywords when their extensions are absent.
    private static String renameLaterKeywords(String body, boolean extensions) {
        return (extensions ? LATER_ONLY : LATER_AND_EXTENSION).matcher(body).replaceAll("umbra_$1");
    }

    // Returns a pattern of whole words from keyword sets.
    private static Pattern keywordPattern(Set<String> first, Set<String> second) {
        StringBuilder words = new StringBuilder();
        for (String word : first) {
            words.append(words.isEmpty() ? "" : "|").append(word);
        }
        for (String word : second) {
            words.append('|').append(word);
        }
        return Pattern.compile("\\b(" + words + ")\\b");
    }
}
