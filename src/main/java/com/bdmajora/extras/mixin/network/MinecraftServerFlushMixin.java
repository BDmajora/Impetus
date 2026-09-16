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

// Brackets the server tick for FlushBatch and flushes every connection that deferred a write; the tick's own network step runs before the player list step, so this sits at the very end where keep-alives and tab list updates have been queued too
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerFlushMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void impetus$beginBatch(CallbackInfo ci) {
        FlushBatch.beginTick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void impetus$flushAll(CallbackInfo ci) {
        FlushBatch.endTick();
        NetworkSystem system = ((MinecraftServer) (Object) this).getNetworkSystem();
        if (system == null) {
            return;
        }
        List<NetworkManager> managers = ((NetworkSystemAccessor) system).impetus$networkManagers();
        // Vanilla guards the list with its own monitor during networkTick and connection accept
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
