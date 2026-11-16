package com.bdmajora.impetus.engine.impl.gl.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Tests constant formatting and immutable builder snapshots.
class ShaderConstantsTest {
    // Returns empty immutable constants from independent builders.
    @Test
    void emptyBuildersAndConstantRemainEmpty() {
        ShaderConstants.Builder first = ShaderConstants.builder();
        ShaderConstants.Builder second = ShaderConstants.builder();

        assertNotSame(first, second);
        assertEquals(List.of(), first.build().getDefineStrings());
        assertEquals(List.of(), second.build().getDefineStrings());
        assertEquals(List.of(), ShaderConstants.EMPTY.getDefineStrings());
        assertThrows(UnsupportedOperationException.class, () -> ShaderConstants.EMPTY.getDefineStrings().add("X"));

        first.add("A");
        assertEquals(List.of(), second.build().getDefineStrings());
        assertEquals(List.of(), ShaderConstants.EMPTY.getDefineStrings());
    }

    // Formats accepted names without changing their contents.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "null", " ", "A B", "A\nB", "\u0000", "é水😀", "#define A"})
    void namesRemainLiteral(String name) {
        ShaderConstants.Builder builder = ShaderConstants.builder();

        assertSame(builder, builder.add(name));
        assertEquals(List.of("#define " + name), builder.build().getDefineStrings());
        assertEquals(List.of("#define " + name), ShaderConstants.builder().add(name, "").build().getDefineStrings());
        assertEquals(List.of("#define " + name + " value"), ShaderConstants.builder().add(name, "value").build().getDefineStrings());
    }

