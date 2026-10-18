package com.bdmajora.impetus.umbra.terrain.mesh;

import com.bdmajora.impetus.umbra.terrain.mesh.GlslDeclarations.Declaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlslDeclarationsTest {
    private static final String SOURCE = String.join("\n",
            "#version 330 compatibility",
            "// out vec3 commented;",
            "/* out vec3 alsoCommented; */",
            "layout(location = 2) flat out int blockId, other[2];",
            "out highp vec4 color = vec4(1.0);",
            "#ifdef WAVES",
            "out vec3 waves;",
            "#endif",
            "uniform sampler2D tex;",
            "out vec4[2] pair;",
            "out VertexData { vec2 uv; flat int id; } vs_out;",
            "uniform Lights { vec4 light; };",
            "struct Material { vec4 albedo; };",
            "void helper(in vec3 a,",
            "            out vec3 b) {",
            "    b = a;",
            "}",
            "const float scale = 2.0;",
            "out vec3",
            "#ifdef X",
            "    split;",
            "#else",
            "    other;",
            "#endif",
            "float plain;",
            "void main() { helper(vec3(0.0), waves); }");

    @Test
    void onlyGlobalStorageDeclarationsAreSeen() {
        List<Declaration> found = GlslDeclarations.collect(SOURCE);
        List<String> storages = found.stream().map(Declaration::storage).toList();
        // Comments, the struct, the function parameters, the plain global and the directive-split statement are not declarations
        assertEquals(List.of("out", "out", "out", "uniform", "out", "out", "uniform", "const"), storages);

        Declaration first = found.getFirst();
        assertEquals("layout(location = 2)", first.layout());
        assertEquals(List.of("flat"), first.qualifiers());
        assertEquals("int", first.type());
        assertEquals("blockId", first.declarators().get(0).name());
        assertEquals("other", first.declarators().get(1).name());
        assertEquals("[2]", first.declarators().get(1).arraySuffix());
        assertFalse(first.isBlock());

        Declaration color = found.get(1);
        assertEquals(List.of("highp"), color.qualifiers());
        assertEquals("= vec4 (1.0)", color.declarators().getFirst().initializer());

        // A type array suffix folds onto the declarator
        assertEquals("[2]", found.get(4).declarators().getFirst().arraySuffix());

        Declaration block = found.get(5);
        assertTrue(block.isBlock());
        assertEquals("VertexData", block.type());
        assertEquals("vs_out", block.declarators().getFirst().name());
        assertTrue(block.blockBody().contains("flat int id;"));

        Declaration anonymous = found.get(6);
        assertTrue(anonymous.isBlock());
        assertEquals("", anonymous.declarators().getFirst().name());
    }

    @Test
    void rewritesKeepLineNumbersAndEverythingElse() {
        String source = String.join("\n",
                "out vec2 texcoord;",
                "out vec4",
                "    color;",
                "float keep;");
        String rewritten = GlslDeclarations.rewrite(source, declaration -> declaration.type() + " " + declaration.declarators().getFirst().name() + ";");
        assertEquals("vec2 texcoord;\nvec4 color;\n\nfloat keep;", rewritten);
        // Answering null leaves the statement as written
        assertEquals(source, GlslDeclarations.rewrite(source, declaration -> null));
    }

    @Test
    void oddStatementsParseToNothing() {
        assertNull(GlslDeclarations.parse("layout(local_size_x = 1) in;"));
        assertNull(GlslDeclarations.parse("invariant gl_Position;"));
        assertNull(GlslDeclarations.parse("out vec3 1bad;"));
        assertNull(GlslDeclarations.parse("out 2D name;"));
        assertNull(GlslDeclarations.parse("out vec3;"));
        assertNotNull(GlslDeclarations.parse("out vec3 /* why */ a, b = vec3(0, 1, 2);"));
    }

    @Test
    void tokensKeepBracketedGroupsWhole() {
        assertEquals(List.of("layout", "(location = 1)", "out", "vec4", "x", "[4]", ";"),
                GlslDeclarations.tokenize("layout(location = 1) out vec4 x[4]; // trailing"));
        assertEquals(List.of("(a   b)"), GlslDeclarations.tokenize("(a /* c */ b)"));
        // An unterminated group runs to the end rather than looping
        assertEquals(List.of("(open"), GlslDeclarations.tokenize("(open"));
        assertEquals(List.of("a"), GlslDeclarations.tokenize("a /* never closed"));
        assertTrue(GlslDeclarations.isIdentifier("_name2"));
        assertFalse(GlslDeclarations.isIdentifier(""));
        assertFalse(GlslDeclarations.isIdentifier("a.b"));
    }

    @Test
    void unterminatedCommentsAndContinuedDirectivesEndTheScan() {
        String continued = "#define LONG \\\n    out vec3 notDeclared;\nout vec3 declared;";
        assertEquals(List.of("declared"), GlslDeclarations.collect(continued).stream().map(d -> d.declarators().getFirst().name()).toList());
        assertTrue(GlslDeclarations.collect("out vec3 a; /* open").size() == 1);
        assertEquals("// comment only", GlslDeclarations.rewrite("// comment only", declaration -> "x"));
    }
}
