package com.bdmajora.impetus.umbra.pipeline.rendering;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComputeDirectivesTest {
    @Test
    void workGroupsReadEitherSpellingAndRejectWhatIsNotAPositiveCount() {
        assertArrayEquals(new int[]{2, 3, 4}, ComputeDirectives.workGroups("const ivec3 workGroups = ivec3(2, 3, 4);"));
        // A single value splats, a 2-component vector leaves z at 1, unsigned suffixes are tolerated
        assertArrayEquals(new int[]{5, 5, 5}, ComputeDirectives.workGroups("const ivec3 workGroups = ivec3(5);"));
        assertArrayEquals(new int[]{2, 7, 1}, ComputeDirectives.workGroups("const ivec2 workGroups = ivec2(2, 7);"));
        assertArrayEquals(new int[]{2, 2, 1}, ComputeDirectives.workGroups("const uvec3 workGroups = uvec3(2u, 2U, 1u);"));
        // A zero, a negative, a float or an overflowing value voids the directive
        assertNull(ComputeDirectives.workGroups("const ivec3 workGroups = ivec3(2, 0, 1);"));
        assertNull(ComputeDirectives.workGroups("const ivec3 workGroups = ivec3(2, -1, 1);"));
        assertNull(ComputeDirectives.workGroups("const vec3 workGroups = vec3(2.0, 1.0, 1.0);"));
        assertNull(ComputeDirectives.workGroups("const ivec3 workGroups = ivec3(99999999999, 1, 1);"));
        // A count mismatch falls through to the named spelling
        assertArrayEquals(new int[]{4, 1, 1}, ComputeDirectives.workGroups(
                "const ivec3 workGroups = ivec3(2, 2);\nconst int workGroupsX = 4;"));
        assertArrayEquals(new int[]{3, 1, 6}, ComputeDirectives.workGroups("const int workGroupsX = 3;\nconst int workGroupsZ = 6;"));
        // The first declaration of an axis wins; zero and overflow are not declarations
        assertArrayEquals(new int[]{3, 1, 1}, ComputeDirectives.workGroups("const int workGroupsX = 3;\nconst int workGroupsX = 9;"));
        assertArrayEquals(new int[]{8, 1, 1}, ComputeDirectives.workGroups("const int workGroupsX = 0;\nconst int workGroupsX = 8;"));
        assertNull(ComputeDirectives.workGroups("const int workGroupsX = 99999999999;"));
        assertNull(ComputeDirectives.workGroups("void main() {}"));
    }

    @Test
    void renderScaleReadsOneOrTwoFactorsAndRejectsGarbage() {
        assertArrayEquals(new float[]{0.5f, 0.25f}, ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(0.5f, 0.25);"));
        assertArrayEquals(new float[]{0.75f, 0.75f}, ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(0.75);"));
        assertArrayEquals(new float[]{1.0f, 0.5f}, ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(1.0, 0.5, 9.0);"));
        assertNull(ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2();"));
        assertNull(ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(a, b);"));
        // NaN is the fixed-dispatch sentinel downstream, so a non-finite factor is not a declaration
        assertNull(ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(NaN, 1.0);"));
        assertNull(ComputeDirectives.renderScale("const vec2 workGroupsRender = vec2(1.0, Infinity);"));
        assertNull(ComputeDirectives.renderScale("void main() {}"));
    }

    @Test
    void localSizeReadsEachAxisInAnyOrderDefaultingTheRestToOne() {
        assertArrayEquals(new int[]{8, 4, 2}, ComputeDirectives.localSize("layout(local_size_x = 8, local_size_y = 4, local_size_z = 2) in;"));
        assertArrayEquals(new int[]{8, 4, 1}, ComputeDirectives.localSize("layout(local_size_y = 4, local_size_x = 8) in;"));
        assertArrayEquals(new int[]{1, 16, 1}, ComputeDirectives.localSize("layout(local_size_y = 16) in;"));
        // Zero or an overflow cannot be divided by, so it counts as 1; a specialisation id is no size
        assertArrayEquals(new int[]{1, 2, 1}, ComputeDirectives.localSize("layout(local_size_x = 0, local_size_y = 2) in;"));
        assertArrayEquals(new int[]{1, 1, 1}, ComputeDirectives.localSize("layout(local_size_x = 99999999999) in;"));
        assertNull(ComputeDirectives.localSize("layout(local_size_x_id = 1) in;"));
        assertNull(ComputeDirectives.localSize("void main() {}"));
    }

    @Test
    void parsingPrefersTheActiveBranchAndFallsBackToTheRawSource() {
        String source = String.join("\n",
                "#ifdef FAST",
                "layout(local_size_x = 4) in;",
                "const ivec3 workGroups = ivec3(1, 1, 1);",
                "#else",
                "layout(local_size_x = 16, local_size_y = 16) in;",
                "const ivec3 workGroups = ivec3(9, 9, 1);",
                "#endif",
                "#ifdef NEVER",
                "const vec2 workGroupsRender = vec2(0.5, 0.5);",
                "#endif");
        ComputeDirectives slow = ComputeDirectives.parse(source, Map.of());
        assertArrayEquals(new int[]{9, 9, 1}, slow.workGroups());
        assertArrayEquals(new int[]{16, 16, 1}, slow.localSize());
        // Missing from the active text, so the raw one answers (the evaluator cannot expand every macro shape)
        assertArrayEquals(new float[]{0.5f, 0.5f}, slow.renderScale());
        ComputeDirectives fast = ComputeDirectives.parse(source, Map.of("FAST", ""));
        assertArrayEquals(new int[]{1, 1, 1}, fast.workGroups());
        assertArrayEquals(new int[]{4, 1, 1}, fast.localSize());
        ComputeDirectives none = ComputeDirectives.parse("void main() {}", Map.of());
        assertNull(none.workGroups());
        assertNull(none.renderScale());
        assertNull(none.localSize());
    }

    @Test
    void commentsAreStrippedInOnePassWithBlocksBecomingASpace() {
        String clean = "const ivec3 workGroups = ivec3(2, 2, 1);";
        assertSame(clean, ComputeDirectives.stripComments(clean));
        assertEquals("a \nb", ComputeDirectives.stripComments("a // x\nb"));
        assertEquals("const int   x;", ComputeDirectives.stripComments("const int /* y */ x;"));
        // A block comment joins no tokens, and one opened inside a line comment opens nothing
        assertEquals("const ivec3 workGroups", ComputeDirectives.stripComments("const/**/ivec3/**/workGroups"));
        assertEquals("a \nb */ c", ComputeDirectives.stripComments("a // /*\nb */ c"));
        assertEquals("x  ", ComputeDirectives.stripComments("x /* never closed"));
        assertEquals("x ", ComputeDirectives.stripComments("x // to the end"));
        assertEquals("/", ComputeDirectives.stripComments("/"));
        assertEquals("a/b", ComputeDirectives.stripComments("a/b"));
        assertEquals("", ComputeDirectives.stripComments(""));
        // A commented-out declaration is not read
        assertNull(ComputeDirectives.parse("// const ivec3 workGroups = ivec3(4, 4, 1);\n/* const int workGroupsX = 2; */", Map.of()).workGroups());
    }

    @Test
    void dispatchArithmeticNeverOverflowsOrDividesByZero() {
        assertEquals(1, ComputeDirectives.ceilDiv(0, 8));
        assertEquals(1, ComputeDirectives.ceilDiv(-5, 8));
        assertEquals(2, ComputeDirectives.ceilDiv(9, 8));
        assertEquals(1, ComputeDirectives.ceilDiv(8, 8));
        assertEquals(7, ComputeDirectives.ceilDiv(7, 0));
        assertEquals(7, ComputeDirectives.ceilDiv(7, -3));
        assertEquals(Integer.MAX_VALUE, ComputeDirectives.ceilDiv(Integer.MAX_VALUE, 1));
        assertEquals(268435456, ComputeDirectives.ceilDiv(Integer.MAX_VALUE, 8));
        assertEquals(60, ComputeDirectives.screenGroups(1920, 0.5f, 16));
        assertEquals(1, ComputeDirectives.screenGroups(1920, -1.0f, 16));
        assertEquals(Integer.MAX_VALUE, ComputeDirectives.screenGroups(Integer.MAX_VALUE, Float.POSITIVE_INFINITY, 1));
        assertTrue(ComputeDirectives.coversVolume(new int[]{2, 2, 16}, new int[]{8, 8, 1}, new int[]{16, 16, 16}));
        assertFalse(ComputeDirectives.coversVolume(new int[]{1, 2, 16}, new int[]{8, 8, 1}, new int[]{16, 16, 16}));
        assertFalse(ComputeDirectives.coversVolume(new int[]{2, 2, 15}, new int[]{8, 8, 1}, new int[]{16, 16, 16}));
        // Products past int range still compare as covering
        assertTrue(ComputeDirectives.coversVolume(new int[]{1 << 20, 1, 1}, new int[]{1 << 12, 1, 1}, new int[]{16, 1, 1}));
    }

    @Test
    void indirectPointersNeedAValidIndexAndAnAlignedOffset() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("indirect.ok", " 1  64 ");
        raw.put("indirect.extra", "0 0 trailing");
        raw.put("indirect.short", "3");
        raw.put("indirect.words", "a b");
        raw.put("indirect.blank", "");
        raw.put("indirect.negative", "-1 0");
        raw.put("indirect.huge", "4294967296 0");
        raw.put("indirect.backwards", "0 -4");
        raw.put("indirect.unaligned", "0 6");
        raw.put("other.key", "0 0");
        Map<String, ComputeDirectives.IndirectPointer> pointers = ComputeDirectives.parseIndirectPointers(raw);
        assertEquals(Map.of("ok", new ComputeDirectives.IndirectPointer(1, 64L), "extra", new ComputeDirectives.IndirectPointer(0, 0L)), pointers);
        assertEquals(java.util.List.of("ok", "extra"), java.util.List.copyOf(pointers.keySet()));
        assertTrue(ComputeDirectives.parseIndirectPointers(Map.of()).isEmpty());
    }

    @Test
    void literalsTolerateSuffixes() {
        assertEquals(3, ComputeDirectives.positiveInt(" 3u "));
        assertEquals(-1, ComputeDirectives.positiveInt("u"));
        assertEquals(-1, ComputeDirectives.positiveInt("0"));
        assertEquals(0.5f, ComputeDirectives.floatLiteral(" 0.5F "));
        assertEquals(2.0f, ComputeDirectives.floatLiteral("2"));
        assertThrows(NumberFormatException.class, () -> ComputeDirectives.floatLiteral("f"));
    }

    @Test
    void aStageDeclaringEverythingNeedsNoRawFallbackAndTheFirstAxisDeclarationWins() {
        ComputeDirectives all = ComputeDirectives.parse(String.join("\n",
                "layout(local_size_x = 4, local_size_x = 8) in;",
                "const ivec3 workGroups = ivec3(2, 2, 2);",
                "const vec2 workGroupsRender = vec2(0.5, 0.5);"), Map.of());
        assertArrayEquals(new int[]{4, 1, 1}, all.localSize());
        assertArrayEquals(new int[]{2, 2, 2}, all.workGroups());
        assertArrayEquals(new float[]{0.5f, 0.5f}, all.renderScale());
    }
}
