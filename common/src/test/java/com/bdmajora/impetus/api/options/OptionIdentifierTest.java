package com.bdmajora.impetus.api.options;

import com.bdmajora.testing.FreshClasses;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.annotation.RetentionPolicy;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OptionIdentifierTest {
    // Ids are interned JVM-wide, so every pair this class makes lives under its own mod ids
    private static final String MOD = "option_identifier_test";

    @Test
    void identifiersAreInterned() {
        OptionIdentifier<Void> a = OptionIdentifier.create("mod", "path");
        OptionIdentifier<Void> b = OptionIdentifier.create("mod", "path");
        assertSame(a, b);
        assertTrue(a.matches(b));
        assertFalse(a.matches(OptionIdentifier.create("mod", "other")));
        assertEquals("mod", a.getModId());
        assertEquals("path", a.getPath());
        assertEquals(void.class, a.getType());
        assertEquals("mod:path", a.toString());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, null);
        assertNotEquals(a, "mod:path");
        assertSame(a, a.<Integer>cast());
    }

    @Test
    void typeMismatchIsRejectedAndEmptyIsAbsent() {
        OptionIdentifier<Integer> typed = OptionIdentifier.create("mod", "typed", Integer.class);
        assertEquals(Integer.class, typed.getType());
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("mod", "typed", String.class));
        assertFalse(OptionIdentifier.isPresent(null));
        assertFalse(OptionIdentifier.isPresent(OptionIdentifier.EMPTY));
        assertTrue(OptionIdentifier.isPresent(typed));
    }

    @Test
    void internAnswersAPairWhateverItsTypeAndMakesANewOneUntyped() {
        // A pair nobody made yet comes back untyped and is the one create hands out after
        OptionIdentifier<?> made = OptionIdentifier.intern("intern_mod", "new");
        assertSame(void.class, made.getType());
        assertEquals("intern_mod:new", made.toString());
        assertSame(made, OptionIdentifier.create("intern_mod", "new"));
        assertSame(made, OptionIdentifier.create("intern_mod", "new", void.class));
        assertSame(made, OptionIdentifier.intern("intern_mod", "new"));
        // ...and still refuses a typed create, as one made by create would
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("intern_mod", "new", Integer.class));
        assertSame(void.class, made.getType());
        // A typed pair is answered as it is, where an untyped create refuses it
        OptionIdentifier<Integer> typed = OptionIdentifier.create("intern_mod", "typed", Integer.class);
        assertSame(typed, OptionIdentifier.intern("intern_mod", "typed"));
        assertSame(Integer.class, typed.getType());
        assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create("intern_mod", "typed"));
        assertSame(typed, OptionIdentifier.intern("intern_mod", "typed"));
        // The empty sentinel is a pair like any other, so it is never duplicated
        assertSame(OptionIdentifier.EMPTY, OptionIdentifier.intern("", ""));
        assertSame(Void.class, OptionIdentifier.EMPTY.getType());
        // Case and the mod id both tell pairs apart
        assertNotSame(made, OptionIdentifier.intern("intern_mod", "New"));
        assertNotSame(made, OptionIdentifier.intern("intern_mod_", "new"));
    }

    @Test
    void theHashIsObjectsHashOfModAndPath() {
        // The value Objects.hash always gave, so hashed collections built before keep their order and buckets
        String[][] pairs = {
                {"mod", "path"}, {"", ""}, {"a", ""}, {"", "a"}, {"ab", "c"}, {"a", "bc"},
                {"hash_mod", "Aa"}, {"hash_mod", "BB"}, {"ü", "∂/x"}, {"😀", "\u0000"}};
        for (String[] pair : pairs) {
            OptionIdentifier<?> id = OptionIdentifier.intern(pair[0], pair[1]);
            assertEquals(Objects.hash(pair[0], pair[1]), id.hashCode(), id.toString());
            assertEquals(id.hashCode(), id.hashCode());
        }
        // Equal pairs hash alike whatever their types
        OptionIdentifier<Integer> typed = OptionIdentifier.create("hash_mod", "typed", Integer.class);
        assertEquals(Objects.hash("hash_mod", "typed"), typed.hashCode());
        // Ids work as hash keys: each pair finds its own entry
        Map<OptionIdentifier<?>, String> map = new HashMap<>();
        for (String[] pair : pairs) {
            map.put(OptionIdentifier.intern(pair[0], pair[1]), pair[0] + ":" + pair[1]);
        }
        assertEquals(pairs.length, map.size());
        for (String[] pair : pairs) {
            assertEquals(pair[0] + ":" + pair[1], map.get(OptionIdentifier.intern(pair[0], pair[1])));
        }
    }

    @Test
    void aWarmHashCodeAllocatesNothing() {
        OptionIdentifier<?> id = OptionIdentifier.create("hash_mod", "allocation");
        int[] sink = {0};
        long allocated = allocatedBy(() -> sink[0] += id.hashCode());
        assertEquals(id.hashCode() * 120_000, sink[0]);
        // A varargs array per call would be at least 2.4 MB here; the slack covers the measuring calls themselves
        assertTrue(allocated < 16_384, allocated + " bytes allocated");
    }

    @Test
    void pairsAreInternedByContentNotByStringIdentity() {
        // Strings built at runtime are other instances than the literals, and still find the same id
        OptionIdentifier<Void> literal = OptionIdentifier.create(MOD, "content");
        String mod = new String(MOD.toCharArray());
        String path = new StringBuilder("con").append("tent").toString();
        assertNotSame(MOD, mod);
        assertNotSame("content", path);
        assertSame(literal, OptionIdentifier.create(mod, path));
        assertSame(literal, OptionIdentifier.create(mod, path, void.class));
        assertSame(literal, OptionIdentifier.intern(mod, path));
        // The id keeps the strings of the call that made it
        assertSame(MOD, literal.getModId());
        assertEquals("content", literal.getPath());
    }

    @Test
    void everyPartOfThePairTellsIdsApart() {
        OptionIdentifier<?> ab = OptionIdentifier.intern(MOD + "_a", "b");
        // Swapped parts, a different case in either part, a trailing space or a prefix are all other pairs
        List<OptionIdentifier<?>> others = List.of(
                OptionIdentifier.intern("b", MOD + "_a"),
                OptionIdentifier.intern(MOD + "_A", "b"),
                OptionIdentifier.intern(MOD + "_a", "B"),
                OptionIdentifier.intern(MOD + "_a ", "b"),
                OptionIdentifier.intern(MOD + "_a", "b "),
                OptionIdentifier.intern(MOD + "_a", ""),
                OptionIdentifier.intern(MOD, "_ab"));
        Set<OptionIdentifier<?>> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.add(ab);
        distinct.addAll(others);
        assertEquals(others.size() + 1, distinct.size());
        for (OptionIdentifier<?> other : others) {
            assertNotEquals(ab, other);
            assertFalse(ab.matches(other));
            assertFalse(other.matches(ab));
        }
    }

    @Test
    void pairsThatPrintAlikeStayApart() {
        // A colon in either part makes toString ambiguous, but the pair is what identifies the id
        OptionIdentifier<?> left = OptionIdentifier.intern(MOD + ":x", "y");
        OptionIdentifier<?> right = OptionIdentifier.intern(MOD, "x:y");
        assertEquals(left.toString(), right.toString());
        assertNotSame(left, right);
        assertNotEquals(left, right);
        assertFalse(left.matches(right));
        assertEquals(MOD + ":x", left.getModId());
        assertEquals("y", left.getPath());
        assertEquals(MOD, right.getModId());
        assertEquals("x:y", right.getPath());
    }

    @Test
    void pairsWithCollidingHashesStayApart() {
        // "Aa" and "BB" share String.hashCode, so these pairs share a hash as well
        OptionIdentifier<?> aa = OptionIdentifier.intern(MOD, "collide_Aa");
        OptionIdentifier<?> bb = OptionIdentifier.intern(MOD, "collide_BB");
        assertEquals(aa.hashCode(), bb.hashCode());
        assertNotSame(aa, bb);
        assertNotEquals(aa, bb);
        assertSame(aa, OptionIdentifier.intern(MOD, "collide_Aa"));
        assertSame(bb, OptionIdentifier.intern(MOD, "collide_BB"));
        // Swapping the colliding halves across the parts keeps the hash the same too
        OptionIdentifier<?> x = OptionIdentifier.intern(MOD + "Aa", "BB");
        OptionIdentifier<?> y = OptionIdentifier.intern(MOD + "BB", "Aa");
        assertEquals(x.hashCode(), y.hashCode());
        assertNotEquals(x, y);
        Map<OptionIdentifier<?>, Integer> map = new HashMap<>(Map.of(aa, 1, bb, 2, x, 3, y, 4));
        assertEquals(1, map.get(aa));
        assertEquals(2, map.get(bb));
        assertEquals(3, map.get(x));
        assertEquals(4, map.get(y));
    }

    @Test
    void unusualButLegalStringsAreKeptAsGiven() {
        String huge = "p".repeat(200_000);
        String[][] pairs = {
                {MOD, " "}, {MOD, "\t\n"}, {MOD, "\u0000"}, {MOD, "\u0000\u0000"}, {MOD, "😀"},
                // A lone surrogate is not valid text but is still a distinct Java string
                {MOD, "\ud83d"}, {MOD, "\ude00"}, {MOD + "é", "x"}, {MOD + "é", "x"},
                {MOD, huge}, {MOD, huge + "q"}, {huge, MOD}};
        Set<OptionIdentifier<?>> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String[] pair : pairs) {
            OptionIdentifier<?> id = OptionIdentifier.intern(pair[0], pair[1]);
            assertEquals(pair[0], id.getModId());
            assertEquals(pair[1], id.getPath());
            assertEquals(pair[0] + ":" + pair[1], id.toString());
            assertEquals(Objects.hash(pair[0], pair[1]), id.hashCode());
            assertSame(id, OptionIdentifier.intern(new String(pair[0].toCharArray()), new String(pair[1].toCharArray())));
            assertTrue(OptionIdentifier.isPresent(id));
            distinct.add(id);
        }
        // The composed and the decomposed accent are different strings and so different pairs
        assertEquals(pairs.length, distinct.size());
    }

    @Test
    void emptyPartsAreLegalAndOnlyTheEmptyPairIsTheSentinel() {
        OptionIdentifier<?> noPath = OptionIdentifier.intern(MOD, "");
        OptionIdentifier<?> noMod = OptionIdentifier.intern("", MOD);
        assertNotSame(noPath, noMod);
        assertTrue(OptionIdentifier.isPresent(noPath));
        assertTrue(OptionIdentifier.isPresent(noMod));
        assertEquals(MOD + ":", noPath.toString());
        assertEquals(":" + MOD, noMod.toString());
        // The sentinel is the ("", "") pair typed Void, so only that exact type gets it back from create
        OptionIdentifier<Void> empty = OptionIdentifier.EMPTY;
        assertEquals("", empty.getModId());
        assertEquals("", empty.getPath());
        assertEquals(":", empty.toString());
        assertSame(empty, OptionIdentifier.create("", "", Void.class));
        assertSame(empty, OptionIdentifier.intern(new String(), new String()));
        clash(":", void.class, Void.class, () -> OptionIdentifier.create("", ""));
        assertSame(Void.class, empty.getType());
        assertFalse(OptionIdentifier.isPresent(OptionIdentifier.intern("", "")));
        assertTrue(empty.matches(OptionIdentifier.EMPTY));
        assertEquals(Objects.hash("", ""), empty.hashCode());
    }

    @Test
    void createHandsBackExactlyTheTypeItWasAskedFor() {
        // Every kind of class: primitive, wrapper, void, array, enum, interface, abstract, nested and generic raw
        Class<?>[] types = {int.class, Integer.class, boolean.class, Boolean.class, void.class, Void.class, Object.class,
                String.class, int[].class, Integer[].class, int[][].class, RetentionPolicy.class, CharSequence.class,
                Number.class, Map.Entry.class, List.class};
        for (Class<?> type : types) {
            String path = "exact/" + type.getName();
            OptionIdentifier<?> id = OptionIdentifier.create(MOD, path, type);
            assertSame(type, id.getType(), path);
            assertSame(id, OptionIdentifier.create(MOD, path, type), path);
            assertSame(id, OptionIdentifier.intern(MOD, path), path);
        }
    }

    @Test
    void anyOtherTypeIsRefusedEvenWhenItHoldsTheSameValues() {
        // A primitive and its wrapper, void and Void, a supertype or subtype, an array and its component type: all refused both ways
        Class<?>[][] clashes = {{int.class, Integer.class}, {void.class, Void.class}, {Object.class, String.class},
                {Number.class, Integer.class}, {CharSequence.class, String.class}, {int[].class, int.class},
                {int[].class, Integer[].class}, {Object[].class, String[].class}, {int[].class, int[][].class}};
        for (Class<?>[] pair : clashes) {
            for (int first = 0; first < 2; first++) {
                Class<?> made = pair[first];
                Class<?> asked = pair[1 - first];
                String path = "clash/" + made.getName() + "/" + asked.getName();
                OptionIdentifier<?> id = OptionIdentifier.create(MOD, path, made);
                clash(MOD + ":" + path, asked, made, () -> OptionIdentifier.create(MOD, path, asked));
                // The refused call changed nothing: the pair keeps its type and its instance
                assertSame(made, id.getType());
                assertSame(id, OptionIdentifier.create(MOD, path, made));
                assertSame(id, OptionIdentifier.intern(MOD, path));
            }
        }
    }

    @Test
    void theUntypedCreateRefusesATypedPairAndTheTypedOneAnUntypedPair() {
        OptionIdentifier<Boolean> typed = OptionIdentifier.create(MOD, "typed_first", Boolean.class);
        clash(MOD + ":typed_first", void.class, Boolean.class, () -> OptionIdentifier.create(MOD, "typed_first"));
        OptionIdentifier<Void> untyped = OptionIdentifier.create(MOD, "untyped_first");
        clash(MOD + ":untyped_first", Boolean.class, void.class, () -> OptionIdentifier.create(MOD, "untyped_first", Boolean.class));
        assertSame(typed, OptionIdentifier.create(MOD, "typed_first", Boolean.class));
        assertSame(untyped, OptionIdentifier.create(MOD, "untyped_first"));
    }

    @Test
    void nullPartsAreRefusedBeforeTheTableIsTouched() {
        npe("Mod id must not be null", () -> OptionIdentifier.create(null, "null_path"));
        npe("Mod id must not be null", () -> OptionIdentifier.create(null, "null_path", Integer.class));
        npe("Mod id must not be null", () -> OptionIdentifier.intern(null, "null_path"));
        npe("Path must not be null", () -> OptionIdentifier.create(MOD + "_null", null));
        npe("Path must not be null", () -> OptionIdentifier.create(MOD + "_null", null, Integer.class));
        npe("Path must not be null", () -> OptionIdentifier.intern(MOD + "_null", null));
        npe("Type must not be null", () -> OptionIdentifier.create(MOD + "_null", "null_type", null));
        // The type is checked first, so a call that is wrong in every part names the type
        npe("Type must not be null", () -> OptionIdentifier.create(null, null, null));
        npe("Mod id must not be null", () -> OptionIdentifier.create(null, null));
        npe("Mod id must not be null", () -> OptionIdentifier.intern(null, null));
        // Not even the mod got an entry, so the refused calls made nothing at all
        assertFalse(mods().containsKey(MOD + "_null"));
        assertFalse(mods().containsKey(null));
        // The pairs were not poisoned: they are made with the type of the first call that names them fully
        OptionIdentifier<Integer> typed = OptionIdentifier.create(MOD + "_null", "null_type", Integer.class);
        assertSame(Integer.class, typed.getType());
        assertSame(void.class, OptionIdentifier.intern(MOD + "_null", "null_path").getType());
        // A null type is refused on a pair that exists as well, and does not change it
        npe("Type must not be null", () -> OptionIdentifier.create(MOD + "_null", "null_type", null));
        assertSame(typed, OptionIdentifier.create(MOD + "_null", "null_type", Integer.class));
    }

    @Test
    void isPresentAndMatchesTreatNullAndTheSentinelAsAbsent() {
        OptionIdentifier<?> id = OptionIdentifier.intern(MOD, "present");
        assertTrue(OptionIdentifier.isPresent(id));
        assertFalse(OptionIdentifier.isPresent(null));
        assertFalse(OptionIdentifier.isPresent(OptionIdentifier.EMPTY));
        // A retyped sentinel is still the sentinel
        assertFalse(OptionIdentifier.isPresent(OptionIdentifier.EMPTY.<Integer>cast()));
        assertTrue(id.matches(id));
        assertFalse(id.matches(null));
        assertFalse(id.matches(OptionIdentifier.EMPTY));
        assertFalse(OptionIdentifier.EMPTY.matches(id));
        assertTrue(id.matches(id.<String>cast()));
    }

    @Test
    void castRetypesWithoutCheckingOrCopying() {
        OptionIdentifier<Integer> typed = OptionIdentifier.create(MOD, "cast", Integer.class);
        OptionIdentifier<String> wrong = typed.cast();
        // Only the static type changed; the id still reports the type it was made with
        assertSame(typed, wrong);
        assertSame(Integer.class, ((OptionIdentifier<?>) wrong).getType());
        assertSame(typed, wrong.<Integer>cast());
    }

    @Test
    void equalsIsAnEquivalenceThatOnlyAnIdForTheSamePairPasses() {
        OptionIdentifier<?> a = OptionIdentifier.intern(MOD, "equals");
        OptionIdentifier<?> b = OptionIdentifier.intern(new String(MOD.toCharArray()), new String("equals".toCharArray()));
        OptionIdentifier<?> other = OptionIdentifier.intern(MOD, "equals_other");
        assertTrue(a.equals(a));
        assertTrue(a.equals(b) && b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(other) || other.equals(a));
        assertFalse(a.equals(null));
        // Not its text, not a lookalike object, not the sentinel
        assertFalse(a.equals(a.toString()));
        assertFalse(a.equals(new Object()));
        assertFalse(a.equals(OptionIdentifier.EMPTY));
        assertFalse(OptionIdentifier.EMPTY.equals(a));
        Set<OptionIdentifier<?>> set = new HashSet<>(List.of(a, b, other));
        assertEquals(2, set.size());
        assertTrue(set.contains(OptionIdentifier.intern(MOD, "equals")));
    }

    @Test
    void theConstructorIsPrivateSoTheFactoriesMakeEveryId() {
        Constructor<?>[] constructors = OptionIdentifier.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertTrue(Modifier.isFinal(OptionIdentifier.class.getModifiers()));
    }

    @Test
    void manyIdsAcrossManyModsAreAllKeptApart() {
        // Enough to grow the mod table and every path table several times over
        int mods = 64;
        int paths = 700;
        int before = interned();
        OptionIdentifier<?>[][] made = new OptionIdentifier<?>[mods][paths];
        for (int m = 0; m < mods; m++) {
            for (int p = 0; p < paths; p++) {
                made[m][p] = p % 2 == 0
                        ? OptionIdentifier.create(MOD + "_bulk" + m, "p" + p, Integer.class)
                        : OptionIdentifier.intern(MOD + "_bulk" + m, "p" + p);
            }
        }
        assertEquals(before + mods * paths, interned());
        Set<OptionIdentifier<?>> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int m = 0; m < mods; m++) {
            for (int p = 0; p < paths; p++) {
                OptionIdentifier<?> id = made[m][p];
                assertTrue(distinct.add(id));
                assertSame(id, OptionIdentifier.intern(MOD + "_bulk" + m, "p" + p));
                assertSame(p % 2 == 0 ? Integer.class : void.class, id.getType());
            }
        }
        // Asking again made nothing new
        assertEquals(before + mods * paths, interned());
    }

    @Test
    void warmLookupsOfExistingPairsAllocateNothing() {
        String mod = MOD + "_warm";
        String typedPath = "typed";
        String untypedPath = "untyped";
        OptionIdentifier<Integer> typed = OptionIdentifier.create(mod, typedPath, Integer.class);
        OptionIdentifier<Void> untyped = OptionIdentifier.create(mod, untypedPath);
        int[] hits = {0};
        // A throwaway id per call, as interning by a probe instance needs, would be at least 24 bytes each: 2.9 MB per lookup kind here
        long typedBytes = allocatedBy(() -> hits[0] += OptionIdentifier.create(mod, typedPath, Integer.class) == typed ? 1 : 0);
        long untypedBytes = allocatedBy(() -> hits[0] += OptionIdentifier.create(mod, untypedPath) == untyped ? 1 : 0);
        long internBytes = allocatedBy(() -> hits[0] += OptionIdentifier.intern(mod, typedPath) == typed ? 1 : 0);
        long equalsBytes = allocatedBy(() -> hits[0] += typed.equals(untyped) ? 0 : 1);
        assertEquals(4 * 120_000, hits[0]);
        assertTrue(typedBytes < 16_384, typedBytes + " bytes allocated by typed create");
        assertTrue(untypedBytes < 16_384, untypedBytes + " bytes allocated by untyped create");
        assertTrue(internBytes < 16_384, internBytes + " bytes allocated by intern");
        assertTrue(equalsBytes < 16_384, equalsBytes + " bytes allocated by equals");
    }

    @Test
    void racingCallsForTheSamePairsAllGetTheSameInstance() throws Exception {
        int threads = 8;
        int pairs = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<OptionIdentifier<?>[]>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                results.add(pool.submit(() -> {
                    // Each thread builds its own strings, so only content can join them
                    String mod = new String((MOD + "_race").toCharArray());
                    OptionIdentifier<?>[] got = new OptionIdentifier<?>[pairs];
                    start.await();
                    for (int p = 0; p < pairs; p++) {
                        String path = "p" + p;
                        got[p] = switch ((p + thread) % 3) {
                            case 0 -> OptionIdentifier.create(mod, path);
                            case 1 -> OptionIdentifier.intern(mod, path);
                            default -> OptionIdentifier.create(mod, path, void.class);
                        };
                    }
                    return got;
                }));
            }
            start.countDown();
            OptionIdentifier<?>[] first = join(results.get(0));
            for (Future<OptionIdentifier<?>[]> result : results) {
                OptionIdentifier<?>[] got = join(result);
                for (int p = 0; p < pairs; p++) {
                    assertSame(first[p], got[p], "p" + p);
                }
            }
            for (int p = 0; p < pairs; p++) {
                assertSame(void.class, first[p].getType());
                assertSame(first[p], OptionIdentifier.intern(MOD + "_race", "p" + p));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void racingTypedCreatesLetExactlyOneTypeWinEachPair() throws Exception {
        int threads = 8;
        int pairs = 500;
        Class<?>[] types = {Integer.class, String.class};
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object[]>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Class<?> type = types[t % types.length];
                results.add(pool.submit(() -> {
                    Object[] got = new Object[pairs];
                    start.await();
                    for (int p = 0; p < pairs; p++) {
                        try {
                            got[p] = OptionIdentifier.create(MOD + "_typed_race", "p" + p, type);
                        } catch (IllegalArgumentException refused) {
                            got[p] = type;
                        }
                    }
                    return got;
                }));
            }
            start.countDown();
            List<Object[]> all = new ArrayList<>();
            for (Future<Object[]> result : results) {
                all.add(join(result));
            }
            for (int p = 0; p < pairs; p++) {
                OptionIdentifier<?> winner = OptionIdentifier.intern(MOD + "_typed_race", "p" + p);
                assertTrue(winner.getType() == Integer.class || winner.getType() == String.class, winner.getType().getName());
                for (int t = 0; t < threads; t++) {
                    Object got = all.get(t)[p];
                    // A thread asking for the winning type got the winner, every other thread was refused
                    if (types[t % types.length] == winner.getType()) {
                        assertSame(winner, got, "p" + p);
                    } else {
                        assertSame(types[t % types.length], got, "p" + p);
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFreshClassStartsWithOnlyTheSentinelInterned() throws Exception {
        // The table is declared before EMPTY, so the sentinel interns itself while the class initialises
        FreshClasses fresh = new FreshClasses(OptionIdentifier.class);
        Class<?> ids = fresh.initialise(OptionIdentifier.class);
        assertNotSame(OptionIdentifier.class, ids);
        Object empty = ids.getField("EMPTY").get(null);
        Map<String, ? extends Map<String, ?>> table = Statics.get(ids, "IDENTIFIERS");
        assertEquals(Set.of(""), table.keySet());
        assertEquals(Map.of("", empty), Map.copyOf(table.get("")));
        assertSame(empty, ids.getMethod("intern", String.class, String.class).invoke(null, "", ""));
        // The fresh class's ids never meet this one's, even for the same pair
        Object own = ids.getMethod("create", String.class, String.class).invoke(null, MOD, "fresh");
        assertNotSame(OptionIdentifier.create(MOD, "fresh"), own);
        assertNotEquals(OptionIdentifier.create(MOD, "fresh"), own);
        InvocationTargetException refused = assertThrows(InvocationTargetException.class,
                () -> ids.getMethod("create", String.class, String.class).invoke(null, "", ""));
        assertInstanceOf(IllegalArgumentException.class, refused.getCause());
    }

    // Refused as a clash naming the id, the type asked for and the type the pair has
    private static void clash(String id, Class<?> asked, Class<?> existing, Executable call) {
        String message = assertThrowsExactly(IllegalArgumentException.class, call).getMessage();
        assertEquals("OptionIdentifier '" + id + "' created with differing class type " + asked + " from existing instance " + existing, message);
    }

    // Fails with exactly a NullPointerException carrying this message
    private static void npe(String message, Executable call) {
        assertEquals(message, assertThrowsExactly(NullPointerException.class, call).getMessage());
    }

    private static Map<String, ? extends Map<String, ?>> mods() {
        return Statics.get(OptionIdentifier.class, "IDENTIFIERS");
    }

    // Every id interned so far in this JVM; tests here run on one thread
    private static int interned() {
        return mods().values().stream().mapToInt(Map::size).sum();
    }

    // Bytes the current thread allocates over 100k runs of the call after 20k warm-up runs, so the JIT has compiled it first
    private static long allocatedBy(Runnable call) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        for (int i = 0; i < 20_000; i++) {
            call.run();
        }
        threads.getCurrentThreadAllocatedBytes();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100_000; i++) {
            call.run();
        }
        return threads.getCurrentThreadAllocatedBytes() - before;
    }

    // Waits for a task and rethrows what failed inside it, so a broken assertion in a thread reads like any other
    private static <T> T join(Future<T> task) throws Exception {
        try {
            return task.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
