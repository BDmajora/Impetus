package com.bdmajora.fulgor.mixin.async.block;

import com.bdmajora.fulgor.async.engine.LightCachedState;
import com.bdmajora.fulgor.async.engine.LightInfo;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Mixin that changes StateImplementation.
@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class BlockStateMixin implements LightCachedState {
    // Packed light info of the state or 0 until computed.
    @Unique
    private int fulgor$lightInfo;

    // Returns the packed light info and computes it on first use.
    @Override
    public int fulgor$lightInfo() {
        int info = this.fulgor$lightInfo;
        if (info != 0) {
            return info;
        }
        return this.fulgor$lightInfo = LightInfo.compute((IBlockState) this);
    }
}
