package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A world of real block-storage sections behind mocked chunks, which is everything the deferred lighting engine
// touches: block states, stored light, sky visibility and the render notification
final class LightWorld {
    final World world = mock(World.class);
    final IChunkProvider provider = mock(IChunkProvider.class);
    final Map<Long, Chunk> chunks = new HashMap<>();
    final List<BlockPos> notified = new ArrayList<>();

    // Positions at or above this see the sky, which is what skylight seeds from
    int skyHeight = Integer.MAX_VALUE;

    LightWorld() {
        Mixins.set(world, "isRemote", false);
        Mixins.set(world, "profiler", new Profiler());
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        when(world.getChunkProvider()).thenReturn(provider);
        when(provider.getLoadedChunk(Mockito.anyInt(), Mockito.anyInt())).thenAnswer(invocation ->
                chunks.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        Mockito.doAnswer(invocation -> notified.add(invocation.getArgument(0))).when(world).notifyLightSet(any());
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    // Registers a chunk of empty sections whose light the engine may read and write
    Chunk chunk(int chunkX, int chunkZ) {
        Chunk chunk = Mc.mock(Chunk.class, ChunkLightingData.class);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new ExtendedBlockStorage(i << 4, true);
        }
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        when(chunk.canSeeSky(any())).thenAnswer(invocation ->
                invocation.<BlockPos>getArgument(0).getY() >= skyHeight);
        Mockito.doAnswer(invocation -> {
            EnumSkyBlock type = invocation.getArgument(0);
            BlockPos pos = invocation.getArgument(1);
            int value = invocation.getArgument(2);
            ExtendedBlockStorage section = sections[pos.getY() >> 4];
            if (type == EnumSkyBlock.SKY) {
                section.setSkyLight(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, value);
            } else {
                section.setBlockLight(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, value);
            }
            return null;
        }).when(chunk).setLightFor(any(), any(), Mockito.anyInt());
        when(((ChunkLightingData) chunk).fulgor$getCachedLightFor(any(), any())).thenAnswer(invocation ->
                light(sections, invocation.getArgument(0), invocation.getArgument(1)));
        chunks.put(key(chunkX, chunkZ), chunk);
        return chunk;
    }

    private static int light(ExtendedBlockStorage[] sections, EnumSkyBlock type, BlockPos pos) {
        ExtendedBlockStorage section = sections[pos.getY() >> 4];
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        return type == EnumSkyBlock.SKY ? section.getSkyLight(x, y, z) : section.getBlockLight(x, y, z);
    }

    void setBlock(BlockPos pos, IBlockState state) {
        Chunk chunk = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        chunk.getBlockStorageArray()[pos.getY() >> 4].set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
    }

    int blockLight(BlockPos pos) {
        Chunk chunk = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        return light(chunk.getBlockStorageArray(), EnumSkyBlock.BLOCK, pos);
    }

    int skyLight(BlockPos pos) {
        Chunk chunk = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        return light(chunk.getBlockStorageArray(), EnumSkyBlock.SKY, pos);
    }

    static IBlockState air() {
        return Blocks.AIR.getDefaultState();
    }
}
