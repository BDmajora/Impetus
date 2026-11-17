package com.bdmajora.impetus.engine.impl.gl.shader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

// Tests import expansion and constant injection.
class ShaderParserTest {
    // Provider backed by a map that fails on unknown names.
    private static Function<String, String> provider(Map<String, String> files) {
        return name -> {
            String src = files.get(name);
            if (src == null) {
                throw new IllegalArgumentException("missing " + name);
            }
            return src;
        };
    }

    // Returns the lines of a source without imports unchanged.
    @Test
    void plainSourceKeepsLines() {
        assertEquals(List.of("#version 330", "void main() {}"), ShaderParser.parseShader("#version 330\nvoid main() {}", provider(Map.of())));
    }

    // Returns no lines for an empty source.
    @Test
    void emptySourceHasNoLines() {
        assertEquals(List.of(), ShaderParser.parseShader("", provider(Map.of())));
    }

    // Splits on every line terminator and drops the final empty line.
    @Test
    void lineTerminatorsAreSplit() {
        assertEquals(List.of("a", "b", "c", "", "d"), ShaderParser.parseShader("a\r\nb\rc\n\nd\n", provider(Map.of())));
        assertEquals(List.of("", ""), ShaderParser.parseShader("\n\n", provider(Map.of())));
        assertEquals(List.of("x"), ShaderParser.parseShader("x", provider(Map.of())));
    }

    // Replaces an import with the lines of the imported file.
    @Test
    void importIsExpandedInPlace() {
        Map<String, String> files = Map.of("ns:a/b.glsl", "one\ntwo");
        assertEquals(List.of("top", "one", "two", "bottom"), ShaderParser.parseShader("top\n#import <ns:a/b.glsl>\nbottom", provider(files)));
    }

    // Expands nested imports and repeated imports of the same file.
    @Test
    void nestedAndRepeatedImports() {
        Map<String, String> files = Map.of("n:a", "A1\n#import <n:b>\nA2", "n:b", "B");
        assertEquals(List.of("A1", "B", "A2", "B", "A1", "B", "A2"), ShaderParser.parseShader("#import <n:a>\n#import <n:b>\n#import <n:a>", provider(files)));
    }

    // Checks the last colon.
    @Test
    void greedyNamespace() {
        Map<String, String> files = Map.of("a:b:c", "X");
        assertEquals(List.of("X"), ShaderParser.parseShader("#import <a:b:c>", provider(files)));
    }

    // Rejects malformed import lines.
    @Test
    void malformedImportsAreRejected() {
        for (String bad : new String[]{"#import", "#import <nocolon>", "#import ns:path", "#import <ns:path> trailing", "#importx"}) {
            assertThrows(IllegalArgumentException.class, () -> ShaderParser.parseShader(bad, provider(Map.of())), bad);
        }
    }

    // Treats an indented import as an ordinary line.
    @Test
    void indentedImportIsPlainText() {
        assertEquals(List.of(" #import <a:b>"), ShaderParser.parseShader(" #import <a:b>", provider(Map.of())));
    }

    // Rejects direct and indirect import cycles.
    @Test
    void cyclesAreRejected() {
        Map<String, String> self = Map.of("n:a", "#import <n:a>");
        assertThrows(IllegalStateException.class, () -> ShaderParser.parseShader("#import <n:a>", provider(self)));

        Map<String, String> loop = Map.of("n:a", "#import <n:b>", "n:b", "#import <n:a>");
        assertThrows(IllegalStateException.class, () -> ShaderParser.parseShader("#import <n:a>", provider(loop)));
    }

    // Allows a file imported twice along different branches.
    @Test
    void diamondImportsAreNotCycles() {
        Map<String, String> files = Map.of("n:a", "#import <n:c>", "n:b", "#import <n:c>", "n:c", "C");
        assertEquals(List.of("C", "C"), ShaderParser.parseShader("#import <n:a>\n#import <n:b>", provider(files)));
    }

