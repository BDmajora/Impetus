package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelWorkerThread;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

// Mixin that changes Profiler.
@Mixin(Profiler.class)
public abstract class ProfilerParallelMixin {
    // Runs at the start of startSection and can cancel it.
    @Inject(method = "startSection(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStartOffThread(String name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    // Runs at the start of func_194340_a and can cancel it.
    @Inject(method = "func_194340_a", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStartSupplierOffThread(Supplier<String> name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    // Runs at the start of endSection and can cancel it.
    @Inject(method = "endSection", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndOffThread(CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    // Runs at the start of endStartSection and can cancel it.
    @Inject(method = "endStartSection", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndStartOffThread(String name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    // Runs at the start of func_194339_b and can cancel it.
    @Inject(method = "func_194339_b", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndStartSupplierOffThread(Supplier<String> name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }
}