    // Formats values without parsing numbers or changing whitespace.
    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "-2147483648", "2147483647", "2147483648", "-2147483649",
            "-9223372036854775808", "9223372036854775807", "9223372036854775808", "-9223372036854775809",
            " ", "\t", "a\nb", "\u0000", "é水😀"})
    void valuesRemainLiteral(String value) {
        ShaderConstants.Builder builder = ShaderConstants.builder();

        assertSame(builder, builder.add("A", value));
        assertEquals(List.of(value.isEmpty() ? "#define A" : "#define A " + value), builder.build().getDefineStrings());
    }

    // Preserves complete names and values across large string lengths.
    @Test
    void longStringsAreNotTruncated() {
        String name = "N".repeat(65_536);
        String value = "水".repeat(65_536);

        assertEquals(List.of("#define " + name + " " + value),
                ShaderConstants.builder().add(name, value).build().getDefineStrings());
    }

    // Rejects null values without reserving a name or changing an existing snapshot.
    @Test
    void nullValueLeavesBuilderUsable() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add("A");
        ShaderConstants before = builder.build();

        assertThrows(NullPointerException.class, () -> builder.add("B", null));
        assertThrows(NullPointerException.class, () -> builder.add(null, null));
        assertEquals(before.getDefineStrings(), builder.build().getDefineStrings());
        assertSame(builder, builder.add("B", "2").add(null));
        assertEquals(List.of("#define A", "#define B 2", "#define null"), builder.build().getDefineStrings());
        assertEquals(List.of("#define A"), before.getDefineStrings());
    }

    // Rejects duplicate names and preserves their original values and positions.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "null", "Aa", "BB"})
    void duplicatesPreserveOriginalDefinition(String name) {
        for (String original : List.of("", "1")) {
            ShaderConstants.Builder builder = ShaderConstants.builder().add(name, original).add("AFTER", "2");
            ShaderConstants before = builder.build();

            for (String replacement : List.of("", "1", "changed")) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> builder.add(name, replacement));
                assertEquals("Constant " + name + " is already defined with value " + original, error.getMessage());
            }
            assertThrows(IllegalArgumentException.class, () -> builder.add(name));
            assertEquals(before.getDefineStrings(), builder.build().getDefineStrings());
            assertSame(before, builder.build());
        }
    }

    // Matches distinct string objects by content when detecting duplicates.
    @Test
    void equalNamesAreDuplicates() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add(new String("NAME"));

        assertThrows(IllegalArgumentException.class, () -> builder.add(new String("NAME"), "2"));
        assertEquals(List.of("#define NAME"), builder.build().getDefineStrings());
    }

    // Keeps immutable snapshots independent of later builder additions.
    @Test
    void repeatedBuildsReuseSnapshotsUntilSuccessfulAddition() {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        ShaderConstants empty = builder.build();
        assertSame(empty, builder.build());

        builder.add("A");
        ShaderConstants first = builder.build();
        assertSame(first, builder.build());
        assertSame(first.getDefineStrings(), first.getDefineStrings());

        builder.add("B", "2");
        ShaderConstants second = builder.build();
        assertSame(second, builder.build());
        assertEquals(List.of(), empty.getDefineStrings());
        assertEquals(List.of("#define A"), first.getDefineStrings());
        assertEquals(List.of("#define A", "#define B 2"), second.getDefineStrings());

        builder.addAll(List.of("C"));
        ShaderConstants third = builder.build();
        builder.addAll(Map.of("D", "4"));
        assertEquals(List.of("#define A", "#define B 2", "#define C"), third.getDefineStrings());
        assertEquals(List.of("#define A", "#define B 2", "#define C", "#define D 4"), builder.build().getDefineStrings());
    }

    // Rejects list mutations through direct access and derived views.
    @Test
    void snapshotsRejectEveryListMutation() {
        List<String> lines = ShaderConstants.builder().add("A").add("B", "2").build().getDefineStrings();

        assertThrows(UnsupportedOperationException.class, () -> lines.add("C"));
        assertThrows(UnsupportedOperationException.class, () -> lines.add(0, "C"));
        assertThrows(UnsupportedOperationException.class, () -> lines.addAll(List.of("C")));
        assertThrows(UnsupportedOperationException.class, () -> lines.set(0, "C"));
        assertThrows(UnsupportedOperationException.class, () -> lines.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> lines.remove("#define A"));
        assertThrows(UnsupportedOperationException.class, lines::clear);
        assertThrows(UnsupportedOperationException.class, () -> lines.removeAll(List.of("#define A")));
        assertThrows(UnsupportedOperationException.class, () -> lines.retainAll(List.of("#define A")));
        assertThrows(UnsupportedOperationException.class, () -> lines.removeIf(line -> true));
        assertThrows(UnsupportedOperationException.class, () -> lines.replaceAll(String::toLowerCase));
        assertThrows(UnsupportedOperationException.class, () -> lines.sort(null));
        assertThrows(UnsupportedOperationException.class, () -> lines.subList(0, 1).clear());

        Iterator<String> iterator = lines.iterator();
        assertEquals("#define A", iterator.next());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        var listIterator = lines.listIterator();
        listIterator.next();
        assertThrows(UnsupportedOperationException.class, () -> listIterator.set("C"));
        assertThrows(UnsupportedOperationException.class, () -> listIterator.add("C"));
        assertThrows(UnsupportedOperationException.class, listIterator::remove);
        assertEquals(List.of("#define A", "#define B 2"), lines);
    }

    // Enforces list boundaries for empty and single element snapshots.
    @Test
    void returnedListsEnforceIndexBoundaries() {
        List<String> empty = ShaderConstants.builder().build().getDefineStrings();
        List<String> single = ShaderConstants.builder().add("A").build().getDefineStrings();

        assertEquals("#define A", single.get(0));
        for (int index : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> empty.get(index));
            if (index != 0) {
                assertThrows(IndexOutOfBoundsException.class, () -> single.get(index));
            }
        }
        assertEquals(List.of(), single.subList(0, 0));
        assertEquals(single, single.subList(0, 1));
        assertEquals(List.of(), single.subList(1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> single.subList(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> single.subList(0, 2));
    }

    // Adds collections in traversal order and ignores later source changes.
    @Test
    void collectionAddsEmptySingleAndMultipleInputs() {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        assertSame(builder, builder.addAll(Collections.emptyList()));
        assertEquals(List.of(), builder.build().getDefineStrings());
        assertSame(builder, builder.addAll(List.of("A")));

        List<String> source = new ArrayList<>(Arrays.asList("C", null, "B", ""));
        assertSame(builder, builder.addAll(source));
        source.clear();

        assertEquals(List.of("#define A", "#define C", "#define null", "#define B", "#define "), builder.build().getDefineStrings());
    }

    // Adds map entries in traversal order and ignores later source changes.
    @Test
    void mapAddsEmptySingleAndMultipleInputs() {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        assertSame(builder, builder.addAll(Collections.emptyMap()));
        assertEquals(List.of(), builder.build().getDefineStrings());
        assertSame(builder, builder.addAll(Map.of("A", "1")));

        Map<String, String> source = new LinkedHashMap<>();
        source.put("C", "3");
        source.put(null, "");
        source.put("B", "2");
        source.put("", " ");
        assertSame(builder, builder.addAll(source));
        source.clear();

        assertEquals(List.of("#define A 1", "#define C 3", "#define null", "#define B 2", "#define   "), builder.build().getDefineStrings());
    }

    // Rejects null bulk inputs without changing a snapshot.
    @Test
    void nullBulkInputsLeaveBuilderUnchanged() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add("A");
        ShaderConstants before = builder.build();

        assertThrows(NullPointerException.class, () -> builder.addAll((Collection<String>) null));
        assertThrows(NullPointerException.class, () -> builder.addAll((Map<String, String>) null));
        assertSame(before, builder.build());
        assertEquals(List.of("#define A"), builder.build().getDefineStrings());
    }

    // Preserves unchanged snapshots across empty bulk calls and repeated failures.
    @Test
    void noChangesKeepCachedSnapshot() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add("A");
        ShaderConstants before = builder.build();

        for (int attempt = 0; attempt < 3; attempt++) {
            builder.addAll(List.of());
            builder.addAll(Map.of());
            assertThrows(IllegalArgumentException.class, () -> builder.addAll(List.of("A")));
            assertThrows(IllegalArgumentException.class, () -> builder.addAll(Map.of("A", "2")));
            assertThrows(NullPointerException.class, () -> builder.add("B", null));
            assertSame(before, builder.build());
        }
    }

    // Keeps the successful collection prefix when a duplicate interrupts insertion.
    @Test
    void collectionDuplicatesRetainSuccessfulPrefix() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add("A", "1");
        ShaderConstants before = builder.build();

        assertThrows(IllegalArgumentException.class, () -> builder.addAll(List.of("B", "A", "C")));
        assertEquals(List.of("#define A 1", "#define B"), builder.build().getDefineStrings());
        assertEquals(List.of("#define A 1"), before.getDefineStrings());
        builder.add("C");

        ShaderConstants.Builder repeated = ShaderConstants.builder();
        assertThrows(IllegalArgumentException.class, () -> repeated.addAll(List.of("X", "X", "Y")));
        assertEquals(List.of("#define X"), repeated.build().getDefineStrings());
    }

    // Keeps the successful map prefix when a duplicate interrupts insertion.
    @Test
    void mapDuplicatesRetainSuccessfulPrefix() {
        ShaderConstants.Builder builder = ShaderConstants.builder().add("A", "1");
        ShaderConstants before = builder.build();
        Map<String, String> source = new LinkedHashMap<>();
        source.put("B", "2");
        source.put("A", "changed");
        source.put("C", "3");

        assertThrows(IllegalArgumentException.class, () -> builder.addAll(source));
        assertEquals(List.of("#define A 1", "#define B 2"), builder.build().getDefineStrings());
        assertEquals(List.of("#define A 1"), before.getDefineStrings());
        builder.add("C", "3");
    }

    // Rejects null map values and retains only the successful prefix.
    @Test
    void nullMapValuesDoNotPoisonBuilder() {
        for (String name : Arrays.asList("BAD", null)) {
            ShaderConstants.Builder builder = ShaderConstants.builder().add("A");
            ShaderConstants before = builder.build();
            Map<String, String> source = new LinkedHashMap<>();
            source.put("B", "2");
            source.put(name, null);
            source.put("C", "3");

            assertThrows(NullPointerException.class, () -> builder.addAll(source));
            assertEquals(List.of("#define A", "#define B 2"), builder.build().getDefineStrings());
            assertEquals(List.of("#define A"), before.getDefineStrings());
            builder.add(name).add("C", "3");
            assertEquals(4, builder.build().getDefineStrings().size());
        }
    }

    // Preserves insertion order across hash collisions and map growth.
    @Test
    void collidingNamesRemainDistinctAndOrdered() {
        ShaderConstants.Builder builder = ShaderConstants.builder();
        List<String> expected = new ArrayList<>();

        for (int index = 0; index < 256; index++) {
            StringBuilder name = new StringBuilder();
            for (int bit = 0; bit < 8; bit++) {
                name.append((index & (1 << bit)) == 0 ? "Aa" : "BB");
            }
            String key = name.toString();
            builder.add(key, Integer.toString(index));
            expected.add("#define " + key + " " + index);
        }

        assertEquals(expected, builder.build().getDefineStrings());
        assertThrows(IllegalArgumentException.class, () -> builder.add("Aa".repeat(8), "changed"));
        assertEquals(expected, builder.build().getDefineStrings());
    }
}
