package com.bdmajora.equilibrium.mixin.world.raycast;

import com.bdmajora.equilibrium.common.world.FastRayCaster;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import javax.annotation.Nullable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin {
    // Replaces the whole method rayTraceBlocks.
    @Nullable
    @Overwrite
    public RayTraceResult rayTraceBlocks(Vec3d start, Vec3d end, boolean stopOnLiquid,
                                         // Flag that skips blocks without a bounding box.
                                         boolean ignoreBlockWithoutBoundingBox,
                                         boolean returnLastUncollidableBlock) {
        return FastRayCaster.rayTraceBlocks((World) (Object) this, start, end, stopOnLiquid,
                ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
    }
}
