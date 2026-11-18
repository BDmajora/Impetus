package com.bdmajora.extras.client.culling;

import com.bdmajora.testing.Mc;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcclusionEngineTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    // Chunks of a test world keyed by chunk key.
    private final Map<Long, ExtendedBlockStorage[]> storages = new HashMap<>();

    // Returns the chunk mock of a column with its sections created on demand.
    private Chunk chunk(int chunkX, int chunkZ) {
        ExtendedBlockStorage[] sections = this.storages.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), key -> new ExtendedBlockStorage[16]);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        return chunk;
    }

    // Places a block in the test world.
    private void place(int x, int y, int z, IBlockState state) {
        ExtendedBlockStorage[] sections = this.storages.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), key -> new ExtendedBlockStorage[16]);
        if (sections[y >> 4] == null) {
            sections[y >> 4] = new ExtendedBlockStorage(y & ~15, true);
        }
        sections[y >> 4].set(x & 15, y & 15, z & 15, state);
    }

    // Returns a grid that knows every chunk in the square of the given radius.
    private OcclusionGrid grid(int radius) {
        Long2ObjectOpenHashMap<Chunk> loaded = new Long2ObjectOpenHashMap<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                loaded.put(ChunkPos.asLong(x, z), this.chunk(x, z));
            }
        }
        OcclusionGrid grid = new OcclusionGrid();
        grid.apply(new OcclusionInbox.Batch(loaded, new Long2ObjectOpenHashMap<>(), new Long2IntOpenHashMap()));
        return grid;
    }

    @Test
    void sectionsBuildTheirOpacityBitsBrickByBrick() {
        assertSame(OcclusionGrid.CLEAR, OcclusionGrid.build(null));
        assertSame(OcclusionGrid.CLEAR, OcclusionGrid.build(new ExtendedBlockStorage(0, true)));
        ExtendedBlockStorage glass = new ExtendedBlockStorage(0, true);
        glass.set(1, 2, 3, Blocks.GLASS.getDefaultState());
        assertSame(OcclusionGrid.CLEAR, OcclusionGrid.build(glass));
        ExtendedBlockStorage stone = new ExtendedBlockStorage(0, true);
        stone.set(5, 9, 14, Blocks.STONE.getDefaultState());
        stone.set(0, 0, 0, Blocks.STONE.getDefaultState());
        long[] bits = OcclusionGrid.build(stone);
        assertEquals(OcclusionGrid.SECTION_LONGS, bits.length);
        assertEquals(2, java.util.Arrays.stream(bits).map(Long::bitCount).sum());
        assertEquals(1L, bits[0]);
        assertNotEquals(0L, bits[OcclusionGrid.brickIndex(5, 9, 14)] & 1L << OcclusionGrid.bitIndex(5, 9, 14));
        // Every block of a section has its own bit.
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    assertTrue(seen.add(OcclusionGrid.brickIndex(x, y, z) * 64 + OcclusionGrid.bitIndex(x, y, z)));
                }
            }
        }
        assertEquals(4096, seen.size());
    }

    @Test
    void theGridReadsLoadedChunksAndAppliesQueuedChanges() {
        this.place(3, 70, 3, Blocks.STONE.getDefaultState());
        this.place(-20, 5, 40, Blocks.DIRT.getDefaultState());
        OcclusionGrid grid = this.grid(3);
        assertEquals(49, grid.columnCount());
        assertTrue(grid.isOpaque(3, 70, 3));
        assertFalse(grid.isOpaque(3, 71, 3));
        assertTrue(grid.isOpaque(-20, 5, 40));
        assertFalse(grid.isOpaque(3, -1, 3));
        assertFalse(grid.isOpaque(3, 256, 3));
        assertFalse(grid.isOpaque(500, 70, 3));
        assertNull(grid.sectionBits(500 >> 4, 4, 0));
        assertSame(OcclusionGrid.CLEAR, grid.sectionBits(0, -1, 0));
        assertSame(OcclusionGrid.CLEAR, grid.sectionBits(0, 16, 0));
        assertSame(OcclusionGrid.CLEAR, grid.sectionBits(0, 2, 0));
        long built = grid.sectionsBuilt();
        grid.sectionBits(0, 4, 0);
        assertEquals(built, grid.sectionsBuilt());
        // A short storage array reads as empty above its top.
        Chunk shortChunk = mock(Chunk.class);
        when(shortChunk.getBlockStorageArray()).thenReturn(new ExtendedBlockStorage[2]);
        OcclusionInbox inbox = new OcclusionInbox();
        inbox.chunk(9, 9, shortChunk);
        grid.apply(inbox.drain());
        assertSame(OcclusionGrid.CLEAR, grid.sectionBits(9, 5, 9));

        // A block change is seen only after its section is queued.
        this.place(3, 71, 3, Blocks.STONE.getDefaultState());
        assertFalse(grid.isOpaque(3, 71, 3));
        inbox.section(0, 4, 0);
        inbox.section(0, -1, 0);
        inbox.section(0, 16, 0);
        inbox.section(77, 4, 77);
        grid.apply(inbox.drain());
        assertTrue(grid.isOpaque(3, 71, 3));

        // Reloading the same chunk forgets every section and an unload drops the column.
        Chunk same = mock(Chunk.class);
        ExtendedBlockStorage[] origin = this.storages.get(ChunkPos.asLong(0, 0));
        when(same.getBlockStorageArray()).thenReturn(origin);
        this.place(4, 70, 4, Blocks.STONE.getDefaultState());
        inbox.chunk(0, 0, same);
        grid.apply(inbox.drain());
        assertTrue(grid.isOpaque(4, 70, 4));
        this.place(5, 70, 5, Blocks.STONE.getDefaultState());
        inbox.chunk(0, 0, same);
        grid.apply(inbox.drain());
        assertTrue(grid.isOpaque(5, 70, 5));
        inbox.chunk(0, 0, null);
        grid.apply(inbox.drain());
        assertFalse(grid.isOpaque(5, 70, 5));
        assertEquals(49, grid.columnCount());

        // A reset replaces every column and drops older queued changes.
        inbox.chunk(1, 1, null);
        inbox.section(1, 1, 1);
        inbox.reset(new Long2ObjectOpenHashMap<>());
        OcclusionInbox.Batch batch = inbox.drain();
        assertTrue(batch.chunks().isEmpty());
        assertTrue(batch.sections().isEmpty());
        grid.apply(batch);
        assertEquals(0, grid.columnCount());
        assertNull(inbox.drain());
        grid.clear();
        assertEquals(0, grid.columnCount());
    }

    // Walks the ray block by block without any skipping.
    private static boolean reference(OcclusionGrid grid, double sx, double sy, double sz, double ex, double ey, double ez, double slack) {
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4D) {
            return true;
        }
        double limit = 1.0D - slack / length;
        int x = MathHelper.floor(sx);
        int y = MathHelper.floor(sy);
        int z = MathHelper.floor(sz);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : (stepX > 0 ? x + 1 - sx : sx - x) * deltaX;
        double maxY = stepY == 0 ? Double.POSITIVE_INFINITY : (stepY > 0 ? y + 1 - sy : sy - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (stepZ > 0 ? z + 1 - sz : sz - z) * deltaZ;
        while (true) {
            double t;
            if (maxX < maxY) {
                if (maxX < maxZ) {
                    x += stepX;
                    t = maxX;
                    maxX += deltaX;
                } else {
                    z += stepZ;
                    t = maxZ;
                    maxZ += deltaZ;
                }
            } else if (maxY < maxZ) {
                y += stepY;
                t = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                t = maxZ;
                maxZ += deltaZ;
            }
            if (t >= limit) {
                return true;
            }
            if (grid.isOpaque(x, y, z)) {
                return false;
            }
        }
    }

    @Test
    void skippingEmptyBricksAndSectionsNeverChangesARayVerdict() {
        Random random = new Random(1234);
        for (int i = 0; i < 1500; i++) {
            this.place(random.nextInt(96) - 48, 40 + random.nextInt(64), random.nextInt(96) - 48, Blocks.STONE.getDefaultState());
        }
        // A solid slab and a hollow tower give long runs of both kinds.
        for (int x = -10; x < 10; x++) {
            for (int z = -10; z < 10; z++) {
                this.place(x, 60, z, Blocks.STONE.getDefaultState());
            }
        }
        OcclusionGrid grid = this.grid(4);
        OcclusionRaycaster raycaster = new OcclusionRaycaster(grid);
        int blocked = 0;
        for (int i = 0; i < 4000; i++) {
            double sx = random.nextDouble() * 100 - 50;
            double sy = 30 + random.nextDouble() * 90;
            double sz = random.nextDouble() * 100 - 50;
            double ex = random.nextDouble() * 100 - 50;
            double ey = random.nextBoolean() ? Math.floor(30 + random.nextDouble() * 90) : 30 + random.nextDouble() * 90;
            double ez = random.nextDouble() * 100 - 50;
            double slack = random.nextInt(3) * 0.5D;
            boolean expected = reference(grid, sx, sy, sz, ex, ey, ez, slack);
            assertEquals(expected, raycaster.isClear(sx, sy, sz, ex, ey, ez, slack), "ray " + i);
            blocked += expected ? 0 : 1;
        }
        assertTrue(blocked > 200 && blocked < 3800, "blocked " + blocked);
        assertTrue(raycaster.takeSteps() > 0);
        assertEquals(0L, raycaster.takeSteps());
    }

    @Test
    void raysStopAtOpaqueBlocksButNotInTheirLastStretch() {
        for (int y = 0; y < 256; y++) {
            for (int z = -16; z < 32; z++) {
                this.place(5, y, z, Blocks.STONE.getDefaultState());
            }
        }
        OcclusionRaycaster raycaster = new OcclusionRaycaster(this.grid(2));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 3.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(10.5, 64.5, 0.5, 0.5, 64.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 70.5, 3.5, 0.0));
        // The block the target sits in never hides it.
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 5.5, 64.5, 0.5, 1.0));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 50.0));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 0.5, 64.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, 0.5, 80.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, 64.5, 0.5, Double.NaN, 64.5, 0.5, 0.0));
        // Rays outside the world height pass unless they come back into it.
        assertTrue(raycaster.isClear(0.5, 300.5, 0.5, 10.5, 300.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, -10.5, 0.5, 10.5, -10.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, 300.5, 0.5, 10.5, 400.5, 0.5, 0.0));
        assertTrue(raycaster.isClear(0.5, -10.5, 0.5, 10.5, -30.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, 300.5, 0.5, 10.5, 100.5, 0.5, 0.0));
        assertFalse(raycaster.isClear(0.5, -10.5, 0.5, 10.5, 100.5, 0.5, 0.0));
        // Chunks the grid does not know are empty.
        assertTrue(raycaster.isClear(1000.5, 64.5, 0.5, 1010.5, 64.5, 0.5, 0.0));
        raycaster.forgetSection();
        assertFalse(raycaster.isClear(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 0.0));
    }

    @Test
    void thePointCacheAnswersEachPointOnceAPassAndGrows() {
        assertThrows(IllegalArgumentException.class, () -> new VisibilityCache(1));
        assertThrows(IllegalArgumentException.class, () -> new VisibilityCache(3));
        assertThrows(IllegalArgumentException.class, () -> new VisibilityCache(VisibilityCache.MAX_CAPACITY * 2));
        VisibilityCache cache = new VisibilityCache(4);
        assertEquals(VisibilityCache.UNKNOWN, cache.get(0, 0, 0));
        cache.put(0, 0, 0, true);
        cache.put(-1, 300, 7, false);
        cache.put(0, 0, 0, false);
        assertEquals(VisibilityCache.BLOCKED, cache.get(0, 0, 0));
        assertEquals(VisibilityCache.BLOCKED, cache.get(-1, 300, 7));
        for (int i = 0; i < 100; i++) {
            cache.put(i, -i, i * 31, i % 2 == 0);
        }
        assertTrue(cache.capacity() >= 256);
        assertEquals(101, cache.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i % 2 == 0 ? VisibilityCache.VISIBLE : VisibilityCache.BLOCKED, cache.get(i, -i, i * 31));
        }
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(VisibilityCache.UNKNOWN, cache.get(1, -1, 31));
        assertNotEquals(VisibilityCache.key(1, 2, 3), VisibilityCache.key(1, 3, 2));
        assertNotEquals(VisibilityCache.key(-1, 0, 0), VisibilityCache.key(1, 0, 0));
        // The stamp wrap wipes old answers.
        cache.put(5, 5, 5, true);
        com.bdmajora.testing.Mixins.set(cache, "stamp", Integer.MAX_VALUE - 1);
        cache.clear();
        assertEquals(VisibilityCache.UNKNOWN, cache.get(5, 5, 5));
        // A full table at the largest size stops storing.
        VisibilityCache full = new VisibilityCache(2);
        com.bdmajora.testing.Mixins.set(full, "size", VisibilityCache.MAX_CAPACITY / 2);
        com.bdmajora.testing.Mixins.set(full, "keys", new long[VisibilityCache.MAX_CAPACITY]);
        full.put(1, 1, 1, true);
        assertEquals(VisibilityCache.UNKNOWN, full.get(1, 1, 1));
    }

    @Test
    void theTesterSeesBoxesAroundAndThroughGapsButNotBehindWalls() {
        // A wall at x 10 from z -16 to 31 and up to y 99 with a window at y 70 z 20.
        for (int y = 0; y < 100; y++) {
            for (int z = -16; z < 32; z++) {
                if (y != 70 || z != 20) {
                    this.place(10, y, z, Blocks.STONE.getDefaultState());
                }
            }
        }
        OcclusionTester tester = new OcclusionTester(this.grid(3));
        tester.begin(0.5, 65.5, 0.5, 1.0, 256.0);
        assertSame(tester.raycaster(), tester.raycaster());
        // Hidden behind the wall.
        assertFalse(tester.isVisible(14.25, 64, 0.25, 14.75, 66, 0.75));
        assertTrue(tester.rays() > 1);
        // In front of the wall.
        assertTrue(tester.isVisible(5, 64, 0, 6, 66, 1));
        // Beyond the wall's end.
        assertTrue(tester.isVisible(14, 64, 60, 15, 66, 61));
        // Far beyond the ray range.
        assertTrue(tester.isVisible(400, 64, 0, 401, 66, 1));
        // Around the camera.
        assertTrue(tester.isVisible(-0.5, 64, -0.5, 1.5, 67, 1.5));
        assertTrue(tester.isVisible(1.0, 64, 1.0, 2.0, 66, 2.0));
        // Seen through the window by a sampled corner while the centre is blocked.
        tester.begin(0.5, 70.5, 20.5, 1.0, 256.0);
        assertFalse(tester.raycaster().isClear(0.5, 70.5, 20.5, 14.5, 68.5, 20.5, 1.0));
        assertTrue(tester.isVisible(14, 66, 19, 15, 71, 22));
        assertFalse(tester.isVisible(14, 30, 0, 15, 32, 1));
        // Seen over the wall top and from beside its ends.
        tester.begin(0.5, 300.5, 0.5, 1.0, 512.0);
        assertTrue(tester.isVisible(14, 64, 0, 15, 66, 1));
        tester.begin(12.5, 65.5, -40.5, 1.0, 256.0);
        assertTrue(tester.isVisible(14, 64, 0, 15, 66, 1));
        tester.begin(12.5, 65.5, 60.5, 1.0, 256.0);
        assertTrue(tester.isVisible(14, 64, 0, 15, 66, 1));
        // From the far side the near box is seen and the box behind the wall is not.
        tester.begin(30.5, 65.5, 0.5, 1.0, 256.0);
        assertTrue(tester.isVisible(14, 64, 0, 15, 66, 1));
        assertFalse(tester.isVisible(4, 64, 0, 5, 66, 1));
        assertEquals(VisibilityCache.BLOCKED, tester.cache().get(5, 64, 0));
        assertFalse(tester.pointVisible(5, 64, 0));
        // A large box behind the wall is sampled with wider strides and stays hidden.
        assertFalse(tester.isVisible(-20, 0, -10, 9, 60, 20));
        // Boxes below and beside the camera use their top and side faces.
        tester.begin(30.5, 120.5, 0.5, 1.0, 256.0);
        assertFalse(tester.isVisible(4, 64, 0, 5, 66, 1));
        tester.begin(30.5, 20.5, -40.5, 1.0, 256.0);
        assertFalse(tester.isVisible(4, 20, -5, 5, 22, -4));
        tester.begin(30.5, 20.5, 60.5, 1.0, 256.0);
        assertFalse(tester.isVisible(4, 20, 4, 5, 22, 5));
        assertEquals(1, OcclusionTester.stride(0, 6));
        assertEquals(2, OcclusionTester.stride(0, 7));
        assertEquals(10, OcclusionTester.stride(0, 60));
    }

    @Test
    void snapshotsTravelFromTheMainThreadToTheWorkerThroughThreeBuffers() {
        OcclusionSnapshot.Exchange exchange = new OcclusionSnapshot.Exchange();
        assertNull(exchange.take(null));
        OcclusionSnapshot first = exchange.producer();
        Cullable target = mock(Cullable.class);
        for (int i = 0; i < 300; i++) {
            first.add(target, i % 2 == 0 ? OcclusionSnapshot.ENTITY : OcclusionSnapshot.BLOCK_ENTITY, i, i + 1, i + 2, i + 3, i + 4, i + 5);
        }
        assertEquals(300, first.count());
        assertSame(target, first.target(299));
        assertEquals(OcclusionSnapshot.BLOCK_ENTITY, first.kind(299));
        assertEquals(304.0D, first.bound(299, 5));
        exchange.publish();
        assertTrue(first.isFresh());
        assertNotSame(first, exchange.producer());
        OcclusionSnapshot taken = exchange.take(null);
        assertSame(first, taken);
        assertFalse(taken.isFresh());
        assertSame(taken, exchange.take(taken));
        // A second publish before a take replaces the older one.
        OcclusionSnapshot second = exchange.producer();
        second.clear();
        second.add(target, OcclusionSnapshot.ENTITY, 0, 0, 0, 1, 1, 1);
        exchange.publish();
        OcclusionSnapshot third = exchange.producer();
        third.clear();
        exchange.publish();
        OcclusionSnapshot newest = exchange.take(taken);
        assertSame(third, newest);
        assertEquals(0, newest.count());
        first.clear();
        assertEquals(0, first.count());
        assertNull(first.target(0));
    }
}
