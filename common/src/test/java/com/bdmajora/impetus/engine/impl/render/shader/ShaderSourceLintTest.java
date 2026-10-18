package com.bdmajora.impetus.engine.impl.render.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// No GLSL compiler runs in the suite, so the two mistakes a driver rejects outright and a reading misses are checked by hand: preprocessor conditionals that never close, and names GLSL keeps for itself
class ShaderSourceLintTest {
    // Keywords and words reserved for future use in GLSL 4.60, which NVIDIA enforces in every version
    private static final String RESERVED = String.join("|", "packed", "active", "common", "partition", "asm", "class", "union", "enum",
            "typedef", "template", "this", "resource", "goto", "inline", "noinline", "public", "static", "extern", "external", "interface",
            "long", "short", "half", "fixed", "unsigned", "superp", "input", "output", "filter", "sizeof", "cast", "namespace", "using",
            "sample", "patch", "buffer", "shared", "precise", "subroutine", "coherent", "volatile", "restrict", "readonly", "writeonly",
            "smooth", "flat", "centroid", "noperspective", "invariant");
    private static final Pattern DECLARED_RESERVED = Pattern.compile(
            "\\b(?:float|double|int|uint|bool|[biud]?vec[234]|mat[234](?:x[234])?|Vertex)\\s+(" + RESERVED + ")\\s*[=;,\\[)]");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^[ \\t]*#[ \\t]*(if|ifdef|ifndef|endif)\\b");

    private static List<Path> shaders() throws IOException, URISyntaxException {
        Path root = Path.of(ShaderLoader.class.getResource("/assets/impetus/shaders").toURI());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        return files;
    }

    // Conditionals that open and close in one file, never closing more than they opened
    static String unbalanced(String source) {
        int depth = 0;
        Matcher directive = DIRECTIVE.matcher(COMMENT.matcher(source).replaceAll(""));
        while (directive.find()) {
            depth += directive.group(1).equals("endif") ? -1 : 1;
            if (depth < 0) {
                return "#endif without #if";
            }
        }
        return depth == 0 ? null : depth + " #if left open";
    }

    // The first variable or parameter named with a reserved word, or null
    static String reservedName(String source) {
        Matcher declared = DECLARED_RESERVED.matcher(COMMENT.matcher(source).replaceAll(""));
        return declared.find() ? declared.group(1) : null;
    }

    @Test
    void everyShaderClosesItsConditionalsAndAvoidsReservedNames() throws IOException, URISyntaxException {
        List<Path> files = shaders();
        assertTrue(files.size() > 20);
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String source = Files.readString(file);
            String conditionals = unbalanced(source);
            if (conditionals != null) {
                problems.add(file.getFileName() + ": " + conditionals);
            }
            String reserved = reservedName(source);
            if (reserved != null) {
                problems.add(file.getFileName() + ": names something '" + reserved + "'");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void theChecksCatchWhatTheDriverRejected() {
        assertEquals("1 #if left open", unbalanced("#ifdef USE_FOG\n#if defined(A) && A > 0\nx\n#endif\n"));
        assertEquals("#endif without #if", unbalanced("#endif\n#if 1\n"));
        assertNull(unbalanced("// #if in a comment\n#ifdef A\n#else\n#endif\n"));
        assertEquals("packed", reservedName("uvec3 packed = uvec3(0);"));
        assertEquals("active", reservedName("    bool active = index < count;"));
        assertNull(reservedName("// bool active = true;\nbool inSection = true; float packedValue;"));
    }
}
