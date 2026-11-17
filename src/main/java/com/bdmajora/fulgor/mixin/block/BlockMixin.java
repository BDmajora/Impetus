package com.bdmajora.fulgor.mixin.block;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.LightInfoBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Mixin that changes Block.
@Mixin(Block.class)
public abstract class BlockMixin implements LightInfoBlock {
    // Flag set once the overrides were looked up.
    @Unique
    private static final int FULGOR_RESOLVED = 1;
    // Flag of a block overriding the position aware getLightValue.
    @Unique
    private static final int FULGOR_POSITION_AWARE_LIGHT_VALUE = 1 << 1;
    // Flag of a block overriding the position aware getLightOpacity.
    @Unique
    private static final int FULGOR_POSITION_AWARE_OPACITY = 1 << 2;

    // Override flags of the block's class.
    @Unique
    private byte fulgor$lightInfoFlags;

    // Returns whether the block's light value depends on its position.
    @Override
    public boolean fulgor$hasPositionAwareLightValue() {
        return (fulgor$lightInfoFlags() & FULGOR_POSITION_AWARE_LIGHT_VALUE) != 0;
    }

    // Returns whether the block's opacity depends on its position.
    @Override
    public boolean fulgor$hasPositionAwareOpacity() {
        return (fulgor$lightInfoFlags() & FULGOR_POSITION_AWARE_OPACITY) != 0;
    }

    // Returns the override flags and looks them up on first use.
    @Unique
    private int fulgor$lightInfoFlags() {
        int flags = this.fulgor$lightInfoFlags;

        if ((flags & FULGOR_RESOLVED) == 0) {
            flags = FULGOR_RESOLVED;

            if (fulgor$overrides("getLightValue")) {
                flags |= FULGOR_POSITION_AWARE_LIGHT_VALUE;
            }

            if (fulgor$overrides("getLightOpacity")) {
                flags |= FULGOR_POSITION_AWARE_OPACITY;
            }

            this.fulgor$lightInfoFlags = (byte) flags;
        }

        return flags;
    }

    // Returns whether the block's class overrides a position aware method and true when unsure.
    @Unique
    private boolean fulgor$overrides(String name) {
        try {
            return getClass()
                    .getMethod(name, IBlockState.class, IBlockAccess.class, BlockPos.class)
                    .getDeclaringClass() != Block.class;
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            Fulgor.LOGGER.warn("Could not determine whether {} overrides {}; assuming it does",
                    getClass().getName(), name, e);
            return true;
        }
    }
}
