package com.bdmajora.coarctatio.client.model.dynamic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;

import java.util.Map;
import java.util.concurrent.ExecutionException;

// State-to-location tables built per block on first use instead of for every block at reload; the state mapper builds a fresh map on each call, so the result is kept, bounded by total entries
public final class StateModelLocations {
    private final LoadingCache<Block, Map<IBlockState, ModelResourceLocation>> byBlock;

    public StateModelLocations(BlockStateMapper mapper) {
        this.byBlock = CacheBuilder.newBuilder()
                .maximumWeight(100000)
                .weigher((Block block, Map<IBlockState, ModelResourceLocation> map) -> Math.max(1, map.size()))
                .build(new CacheLoader<Block, Map<IBlockState, ModelResourceLocation>>() {
                    // Mappers are not written for concurrent use and the chunk builders ask from several threads
                    @Override
                    public Map<IBlockState, ModelResourceLocation> load(Block block) {
                        synchronized (mapper) {
                            return ImmutableMap.copyOf(mapper.getVariants(block));
                        }
                    }
                });
    }

    public ModelResourceLocation locationFor(IBlockState state) {
        try {
            return this.byBlock.get(state.getBlock()).get(state);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
