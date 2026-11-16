package com.bdmajora.equilibrium.mixin.world.tick_scheduler;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

// Mixin that changes WorldServer.
@Mixin(WorldServer.class)
public class WorldServerMixin {
    // Reference to the field pending tick list entries hash set of the target.
    @Shadow
    @Final
    @Mutable
    private Set<NextTickListEntry> pendingTickListEntriesHashSet;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$useFastutilSet(CallbackInfo ci) {
        this.pendingTickListEntriesHashSet = new ObjectOpenHashSet<>();
    }
}
