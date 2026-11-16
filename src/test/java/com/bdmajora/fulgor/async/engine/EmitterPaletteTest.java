package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.BlockStatePaletteHashMap;
import net.minecraft.world.chunk.BlockStatePaletteLinear;
import net.minecraft.world.chunk.BlockStatePaletteRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmitterPaletteTest {
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

    private static Object palette(ExtendedBlockStorage section) {
        return Mixins.get(section.getData(), "palette");
    }

    // A non-emitting block whose state light lookup counts the calls the engine makes
    private static final class CountingBlock extends Block {
        final AtomicInteger lookups = new AtomicInteger();

        CountingBlock() {
            super(Material.ROCK);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getLightValue(IBlockState state) {
            this.lookups.incrementAndGet();
            return 0;
        }
    }

    @Test
    void aLocalPaletteTellsWhetherASectionCanHoldALightSource() {
        ExtendedBlockStorage section = new ExtendedBlockStorage(64, true);
        assertInstanceOf(BlockStatePaletteLinear.class, palette(section));
        assertFalse(EmitterPalette.mayEmitLight(section));
        section.set(1, 2, 3, Blocks.STONE.getDefaultState());
        assertFalse(EmitterPalette.mayEmitLight(section));
        section.set(1, 2, 3, Blocks.GLOWSTONE.getDefaultState());
        assertTrue(EmitterPalette.mayEmitLight(section));
        // Palettes never shrink, so a replaced light source still counts: a wasted scan, never a missed light
        section.set(1, 2, 3, Blocks.STONE.getDefaultState());
        assertTrue(EmitterPalette.mayEmitLight(section));

        // Past sixteen states the palette is a hash map, still local and still read
        ExtendedBlockStorage wide = new ExtendedBlockStorage(64, true);
        for (int meta = 0; meta < 16; meta++) {
            wide.set(meta, 0, 0, Blocks.WOOL.getStateFromMeta(meta));
            wide.set(meta, 1, 0, Blocks.STAINED_GLASS.getStateFromMeta(meta));
        }
        assertInstanceOf(BlockStatePaletteHashMap.class, palette(wide));
        assertFalse(EmitterPalette.mayEmitLight(wide));
        wide.set(15, 15, 15, Blocks.TORCH.getDefaultState());
        assertTrue(EmitterPalette.mayEmitLight(wide));
    }

    @Test
    void theGlobalPaletteAWorldContextBlockAndAnUnknownPaletteAreAlwaysScanned() {
        // Past 256 states the section indexes the global registry, which lists every light source in the game
        ExtendedBlockStorage crowded = new ExtendedBlockStorage(64, true);
        java.util.Set<IBlockState> distinct = new java.util.LinkedHashSet<>();
        for (IBlockState state : Block.BLOCK_STATE_IDS) {
            if (distinct.size() < 300 && state.getLightValue() == 0) {
                distinct.add(state);
            }
        }
        int index = 0;
        for (IBlockState state : distinct) {
            crowded.set(index & 15, (index >> 8) & 15, (index >> 4) & 15, state);
            index++;
        }
        assertInstanceOf(BlockStatePaletteRegistry.class, palette(crowded));
        assertTrue(EmitterPalette.mayEmitLight(crowded));

        // A block that answers its light from the world may glow anywhere, whatever its state says
        Block contextual = new Block(Material.ROCK) {
            @Override
            public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
                return 0;
            }
        };
        ExtendedBlockStorage section = new ExtendedBlockStorage(64, true);
        section.set(0, 0, 0, contextual.getDefaultState());
        assertTrue(EmitterPalette.mayEmitLight(section));
        assertTrue(EmitterPalette.mayEmit(LightInfo.of(contextual.getDefaultState())));
        assertFalse(EmitterPalette.mayEmit(LightInfo.of(Blocks.STONE.getDefaultState())));
        assertTrue(EmitterPalette.mayEmit(LightInfo.of(Blocks.LIT_PUMPKIN.getDefaultState())));

        // A storage whose palette is not one of vanilla's is scanned as before
        ExtendedBlockStorage foreign = mock(ExtendedBlockStorage.class);
        when(foreign.getData()).thenReturn(mock(BlockStateContainer.class));
        assertTrue(EmitterPalette.mayEmitLight(foreign));
    }

    @Test
    void lightingSkipsTheBlocksOfSectionsWithoutALightSource() {
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        CountingBlock dark = new CountingBlock();
        for (int y = 64; y < 80; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    fixture.setBlock(new BlockPos(x, y, z), dark.getDefaultState());
                }
            }
        }
        // A light source in another section of the same chunk is still found and spread
        BlockPos lamp = new BlockPos(8, 40, 8);
        fixture.setBlock(lamp, Blocks.GLOWSTONE.getDefaultState());
        dark.lookups.set(0);

        BlockLightEngine engine = new BlockLightEngine(fixture.world);
        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
        assertEquals(15, fixture.blockLight(lamp));
        assertEquals(14, fixture.blockLight(lamp.up()));
        assertEquals(0, fixture.blockLight(new BlockPos(8, 70, 8)));
        // One palette read for the dark section instead of a lookup for each of its 4096 blocks
        assertTrue(dark.lookups.get() < 64, "lookups " + dark.lookups.get());
    }

    @Test
    void aFluidloggedChunkIsScannedBlockByBlock() {
        Statics.set(Fulgor.class, "fluidloggedApi", true);
        AsyncWorld fixture = new AsyncWorld(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                fixture.chunk(x, z);
            }
        }
        Chunk chunk = fixture.chunks.get(AsyncWorld.key(0, 0));
        BlockPos stone = new BlockPos(8, 70, 8);
        fixture.setBlock(stone, Blocks.STONE.getDefaultState());
        // A glowing fluid shares every position, and no block in the palette emits
        IFluidStateContainer container = (x, y, z, fallback) -> new FluidState(Blocks.SEA_LANTERN.getDefaultState());
        IFluidStateCapability.next[0] = y -> container;
        try {
            assertFalse(EmitterPalette.mayEmitLight(chunk.getBlockStorageArray()[4]));
            BlockLightEngine engine = new BlockLightEngine(fixture.world);
            engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
            assertEquals(15, fixture.blockLight(stone));
        } finally {
            IFluidStateCapability.next[0] = null;
        }
    }
}
