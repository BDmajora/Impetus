package com.bdmajora.extras.mixin.steady_debug_hud;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiOverlayDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

// Mixin that caches the F3 overlay text.
@Mixin(GuiOverlayDebug.class)
public abstract class GuiOverlayDebugMixin {
    // Left column of the debug screen as last built.
    @Unique
    private final List<String> impetus$leftCache = new ArrayList<>();
    // Right column of the debug screen as last built.
    @Unique
    private final List<String> impetus$rightCache = new ArrayList<>();
    // Time of the next rebuild.
    @Unique
    private long impetus$nextUpdateNanos;
    // Whether this frame rebuilds the columns.
    @Unique
    private boolean impetus$rebuild = true;

    // Wraps the whole method so lines other mixins append inside call() (the dynamic light line) land in the cache too
    @WrapMethod(method = "call()Ljava/util/List;")
    private List<String> impetus$steadyLeft(Operation<List<String>> original) {
        ExtrasConfig.ExtraSettings settings = Extras.options().extra;
        if (!settings.steadyDebugHud) {
            // Rebuilds on the first frame after the switch turns back on.
            this.impetus$rebuild = true;
            this.impetus$nextUpdateNanos = 0L;
            return original.call();
        }
        long now = System.nanoTime();
        this.impetus$rebuild = now >= this.impetus$nextUpdateNanos || this.impetus$leftCache.isEmpty();
        if (!this.impetus$rebuild) {
            return new ArrayList<>(this.impetus$leftCache);
        }
        this.impetus$nextUpdateNanos = now + settings.steadyDebugHudRefreshInterval * 50_000_000L;
        List<String> lines = original.call();
        this.impetus$leftCache.clear();
        this.impetus$leftCache.addAll(lines);
        return lines;
    }

    // Returns the right column cached or rebuilt with the left column.
    @WrapMethod(method = "getDebugInfoRight()Ljava/util/List;")
    private List<String> impetus$steadyRight(Operation<List<String>> original) {
        if (!Extras.options().extra.steadyDebugHud) {
            return original.call();
        }
        if (!this.impetus$rebuild && !this.impetus$rightCache.isEmpty()) {
            return new ArrayList<>(this.impetus$rightCache);
        }
        List<String> lines = original.call();
        this.impetus$rightCache.clear();
        this.impetus$rightCache.addAll(lines);
        return lines;
    }
}
