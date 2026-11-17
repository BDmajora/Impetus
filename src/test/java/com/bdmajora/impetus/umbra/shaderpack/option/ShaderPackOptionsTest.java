package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.shaderpack.OptionalBoolean;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.option.values.ImmutableOptionValues;
import com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues;
import com.bdmajora.impetus.umbra.shaderpack.option.values.OptionValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackOptionsTest {
    private static AbsolutePackPath path(String path) {
        return AbsolutePackPath.fromAbsolutePath(path);
    }

    @Test
    void linesThatOnlyLookLikeOptionsAreDiagnosed() {
        List<String> lines = List.of(
                "#define GOOD // Label",
                "#define",
                "#definex",
                "#define ?x",
                "#define BAD$NAME",
                "// #define COMMENTED 2 // [1 2]",
                "#define NOVALUE ?",
                "#define NOCOMMENT 1",
                "#define GLUED 1x",
                "#define TRAILING 1 extra",
                "#define NOLIST 1 // no list",
                "#define TIGHT 1// [1 2]",
                "x = 1; // #define LATE",
                "const",
                "const vec3 v = vec3(1);",
                "const int= 1;",
                "const int ;",
                "const int shadowDistance 1;",
                "const int shadowDistance = ;",
                "const int shadowDistance = 1",
                "const int shadowDistance = 1; extra",
                "const bool shadowtexNearest = maybe;",
                "const bool unknownBool = true;",
                "const int unknownInt = 1; // [1 2]",
                "const float sunPathRotation = 1.0; // no list",
                "const float wetnessHalflife = 600.0; // [300.0 600.0]",
                "const bool generateShadowMipmap = false; // comment",
                "#ifdef",
                "#ifdef GOOD extra",
                "#ifndef GOOD",
                "plain line");
        OptionAnnotatedSource source = new OptionAnnotatedSource(lines);
        assertEquals(List.of(0, 26), new ArrayList<>(new java.util.TreeSet<>(source.getBooleanOptions().keySet())));
        assertEquals(List.of(11, 25), new ArrayList<>(new java.util.TreeSet<>(source.getStringOptions().keySet())));
        assertEquals(Map.of("GOOD", List.of(29)), source.getBooleanDefineReferences());
        for (int line : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24}) {
            assertTrue(source.getDiagnostics().containsKey(line), lines.get(line));
        }
        BooleanOption good = source.getBooleanOptions().get(0);
        assertEquals(OptionType.DEFINE, good.getType());
        assertEquals(Optional.of("Label"), good.getComment());
        assertEquals("BooleanDefineOption{name=GOOD, comment=Optional[Label], defaultValue=true}", good.toString());
        assertEquals(Optional.of("comment"), source.getBooleanOptions().get(26).getComment());
        StringOption wetness = source.getStringOptions().get(25);
        assertEquals(OptionType.CONST, wetness.getType());
        assertEquals(List.of("300.0", "600.0"), wetness.getAllowedValues());
        assertEquals(Optional.empty(), wetness.getComment());
        assertEquals(1, new OptionAnnotatedSource("#define A 1 // [1]\r\n#define B").getBooleanOptions().size());
    }

    @Test
    void stringOptionsNeedAnAllowedValuesList() {
        assertNull(StringOption.create(OptionType.DEFINE, "A", null, "1"));
        assertNull(StringOption.create(OptionType.DEFINE, "A", "no list", "1"));
        assertNull(StringOption.create(OptionType.DEFINE, "A", "[unclosed", "1"));
        // A default missing from the list is added to it
        StringOption option = StringOption.create(OptionType.DEFINE, "A", "Label [2 3] tail", "1");
        assertEquals(List.of("2", "3", "1"), option.getAllowedValues());
        assertEquals(Optional.of("Label  tail"), option.getComment());
        assertEquals("1", option.getDefaultValue());
        assertEquals("A", option.getName());
    }

    // Returns two files declaring the same options.
    private static ShaderPackOptions twoFiles(Map<String, String> changed) {
        Map<AbsolutePackPath, String> sources = new LinkedHashMap<>();
        sources.put(path("/a.fsh"), String.join("\n",
                "#define SHARED",
                "#define CLASH",
                "#define LEVEL 1 // [1 2 3]",
                "#define MODE 1 // [1 2]",
                "#ifdef SHARED", "#endif", "#ifdef CLASH", "#endif"));
        sources.put(path("/b.fsh"), String.join("\n",
                "#define SHARED // Shared toggle",
                "//#define CLASH",
                "#define LEVEL 1 // Level [1 2 3]",
                "#define MODE 2 // [1 2]"));
        sources.put(path("/c.fsh"), "#define LONELY 5 // [5 6]");
        return new ShaderPackOptions(sources, changed);
    }

    @Test
    void duplicateDeclarationsMergeWhenTheyAgree() {
        OptionSet set = twoFiles(Map.of()).getOptionSet();
        MergedBooleanOption shared = set.getBooleanOptions().get("SHARED");
        assertEquals(2, shared.getLocations().size());
        // The declaration with a label wins
        assertEquals(Optional.of("Shared toggle"), shared.getOption().getComment());
        MergedStringOption level = set.getStringOptions().get("LEVEL");
        assertEquals(2, level.getLocations().size());
        assertEquals(Optional.of("Level"), level.getOption().getComment());
        OptionLocation location = level.getLocations().iterator().next();
        assertEquals(2, location.getLineIndex());
        assertTrue(location.getFilePath().getPathString().endsWith(".fsh"));
        // A disagreement keeps whichever came first rather than dropping the option
        assertEquals(1, set.getBooleanOptions().get("CLASH").getLocations().size());
        assertEquals(1, set.getStringOptions().get("MODE").getLocations().size());
        assertTrue(set.isBooleanOption("SHARED"));
        assertFalse(set.isBooleanOption("LEVEL"));
    }

    @Test
    void valuesOnlyRememberWhatDiffersFromTheDefault() {
        ShaderPackOptions options = twoFiles(Map.of("SHARED", "false", "CLASH", "maybe", "LEVEL", "3", "LONELY", "5", "UNKNOWN", "1"));
        OptionSet set = options.getOptionSet();
        MutableOptionValues values = (MutableOptionValues) options.getOptionValues();
        // Checks an invalid boolean and a default value.
        assertEquals(Map.of("SHARED", false), values.getBooleanValues());
        assertEquals(Map.of("LEVEL", "3"), values.getStringValues());
        assertEquals(2, values.getOptionsChanged());
        assertSame(set, values.getOptions());
        assertSame(set, values.getOptionSet());
        assertEquals(OptionalBoolean.FALSE, values.getBooleanValue("SHARED"));
        assertEquals(OptionalBoolean.DEFAULT, values.getBooleanValue("CLASH"));
        assertFalse(values.getBooleanValueOrDefault("SHARED"));
        assertEquals(set.getBooleanOptions().get("CLASH").getOption().getDefaultValue(), values.getBooleanValueOrDefault("CLASH"));
        // Checks an undeclared option.
        assertTrue(values.getBooleanValueOrDefault("NOT_AN_OPTION"));
        assertEquals("3", values.getStringValueOrDefault("LEVEL"));
        assertEquals("5", values.getStringValueOrDefault("LONELY"));
        assertEquals(Optional.empty(), values.getStringValue("LONELY"));

        ImmutableOptionValues frozen = values.toImmutable();
        assertSame(frozen, frozen.toImmutable());
        assertEquals(OptionalBoolean.FALSE, frozen.getBooleanValue("SHARED"));
        MutableOptionValues copy = frozen.mutableCopy();
        assertEquals(values.getBooleanValues(), copy.getBooleanValues());
        assertEquals(values.getStringValues(), copy.getStringValues());
        // Checks a value set back to its default.
        copy.addAll(Map.of("SHARED", "true", "LEVEL", "1"));
        assertEquals(0, copy.getOptionsChanged());
        assertEquals(2, values.mutableCopy().getOptionsChanged());

        String edited = options.getEditedSources().get(path("/a.fsh"));
        assertTrue(edited.startsWith("//#define SHARED\n"), edited);
        assertTrue(edited.contains("#define LEVEL 3 // OptionAnnotatedSource: Changed option"), edited);
    }

    @Test
    void profilesResolveTheirParentsAndMatchCurrentValues() {
        OptionSet set = twoFiles(Map.of()).getOptionSet();
        Map<String, List<String>> tree = new LinkedHashMap<>();
        tree.put("LOW", List.of("!SHARED", "LEVEL=1", "!program.composite2", "profile.BASE"));
        tree.put("BASE", List.of("LONELY:5"));
        tree.put("HIGH", List.of("SHARED", "LEVEL=3", "BOGUS"));
        ProfileSet profiles = ProfileSet.fromTree(tree, set);
        assertEquals(3, profiles.size());
        List<String> order = new ArrayList<>();
        profiles.forEach((name, profile) -> order.add(name));
        assertEquals(List.of("LOW", "BASE", "HIGH"), order);

        // Checks the profile order.
        OptionValues defaults = new MutableOptionValues(set, Map.of());
        ProfileSet.ProfileResult atDefaults = profiles.scan(set, defaults);
        assertEquals("BASE", atDefaults.current.orElseThrow().name);
        assertEquals("LOW", atDefaults.next.name);
        assertEquals("HIGH", atDefaults.previous.name);
        ProfileSet.ProfileResult low = profiles.scan(set, new MutableOptionValues(set, Map.of("SHARED", "false")));
        Profile current = low.current.orElseThrow();
        assertEquals("LOW", current.name);
        assertEquals(3, current.precedence);
        assertEquals(List.of("composite2"), current.disabledPrograms);
        assertEquals(Map.of("SHARED", "false", "LEVEL", "1", "LONELY", "5"), current.optionValues);
        assertEquals("HIGH", low.next.name);
        assertEquals("BASE", low.previous.name);

        // Checks the neighbours with no match.
        ProfileSet onlyHigh = ProfileSet.fromTree(Map.of("HIGH", List.of("LEVEL=3")), set);
        ProfileSet.ProfileResult none = onlyHigh.scan(set, defaults);
        assertTrue(none.current.isEmpty());
        assertEquals("HIGH", none.next.name);
        assertEquals("HIGH", none.previous.name);
        ProfileSet.ProfileResult empty = new ProfileSet(new LinkedHashMap<>()).scan(set, defaults);
        assertTrue(empty.current.isEmpty());
        assertNull(empty.next);

        assertThrows(IllegalArgumentException.class, () -> ProfileSet.fromTree(Map.of("A", List.of("profile.MISSING")), set));
        Map<String, List<String>> cycle = new LinkedHashMap<>();
        cycle.put("A", List.of("profile.B"));
        cycle.put("B", List.of("profile.A"));
        assertThrows(IllegalArgumentException.class, () -> ProfileSet.fromTree(cycle, set));
    }

    @Test
    void sharedParentsAreNotMistakenForCycles() {
        OptionSet set = twoFiles(Map.of()).getOptionSet();
        Map<String, List<String>> diamond = new LinkedHashMap<>();
        diamond.put("TOP", List.of("profile.LEFT", "profile.RIGHT"));
        diamond.put("LEFT", List.of("profile.BASE"));
        diamond.put("RIGHT", List.of("profile.BASE"));
        diamond.put("BASE", List.of("LEVEL=1"));
        assertEquals(4, ProfileSet.fromTree(diamond, set).size());
        Map<String, List<String>> twice = new LinkedHashMap<>();
        twice.put("A", List.of("profile.B", "profile.B"));
        twice.put("B", List.of("LEVEL=2"));
        assertEquals(2, ProfileSet.fromTree(twice, set).size());
        Map<String, List<String>> cycle = new LinkedHashMap<>();
        cycle.put("A", List.of("profile.B"));
        cycle.put("B", List.of("profile.A"));
        assertThrows(IllegalArgumentException.class, () -> ProfileSet.fromTree(cycle, set));
        assertThrows(IllegalArgumentException.class, () -> ProfileSet.fromTree(Map.of("S", List.of("profile.S")), set));
    }

    // Parses a deep diamond of includes quickly and merges every level once.
    @Test
    void diamondIncludesParseOncePerProfile() {
        OptionSet set = twoFiles(Map.of()).getOptionSet();
        Map<String, List<String>> tree = new LinkedHashMap<>();
        int levels = 40;
        tree.put("P0", List.of("LEVEL=0"));
        for (int i = 1; i < levels; i++) {
            tree.put("A" + i, List.of("profile.P" + (i - 1), "A" + i + "=1"));
            tree.put("B" + i, List.of("profile.P" + (i - 1), "B" + i + "=1"));
            tree.put("P" + i, List.of("profile.A" + i, "profile.B" + i));
        }
        long start = System.nanoTime();
        ProfileSet profiles = ProfileSet.fromTree(tree, set);
        assertTrue(System.nanoTime() - start < 5_000_000_000L);
        assertEquals(tree.size(), profiles.size());
        Profile[] top = new Profile[1];
        profiles.forEach((name, profile) -> {
            if (name.equals("P" + (levels - 1))) {
                top[0] = profile;
            }
        });
        assertEquals("0", top[0].optionValues.get("LEVEL"));
        assertEquals(1 + 2 * (levels - 1), top[0].optionValues.size());
        Map<String, List<String>> cycle = Map.of("X", List.of("profile.Y"), "Y", List.of("profile.X"));
        assertThrows(IllegalArgumentException.class, () -> ProfileSet.fromTree(cycle, set));
    }

    // Leaves a commented define alone when its option stays off.
    @Test
    void disabledDefinesAreNotCommentedTwice() {
        OptionAnnotatedSource source = new OptionAnnotatedSource("//#define SOFT // Soft shadows\n#ifdef SOFT\n#endif");
        com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues values =
                new com.bdmajora.impetus.umbra.shaderpack.option.values.MutableOptionValues(
                        source.getOptionSet(com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath.fromAbsolutePath("/a.glsl"), java.util.Set.of("SOFT")),
                        java.util.Map.of("SOFT", "false"));
        String edited = source.apply(values);
        assertTrue(edited.startsWith("//#define SOFT // Soft shadows\n"), edited);
    }
}
