package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

// CTM brackets every quad operation with a thread-local profiler section, which costs more than the work it measures once models bake on the render and chunk threads; a real profiler measures CTM better. Applied only when CTM is present
@Pseudo
@Mixin(targets = "team/chisel/ctm/client/util/ProfileUtil", remap = false)
public abstract class CtmProfileUtilMixin {
    /**
     * @author embeddedt, bdmajora
     * @reason Profiling off
     */
    @Overwrite
    public static void start(String section) {
    }

    /**
     * @author embeddedt, bdmajora
     * @reason Profiling off
     */
    @Overwrite
    public static void end() {
    }

    /**
     * @author embeddedt, bdmajora
     * @reason Profiling off
     */
    @Overwrite
    public static void endAndStart(String section) {
    }
}
