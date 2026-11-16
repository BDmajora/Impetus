package com.bdmajora.dynamiclights.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Mixin that changes RenderGlobal.
@Mixin(RenderGlobal.class)
public interface RenderGlobalRebuildAccessor {
    // Calls the method mark blocks for update.
    @Invoker("markBlocksForUpdate")
    void impetus$markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     boolean updateImmediately);
}
