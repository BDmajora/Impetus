package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Bridge to the light level of a dynamic lights mod.
public final class DynamicLightsBridge {
    // Class of the dynamic lights mod.
    private static final String CLASS_NAME = "atomicstryker.dynamiclights.client.DynamicLights";

    // Get light value.
    private static final MethodHandle GET_LIGHT_VALUE = resolve();

    // Prevents instantiation
    private DynamicLightsBridge() {
    }

    // Returns whether the dynamic lights mod's hook was found.
    public static boolean isAvailable() {
        return GET_LIGHT_VALUE != null;
    }

    // Returns the light of a block with dynamic light added.
    public static int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        try {
            return (int) GET_LIGHT_VALUE.invokeExact(state.getBlock(), state, world, pos);
        } catch (Throwable t) {
            throw new IllegalStateException("Dynamic Lights threw while reporting luminance at " + pos, t);
        }
    }

    // Finds the light level method or returns null.
    private static MethodHandle resolve() {
        if (!Fulgor.hasDynamicLights()) {
            return null;
        }

        try {
            Class<?> clazz = Class.forName(CLASS_NAME);
            MethodType type = MethodType.methodType(int.class, Block.class, IBlockState.class,
                    IBlockAccess.class, BlockPos.class);

            return MethodHandles.lookup().findStatic(clazz, "getLightValue", type);
        } catch (ReflectiveOperationException | LinkageError e) {
            Fulgor.LOGGER.warn("Dynamic Lights is installed but {}.getLightValue could not be bound; "
                    + "block luminance will come from the block state instead", CLASS_NAME, e);
            return null;
        }
    }
}
