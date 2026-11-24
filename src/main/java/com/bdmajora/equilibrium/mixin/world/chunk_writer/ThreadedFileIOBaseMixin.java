package com.bdmajora.equilibrium.mixin.world.chunk_writer;

import net.minecraft.world.storage.ThreadedFileIOBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes ThreadedFileIOBase.
@Mixin(ThreadedFileIOBase.class)
public abstract class ThreadedFileIOBaseMixin {
    // Vanilla rests 10 ms after every write, so at most 100 chunks are written per second while the server may unload 2000.
    // The rest wait in memory as complete NBT trees. The writer still rests whenever nothing is queued.
    @ModifyConstant(method = "processQueue", constant = @Constant(longValue = 10L), require = 0)
    private long equilibrium$noRestBetweenWrites(long vanilla) {
        return 0L;
    }
}
