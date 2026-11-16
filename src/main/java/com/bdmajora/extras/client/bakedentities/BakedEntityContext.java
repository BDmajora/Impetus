package com.bdmajora.extras.client.bakedentities;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

// Position that the baked model reads for the current thread.
public final class BakedEntityContext {
    // Context of each thread.
    private static final ThreadLocal<BakedEntityContext> CURRENT = ThreadLocal.withInitial(BakedEntityContext::new);

    // World of the position.
    public IBlockAccess access;
    // Position of the block.
    public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    // Whether the context is set.
    private boolean valid;

    // Prevents instantiation
    private BakedEntityContext() {
    }

    // Stores the world and position.
    public static void set(IBlockAccess access, BlockPos pos) {
        BakedEntityContext context = CURRENT.get();
        context.access = access;
        context.pos.setPos(pos);
        context.valid = true;
    }

    // Clears the context.
    public static void clear() {
        BakedEntityContext context = CURRENT.get();
        context.access = null;
        context.valid = false;
    }

    // Returns the context or null when it is not set.
    public static BakedEntityContext get() {
        BakedEntityContext context = CURRENT.get();
        return context.valid ? context : null;
    }
}
