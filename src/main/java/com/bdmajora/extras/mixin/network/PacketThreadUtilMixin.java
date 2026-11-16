package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.RetiredNetHandler;
import com.google.common.util.concurrent.ListenableFuture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.util.IThreadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes PacketThreadUtil.
@Mixin(PacketThreadUtil.class)
public abstract class PacketThreadUtilMixin {
    // Wraps the call of IThreadListener.addScheduledTask in checkThreadAndEnqueue.
    @WrapOperation(method = "checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IThreadListener;addScheduledTask(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"))
    private static ListenableFuture<Object> impetus$skipOnceRetired(IThreadListener scheduler, Runnable task, Operation<ListenableFuture<Object>> original,
                                                                    @Local(argsOnly = true) INetHandler handler) {
        if (handler instanceof RetiredNetHandler retired) {
            Runnable guarded = () -> {
                if (!retired.impetus$isRetired()) {
                    task.run();
                }
            };
            return original.call(scheduler, guarded);
        }
        return original.call(scheduler, task);
    }
}
