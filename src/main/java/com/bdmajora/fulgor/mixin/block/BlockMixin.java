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
    // Field fulgor resolved added to the target.
    @Unique
    private static final int FULGOR_RESOLVED = 1;
    // Field fulgor position aware light value added to the target.
    @Unique
    private static final int FULGOR_POSITION_AWARE_LIGHT_VALUE = 1 << 1;
    // Field fulgor position aware opacity added to the target.
    @Unique
    private static final int FULGOR_POSITION_AWARE_OPACITY = 1 << 2;

    // Field light info flags added to the target.
    @Unique
    private byte fulgor$lightInfoFlags;

    // Returns whether position aware light value.
    @Override
    public boolean fulgor$hasPositionAwareLightValue() {
        return (fulgor$lightInfoFlags() & FULGOR_POSITION_AWARE_LIGHT_VALUE) != 0;
    }

    // Returns whether position aware opacity.
    @Override
    public boolean fulgor$hasPositionAwareOpacity() {
        return (fulgor$lightInfoFlags() & FULGOR_POSITION_AWARE_OPACITY) != 0;
    }

    // Helper method light info flags added to the target.
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

    // Helper method overrides added to the target.
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
