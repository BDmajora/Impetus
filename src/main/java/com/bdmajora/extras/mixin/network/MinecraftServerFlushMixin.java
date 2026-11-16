package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.FlushBatch;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerFlushMixin {
    // Runs at the start of tick.
    @Inject(method = "tick", at = @At("HEAD"))
    private void impetus$beginBatch(CallbackInfo ci) {
        FlushBatch.beginTick();
    }

    // Runs before every return of tick.
    @Inject(method = "tick", at = @At("RETURN"))
    private void impetus$flushAll(CallbackInfo ci) {
        FlushBatch.endTick();
        NetworkSystem system = ((MinecraftServer) (Object) this).getNetworkSystem();
        if (system == null) {
            return;
        }
        List<NetworkManager> managers = ((NetworkSystemAccessor) system).impetus$networkManagers();
        synchronized (managers) {
            for (int i = 0, n = managers.size(); i < n; i++) {
                NetworkManager manager = managers.get(i);
                if (!manager.hasNoChannel() && manager.isChannelOpen()) {
                    ((FlushBatch.Deferrable) manager).impetus$flushDeferred();
                }
            }
        }
    }
}
