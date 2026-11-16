package com.bdmajora.coarctatio.mixin.core;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes EntityDataManager.
@Mixin(EntityDataManager.class)
public abstract class EntityDataManagerMixin {
    @Mutable
    @Shadow
    @Final
    private Map<Integer, EntityDataManager.DataEntry<?>> entries;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactEntries(CallbackInfo ci) {
        this.entries = new Int2ObjectOpenHashMap<>();
    }
}
