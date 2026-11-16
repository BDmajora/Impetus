package com.bdmajora.equilibrium.mixin.collections.mob_spawning;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldEntitySpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

// Mixin that changes WorldEntitySpawner.
@Mixin(WorldEntitySpawner.class)
public class WorldEntitySpawnerMixin {
    // Reference to the field eligible chunks for spawning of the target.
    @Shadow
    @Final
    @Mutable
    private Set<ChunkPos> eligibleChunksForSpawning;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void equilibrium$useFastutilSet(CallbackInfo ci) {
        this.eligibleChunksForSpawning = new ObjectOpenHashSet<>();
    }
}