    // Reports a null import source and keeps the parser reusable afterwards.
    @Test
    void nullImportSourceIsRejected() {
        Function<String, String> nulls = name -> null;
        assertThrows(IllegalStateException.class, () -> ShaderParser.parseShader("#import <n:a>", nulls));
        assertEquals(List.of("x"), ShaderParser.parseShader("x", nulls));
    }

    // Propagates provider failures and clears the cycle state.
    @Test
    void providerFailuresPropagate() {
        RuntimeException boom = new RuntimeException("boom");
        Function<String, String> failing = name -> {
            throw boom;
        };
        assertSame(boom, assertThrows(RuntimeException.class, () -> ShaderParser.parseShader("#import <n:a>", failing)));
    }

    // Rejects null arguments.
    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> ShaderParser.parseShader(null, provider(Map.of())));
        assertThrows(NullPointerException.class, () -> ShaderParser.parseShader("x", null));
        assertThrows(NullPointerException.class, () -> ShaderParser.parseShader(null, provider(Map.of()), ShaderConstants.EMPTY));
        assertThrows(NullPointerException.class, () -> ShaderParser.parseShader("x", null, ShaderConstants.EMPTY));
        assertThrows(NullPointerException.class, () -> ShaderParser.parseShader("x", provider(Map.of()), null));
    }

    // Returns a mutable list the caller owns.
    @Test
    void resultIsMutable() {
        List<String> lines = ShaderParser.parseShader("a", provider(Map.of()));
        lines.add("b");
        assertEquals(List.of("a", "b"), lines);
    }

    // Inserts definitions after the first line.
    @Test
    void definesFollowFirstLine() {
        ShaderConstants constants = ShaderConstants.builder().add("A").add("B", "2").build();
        assertEquals("#version 330\n#define A\n#define B 2\nvoid main() {}", ShaderParser.parseShader("#version 330\nvoid main() {}", provider(Map.of()), constants));
    }

    // Inserts definitions after the first line of an expanded import.
    @Test
    void definesFollowFirstExpandedLine() {
        ShaderConstants constants = ShaderConstants.builder().add("A").build();
        Map<String, String> files = Map.of("n:v", "#version 150\nx");
        assertEquals("#version 150\n#define A\nx\ny", ShaderParser.parseShader("#import <n:v>\ny", provider(files), constants));
    }

    // Appends definitions after a single line source.
    @Test
    void definesAfterSingleLine() {
        ShaderConstants constants = ShaderConstants.builder().add("A").build();
        assertEquals("v\n#define A", ShaderParser.parseShader("v", provider(Map.of()), constants));
    }

    // Emits only definitions for an empty source.
    @Test
    void definesForEmptySource() {
        ShaderConstants constants = ShaderConstants.builder().add("A").add("B").build();
        assertEquals("#define A\n#define B", ShaderParser.parseShader("", provider(Map.of()), constants));
        assertEquals("", ShaderParser.parseShader("", provider(Map.of()), ShaderConstants.EMPTY));
    }

    // Joins lines with single newlines and keeps empty lines.
    @Test
    void emptyLinesSurviveJoin() {
        assertEquals("a\n\nb", ShaderParser.parseShader("a\n\nb\n", provider(Map.of()), ShaderConstants.EMPTY));
        assertEquals("\n", ShaderParser.parseShader("\n\n", provider(Map.of()), ShaderConstants.EMPTY));
        assertEquals("\n#define A\n", ShaderParser.parseShader("\n\n", provider(Map.of()), ShaderConstants.builder().add("A").build()));
    }

    // Expands a deep import chain without losing lines.
    @Test
    void deepChain() {
        Map<String, String> files = new java.util.HashMap<>();
        int depth = 200;
        for (int i = 0; i < depth; i++) {
            files.put("n:" + i, "L" + i + "\n#import <n:" + (i + 1) + ">");
        }
        files.put("n:" + depth, "end");
        List<String> lines = ShaderParser.parseShader("#import <n:0>", provider(files));
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            expected.add("L" + i);
        }
        expected.add("end");
        assertEquals(expected, lines);
    }
}
