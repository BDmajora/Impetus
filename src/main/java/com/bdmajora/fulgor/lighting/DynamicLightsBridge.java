package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Reaches AtomicStryker's Dynamic Lights without compiling against it, for held and dropped sources; bound via invokeExact so it inlines, and a missing class just leaves it unavailable
public final class DynamicLightsBridge {
    private static final String CLASS_NAME = "atomicstryker.dynamiclights.client.DynamicLights";

    private static final MethodHandle GET_LIGHT_VALUE = resolve();

    private DynamicLightsBridge() {
    }

    // True only when the mod was found and the handle resolved
    public static boolean isAvailable() {
        return GET_LIGHT_VALUE != null;
    }

    // Asks the mod for luminance at a position; rethrows with the position since a bare handle trace is useless
    public static int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        try {
            return (int) GET_LIGHT_VALUE.invokeExact(state.getBlock(), state, world, pos);
        } catch (Throwable t) {
            // invokeExact forces this catch; rethrow with the position since a bare MethodHandle stack trace is useless
            throw new IllegalStateException("Dynamic Lights threw while reporting luminance at " + pos, t);
        }
    }

    // Looks the target method up once at class init; any failure yields null rather than a crash
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
