package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A world the async light engines can work in: real sections and SWMR nibbles behind mocked chunks, with the
// light-readiness flags and emptiness maps the engines read and write
public final class AsyncWorld {
    public final World world;
    public final Map<Long, Chunk> chunks = new HashMap<>();

    public AsyncWorld(boolean client) {
        this(client, World.class);
    }

    // The world type matters to code that checks for a WorldServer before touching the player chunk map
    public AsyncWorld(boolean client, Class<? extends World> type) {
        this.world = Mc.mock(type, AsyncLitWorld.class);
        Mixins.set(world, "isRemote", client);
        WorldProvider dimension = mock(WorldProvider.class);
        when(dimension.hasSkyLight()).thenReturn(true);
        Mixins.set(world, "provider", dimension);
        when(((AsyncLitWorld) world).fulgor$getAnyChunkImmediately(anyInt(), anyInt())).thenAnswer(invocation ->
                chunks.get(key(invocation.getArgument(0), invocation.getArgument(1))));
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    // A chunk of empty sections, its nibbles all NULL and its light already usable to the engine
    public Chunk chunk(int chunkX, int chunkZ) {
        Chunk chunk = Mc.mock(Chunk.class, AsyncLitChunk.class);
        Mixins.set(chunk, "x", chunkX);
        Mixins.set(chunk, "z", chunkZ);
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new ExtendedBlockStorage(i << 4, true);
        }
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        AsyncLitChunk lit = (AsyncLitChunk) chunk;
        SWMRNibbleArray[][] nibbles = {ChunkLightHelper.newNullNibbles(), ChunkLightHelper.newNullNibbles()};
        boolean[][] emptiness = new boolean[2][];
        when(lit.fulgor$getBlockNibbles()).thenAnswer(invocation -> nibbles[0]);
        when(lit.fulgor$getSkyNibbles()).thenAnswer(invocation -> nibbles[1]);
        Mockito.doAnswer(invocation -> nibbles[0] = invocation.getArgument(0)).when(lit).fulgor$setBlockNibbles(any());
        Mockito.doAnswer(invocation -> nibbles[1] = invocation.getArgument(0)).when(lit).fulgor$setSkyNibbles(any());
        when(lit.fulgor$getBlockEmptinessMap()).thenAnswer(invocation -> emptiness[0]);
        when(lit.fulgor$getSkyEmptinessMap()).thenAnswer(invocation -> emptiness[1]);
        Mockito.doAnswer(invocation -> emptiness[0] = invocation.getArgument(0)).when(lit).fulgor$setBlockEmptinessMap(any());
        Mockito.doAnswer(invocation -> emptiness[1] = invocation.getArgument(0)).when(lit).fulgor$setSkyEmptinessMap(any());
        when(lit.fulgor$isLightUsable()).thenReturn(true);
        when(chunk.getWorld()).thenReturn(world);
        chunks.put(key(chunkX, chunkZ), chunk);
        return chunk;
    }

    public void setBlock(BlockPos pos, IBlockState state) {
        chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4)).getBlockStorageArray()[pos.getY() >> 4]
                .set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
    }

    // Visible block light at a world position, the way a reader outside the engine sees it
    public int blockLight(BlockPos pos) {
        Chunk chunk = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        return ChunkLightHelper.getBlockLight(((AsyncLitChunk) chunk).fulgor$getBlockNibbles(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    public int skyLight(BlockPos pos) {
        Chunk chunk = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        return ChunkLightHelper.getSkyLight(((AsyncLitChunk) chunk).fulgor$getSkyNibbles(),
                pos.getX(), pos.getY(), pos.getZ());
    }
}
