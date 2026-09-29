package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.api.LightInfoBlock;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightingUtilitiesTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @AfterEach
    void forgetCompatibility() {
        Statics.set(Fulgor.class, "dynamicLights", false);
        Statics.set(Fulgor.class, "fluidloggedApi", false);
        Statics.set(Fulgor.class, "cachedBlockLightInfo", false);
    }

    @Test
    void sectionReadsSkipTheChunkMachinery() {
        ExtendedBlockStorage section = new ExtendedBlockStorage(0, true);
        section.set(1, 2, 3, Blocks.STONE.getDefaultState());
        Chunk chunk = mock(Chunk.class);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        sections[0] = section;
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        assertSame(Blocks.STONE.getDefaultState(), LightUtil.posToState(new BlockPos(1, 2, 3), chunk));
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(1, 2, 3), Chunk.NULL_BLOCK_STORAGE));
        // Coordinates are masked into the section, so a world position resolves to the same slot
        assertSame(Blocks.STONE.getDefaultState(), LightUtil.posToState(new BlockPos(17, 2, 19), section));
    }

    @Test
    void luminanceAndOpacityTakeTheCachedPathOnlyWhenItIsSafe() {
        IBlockAccess world = mock(IBlockAccess.class);
        BlockPos pos = new BlockPos(0, 64, 0);
        Chunk chunk = mock(Chunk.class);
        IBlockState glowstone = Blocks.GLOWSTONE.getDefaultState();
        // With the cache off both answers come from the position-aware overloads
        assertEquals(15, LightUtil.getLightValue(glowstone, world, pos, chunk));
        assertEquals(255, LightUtil.getLightOpacity(glowstone, world, pos, chunk));
        // With it on, a block that declares no position-aware override answers from the state alone
        Statics.set(Fulgor.class, "cachedBlockLightInfo", true);
        assertEquals(15, LightUtil.getLightValue(glowstone, world, pos, chunk));
        assertEquals(255, LightUtil.getLightOpacity(glowstone, world, pos, chunk));
        // A block that does override them is asked through the world again
        com.bdmajora.testing.SplicedAnswers.positionAwareLightValue = true;
        com.bdmajora.testing.SplicedAnswers.positionAwareOpacity = true;
        assertEquals(15, LightUtil.getLightValue(glowstone, world, pos, chunk));
        assertEquals(255, LightUtil.getLightOpacity(glowstone, world, pos, chunk));
        com.bdmajora.testing.SplicedAnswers.reset();
        LightInfoBlock info = (LightInfoBlock) (Object) Blocks.GLOWSTONE;
        assertFalse(info.fulgor$hasPositionAwareLightValue());
        assertFalse(info.fulgor$hasPositionAwareOpacity());
    }

    @Test
    void openFacesFollowTheShapeOfTheBlock() {
        assertEquals(FaceLightRules.FACE_UP, FaceLightRules.openFaces(Blocks.STONE_SLAB.getDefaultState()));
        assertEquals(FaceLightRules.FACE_DOWN, FaceLightRules.openFaces(Blocks.STONE_SLAB.getDefaultState()
                .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP)));
        assertEquals(0, FaceLightRules.openFaces(Blocks.DOUBLE_STONE_SLAB.getDefaultState()));
        assertEquals(FaceLightRules.FACE_UP, FaceLightRules.openFaces(Blocks.OAK_STAIRS.getDefaultState()));
        assertEquals(FaceLightRules.FACE_DOWN, FaceLightRules.openFaces(Blocks.OAK_STAIRS.getDefaultState()
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP)));
        assertEquals(FaceLightRules.FACE_UP, FaceLightRules.openFaces(Blocks.WATER.getDefaultState()));
        assertEquals(0, FaceLightRules.openFaces(Blocks.STONE.getDefaultState()));

        // Light folded through an open face still attenuates, except full skylight
        assertEquals(9, FaceLightRules.fold(5, 10, EnumSkyBlock.BLOCK));
        assertEquals(5, FaceLightRules.fold(5, 3, EnumSkyBlock.BLOCK));
        assertEquals(15, FaceLightRules.fold(5, 15, EnumSkyBlock.SKY));
        assertEquals(14, FaceLightRules.fold(5, 15, EnumSkyBlock.BLOCK));
        BlockPos pos = new BlockPos(0, 64, 0);
        assertEquals(11, FaceLightRules.foldOpenFaces(FaceLightRules.FACE_UP, 0, EnumSkyBlock.BLOCK, pos,
                (type, at) -> at.equals(pos.up()) ? 12 : 0));
        // A closed face contributes nothing, and a level already at full stops the walk
        assertEquals(0, FaceLightRules.foldOpenFaces(0, 0, EnumSkyBlock.BLOCK, pos, (type, at) -> 12));
        assertEquals(15, FaceLightRules.foldOpenFaces(FaceLightRules.FACE_UP, 15, EnumSkyBlock.BLOCK, pos,
                (type, at) -> 15));
        // Ambient occlusion treats a level-one emitter as unlit so it keeps smooth lighting
        assertEquals(0, FaceLightRules.ambientOcclusionEmission(1));
        assertEquals(0, FaceLightRules.ambientOcclusionEmission(0));
        assertEquals(13, FaceLightRules.ambientOcclusionEmission(14));
        assertEquals(15, FaceLightRules.ambientOcclusionEmission(30));
    }

    @Test
    void boundaryFlagsAreIndexedWrittenAndRead() {
        assertEquals(NeighborLightFlags.BoundaryFacing.OUT,
                NeighborLightFlags.BoundaryFacing.IN.getOpposite());
        assertEquals(NeighborLightFlags.BoundaryFacing.IN,
                NeighborLightFlags.BoundaryFacing.OUT.getOpposite());
        int block = NeighborLightFlags.index(EnumSkyBlock.BLOCK, EnumFacing.EAST,
                EnumFacing.AxisDirection.NEGATIVE, NeighborLightFlags.BoundaryFacing.IN);
        int sky = NeighborLightFlags.index(EnumSkyBlock.SKY, EnumFacing.EAST,
                EnumFacing.AxisDirection.NEGATIVE, NeighborLightFlags.BoundaryFacing.IN);
        assertEquals(block + 16, sky);
        assertTrue(block >= 0 && sky < Fulgor.BOUNDARY_FLAG_COUNT);
        assertEquals(EnumFacing.AxisDirection.NEGATIVE, NeighborLightFlags.axisDirection(EnumFacing.EAST, 0, 3));
        assertEquals(EnumFacing.AxisDirection.POSITIVE, NeighborLightFlags.axisDirection(EnumFacing.EAST, 0, 12));
        assertEquals(EnumFacing.AxisDirection.NEGATIVE, NeighborLightFlags.axisDirection(EnumFacing.NORTH, 3, 0));
        assertEquals(EnumFacing.AxisDirection.POSITIVE, NeighborLightFlags.axisDirection(EnumFacing.NORTH, 12, 0));

        short[] flags = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        Chunk chunk = Mc.mock(Chunk.class, ChunkLightingData.class);
        ChunkLightingData data = (ChunkLightingData) chunk;
        when(data.fulgor$getNeighborLightChecks()).thenReturn(flags);
        NeighborLightFlags.flagBoundary(chunk, (short) 0b101, EnumSkyBlock.BLOCK, EnumFacing.EAST,
                EnumFacing.AxisDirection.NEGATIVE, NeighborLightFlags.BoundaryFacing.IN);
        assertEquals(0b101, flags[block]);
        Mockito.verify(chunk).markDirty();
        Mockito.verify(data).fulgor$initNeighborLightChecks();

        // Writing skips an all-zero table, and reading rejects one of the wrong length
        NBTTagCompound compound = new NBTTagCompound();
        NeighborLightFlags.write(chunk, compound);
        assertTrue(compound.hasKey(NeighborLightFlags.NBT_KEY, 9));
        NBTTagCompound empty = new NBTTagCompound();
        NeighborLightFlags.write(Mc.mock(Chunk.class, ChunkLightingData.class), empty);
        assertFalse(empty.hasKey(NeighborLightFlags.NBT_KEY, 9));
        java.util.Arrays.fill(flags, (short) 0);
        NBTTagCompound zeroed = new NBTTagCompound();
        NeighborLightFlags.write(chunk, zeroed);
        assertFalse(zeroed.hasKey(NeighborLightFlags.NBT_KEY, 9));

        short[] loaded = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        Chunk target = Mc.mock(Chunk.class, ChunkLightingData.class);
        when(((ChunkLightingData) target).fulgor$getNeighborLightChecks()).thenReturn(loaded);
        NeighborLightFlags.read(target, compound);
        assertEquals(0b101, loaded[block]);
        NeighborLightFlags.read(target, new NBTTagCompound());
        NBTTagCompound wrongLength = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagShort((short) 1));
        wrongLength.setTag(NeighborLightFlags.NBT_KEY, list);
        NeighborLightFlags.read(target, wrongLength);
        assertEquals(0b101, loaded[block]);
    }

    @Test
    void theChunkSliceAnswersFromItsOwnSnapshot() {
        IChunkProvider provider = mock(IChunkProvider.class);
        Chunk centre = mock(Chunk.class);
        when(provider.getLoadedChunk(any(Integer.class).intValue(), any(Integer.class).intValue())).thenReturn(null);
        when(provider.getLoadedChunk(0, 0)).thenReturn(centre);
        WorldChunkSlice slice = new WorldChunkSlice(provider, 0, 0);
        assertSame(centre, slice.getChunkFromWorldCoords(5, 5));
        assertNull(slice.getChunkFromWorldCoords(20, 20));
        assertNull(slice.getChunkFromWorldCoords(1000, 1000));
        assertFalse(slice.isLoaded(8, 8, 16));
        assertTrue(slice.isLoaded(8, 8, 4));

        // A fully loaded neighbourhood answers yes for the whole radius
        Chunk any = mock(Chunk.class);
        when(provider.getLoadedChunk(Mockito.anyInt(), Mockito.anyInt())).thenReturn(any);
        WorldChunkSlice full = new WorldChunkSlice(provider, 4, -4);
        assertTrue(full.isLoaded(64, -64, 32));
        assertSame(any, full.getChunkFromWorldCoords(64, -64));
    }
}
