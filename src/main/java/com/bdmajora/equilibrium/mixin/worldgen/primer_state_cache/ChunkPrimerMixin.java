package com.bdmajora.equilibrium.mixin.worldgen.primer_state_cache;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.ChunkPrimer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Mixin that changes ChunkPrimer.
@Mixin(ChunkPrimer.class)
public abstract class ChunkPrimerMixin {
    // Reference to the field data of the target.
    @Shadow
    @Final
    private char[] data;

    // Reference to the field default state of the target.
    @Shadow
    @Final
    private static IBlockState DEFAULT_STATE;

    // Field last set state added to the target.
    @Unique
    private IBlockState equilibrium$lastSetState;

    // Field last set id added to the target.
    @Unique
    private char equilibrium$lastSetId;

    // Field last get id added to the target.
    @Unique
    private char equilibrium$lastGetId;

    // Field last get state added to the target.
    @Unique
    private IBlockState equilibrium$lastGetState;

    // Replaces the whole method getBlockState.
    @Overwrite
    @SuppressWarnings("deprecation")
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

    // Replaces the whole method setBlockState.
    @Overwrite
    @SuppressWarnings("deprecation")
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
