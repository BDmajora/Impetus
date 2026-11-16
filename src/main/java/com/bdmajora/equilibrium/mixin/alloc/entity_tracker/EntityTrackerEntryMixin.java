package com.bdmajora.equilibrium.mixin.alloc.entity_tracker;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

// Mixin that changes EntityTrackerEntry.
@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    // Reference to the field tracking players of the target.
    @Shadow
    @Final
    @Mutable
    public Set<EntityPlayerMP> trackingPlayers;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$useFastutilSet(CallbackInfo ci) {
        this.trackingPlayers = new ReferenceOpenHashSet<>();
    }
}
