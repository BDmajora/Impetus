package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EngineTailsTest {
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
    }

    @AfterEach
    void forgetCompatibility() {
        Statics.set(Fulgor.class, "fluidloggedApi", false);
        FluidState.next = FluidState.EMPTY;
        IFluidStateCapability.next[0] = null;
    }

    private static AsyncWorld neighbourhood() {
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        return fixture;
    }

    @Test
    void emptyingASectionHidesItsNibbleOnEveryLane() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        for (BfsLightEngine engine : new BfsLightEngine[] {
                new BlockLightEngine(fixture.world), new SkyLightEngine(fixture.world)}) {
            engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
            // Hiding keeps the data around while a decrease is still propagating
            Mixins.call(engine, "setNibbleNull", 0, 4, 0);
            Mixins.call(engine, "initNibble", 0, 4, 0, false, true);
        }
    }

    @Test
    void theSkyLaneRelightsAColumnWhenABlockChanges() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        SkyLightEngine engine = new SkyLightEngine(fixture.world);
        // A ceiling over the column, so the sections below it hold real skylight data
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixture.setBlock(new BlockPos(x, 100, z), Blocks.STONE.getDefaultState());
            }
        }
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), true);
        assertEquals(0, fixture.skyLight(new BlockPos(8, 100, 8)));
        // Under the ceiling only what creeps in from the open chunks around is left
        assertEquals(7, fixture.skyLight(new BlockPos(8, 99, 8)));
        assertEquals(15, fixture.skyLight(new BlockPos(8, 101, 8)));

        // Opening a hole in it lets the sky back down the column
        fixture.setBlock(new BlockPos(8, 100, 8), Blocks.AIR.getDefaultState());
        engine.blockChanged(8, 100, 8);
        // Skylight falls straight down an open column without attenuating
        assertEquals(15, fixture.skyLight(new BlockPos(8, 100, 8)));
        assertEquals(15, fixture.skyLight(new BlockPos(8, 99, 8)));
    }

    @Test
    void aFluidloggedBlockContributesItsOwnOpacityAndLight() {
        Statics.set(Fulgor.class, "fluidloggedApi", true);
        Chunk chunk = mock(Chunk.class);
        // With no capability on the chunk there is nothing to fold in
        assertNull(FluidLightBridge.capabilityOf(chunk));
        assertEquals(3, FluidLightBridge.maxOpacityAt(chunk, 3, 0, 64, 0, new BlockPos.MutableBlockPos()));

        IFluidStateContainer container = (x, y, z, fallback) -> FluidState.next;
        IFluidStateCapability capability = y -> container;
        IFluidStateCapability.next[0] = capability;
        assertSame(capability, FluidLightBridge.capabilityOf(chunk));
        // An empty position still answers with the block's own values
        int info = LightInfo.of(Blocks.AIR.getDefaultState());
        assertEquals(info, FluidLightBridge.merge(info, capability, 0, 64, 0, null, new BlockPos.MutableBlockPos()));
        assertEquals(3, FluidLightBridge.maxOpacityAt(chunk, 3, 0, 64, 0, new BlockPos.MutableBlockPos()));

        // A fluid sharing the position raises both opacity and emission to the brighter of the two
        FluidState.next = new FluidState(Blocks.SEA_LANTERN.getDefaultState());
        int merged = FluidLightBridge.merge(info, capability, 0, 64, 0, null, new BlockPos.MutableBlockPos());
        assertEquals(15, LightInfo.emission(merged));
        assertEquals(15, LightInfo.opacity(merged));
        assertEquals(15, FluidLightBridge.maxOpacityAt(chunk, 3, 0, 64, 0, new BlockPos.MutableBlockPos()));

        // The deferred engine reads the same pair through its own compat layer
        World world = mock(World.class);
        assertEquals(15, com.bdmajora.fulgor.lighting.LightUtil.getLightValue(
                Blocks.AIR.getDefaultState(), world, BlockPos.ORIGIN, chunk));
        assertEquals(255, com.bdmajora.fulgor.lighting.LightUtil.getLightOpacity(
                Blocks.STONE.getDefaultState(), world, BlockPos.ORIGIN, chunk));
        FluidState.next = FluidState.EMPTY;
        assertEquals(0, com.bdmajora.fulgor.lighting.LightUtil.getLightValue(
                Blocks.AIR.getDefaultState(), world, BlockPos.ORIGIN, chunk));
        assertEquals(255, com.bdmajora.fulgor.lighting.LightUtil.getLightOpacity(
                Blocks.STONE.getDefaultState(), world, BlockPos.ORIGIN, chunk));
    }

    // A minimal engine cache, so the base hook the real engines all override can still be exercised
    private static final class BareCache extends LightEngineCache {
        BareCache(World world) {
            super(world);
        }

        void callOnNibbleVisible() {
            super.onNibbleVisible(0, new SWMRNibbleArray());
        }

        @Override
        protected void resetTaskState() {
        }

        @Override
        protected boolean[] getEmptinessMap(Chunk chunk) {
            return null;
        }

        @Override
        protected SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk) {
            return null;
        }

        @Override
        protected boolean canUseChunk(Chunk chunk) {
            return false;
        }
    }

    @Test
    void theCachesOwnPublishHookDoesNothingByItself() {
        AsyncWorld fixture = new AsyncWorld(false);
        new BareCache(fixture.world).callOnNibbleVisible();
    }

    @Test
    void theFakeBlockAccessAnswersOnlyForTheBlockBeingProbed() throws Exception {
        Class<?> type = Class.forName("com.bdmajora.fulgor.async.engine.FaceOcclusion$FakeBlockAccess");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        net.minecraft.world.IBlockAccess access = (net.minecraft.world.IBlockAccess) constructor.newInstance();
        Mixins.set(access, "state", Blocks.STONE_SLAB.getDefaultState());
        assertEquals(Blocks.STONE_SLAB.getDefaultState(), access.getBlockState(BlockPos.ORIGIN));
        assertEquals(Blocks.AIR.getDefaultState(), access.getBlockState(new BlockPos(1, 1, 1)));
        assertFalse(access.isAirBlock(BlockPos.ORIGIN));
        assertTrue(access.isAirBlock(new BlockPos(1, 1, 1)));
        assertNull(access.getTileEntity(BlockPos.ORIGIN));
        assertEquals(7, access.getCombinedLight(BlockPos.ORIGIN, 7));
        assertEquals(0, access.getStrongPower(BlockPos.ORIGIN, net.minecraft.util.EnumFacing.UP));
        assertNotNull(access.getBiome(BlockPos.ORIGIN));
        assertEquals(net.minecraft.world.WorldType.DEFAULT, access.getWorldType());
        assertTrue(access.isSideSolid(BlockPos.ORIGIN, net.minecraft.util.EnumFacing.DOWN, false));
        assertTrue(access.isSideSolid(new BlockPos(1, 1, 1), net.minecraft.util.EnumFacing.DOWN, true));
    }

    @Test
    void theSkyLaneRechecksItsNullSectionsDuringAnEdgePass() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        SkyLightEngine engine = new SkyLightEngine(fixture.world);
        // A ceiling so the sections under it are dim and the ones over it are open sky
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fixture.setBlock(new BlockPos(x, 100, z), Blocks.STONE.getDefaultState());
            }
        }
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.skyLight(new BlockPos(8, 101, 8)));

        // The sky lane's own edge pass rebuilds the nibble cache and re-checks the column's null sections before the shared pass runs
        engine.setupCaches(7, 128, 7, true, false);
        try {
            engine.checkChunkEdges(chunk, 0, 8);
            engine.updateVisible();
        } finally {
            engine.destroyCaches();
        }
        // Both sides of every edge already agreed, so the pass leaves the column as it found it
        assertEquals(15, fixture.skyLight(new BlockPos(8, 101, 8)));
        // Under the ceiling only what creeps in from the open chunks around is left
        assertEquals(7, fixture.skyLight(new BlockPos(8, 99, 8)));
    }
}
