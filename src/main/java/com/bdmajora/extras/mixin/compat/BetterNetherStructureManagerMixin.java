package com.bdmajora.extras.mixin.compat;

import com.bdmajora.extras.compat.BetterNetherSaves;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BetterNether 1.12.2's city generator; pseudo with require = 0 so a missing BN, or a build that moved these methods, changes nothing.
@Pseudo
@Mixin(targets = "paulevs.betternether.structures.big.StructureManager", remap = false)
public abstract class BetterNetherStructureManagerMixin {
    // generate is the only method that changes what save writes.
    @Inject(method = "generate(Lnet/minecraft/world/World;II)V", at = @At("HEAD"), require = 0, remap = false)
    private void impetus$markDirty(World world, int chunkX, int chunkZ, CallbackInfo ci) {
        BetterNetherSaves.markDirty(this);
    }

    // Skips an unchanged save and moves a changed one off the server thread.
    @Inject(method = "save(Lnet/minecraft/world/World;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void impetus$saveOffThread(World world, CallbackInfo ci) {
        if (BetterNetherSaves.save(this, world)) {
            ci.cancel();
        }
    }

    // A rejoin reloads the file; the previous session's write must be on disk first.
    @Inject(method = "load(Lnet/minecraft/world/World;)V", at = @At("HEAD"), require = 0, remap = false)
    private void impetus$finishWritesBeforeLoad(World world, CallbackInfo ci) {
        BetterNetherSaves.flush();
    }
}
