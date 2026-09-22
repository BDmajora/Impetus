package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.FaceLightOcclusion;
import com.bdmajora.fulgor.lighting.DynamicLightsBridge;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

// Everything the BFS asks per neighbour visit packed into one int and memoised on the state: opacity in bits 0-3, emission in 4-7, REGISTRY/DYNAMIC/SIDED/CONTEXT flags in 8-12, face-solid bits by axis direction in 16-21, COMPUTED in bit 30 so zero means unknown
public final class LightInfo {
    public static final int OPACITY_MASK = 0xF;
    public static final int EMISSION_SHIFT = 4;
    // Block is in FaceOcclusion's sided-transparency table
    public static final int REGISTRY = 1 << 8;
    // Block implements FaceLightOcclusion
    public static final int DYNAMIC = 1 << 9;
    // REGISTRY and opacity above one, the gate the calculate paths use
    public static final int SIDED = 1 << 10;
    // Block overrides Forge's position-aware opacity, so the cached value is a placeholder
    public static final int CONTEXT_OPACITY = 1 << 11;
    // Same for the position-aware light value
    public static final int CONTEXT_EMISSION = 1 << 12;
    public static final int CONTEXT_MASK = CONTEXT_OPACITY | CONTEXT_EMISSION;
    public static final int FACE_SHIFT = 16;
    public static final int FACE_MASK = 0x3F;
    public static final int COMPUTED = 1 << 30;

    // Resolved once per block class; a failed probe takes the slow contextual path for both values rather than caching a wrong one
    private static final ClassValue<Integer> CONTEXT_FLAGS = new ClassValue<Integer>() {
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
            // Dynamic Lights answers per position for any block, so emission is never cacheable while it is installed
            if (Fulgor.hasDynamicLights()) {
                flags |= CONTEXT_EMISSION;
            }
            return flags;
        }
    };

    private LightInfo() {
    }

    // The packed info for a state, from the state's own cache when it has one
    public static int of(IBlockState state) {
        if (state instanceof LightCachedState) {
            return ((LightCachedState) state).fulgor$lightInfo();
        }
        return compute(state);
    }

    @SuppressWarnings("deprecation")
    public static int compute(IBlockState state) {
        Block block = state.getBlock();
        int opacity = clampLight(state.getLightOpacity());
        // Clamped, not masked: a block reporting 16+ is full-bright, not dark
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
                // No table exists for interface blocks and isFaceSolid reports every face solid for them
                faceBits = FACE_MASK;
            } else {
                int meta;
                // A block that cannot express this state as a meta falls back to every face solid, the table's own default
                try {
                    meta = block.getMetaFromState(state);
                } catch (Throwable t) {
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

    public static boolean hasContextualValues(int info) {
        return (info & CONTEXT_MASK) != 0;
    }

    // Replaces only the position-dependent values; the mutable position is reset before each call so a misbehaving block cannot corrupt the next one's coordinates
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

    public static int withLightValues(int info, int opacity, int emission) {
        int resolved = info & ~(OPACITY_MASK | (OPACITY_MASK << EMISSION_SHIFT) | SIDED);
        resolved |= clampLight(opacity) | (clampLight(emission) << EMISSION_SHIFT);
        if ((resolved & REGISTRY) != 0 && (resolved & OPACITY_MASK) > 1) {
            resolved |= SIDED;
        }
        return resolved;
    }

    private static int clampLight(int value) {
        return Math.min(15, Math.max(0, value));
    }

    public static int opacity(int info) {
        return info & OPACITY_MASK;
    }

    public static int emission(int info) {
        return (info >>> EMISSION_SHIFT) & 0xF;
    }

    public static boolean isFaceSolid(int info, int dirOrdinal) {
        return (info & (1 << (FACE_SHIFT + dirOrdinal))) != 0;
    }

    public static int faceBits(int info) {
        return (info >>> FACE_SHIFT) & FACE_MASK;
    }

    // Absorption (1-15) for light entering the state through the given face; only DYNAMIC blocks still dispatch to the interface
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
