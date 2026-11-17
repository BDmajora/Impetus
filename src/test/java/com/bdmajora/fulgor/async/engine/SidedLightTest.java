package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SidedLightTest {
    // Engine direction bit of up.
    private static final int UP = 1 << 4;
    private static final int DOWN = 1 << 5;
    private static final int SIDES = 0xF;

    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
        FaceOcclusion.registerDefaults();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
    }

    @AfterEach
    void forgetTestBlocks() {
        Set<Block> sided = Statics.get(FaceOcclusion.class, "HAS_SIDED_TRANSPARENCY");
        sided.removeIf(block -> block instanceof DirectionalLamp);
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

    // Places a floor over every chunk.
    private static void floor(AsyncWorld fixture, int y, IBlockState state) {
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                fixture.setBlock(new BlockPos(x, y, z), state);
            }
        }
    }

    @Test
    void aPartialBlockWithNoSolidFaceKeepsItsOwnOpacity() {
        // Checks water is not sided.
        assertFalse(FaceOcclusion.hasSidedTransparency(Blocks.WATER));
        int water = LightInfo.of(Blocks.WATER.getDefaultState());
        assertEquals(0, water & LightInfo.REGISTRY);
        for (int dir = 0; dir < 6; dir++) {
            assertEquals(3, LightInfo.absorption(water, Blocks.WATER.getDefaultState(), dir));
        }
        assertEquals(0, LightInfo.blockedFaces(water, Blocks.WATER.getDefaultState()));
        // Checks a grass path is not sided.
        assertFalse(FaceOcclusion.hasSidedTransparency(Blocks.GRASS_PATH));
        assertEquals(-1, FaceOcclusion.sidedFaces(Blocks.GRASS_PATH, 0));
        assertEquals(15, LightInfo.absorption(LightInfo.of(Blocks.GRASS_PATH.getDefaultState()),
                Blocks.GRASS_PATH.getDefaultState(), 4));
        assertEquals(3, FaceOcclusion.resolveScalarAbsorption(Blocks.WATER.getDefaultState(), 4));
    }

    @Test
    void farmlandClosesTheBottomItsBoxFills() {
        // Checks farmland is sided.
        assertTrue(FaceOcclusion.hasSidedTransparency(Blocks.FARMLAND));
        assertEquals(SIDES | DOWN, FaceOcclusion.sidedFaces(Blocks.FARMLAND, 0));
        int farmland = LightInfo.of(Blocks.FARMLAND.getDefaultState());
        assertEquals(SIDES | DOWN, LightInfo.blockedFaces(farmland, Blocks.FARMLAND.getDefaultState()));
        assertEquals(1, LightInfo.absorption(farmland, Blocks.FARMLAND.getDefaultState(), 4));
        assertEquals(15, LightInfo.absorption(farmland, Blocks.FARMLAND.getDefaultState(), 5));
        // A slab keeps the faces Forge already gave it
        assertEquals(DOWN, FaceOcclusion.sidedFaces(Blocks.STONE_SLAB, 0));
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 0, 5));
        assertFalse(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 0, 4));
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE_SLAB, 0, 6));
        // A block with no table reads as solid everywhere
        assertTrue(FaceOcclusion.isFaceSolid(Blocks.STONE, 0, 4));
        assertEquals(-1, FaceOcclusion.sidedFaces(Blocks.STONE_SLAB, -1));
        assertEquals(-1, FaceOcclusion.sidedFaces(Blocks.STONE_SLAB, 16));
    }

    @Test
    void theBoundingBoxOnlyClosesFacesItFills() {
        IBlockAccess access = mock(IBlockAccess.class);
        assertEquals(DOWN, FaceOcclusion.coveredFaces(box(0, 0, 0, 1, 0.5, 1), access));
        assertEquals(UP, FaceOcclusion.coveredFaces(box(0, 0.5, 0, 1, 1, 1), access));
        assertEquals(1, FaceOcclusion.coveredFaces(box(0.5, 0, 0, 1, 1, 1), access));
        assertEquals(1 << 1, FaceOcclusion.coveredFaces(box(0, 0, 0, 0.5, 1, 1), access));
        assertEquals(1 << 2, FaceOcclusion.coveredFaces(box(0, 0, 0.5, 1, 1, 1), access));
        assertEquals(1 << 3, FaceOcclusion.coveredFaces(box(0, 0, 0, 1, 1, 0.5), access));
        // Checks a whole cube box.
        assertEquals(0, FaceOcclusion.coveredFaces(box(0, 0, 0, 1, 1, 1), access));
        // A box inset on every axis fills no face
        assertEquals(0, FaceOcclusion.coveredFaces(box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9), access));
        IBlockState noBox = mock(IBlockState.class);
        assertEquals(0, FaceOcclusion.coveredFaces(noBox, access));
        IBlockState failing = mock(IBlockState.class);
        when(failing.getBoundingBox(any(), any())).thenThrow(new IllegalStateException("needs a world"));
        assertEquals(0, FaceOcclusion.coveredFaces(failing, access));
        IBlockState fatal = mock(IBlockState.class);
        when(fatal.getBoundingBox(any(), any())).thenThrow(new StackOverflowError());
        assertThrows(StackOverflowError.class, () -> FaceOcclusion.coveredFaces(fatal, access));
    }

    private static IBlockState box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        IBlockState state = mock(IBlockState.class);
        when(state.getBoundingBox(any(), any())).thenReturn(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        return state;
    }

    @Test
    void lightCrossesWaterAtVanillaCost() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        // Builds a stone box around a water tube.
        for (int x = 7; x <= 14; x++) {
            for (int y = 63; y <= 65; y++) {
                for (int z = 7; z <= 9; z++) {
                    fixture.setBlock(new BlockPos(x, y, z), Blocks.STONE.getDefaultState());
                }
            }
        }
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, Blocks.GLOWSTONE.getDefaultState());
        for (int x = 9; x <= 13; x++) {
            fixture.setBlock(new BlockPos(x, 64, 8), Blocks.WATER.getDefaultState());
        }

        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(source));
        assertEquals(12, fixture.blockLight(source.east()));
        assertEquals(9, fixture.blockLight(source.east(2)));
        assertEquals(6, fixture.blockLight(source.east(3)));
        assertEquals(3, fixture.blockLight(source.east(4)));
        assertEquals(0, fixture.blockLight(source.east(5)));
    }

    @Test
    void aFarmlandFloorKeepsLightOutOfTheRoomBelow() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        floor(fixture, 100, Blocks.FARMLAND.getDefaultState());
        BlockPos lamp = new BlockPos(8, 101, 8);
        fixture.setBlock(lamp, Blocks.GLOWSTONE.getDefaultState());

        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        // Checks the farmland light.
        assertEquals(14, fixture.blockLight(lamp.down()));
        assertEquals(0, fixture.blockLight(lamp.down(2)));
        assertEquals(0, fixture.blockLight(lamp.down(3)));
    }

    @Test
    void anEdgeRecheckDoesNotCountLightBehindAClosedNeighbourFace() {
        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        floor(fixture, 64, Blocks.STONE_SLAB.getDefaultState());
        BlockPos lamp = new BlockPos(8, 65, 8);
        fixture.setBlock(lamp, Blocks.GLOWSTONE.getDefaultState());

        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(14, fixture.blockLight(lamp.down()));
        assertEquals(0, fixture.blockLight(lamp.down(2)));

        // Checks the air under the slab.
        engine.setupCaches(7, 128, 7, true, true);
        try {
            assertEquals(0, engine.calculateLightValue(8, 63, 8, 0));
            // The slab itself takes the lamp's light through its open top
            assertEquals(14, engine.calculateLightValue(8, 64, 8, 14));
            // Checks the first brighter neighbour wins.
            assertEquals(12, engine.calculateLightValue(8, 64, 8, 0));
        } finally {
            engine.destroyCaches();
        }
    }

    @Test
    void aBlockAnsweringItsOwnFacesLetsLightOutWhereItLetsLightIn() {
        DirectionalLamp block = new DirectionalLamp();
        Set<Block> sided = Statics.get(FaceOcclusion.class, "HAS_SIDED_TRANSPARENCY");
        sided.add(block);
        IBlockState lamp = block.getDefaultState();
        int info = LightInfo.of(lamp);
        assertNotEquals(0, info & LightInfo.DYNAMIC);
        assertNotEquals(0, info & LightInfo.REGISTRY);
        // Checks the blocked faces.
        assertEquals(SIDES | DOWN, LightInfo.blockedFaces(info, lamp));

        AsyncWorld fixture = neighbourhood();
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos source = new BlockPos(8, 64, 8);
        fixture.setBlock(source, lamp);
        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(source));
        // Checks the light through the open face.
        assertEquals(14, fixture.blockLight(source.up()));
        assertEquals(13, fixture.blockLight(source.up().east()));
        assertEquals(12, fixture.blockLight(source.east()));
        // Under the lamp only what came round its closed sides is left
        assertEquals(10, fixture.blockLight(source.down()));

        // The recheck counts the lamp only through its open face
        engine.setupCaches(7, 128, 7, true, true);
        try {
            assertEquals(14, engine.calculateLightValue(8, 65, 8, 13));
            assertEquals(12, engine.calculateLightValue(9, 64, 8, 12));
        } finally {
            engine.destroyCaches();
        }
    }

    @Test
    void anUnregisteredBlockHasNoClosedFaces() {
        // Checks an unregistered interface block.
        DirectionalLamp block = new DirectionalLamp();
        int info = LightInfo.of(block.getDefaultState());
        assertEquals(0, info & LightInfo.REGISTRY);
        assertEquals(0, LightInfo.blockedFaces(info, block.getDefaultState()));
        // A block whose metadata cannot be read is treated as one that is not sided
        IBlockState broken = mock(IBlockState.class);
        BrokenMetaSlab slab = new BrokenMetaSlab();
        when(broken.getBlock()).thenReturn(slab);
        when(broken.getLightOpacity()).thenReturn(15);
        Set<Block> sided = Statics.get(FaceOcclusion.class, "HAS_SIDED_TRANSPARENCY");
        sided.add(slab);
        try {
            int brokenInfo = LightInfo.compute(broken);
            assertEquals(0, brokenInfo & LightInfo.REGISTRY);
            assertEquals(15, LightInfo.absorption(brokenInfo, broken, 4));
        } finally {
            sided.remove(slab);
        }
    }

    // A glowing block that is open on top only and reports so itself
    private static final class DirectionalLamp extends Block implements FaceLightOcclusion {
        DirectionalLamp() {
            super(Material.ROCK);
            setLightLevel(1.0F);
        }

        @Override
        public int getDirectionalLightOpacity(IBlockState state, EnumFacing direction) {
            return direction == EnumFacing.UP ? 0 : 15;
        }
    }

    // A block whose metadata lookup throws
    private static final class BrokenMetaSlab extends Block {
        BrokenMetaSlab() {
            super(Material.ROCK);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            throw new IllegalStateException("no meta");
        }
    }
}
