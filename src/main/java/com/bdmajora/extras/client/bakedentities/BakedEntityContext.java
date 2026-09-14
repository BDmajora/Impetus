package com.bdmajora.extras.client.bakedentities;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

// The world and position a block model is being asked about, set by the chunk mesher and vanilla's dispatcher around getQuads; IBakedModel.getQuads has no world argument, and these models need the block entity behind the state
public final class BakedEntityContext {
    private static final ThreadLocal<BakedEntityContext> CURRENT = ThreadLocal.withInitial(BakedEntityContext::new);

    public IBlockAccess access;
    public final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private boolean valid;

    private BakedEntityContext() {
    }

    // Pins the world and position for the calling thread until clear()
    public static void set(IBlockAccess access, BlockPos pos) {
        BakedEntityContext context = CURRENT.get();
        context.access = access;
        context.pos.setPos(pos);
        context.valid = true;
    }

    public static void clear() {
        BakedEntityContext context = CURRENT.get();
        context.access = null;
        context.valid = false;
    }

    // The pinned context, or null when the model is asked outside a world render (item, GUI, debug)
    public static BakedEntityContext get() {
        BakedEntityContext context = CURRENT.get();
        return context.valid ? context : null;
    }
}
