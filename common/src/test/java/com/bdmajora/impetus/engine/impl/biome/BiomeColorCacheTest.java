package com.bdmajora.impetus.engine.impl.biome;

import com.bdmajora.impetus.engine.impl.util.position.PositionalSupplier;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BiomeColorCacheTest {
    private static final int NULL_TINT = 0x00ABCDEF;

    private interface Tint {
        int color(int biome, int x, int y, int z);
    }

    private record Call(Tint tint, int biome, int x, int y, int z) {}

    // Logs every resolve so tests can count work and check which cells were asked
    private static final class Cache extends BiomeColorCache<Integer, Tint> {
        final List<Call> calls = new ArrayList<>();

        Cache(PositionalSupplier<Integer> biomes, int radius) {
            super(biomes, radius);
        }

        @Override
        protected int resolveColor(Tint tint, Integer biome, int worldX, int worldY, int worldZ) {
            calls.add(new Call(tint, biome, worldX, worldY, worldZ));
            return tint == null ? NULL_TINT : tint.color(biome, worldX, worldY, worldZ);
        }
    }

    // Biome id hashes the position, so a resolve can be matched to the cell it was asked for
    private static final PositionalSupplier<Integer> BIOMES = BiomeColorCacheTest::biomeAt;
    // Colour carries the low byte of each axis, unique across any region the cache keeps
    private static final Tint POS = (biome, x, y, z) -> pos(x, y, z);
    private static final Tint NOISE = (biome, x, y, z) -> noise(x, y, z);

    private static int biomeAt(int x, int y, int z) {
        return x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }

    private static int pos(int x, int y, int z) {
        return 0xFF000000 | (x & 0xFF) << 16 | (y & 0xFF) << 8 | z & 0xFF;
    }

    private static int noise(int x, int y, int z) {
        int h = x * 0x9E3779B1 ^ y * 0x7FEB352D ^ z * 0x846CA68B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return 0xFF000000 | h & 0xFFFFFF;
    }

    private static Cache at(int radius, int sectionX, int sectionY, int sectionZ) {
        Cache cache = new Cache(BIOMES, radius);
        cache.update(new SectionPos(sectionX, sectionY, sectionZ));
        return cache;
    }

    // Edge-clamped box average along x then z, flooring each channel per pass like BoxBlur
    private static int[] referenceBlur(int[] field, int size, int radius) {
        return boxPass(boxPass(field, size, radius, 1, 0), size, radius, 0, 1);
    }

    private static int[] boxPass(int[] src, int size, int radius, int stepX, int stepZ) {
        int[] out = new int[src.length];
        int window = radius * 2 + 1;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int red = 0, green = 0, blue = 0;
                for (int k = -radius; k <= radius; k++) {
                    int c = src[Math.clamp(z + k * stepZ, 0, size - 1) * size + Math.clamp(x + k * stepX, 0, size - 1)];
                    red += (c >> 16) & 0xFF;
                    green += (c >> 8) & 0xFF;
                    blue += c & 0xFF;
                }
                out[z * size + x] = 0xFF000000 | (red / window) << 16 | (green / window) << 8 | blue / window;
            }
        }
        return out;
    }

    private static Object field(Cache cache, String name) throws ReflectiveOperationException {
        Field field = BiomeColorCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(cache);
    }

    // How many y layers of a resolver hold a buffer
    private static long heldLayers(Cache cache, Tint tint) throws ReflectiveOperationException {
        Object[] layers = (Object[]) ((Map<?, ?>) field(cache, "slices")).get(tint);
        return java.util.Arrays.stream(layers).filter(java.util.Objects::nonNull).count();
    }

    @Test
    void refusesNullBiomeData() {
        assertThrows(NullPointerException.class, () -> new Cache(null, 0));
    }

    @Test
    void readsBeforeTheFirstUpdateMatchSectionZero() {
        for (int radius : new int[]{0, 3}) {
            Cache fresh = new Cache(BIOMES, radius);
            Cache centred = at(radius, 0, 0, 0);
            for (int[] p : new int[][]{{5, 5, 5}, {0, 0, 0}, {15, 15, 15}, {-40, 3, 99}}) {
                assertEquals(centred.getColor(NOISE, p[0], p[1], p[2]), fresh.getColor(NOISE, p[0], p[1], p[2]), "radius " + radius);
            }
        }
    }

    @Test
    void unblendedReadsResolveOnlyTheCellsAsked() {
        Cache cache = at(0, 0, 0, 0);
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        assertEquals(pos(-2, 17, 9), cache.getColor(POS, -2, 17, 9));
        // Clamps onto the cell already resolved
        assertEquals(pos(-2, 17, 9), cache.getColor(POS, -3, 18, 9));
        assertEquals(List.of(new Call(POS, biomeAt(5, 5, 5), 5, 5, 5), new Call(POS, biomeAt(-2, 17, 9), -2, 17, 9)), cache.calls);
    }

    @Test
    void unblendedReadsReturnTheResolverValueAsIs() {
        int[] colours = {0, 0x00123456, 0x80FFFFFF, -1};
        Tint byX = (biome, x, y, z) -> colours[x];
        Cache cache = at(0, 0, 0, 0);
        for (int round = 0; round < 2; round++) {
            for (int x = 0; x < colours.length; x++) {
                assertEquals(colours[x], cache.getColor(byX, x, 5, 5));
            }
        }
        // Zero is a colour like any other, so it stays cached too
        assertEquals(colours.length, cache.calls.size());
    }

    @Test
    void unblendedColoursCoverEveryCellOfTheRegion() {
        Cache cache = at(0, 1, -2, 3);
        int minX = 14, minY = -34, minZ = 46;
        for (int round = 0; round < 2; round++) {
            for (int y = minY; y < minY + 20; y++) {
                for (int z = minZ; z < minZ + 20; z++) {
                    for (int x = minX; x < minX + 20; x++) {
                        int colour = cache.getColor(POS, x, y, z);
                        if (colour != pos(x, y, z)) {
                            fail("cell " + x + "," + y + "," + z);
                        }
                    }
                }
            }
            assertEquals(20 * 20 * 20, cache.calls.size());
        }
        for (Call call : cache.calls) {
            assertEquals(biomeAt(call.x(), call.y(), call.z()), call.biome());
        }
    }

    @Test
    void positionsOutsideTheRegionClampToItsEdge() {
        int min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
        Cache sharp = at(0, 0, 0, 0);
        assertEquals(pos(-2, 5, 5), sharp.getColor(POS, min, 5, 5));
        assertEquals(pos(17, 5, 5), sharp.getColor(POS, max, 5, 5));
        assertEquals(pos(5, -2, 5), sharp.getColor(POS, 5, min, 5));
        assertEquals(pos(5, 17, 5), sharp.getColor(POS, 5, max, 5));
        assertEquals(pos(5, 5, -2), sharp.getColor(POS, 5, 5, min));
        assertEquals(pos(5, 5, 17), sharp.getColor(POS, 5, 5, max));
        assertEquals(pos(-2, 17, -2), sharp.getColor(POS, min, max, min));
        assertEquals(pos(-2, -2, 17), sharp.getColor(POS, -3, -1000, 18));
        Cache blended = at(3, 0, 0, 0);
        assertEquals(blended.getColor(NOISE, -5, 5, 5), blended.getColor(NOISE, min, 5, 5));
        assertEquals(blended.getColor(NOISE, 20, 5, 20), blended.getColor(NOISE, max, 5, max));
        assertEquals(blended.getColor(NOISE, 5, -2, 5), blended.getColor(NOISE, 5, min, 5));
        assertEquals(blended.getColor(NOISE, 5, 17, 5), blended.getColor(NOISE, 5, max, 5));
    }

    @Test
    void sectionsAtTheIntLimitsStayInsideTheirRegion() {
        int flatColour = 0xFF336699;
        Tint flat = (biome, x, y, z) -> flatColour;
        for (int section : new int[]{Integer.MAX_VALUE >> 4, Integer.MIN_VALUE >> 4}) {
            int first = section * 16;
            for (int axis = 0; axis < 3; axis++) {
                SectionPos origin = new SectionPos(axis == 0 ? section : 0, axis == 1 ? section : 0, axis == 2 ? section : 0);
                Cache sharp = new Cache(BIOMES, 0);
                sharp.update(origin);
                Cache blended = new Cache(BIOMES, 2);
                blended.update(origin);
                int[] p = {5, 5, 5};
                for (int k = 0; k < 16; k++) {
                    p[axis] = first + k;
                    assertEquals(pos(p[0], p[1], p[2]), sharp.getColor(POS, p[0], p[1], p[2]), "axis " + axis + " block " + p[axis]);
                    assertEquals(flatColour, blended.getColor(flat, p[0], p[1], p[2]));
                }
                // The far end of the int range clamps to the near edge of the region
                p[axis] = section > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
                int edge = section > 0 ? first - 2 : first + 17;
                int[] e = {5, 5, 5};
                e[axis] = edge;
                assertEquals(pos(e[0], e[1], e[2]), sharp.getColor(POS, p[0], p[1], p[2]), "axis " + axis);
            }
        }
    }

    @Test
    void negativeRadiiActLikeZero() {
        for (int radius : new int[]{-1, -3, -10, Integer.MIN_VALUE}) {
            Cache cache = new Cache(BIOMES, radius);
            cache.update(new SectionPos(1, 0, 1));
            assertEquals(pos(14, 5, 20), cache.getColor(POS, 14, 5, 20), "radius " + radius);
            assertEquals(pos(33, 5, 20), cache.getColor(POS, 33, 5, 20), "radius " + radius);
            assertEquals(pos(14, 5, 33), cache.getColor(POS, 13, 5, 40), "radius " + radius);
            assertEquals(3, cache.calls.size());
        }
    }

    @Test
    void radiiPastFourteenAreCapped() {
        for (int radius : new int[]{14, 15, 1000, Integer.MAX_VALUE}) {
            Cache cache = at(radius, 0, 0, 0);
            cache.getColor(POS, 5, 5, 5);
            assertEquals(48 * 48, cache.calls.size(), "radius " + radius);
            IntSummaryStatistics xs = cache.calls.stream().mapToInt(Call::x).summaryStatistics();
            IntSummaryStatistics zs = cache.calls.stream().mapToInt(Call::z).summaryStatistics();
            assertEquals(-16, xs.getMin());
            assertEquals(31, xs.getMax());
            assertEquals(-16, zs.getMin());
            assertEquals(31, zs.getMax());
        }
    }

    @Test
    void blendedLayersMatchAReferenceBoxBlur() {
        for (int radius : new int[]{1, 2, 5, 14}) {
            Cache cache = at(radius, 1, 0, -2);
            int size = 20 + radius * 2;
            int minX = 16 - 2 - radius, minZ = -32 - 2 - radius, y = 9;
            int[] field = new int[size * size];
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    field[z * size + x] = noise(minX + x, y, minZ + z);
                }
            }
            int[] expected = referenceBlur(field, size, radius);
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int colour = cache.getColor(NOISE, minX + x, y, minZ + z);
                    if (colour != expected[z * size + x]) {
                        fail("radius " + radius + " cell " + x + "," + z + ": " + Integer.toHexString(colour) + " != " + Integer.toHexString(expected[z * size + x]));
                    }
                }
            }
            assertEquals(size * size, cache.calls.size());
        }
    }

    @Test
    void blendedFillResolvesEachRegionCellOnce() {
        Cache cache = at(3, 0, 0, 0);
        int cells = 26 * 26;
        cache.getColor(POS, 5, 5, 5);
        Set<List<Integer>> asked = cache.calls.stream().map(c -> List.of(c.x(), c.y(), c.z())).collect(Collectors.toSet());
        assertEquals(cells, cache.calls.size());
        assertEquals(cells, asked.size());
        for (Call call : cache.calls) {
            assertSame(POS, call.tint());
            assertEquals(5, call.y());
            assertTrue(call.x() >= -5 && call.x() <= 20 && call.z() >= -5 && call.z() <= 20, call.toString());
            assertEquals(biomeAt(call.x(), call.y(), call.z()), call.biome());
        }
        cache.getColor(POS, 20, 5, -5);
        cache.getColor(POS, 6, 5, 6);
        assertEquals(cells, cache.calls.size());
        cache.getColor(POS, 5, 6, 5);
        assertEquals(cells * 2, cache.calls.size());
        assertTrue(cache.calls.subList(cells, cells * 2).stream().allMatch(c -> c.y() == 6));
    }

    @Test
    void uniformLayersKeepTheirRawColour() {
        for (int colour : new int[]{0, 0x00123456, 0x80FFFFFF}) {
            Tint flat = (biome, x, y, z) -> colour;
            Cache cache = at(4, 0, 0, 0);
            assertEquals(colour, cache.getColor(flat, 5, 5, 5));
            assertEquals(colour, cache.getColor(flat, -6, 5, -6));
        }
    }

    @Test
    void mixedLayersBlendToOpaque() {
        Tint checker = (biome, x, y, z) -> ((x + z) & 1) == 0 ? 0x00FFFFFF : 0;
        Cache cache = at(1, 0, 0, 0);
        int colour = cache.getColor(checker, 8, 8, 8);
        assertEquals(0xFF, colour >>> 24);
        int red = (colour >> 16) & 0xFF;
        assertTrue(red > 0 && red < 0xFF, Integer.toHexString(colour));
    }

    @Test
    void eachResolverKeepsItsOwnColours() {
        Tint red = (biome, x, y, z) -> 0xFFFF0000;
        Tint blue = (biome, x, y, z) -> 0xFF0000FF;
        for (int radius : new int[]{0, 3}) {
            Cache cache = at(radius, 0, 0, 0);
            for (int i = 0; i < 4; i++) {
                assertEquals(0xFFFF0000, cache.getColor(red, 5 + i, 5, 5));
                assertEquals(0xFF0000FF, cache.getColor(blue, 5 + i, 5, 5));
            }
            long redCalls = cache.calls.stream().filter(c -> c.tint() == red).count();
            long blueCalls = cache.calls.stream().filter(c -> c.tint() == blue).count();
            assertEquals(radius == 0 ? 4 : 26 * 26, redCalls);
            assertEquals(redCalls, blueCalls);
        }
    }

    @Test
    void aNullResolverIsAKeyOfItsOwn() {
        Cache cache = at(0, 0, 0, 0);
        assertEquals(NULL_TINT, cache.getColor(null, 5, 5, 5));
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        assertEquals(NULL_TINT, cache.getColor(null, 5, 5, 5));
        assertEquals(2, cache.calls.size());
        assertNull(cache.calls.get(0).tint());
        assertSame(POS, cache.calls.get(1).tint());
    }

    @Test
    void updateInvalidatesEvenOnTheSameOrigin() {
        int[] biome = {1};
        Tint byBiome = (b, x, y, z) -> 0xFF000000 | b;
        for (int radius : new int[]{0, 2}) {
            biome[0] = 1;
            Cache cache = new Cache((x, y, z) -> biome[0], radius);
            cache.update(new SectionPos(0, 0, 0));
            assertEquals(0xFF000001, cache.getColor(byBiome, 5, 5, 5));
            biome[0] = 2;
            assertEquals(0xFF000001, cache.getColor(byBiome, 5, 5, 5));
            cache.update(new SectionPos(0, 0, 0));
            assertEquals(0xFF000002, cache.getColor(byBiome, 5, 5, 5));
        }
    }

    @Test
    void updateMovesTheRegion() {
        Cache cache = at(0, 0, 0, 0);
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        cache.update(new SectionPos(2, -1, 0));
        assertEquals(pos(37, -10, 5), cache.getColor(POS, 37, -10, 5));
        // The old position now lies past the new region's low x and high y edges
        assertEquals(pos(30, 1, 5), cache.getColor(POS, 5, 5, 5));
    }

    @Test
    void aNullOriginIsRefusedAndTheCacheKeepsItsRegion() {
        Cache cache = at(0, 0, 0, 0);
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        int before = cache.calls.size();
        assertThrows(NullPointerException.class, () -> cache.update(null));
        assertEquals(pos(5, 5, 5), cache.getColor(POS, 5, 5, 5));
        assertEquals(before, cache.calls.size());
        assertEquals(pos(17, 5, 5), cache.getColor(POS, 100, 5, 5));
    }

    @Test
    void aThrowingResolveIsRetriedOnTheNextRead() {
        for (int radius : new int[]{0, 2}) {
            AtomicBoolean armed = new AtomicBoolean(true);
            Tint flaky = (biome, x, y, z) -> {
                if (armed.getAndSet(false)) {
                    throw new IllegalStateException("flaky");
                }
                return noise(x, y, z);
            };
            Cache cache = at(radius, 0, 0, 0);
            assertThrows(IllegalStateException.class, () -> cache.getColor(flaky, 5, 5, 5));
            Cache steady = at(radius, 0, 0, 0);
            assertEquals(steady.getColor(NOISE, 5, 5, 5), cache.getColor(flaky, 5, 5, 5), "radius " + radius);
            assertEquals(steady.getColor(NOISE, 6, 5, 7), cache.getColor(flaky, 6, 5, 7), "radius " + radius);
        }
    }

    @Test
    void onlyTouchedLayersHoldBuffers() throws ReflectiveOperationException {
        Cache sharp = at(0, 0, 0, 0);
        sharp.getColor(POS, 5, 5, 5);
        assertEquals(1, heldLayers(sharp, POS));
        sharp.getColor(POS, 5, 6, 5);
        sharp.getColor(POS, 6, 6, 5);
        assertEquals(2, heldLayers(sharp, POS));
        // Unblended caches never blur, so they keep no scratch buffer
        assertNull(field(sharp, "tempColorBuffer"));
        Cache blended = at(2, 0, 0, 0);
        blended.getColor(POS, 5, 5, 5);
        assertEquals(1, heldLayers(blended, POS));
        assertNotNull(field(blended, "tempColorBuffer"));
    }
}
