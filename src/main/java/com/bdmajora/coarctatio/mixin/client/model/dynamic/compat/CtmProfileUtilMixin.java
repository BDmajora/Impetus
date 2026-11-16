package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

// Mixin that changes ProfileUtil.
@Pseudo
@Mixin(targets = "team/chisel/ctm/client/util/ProfileUtil", remap = false)
public abstract class CtmProfileUtilMixin {
    // Replaces the whole method start.
    @Overwrite
    public static void start(String section) {
    }

    // Replaces the whole method end.
    @Overwrite
    public static void end() {
    }

    // Replaces the whole method endAndStart.
    @Overwrite
    public static void endAndStart(String section) {
    }
}
