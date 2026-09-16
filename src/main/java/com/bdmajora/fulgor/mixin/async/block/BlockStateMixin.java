package com.bdmajora.fulgor.mixin.async.block;

import com.bdmajora.fulgor.async.engine.LightCachedState;
import com.bdmajora.fulgor.async.engine.LightInfo;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Memoises the packed light info on every block state (Starlight's per-state opacity cache); the racy lazy init is deliberate, since compute is pure and an int store is atomic, and Coarctatio's states inherit the field
@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class BlockStateMixin implements LightCachedState {
    @Unique
    private int fulgor$lightInfo;

    @Override
    public int fulgor$lightInfo() {
        int info = this.fulgor$lightInfo;
        if (info != 0) {
            return info;
        }
        return this.fulgor$lightInfo = LightInfo.compute((IBlockState) this);
    }
}
