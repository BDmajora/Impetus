package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.impl.world.WorldSaveTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes MinecraftServer.
@Mixin(MinecraftServer.class)
public class ServerSaveIndicatorMixin {
    // Records every world save for the autosave indicator.
    @WrapMethod(method = "saveAllWorlds")
    private void impetus$trackSave(boolean isSilent, Operation<Void> original) {
        WorldSaveTracker.duringSave(() -> original.call(isSilent));
    }
}
