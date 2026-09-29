package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LightingEngineTest {
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
        Statics.set(Fulgor.class, "cachedBlockLightInfo", false);
        // Everything here is small enough to run on the calling thread unless a test says otherwise
        FulgorConfig.get().parallelLightUpdates = false;
    }

    @Test
    void aLightSourceSpreadsWithDistanceFalloff() {
        LightWorld fixture = new LightWorld();
        fixture.chunk(0, 0);
        LightingEngine engine = new LightingEngine(fixture.world);
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());

        engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source);
        engine.processLightUpdates();
        assertEquals(15, fixture.blockLight(source));
        assertEquals(14, fixture.blockLight(source.east()));
        assertEquals(13, fixture.blockLight(source.east().east()));
        assertEquals(12, fixture.blockLight(source.east(2).up()));
        // Fifteen blocks away nothing is left
        assertEquals(0, fixture.blockLight(new BlockPos(8, 79, 8)));
        assertFalse(fixture.notified.isEmpty());

        // Removing the source darkens everything it lit
        fixture.setBlock(source, LightWorld.air());
        engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source);
        engine.processLightUpdates();
        assertEquals(0, fixture.blockLight(source));
        assertEquals(0, fixture.blockLight(source.east()));
    }

    @Test
    void opaqueBlocksStopTheSpread() {
        LightWorld fixture = new LightWorld();
        fixture.chunk(0, 0);
        LightingEngine engine = new LightingEngine(fixture.world);
        BlockPos source = new BlockPos(4, 64, 4);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        fixture.setBlock(source.east(), Blocks.STONE.getDefaultState());
        engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source);
        engine.processLightUpdates();
        assertEquals(15, fixture.blockLight(source));
        // The wall itself takes no light, and what is behind it is only lit the long way round
        assertEquals(0, fixture.blockLight(source.east()));
        assertEquals(14, fixture.blockLight(source.up()));
        assertEquals(13, fixture.blockLight(source.up().east()));
        assertEquals(11, fixture.blockLight(source.east().east()));
    }

    @Test
    void lightCrossesIntoALoadedNeighbourAndStopsAtAnUnloadedOne() {
        LightWorld fixture = new LightWorld();
        fixture.chunk(0, 0);
        fixture.chunk(1, 0);
        LightingEngine engine = new LightingEngine(fixture.world);
        BlockPos source = new BlockPos(15, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source);
        engine.processLightUpdates();
        assertEquals(14, fixture.blockLight(new BlockPos(16, 64, 8)));
        assertEquals(13, fixture.blockLight(new BlockPos(17, 64, 8)));
        // Nothing propagates into the chunk that is not there
        assertNull(fixture.chunks.get((0L << 32) | (0xFFFFFFFFL & -1)));
        assertEquals(12, fixture.blockLight(new BlockPos(18, 64, 8)));
    }

    @Test
    void skylightSeedsFromWhatCanSeeTheSky() {
        LightWorld fixture = new LightWorld();
        fixture.skyHeight = 64;
        fixture.chunk(0, 0);
        LightingEngine engine = new LightingEngine(fixture.world);
        BlockPos open = new BlockPos(8, 64, 8);
        engine.scheduleLightUpdate(EnumSkyBlock.SKY, open);
        engine.processLightUpdates();
        assertEquals(15, fixture.skyLight(open));
        assertEquals(14, fixture.skyLight(open.down()));
        assertEquals(13, fixture.skyLight(open.down(2)));
        assertEquals(0, fixture.blockLight(open));
    }

    @Test
    void scheduledPositionsAreCollapsedAndFlushedWhenTheQueueIsFull() {
        LightWorld fixture = new LightWorld();
        fixture.chunk(0, 0);
        FulgorConfig.get().maxScheduledUpdates = 1 << 12;
        LightingEngine engine = new LightingEngine(fixture.world);
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        // The same position scheduled repeatedly is queued once
        for (int i = 0; i < 10; i++) {
            engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source);
        }
        engine.processLightUpdates();
        assertEquals(15, fixture.blockLight(source));

        // Past the cap the engine stops deferring and runs the batch itself
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, new BlockPos(x, 64 + (y - 8), z));
                }
            }
        }
        assertEquals(15, fixture.blockLight(source));
        engine.processLightUpdates();
        assertEquals(15, fixture.blockLight(source));
    }

    @Test
    void aWideBatchIsSplitAcrossThePool() {
        LightWorld fixture = new LightWorld();
        FulgorConfig.get().parallelLightUpdates = true;
        FulgorConfig.get().parallelLightThreads = 2;
        FulgorConfig.get().parallelMinPositions = 8;
        FulgorConfig.get().parallelMinChunks = 3;
        LightingEngine engine = new LightingEngine(fixture.world);
        // Four chunks far enough apart that the scheduler may run them at once
        BlockPos[] sources = {
                new BlockPos(8, 64, 8),
                new BlockPos(408, 64, 8),
                new BlockPos(8, 64, 408),
                new BlockPos(408, 64, 408)};
        for (BlockPos source : sources) {
            fixture.chunk(source.getX() >> 4, source.getZ() >> 4);
            fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        }
        for (BlockPos source : sources) {
            for (int i = 0; i < 3; i++) {
                engine.scheduleLightUpdate(EnumSkyBlock.BLOCK, source.up(i));
            }
        }
        engine.processLightUpdates();
        for (BlockPos source : sources) {
            assertEquals(15, fixture.blockLight(source), source.toString());
            assertEquals(14, fixture.blockLight(source.east()));
        }
        // The notifications the workers deferred are replayed by the calling thread
        assertFalse(fixture.notified.isEmpty());

        // Two chunks is below the spread threshold, so the same batch runs inline
        LightWorld small = new LightWorld();
        small.chunk(0, 0);
        small.chunk(30, 0);
        LightingEngine inline = new LightingEngine(small.world);
        small.setBlock(new BlockPos(8, 64, 8), Blocks.GLOWSTONE.getDefaultState());
        small.setBlock(new BlockPos(488, 64, 8), Blocks.GLOWSTONE.getDefaultState());
        for (int i = 0; i < 5; i++) {
            inline.scheduleLightUpdate(EnumSkyBlock.BLOCK, new BlockPos(8, 64 + i, 8));
            inline.scheduleLightUpdate(EnumSkyBlock.BLOCK, new BlockPos(488, 64 + i, 8));
        }
        inline.processLightUpdates();
        assertEquals(15, small.blockLight(new BlockPos(8, 64, 8)));
        assertEquals(15, small.blockLight(new BlockPos(488, 64, 8)));
    }
}
