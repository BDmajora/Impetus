package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BfsLightEngineTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
    }

    // A 3x3 neighbourhood, which is the smallest the engine will work in
    private static AsyncWorld neighbourhood(boolean client) {
        AsyncWorld fixture = new AsyncWorld(client);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        return fixture;
    }

    @Test
    void lightingAChunkSpreadsFromItsEmitters() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());

        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertFalse(engine.wasQueueOverflowed());
        assertEquals(15, fixture.blockLight(source));
        assertEquals(14, fixture.blockLight(source.east()));
        assertEquals(13, fixture.blockLight(source.east(2)));
        assertEquals(0, fixture.blockLight(source.up(15)));
        // The emptiness map now records which sections hold blocks
        boolean[] emptiness = ((AsyncLitChunk) chunk).fulgor$getBlockEmptinessMap();
        assertNotNull(emptiness);
        assertFalse(emptiness[4]);
        assertTrue(emptiness[0]);
    }

    @Test
    void anOpaqueWallBlocksTheSpread() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        for (int y = 60; y <= 68; y++) {
            for (int z = 4; z <= 12; z++) {
                fixture.setBlock(new BlockPos(9, y, z), Blocks.STONE.getDefaultState());
            }
        }
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(source));
        assertEquals(0, fixture.blockLight(new BlockPos(9, 64, 8)));
        // Behind the wall the light has to go the long way round, so it is much dimmer than 13
        assertTrue(fixture.blockLight(new BlockPos(10, 64, 8)) < 13);
    }

    @Test
    void aBlockChangeRelightsOnlyWhatItAffects() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(0, fixture.blockLight(new BlockPos(8, 64, 8)));

        // Placing a lamp brightens its surroundings
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        engine.blockChanged(source.getX(), source.getY(), source.getZ());
        assertEquals(15, fixture.blockLight(source));
        assertEquals(14, fixture.blockLight(source.north()));

        // Removing it takes the light away again
        fixture.setBlock(source, Blocks.AIR.getDefaultState());
        engine.blockChanged(source.getX(), source.getY(), source.getZ());
        assertEquals(0, fixture.blockLight(source));
        assertEquals(0, fixture.blockLight(source.north()));

        // A change in a chunk the engine cannot see is ignored
        engine.blockChanged(1000, 64, 1000);
    }

    @Test
    void severalChangesInOneChunkAreAppliedAsABatch() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        BlockPos first = new BlockPos(4, 64, 4);
        BlockPos second = new BlockPos(12, 70, 12);
        fixture.setBlock(first, Blocks.GLOWSTONE.getDefaultState());
        fixture.setBlock(second, Blocks.GLOWSTONE.getDefaultState());
        IntOpenHashSet changed = new IntOpenHashSet();
        changed.add(local(first));
        changed.add(local(second));
        engine.blocksChangedInChunk(0, 0, changed, null);
        assertEquals(15, fixture.blockLight(first));
        assertEquals(15, fixture.blockLight(second));

        // A section that has become non-empty is reconciled even without listed positions
        Boolean[] sections = new Boolean[16];
        sections[5] = Boolean.FALSE;
        engine.blocksChangedInChunk(0, 0, null, sections);
        assertEquals(15, fixture.blockLight(second));
        // Nothing at all queued is a no-op, as is a chunk the engine cannot see
        engine.blocksChangedInChunk(0, 0, null, null);
        engine.blocksChangedInChunk(40, 40, changed, null);
    }

    private static int local(BlockPos pos) {
        return (pos.getX() & 15) | ((pos.getZ() & 15) << 4) | (pos.getY() << 8);
    }

    @Test
    void skylightFallsDownOpenColumns() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        // A floor at y=64, so everything above it is open to the sky
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixture.setBlock(new BlockPos(x, 64, z), Blocks.STONE.getDefaultState());
            }
        }
        SkyLightEngine engine = new SkyLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.skyLight(new BlockPos(8, 70, 8)));
        assertEquals(15, fixture.skyLight(new BlockPos(8, 65, 8)));
        // The floor itself is dark, and under it only what creeps in from the open chunks around is left
        assertEquals(0, fixture.skyLight(new BlockPos(8, 64, 8)));
        assertEquals(7, fixture.skyLight(new BlockPos(8, 63, 8)));
    }

    @Test
    void savedChunksAreLoadedInWithoutABfsPass() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        fixture.setBlock(new BlockPos(8, 64, 8), Blocks.GLOWSTONE.getDefaultState());
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.loadInChunk(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));
        // No propagation ran, so nothing is lit, but the emptiness map is set up
        assertEquals(0, fixture.blockLight(new BlockPos(8, 64, 8)));
        assertNotNull(((AsyncLitChunk) chunk).fulgor$getBlockEmptinessMap());
        // A chunk the engine cannot see is skipped
        engine.loadInChunk(fixture.chunk(40, 40), new Boolean[16]);
    }

    @Test
    void edgeChecksReconcileAgainstTheNeighbourTheySee() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        Chunk east = fixture.chunks.get(AsyncWorld.key(1, 0));
        BlockPos source = new BlockPos(20, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(east, BfsLightEngine.getEmptySectionsForChunk(east), false);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(source));

        IntOpenHashSet sections = new IntOpenHashSet();
        for (int s = -1; s <= 16; s++) {
            sections.add(s);
        }
        engine.checkChunkEdges(0, 0, sections);
        // The light that belongs across the border is still there after the check
        assertEquals(15, fixture.blockLight(source));
        engine.checkChunkEdges(40, 40, sections);
    }

    @Test
    void theClientSideEngineMarksTheRangeItChanged() {
        AsyncWorld fixture = neighbourhood(true);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        engine.blockChanged(source.getX(), source.getY(), source.getZ());
        Mockito.verify(fixture.world, Mockito.atLeastOnce())
                .markBlockRangeForRenderUpdate(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                        Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void theEmptySectionSurveyOnlyCountsBlocks() {
        AsyncWorld fixture = neighbourhood(false);
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        Boolean[] empty = BfsLightEngine.getEmptySectionsForChunk(chunk);
        assertEquals(16, empty.length);
        assertTrue(empty[4]);
        fixture.setBlock(new BlockPos(1, 64, 1), Blocks.STONE.getDefaultState());
        assertFalse(BfsLightEngine.getEmptySectionsForChunk(chunk)[4]);
        assertTrue(BfsLightEngine.isEmptyOfBlocks(null));
    }
}
