package com.bdmajora.equilibrium.mixin.worldgen.primer_state_cache;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.ChunkPrimer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Terrain generation writes long runs of the same state (stone, water) and reads them back column by column; one-entry caches in both directions turn the identity-map lookup per block into a reference compare, with the same ids and states as before
@Mixin(ChunkPrimer.class)
public abstract class ChunkPrimerMixin {
    @Shadow
    @Final
    private char[] data;

    @Shadow
    @Final
    private static IBlockState DEFAULT_STATE;

    @Unique
    private IBlockState equilibrium$lastSetState;

    @Unique
    private char equilibrium$lastSetId;

    @Unique
    private char equilibrium$lastGetId;

    @Unique
    private IBlockState equilibrium$lastGetState;

    // Same table read as vanilla, skipped while consecutive reads land on the same id. @author/@reason are Mixin's required @Overwrite metadata
    /**
     * @author bdmajora
     * @reason one-entry decode cache on the primer's hot read path
     */
    @Overwrite
    public IBlockState getBlockState(int x, int y, int z) {
        char id = this.data[x << 12 | z << 8 | y];
        if (id == this.equilibrium$lastGetId && this.equilibrium$lastGetState != null) {
            return this.equilibrium$lastGetState;
        }
        IBlockState state = Block.BLOCK_STATE_IDS.getByValue(id);
        if (state == null) {
            state = DEFAULT_STATE;
        }
        this.equilibrium$lastGetId = id;
        this.equilibrium$lastGetState = state;
        return state;
    }

    // Same id as vanilla's identity lookup, resolved once per run of a state. @author/@reason are Mixin's required @Overwrite metadata
    /**
     * @author bdmajora
     * @reason one-entry encode cache on the primer's hot write path
     */
    @Overwrite
    public void setBlockState(int x, int y, int z, IBlockState state) {
        char id;
        if (state == this.equilibrium$lastSetState) {
            id = this.equilibrium$lastSetId;
        } else {
            id = (char) Block.BLOCK_STATE_IDS.get(state);
            this.equilibrium$lastSetState = state;
            this.equilibrium$lastSetId = id;
        }
        this.data[x << 12 | z << 8 | y] = id;
    }
}
