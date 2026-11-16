package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import com.bdmajora.fulgor.lighting.DynamicLightsBridge;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

// Packed light properties of a block state.
public final class LightInfo {
    // Mask of the opacity bits.
    public static final int OPACITY_MASK = 0xF;
    // Shift of the emission bits.
    public static final int EMISSION_SHIFT = 4;
    // Flag of a block with sided transparency.
    public static final int REGISTRY = 1 << 8;
    // Flag of a block that reports its own face occlusion.
    public static final int DYNAMIC = 1 << 9;
    // Flag of a sided block whose opacity exceeds one.
    public static final int SIDED = 1 << 10;
    // Flag of an opacity that depends on the world.
    public static final int CONTEXT_OPACITY = 1 << 11;
    // Flag of an emission that depends on the world.
    public static final int CONTEXT_EMISSION = 1 << 12;
    // Mask of both world dependent flags.
    public static final int CONTEXT_MASK = CONTEXT_OPACITY | CONTEXT_EMISSION;
    // Shift of the solid face bits.
    public static final int FACE_SHIFT = 16;
    // Mask of the six solid face bits.
    public static final int FACE_MASK = 0x3F;
    // Flag of a computed value.
    public static final int COMPUTED = 1 << 30;

    // World dependent flags of each block class.
    private static final ClassValue<Integer> CONTEXT_FLAGS = new ClassValue<Integer>() {
        // Returns the flags of a class from the methods it overrides.
        @Override
        protected Integer computeValue(Class<?> blockClass) {
            int flags = 0;
            try {
                if (blockClass.getMethod("getLightOpacity", IBlockState.class, IBlockAccess.class, BlockPos.class).getDeclaringClass() != Block.class) {
                    flags |= CONTEXT_OPACITY;
                }
                if (blockClass.getMethod("getLightValue", IBlockState.class, IBlockAccess.class, BlockPos.class).getDeclaringClass() != Block.class) {
                    flags |= CONTEXT_EMISSION;
                }
            } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
                flags = CONTEXT_MASK;
            }
            if (Fulgor.hasDynamicLights()) {
                flags |= CONTEXT_EMISSION;
            }
            return flags;
        }
    };

    // Prevents instantiation
    private LightInfo() {
    }

    // Returns the cached info of a state or computes it.
    public static int of(IBlockState state) {
        if (state instanceof LightCachedState) {
            return ((LightCachedState) state).fulgor$lightInfo();
        }
        return compute(state);
    }

    // Computes the info of a state.
    @SuppressWarnings("deprecation")
    public static int compute(IBlockState state) {
        Block block = state.getBlock();
        int opacity = clampLight(state.getLightOpacity());
        int emission = clampLight(state.getLightValue());
        int info = COMPUTED | CONTEXT_FLAGS.get(block.getClass()) | opacity | (emission << EMISSION_SHIFT);

        boolean dynamic = block instanceof FaceLightOcclusion;
        if (dynamic) {
            info |= DYNAMIC;
        }
        if (FaceOcclusion.hasSidedTransparency(block)) {
            info |= REGISTRY;
            if (opacity > 1) {
                info |= SIDED;
            }
            int faceBits;
            if (dynamic) {
                faceBits = FACE_MASK;
            } else {
                int meta;
                try {
                    meta = block.getMetaFromState(state);
                } catch (VirtualMachineError fatal) {
                    throw fatal;
                } catch (Throwable t) {
                    // Reads a block whose metadata fails as having no known metadata
                    meta = 16;
                }
                faceBits = 0;
                for (int dir = 0; dir < 6; ++dir) {
                    if (FaceOcclusion.isFaceSolid(block, meta, dir)) {
                        faceBits |= 1 << dir;
                    }
                }
            }
            info |= faceBits << FACE_SHIFT;
        }
        return info;
    }

    // Returns whether the info depends on the world.
    public static boolean hasContextualValues(int info) {
        return (info & CONTEXT_MASK) != 0;
    }

    // Returns the info with the world dependent values of a position.
    public static int resolveContextual(int info, IBlockState state, IBlockAccess access, BlockPos.MutableBlockPos pos,
                                        int x, int y, int z) {
        int flags = info & CONTEXT_MASK;
        if (flags == 0) {
            return info;
        }

        int opacity = opacity(info);
        int emission = emission(info);
        if ((flags & CONTEXT_OPACITY) != 0) {
            pos.setPos(x, y, z);
            opacity = clampLight(state.getLightOpacity(access, pos));
        }
        if ((flags & CONTEXT_EMISSION) != 0) {
            pos.setPos(x, y, z);
            emission = clampLight(Fulgor.hasDynamicLights() && DynamicLightsBridge.isAvailable()
                    ? DynamicLightsBridge.getLightValue(state, access, pos)
                    : state.getLightValue(access, pos));
        }
        return withLightValues(info, opacity, emission);
    }

    // Returns the info with new light values and the sided flag updated.
    public static int withLightValues(int info, int opacity, int emission) {
        int resolved = info & ~(OPACITY_MASK | (OPACITY_MASK << EMISSION_SHIFT) | SIDED);
        resolved |= clampLight(opacity) | (clampLight(emission) << EMISSION_SHIFT);
        if ((resolved & REGISTRY) != 0 && (resolved & OPACITY_MASK) > 1) {
            resolved |= SIDED;
        }
        return resolved;
    }

    // Returns a value limited to between 0 and 15.
    private static int clampLight(int value) {
        return Math.min(15, Math.max(0, value));
    }

    // Returns the opacity of the info.
    public static int opacity(int info) {
        return info & OPACITY_MASK;
    }

    // Returns the emission of the info.
    public static int emission(int info) {
        return (info >>> EMISSION_SHIFT) & 0xF;
    }

    // Returns whether a face of the info is solid.
    public static boolean isFaceSolid(int info, int dirOrdinal) {
        return (info & (1 << (FACE_SHIFT + dirOrdinal))) != 0;
    }

    // Returns the solid face bits of the info.
    public static int faceBits(int info) {
        return (info >>> FACE_SHIFT) & FACE_MASK;
    }

    // Returns the light absorbed through a face.
    public static int absorption(int info, IBlockState state, int dirOrdinal) {
        if ((info & DYNAMIC) != 0) {
            return FaceOcclusion.resolveScalarAbsorption(state, dirOrdinal);
        }
        int opacity = info & OPACITY_MASK;
        if ((info & SIDED) != 0) {
            return isFaceSolid(info, dirOrdinal) ? opacity : 1;
        }
        return Math.max(1, opacity);
    }
}
