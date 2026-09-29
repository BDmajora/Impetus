package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.include.IncludeProcessor;
import com.bdmajora.impetus.umbra.shaderpack.parsing.ParsedString;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackParsingTest {
    private static boolean eval(String expression, Map<String, String> defines) {
        return PropertiesPreprocessor.evaluateBooleanExpression(expression, defines).orElseThrow();
    }

    private static boolean eval(String expression) {
        return eval(expression, Map.of());
    }

    @Test
    void conditionalExpressionsFollowTheCPreprocessor() {
        Map<String, String> defines = Map.of("A", "3", "B", "A + 1", "HEX", "0x10", "EMPTY", "", "LOOP", "LOOP",
                "BAD", "3 +", "FLAG", "");
        assertTrue(eval("1 || 0"));
        assertFalse(eval("0 || 0"));
        assertTrue(eval("1 && 2"));
        assertFalse(eval("1 && 0"));
        assertTrue(eval("2 == 2") && eval("2 != 3"));
        assertTrue(eval("1 < 2") && eval("2 <= 2") && eval("3 > 2") && eval("3 >= 3"));
        assertTrue(eval("1 + 2 - 3 == 0"));
        assertTrue(eval("2 * 3 / 2 % 2 == 1"));
        // Division by zero is zero rather than an exception
        assertTrue(eval("5 / 0 == 0") && eval("5 % 0 == 0"));
        assertTrue(eval("!0") && eval("-1 < 0") && eval("+1"));
        assertTrue(eval("(1 + 1) * 2 == 4"));
        // Identifiers resolve through their defines, chained, with undefined and flags as zero
        assertTrue(eval("A == 3", defines));
        assertTrue(eval("B == 4", defines));
        assertTrue(eval("HEX == 16", defines));
        assertTrue(eval("EMPTY == 0", defines));
        assertTrue(eval("UNDEFINED == 0", defines));
        assertTrue(eval("LOOP == 0", defines));
        assertTrue(eval("BAD == 0", defines));
        assertTrue(eval("defined(FLAG) && defined A && !defined(NOPE)", defines));
        // Floats truncate like JCPP, suffixes are ignored
        assertTrue(eval("1.9 == 1") && eval(".5 == 0") && eval("1e+2 == 100") && eval("2.5f == 2"));
        assertTrue(eval("10u == 10") && eval("0X1F == 31"));
        // Malformed expressions are reported as unevaluable
        for (String bad : new String[] {"(1", "1 2", "", "@", "defined(A", "1.2.3 == 1", "0x == 1", "08 == 8"}) {
            assertEquals(Optional.empty(), PropertiesPreprocessor.evaluateBooleanExpression(bad, defines), bad);
        }
        assertEquals(Optional.empty(), PropertiesPreprocessor.tryEvaluateBooleanExpression("(", defines));
        assertEquals(Optional.of(true), PropertiesPreprocessor.tryEvaluateBooleanExpression("A", defines));
    }

    @Test
    void propertiesConditionalsSelectTheActiveLines() {
        String source = String.join("\n",
                "#if A > 1 // label",
                "a = yes",
                "#elif 1",
                "a = elif",
                "#else /* note */",
                "a = else",
                "#endif",
                "#ifdef MISSING",
                "b = hidden",
                "#if 1",
                "b = nested",
                "#endif",
                "#elif 1",
                "b = elif",
                "#endif",
                "#ifndef MISSING",
                "c = shown \\",
                "  continued",
                "#endif",
                "#define LOCAL 7",
                "#define FN(x) x",
                "#define BARE",
                "#if LOCAL == 7 && defined(FN) && defined(BARE)",
                "d = local",
                "#endif",
                "#undef LOCAL",
                "#ifdef LOCAL",
                "e = gone",
                "#else",
                "e = undefined",
                "#endif",
                "#ifdef MISSING",
                "#define HIDDEN 1",
                "#undef A",
                "#endif",
                "#pragma ignored",
                "#elif 1",
                "#else",
                "#endif",
                "size = A A.5 B_A image.A",
                "sliders = A",
                "screen.X = A",
                "profile.P = A",
                "plain line",
                "chain = CHAIN FLAGGED FLOATY",
                "#if 1",
                "trailing\\\\",
                "last = continued \\");
        Map<String, String> defines = Map.of("A", "2", "CHAIN", "A", "FLAGGED", "", "FLOATY", "0.5");
        String plain = PropertiesPreprocessor.preprocess(source, defines);
        assertEquals(List.of("a = yes", "b = elif", "c = shown  continued", "d = local", "e = undefined", "size = A A.5 B_A image.A",
                "sliders = A", "screen.X = A", "profile.P = A", "plain line", "chain = CHAIN FLAGGED FLOATY",
                "trailing\\\\", "last = continued "), List.of(plain.split("\n")));
        // The properties flavour also substitutes numeric macros into values, never into keys or menu lines
        String expanded = PropertiesPreprocessor.preprocessProperties(source, defines);
        assertTrue(expanded.contains("size = 2 2.5 B_A image.A"), expanded);
        assertTrue(expanded.contains("sliders = A"), expanded);
        assertTrue(expanded.contains("screen.X = A"), expanded);
        assertTrue(expanded.contains("profile.P = A"), expanded);
        assertTrue(expanded.contains("chain = 2 FLAGGED 0.5"), expanded);
        assertEquals("a = 1\n", PropertiesPreprocessor.preprocessProperties("a = 1", Map.of()));
        assertEquals("x = 1\n", PropertiesPreprocessor.preprocessProperties("x = 1", Map.of("Y", "2")));
        // A define chain that never reaches a number is left alone
        assertEquals("x = LOOP\n", PropertiesPreprocessor.preprocessProperties("x = LOOP", Map.of("LOOP", "LOOP")));
        assertEquals("x = NAME\n", PropertiesPreprocessor.preprocessProperties("x = NAME", Map.of("NAME", "word")));
        assertEquals("", PropertiesPreprocessor.preprocess("#elif 1\n#else\n#endif", Map.of()));
    }

    @Test
    void glslDefinesGoRightAfterTheVersion() {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("FLAG", "");
        defines.put("NULLED", null);
        defines.put("VALUE", "2");
        assertEquals(List.of("#version 330 core", "#define FLAG", "#define NULLED", "#define VALUE 2", "// before", "void main() {}"),
                GlslPreprocessor.injectDefines(List.of("// before", "#version 330 core\r", "#version 120", "void main() {}"), defines)
                        .stream().map(String::strip).toList());
        assertEquals(List.of("#version 120", "#define VALUE 2", "void main() {}"),
                GlslPreprocessor.injectDefines(List.of("void main() {}"), Map.of("VALUE", "2")));
    }

    @Test
    void conditionalsCanBeResolvedForScanning() {
        assertNull(GlslPreprocessor.resolveConditionals(null, Map.of()));
        assertEquals("", GlslPreprocessor.resolveConditionals("", Map.of()));
        assertEquals("kept\n", GlslPreprocessor.resolveConditionals("#if 1\nkept\n#else\ndropped\n#endif", Map.of()));
        // Resolving to nothing keeps the raw source instead
        String unterminated = "#if 0\neverything\n";
        assertEquals(unterminated, GlslPreprocessor.resolveConditionals(unterminated, Map.of()));
    }

    @Test
    void floatConditionalsAreFoldedToIntegers() {
        assertNull(GlslPreprocessor.foldFloatConditionals(null, Map.of()));
        assertEquals("no directives", GlslPreprocessor.foldFloatConditionals("no directives", Map.of()));
        String source = String.join("\n",
                "#if MOTION_BLUR > 0.0",
                "#elif SPEED > 1",
                "#else // fallback",
                "#endif",
                "#if (in(biome, X)",
                "#endif",
                "#elif ( \\",
                "#endif",
                "#if 1",
                "#endif",
                "#if HALF",
                "#endif",
                "#if defined(HALF) && WEIRD",
                "#endif",
                "/* #if 2.0 > 1.0",
                "#endif */",
                "#if 1 // trailing comment /* both",
                "#endif",
                "#ifdef MOTION_BLUR",
                "#define INSIDE 0.5",
                "#undef MOTION_BLUR",
                "#endif",
                "#ifndef MOTION_BLUR",
                "#if INSIDE > 0",
                "#endif",
                "#endif",
                "#if 0",
                "#define NEVER 1",
                "#undef HALF",
                "#elif 2.5 > 3.0",
                "#endif",
                "// #if 3.0",
                "int x; /* open",
                "*/ #if 1.0");
        Map<String, String> defines = Map.of("MOTION_BLUR", "1.0", "SPEED", "2", "HALF", "0.5", "WEIRD", "@", "DEEP", "DEEP");
        String folded = GlslPreprocessor.foldFloatConditionals(source, defines);
        String[] lines = folded.split("\n", -1);
        assertEquals("#if 1", lines[0]);
        assertEquals("#elif SPEED > 1", lines[1]);
        // Unbalanced parentheses fold to false, a continued line is left for the driver
        assertEquals("#if 0", lines[4]);
        assertEquals("#elif ( \\", lines[6]);
        assertEquals("#if 1", lines[8]);
        // A macro holding a float truncates like JCPP
        assertEquals("#if 0", lines[10]);
        assertEquals("#if defined(HALF) && WEIRD", lines[12]);
        // A directive inside a block comment is not one
        assertEquals("/* #if 2.0 > 1.0", lines[14]);
        // INSIDE was defined, and MOTION_BLUR undefined, by the active branch above
        assertEquals("#if 0", lines[23]);
        assertEquals("#elif 0", lines[29]);
        assertEquals(lines.length, source.split("\n", -1).length);
        // A self-referential macro stops expanding rather than looping
        assertEquals("#if DEEP", GlslPreprocessor.foldFloatConditionals("#if DEEP", Map.of("DEEP", "DEEP")));
    }

    @Test
    void extensionsAreHoistedUnderTheVersion() {
        assertNull(GlslPreprocessor.hoistExtensionDirectives(null));
        assertEquals("void main() {}", GlslPreprocessor.hoistExtensionDirectives("void main() {}"));
        // Already in place, or nothing but directives, leaves the source alone
        String placed = "#version 330\n#extension GL_ARB_foo : enable\nvoid main() {}";
        assertEquals(placed, GlslPreprocessor.hoistExtensionDirectives(placed));
        String directives = "#version 330\n// only\n/* block */\n#extension GL_ARB_foo : enable";
        assertEquals(directives, GlslPreprocessor.hoistExtensionDirectives(directives));
        String misplaced = String.join("\n", "#version 330", "/* a", "still comment */ /* inline */", "uniform float x;",
                "#extension GL_ARB_foo : enable", "#  extension GL_ARB_foo : enable", "#extension GL_ARB_bar : require\r");
        assertEquals(String.join("\n", "#version 330", "#extension GL_ARB_foo : enable", "#  extension GL_ARB_foo : enable",
                        "#extension GL_ARB_bar : require", "/* a", "still comment */ /* inline */", "uniform float x;", "", "", ""),
                GlslPreprocessor.hoistExtensionDirectives(misplaced));
        // Without a #version the directives go to the very top
        assertEquals("#extension GL_ARB_foo : enable\nint x;\n",
                GlslPreprocessor.hoistExtensionDirectives("int x;\n#extension GL_ARB_foo : enable"));
    }

    @Test
    void integerSamplerLookupsUseTheCoreFunctions() {
        assertNull(GlslPreprocessor.rewriteIntegerSamplerLookups("p", null));
        assertEquals("float x;", GlslPreprocessor.rewriteIntegerSamplerLookups("p", "float x;"));
        String floatOnly = "uniform sampler2D tex; vec4 c = texture2D(tex, uv);";
        assertEquals(floatOnly, GlslPreprocessor.rewriteIntegerSamplerLookups("p", floatOnly));
        String source = String.join("\n",
                "uniform usampler2D voxels, more[2];",
                "uniform isampler3D volume, vec3;",
                "uint read(usampler3D s, vec3 p) { return texture3D(s, p).r; }",
                "uniform usampler2D ;",
                "vec4 a = texture2D(voxels, uv);",
                "vec4 b = texture2DLod(more[1], uv, 0.0);",
                "vec4 c = texture2D(pick(voxels), uv);",
                "vec4 d = texture2D(plain, uv);",
                "vec4 e = texture3D(volume, p);");
        String rewritten = GlslPreprocessor.finalizeForDriver("p", source);
        assertTrue(rewritten.contains("vec4 a = texture(voxels, uv);"), rewritten);
        assertTrue(rewritten.contains("vec4 b = textureLod(more[1], uv, 0.0);"), rewritten);
        assertTrue(rewritten.contains("return texture(s, p).r;"), rewritten);
        assertTrue(rewritten.contains("texture2D(pick(voxels), uv)"), rewritten);
        assertTrue(rewritten.contains("texture2D(plain, uv)"), rewritten);
        assertTrue(rewritten.contains("vec4 e = texture(volume, p);"), rewritten);
    }

    @Test
    void parsedStringsTakeTokensOffTheFront() {
        ParsedString s = new ParsedString("  //// word_1 -2.5f 7F .5 rest");
        assertFalse(s.takeLiteral("x"));
        assertTrue(s.takeSomeWhitespace());
        assertFalse(s.takeSomeWhitespace());
        assertTrue(s.takeComments());
        assertTrue(s.currentlyContains("word"));
        assertFalse(s.takeComments());
        assertTrue(s.takeSomeWhitespace());
        assertNull(s.takeNumber());
        assertEquals("word_1", s.takeWord());
        s.takeSomeWhitespace();
        assertEquals("-2.5f", s.takeWordOrNumber());
        s.takeSomeWhitespace();
        assertEquals("7F", s.takeNumber());
        s.takeSomeWhitespace();
        assertNull(s.takeWord());
        assertEquals(".5 rest", s.takeRest());
        assertFalse(s.isEnd());
        ParsedString empty = new ParsedString("");
        assertNull(empty.takeWord());
        assertNull(empty.takeNumber());
        assertFalse(empty.takeSomeWhitespace());
        assertTrue(empty.isEnd());
        assertNull(new ParsedString("1.x").takeNumber());
        assertEquals("12", new ParsedString("12").takeNumber());
        assertEquals("1.5", new ParsedString("1.5").takeNumber());
        assertEquals("3", new ParsedString("3;").takeNumber());
        assertEquals("name", new ParsedString("name").takeWordOrNumber());
    }

    @Test
    void constDirectivesReadTheLastDeclaration() {
        ConstDirectives consts = new ConstDirectives(String.join("\n",
                "const int shadowMapResolution = 1024;",
                "const int shadowMapResolution = 2048 * 2;",
                "const int broken = x;",
                "const int huge = 99999999999;",
                "const float sunPathRotation = -12.5f;",
                "const float strange = abc;",
                "const float overflow = 1e999;",
                "const int shadowDistance = 120;",
                "const bool shadowHardwareFiltering = true;",
                "const bool generateShadowMipmap = false;",
                "const bool odd = maybe;"));
        assertEquals(2048, consts.getInt("shadowMapResolution", 0));
        assertEquals(7, consts.getInt("broken", 7));
        assertEquals(7, consts.getInt("huge", 7));
        assertEquals(7, consts.getInt("missing", 7));
        assertEquals(-12.5F, consts.getFloat("sunPathRotation"));
        // An int-typed const answers a float read
        assertEquals(120.0F, consts.getFloat("shadowDistance", 0));
        assertNull(consts.getFloat("strange"));
        assertNull(consts.getFloat("missing"));
        assertEquals(Float.POSITIVE_INFINITY, consts.getFloat("overflow"));
        assertEquals(3.0F, consts.getFloat("missing", 3.0F));
        assertTrue(consts.getBool("shadowHardwareFiltering"));
        assertFalse(consts.getBool("generateShadowMipmap", true));
        assertEquals(Optional.empty(), consts.getOptionalBool("odd"));
        assertTrue(consts.getBool("odd", true));
        assertFalse(consts.getBool("missing"));
    }

    @Test
    void includesResolveRelativelyAndRefuseCycles() {
        AbsolutePackPath root = AbsolutePackPath.fromAbsolutePath("/programs/./a/../main.fsh");
        assertEquals("/programs/main.fsh", root.getPathString());
        assertEquals("AbsolutePackPath[/programs/main.fsh]", root.toString());
        assertEquals("/programs/lib.glsl", root.resolve("lib.glsl").getPathString());
        assertEquals("/lib.glsl", root.resolve("/lib.glsl").getPathString());
        assertEquals("/top.glsl", AbsolutePackPath.fromAbsolutePath("/main.fsh").resolve("top.glsl").getPathString());
        assertEquals("/", AbsolutePackPath.fromAbsolutePath("/../..").getPathString());
        assertThrows(IllegalArgumentException.class, () -> AbsolutePackPath.fromAbsolutePath("relative"));
        assertEquals(root, AbsolutePackPath.fromAbsolutePath("/programs/main.fsh"));
        assertEquals(root, root);
        assertNotEquals(root, "/programs/main.fsh");
        assertEquals(root.hashCode(), AbsolutePackPath.fromAbsolutePath("//programs//main.fsh").hashCode());

        Map<AbsolutePackPath, String> sources = Map.of(
                AbsolutePackPath.fromAbsolutePath("/a.glsl"), "#include \"b.glsl\"\r\na",
                AbsolutePackPath.fromAbsolutePath("/b.glsl"), "#include <a.glsl>\rb");
        IncludeProcessor processor = new IncludeProcessor(sources);
        assertThrows(IllegalStateException.class, () -> processor.process(AbsolutePackPath.fromAbsolutePath("/a.glsl")));
        assertThrows(IllegalStateException.class, () -> processor.process(AbsolutePackPath.fromAbsolutePath("/none.glsl")));
    }
}
