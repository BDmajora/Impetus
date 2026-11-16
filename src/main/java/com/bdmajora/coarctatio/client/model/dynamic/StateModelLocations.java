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

// Finds the model location of a block state through a bounded cache.
public final class StateModelLocations {
    // Variants of each block.
    private final LoadingCache<Block, Map<IBlockState, ModelResourceLocation>> byBlock;

    // Creates the lookup and rejects a null mapper.
    public StateModelLocations(BlockStateMapper mapper) {
        java.util.Objects.requireNonNull(mapper, "mapper");
        this.byBlock = CacheBuilder.newBuilder()
                .maximumWeight(100000)
                .weigher((Block block, Map<IBlockState, ModelResourceLocation> map) -> Math.max(1, map.size()))
                .build(new CacheLoader<Block, Map<IBlockState, ModelResourceLocation>>() {
                    @Override
                    public Map<IBlockState, ModelResourceLocation> load(Block block) {
                        synchronized (mapper) {
                            return ImmutableMap.copyOf(mapper.getVariants(block));
                        }
                    }
                });
    }

    // Returns the model location of the state.
    public ModelResourceLocation locationFor(IBlockState state) {
        try {
            return this.byBlock.get(state.getBlock()).get(state);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
