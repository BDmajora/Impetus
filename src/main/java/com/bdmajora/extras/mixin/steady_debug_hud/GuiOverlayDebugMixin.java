package com.bdmajora.extras.mixin.steady_debug_hud;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes GuiOverlayDebug.
@Mixin(GuiOverlayDebug.class)
public abstract class GuiOverlayDebugMixin {
    // Field left cache added to the target.
    @Unique
    private final java.util.List<String> impetus$leftCache = new java.util.ArrayList<>();
    // Field right cache added to the target.
    @Unique
    private final java.util.List<String> impetus$rightCache = new java.util.ArrayList<>();
    // Field next update nanos added to the target.
    @Unique
    private long impetus$nextUpdateNanos;
    // Field rebuild added to the target.
    @Unique
    private boolean impetus$rebuild = true;

    // Runs at the start of renderDebugInfo.
    @Inject(method = "renderDebugInfo", at = @At("HEAD"))
    private void impetus$decideRebuild(ScaledResolution resolution, CallbackInfo ci) {
        ExtrasConfig.ExtraSettings settings = Extras.options().extra;

        if (!settings.steadyDebugHud) {
            this.impetus$rebuild = true;
            return;
        }

        long now = System.nanoTime();
        if (now >= this.impetus$nextUpdateNanos) {
            this.impetus$rebuild = true;
            this.impetus$nextUpdateNanos = now + settings.steadyDebugHudRefreshInterval * 50_000_000L;
        } else {
            this.impetus$rebuild = false;
        }
    }

    // Runs at the start of call and can cancel it.
    @Inject(method = "call()Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void impetus$leftFromCache(CallbackInfoReturnable<java.util.List<String>> cir) {
        if (!this.impetus$rebuild) {
            cir.setReturnValue(new java.util.ArrayList<>(this.impetus$leftCache));
        }
    }

    // Runs before every return of call.
    @Inject(method = "call()Ljava/util/List;", at = @At("RETURN"))
    private void impetus$cacheLeft(CallbackInfoReturnable<java.util.List<String>> cir) {
        if (this.impetus$rebuild) {
            this.impetus$leftCache.clear();
            this.impetus$leftCache.addAll(cir.getReturnValue());
        }
    }

    // Runs at the start of getDebugInfoRight and can cancel it.
    @Inject(method = "getDebugInfoRight()Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void impetus$rightFromCache(CallbackInfoReturnable<java.util.List<String>> cir) {
        if (!this.impetus$rebuild) {
            cir.setReturnValue(new java.util.ArrayList<>(this.impetus$rightCache));
        }
    }

    // Runs before every return of getDebugInfoRight.
    @Inject(method = "getDebugInfoRight()Ljava/util/List;", at = @At("RETURN"))
    private void impetus$cacheRight(CallbackInfoReturnable<java.util.List<String>> cir) {
        if (this.impetus$rebuild) {
            this.impetus$rightCache.clear();
            this.impetus$rightCache.addAll(cir.getReturnValue());
        }
    }
}
