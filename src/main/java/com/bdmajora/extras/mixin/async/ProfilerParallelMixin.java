package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelWorkerThread;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

// The profiler is a single section stack owned by the server thread; a worker pushing into it would interleave with the main thread's sections, so worker calls are dropped at the door
@Mixin(Profiler.class)
public abstract class ProfilerParallelMixin {
    @Inject(method = "startSection(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStartOffThread(String name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    @Inject(method = "func_194340_a", at = @At("HEAD"), cancellable = true)
    private void impetus$skipStartSupplierOffThread(Supplier<String> name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    @Inject(method = "endSection", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndOffThread(CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    @Inject(method = "endStartSection", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndStartOffThread(String name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }

    @Inject(method = "func_194339_b", at = @At("HEAD"), cancellable = true)
    private void impetus$skipEndStartSupplierOffThread(Supplier<String> name, CallbackInfo ci) {
        if (ParallelWorkerThread.isCurrent()) {
            ci.cancel();
        }
    }
}
