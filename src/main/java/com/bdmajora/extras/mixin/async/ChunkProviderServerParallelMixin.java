package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ConcurrentLong2ObjectMap;
import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The chunk table read from every thread and written from a few: lookups go to a concurrent map with no lock, and the load/generate/unload paths serialise on the world monitor so a worker that walks into unloaded terrain generates it alone, with the generator's shared scratch state safe
@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerParallelMixin {
    @Shadow
    @Final
    @Mutable
    public Long2ObjectMap<Chunk> loadedChunks;

    @Shadow
    @Final
    public WorldServer world;

    @Shadow
    public abstract Chunk getLoadedChunk(int x, int z);

    @Unique
    private boolean impetus$parallel;

    // Only server worlds own a ChunkProviderServer, so the install flag alone decides
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$installConcurrentMap(CallbackInfo ci) {
        if (!ParallelProcessor.INSTALLED) {
            return;
        }
        this.impetus$parallel = true;
        this.loadedChunks = new ConcurrentLong2ObjectMap<>(this.loadedChunks);
    }

    // The loaded case, which is nearly every call, never touches the lock
    @WrapMethod(method = "provideChunk")
    private Chunk impetus$lockedProvideChunk(int x, int z, Operation<Chunk> original) {
        if (!this.impetus$parallel) {
            return original.call(x, z);
        }
        Chunk loaded = this.getLoadedChunk(x, z);
        if (loaded != null) {
            return loaded;
        }
        // The generator's noise buffers and the biome layer scratch pool are shared across dimensions, so generation is single-file across every world, not just this one; the world monitor comes first to keep the order every other path uses
        synchronized (this.world) {
            synchronized (ParallelProcessor.WORLDGEN_LOCK) {
                return original.call(x, z);
            }
        }
    }

    @WrapMethod(method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;")
    private Chunk impetus$lockedLoadChunk(int x, int z, Runnable callback, Operation<Chunk> original) {
        if (!this.impetus$parallel) {
            return original.call(x, z, callback);
        }
        Chunk loaded = this.getLoadedChunk(x, z);
        if (loaded != null) {
            if (callback != null) {
                callback.run();
            }
            return loaded;
        }
        synchronized (this.world) {
            return original.call(x, z, callback);
        }
    }

    @WrapMethod(method = "queueUnload")
    private void impetus$lockedQueueUnload(Chunk chunk, Operation<Void> original) {
        if (!this.impetus$parallel) {
            original.call(chunk);
            return;
        }
        synchronized (this.world) {
            original.call(chunk);
        }
    }

    @WrapMethod(method = "queueUnloadAll")
    private void impetus$lockedQueueUnloadAll(Operation<Void> original) {
        if (!this.impetus$parallel) {
            original.call();
            return;
        }
        synchronized (this.world) {
            original.call();
        }
    }

    @WrapMethod(method = "tick")
    private boolean impetus$lockedTick(Operation<Boolean> original) {
        if (!this.impetus$parallel) {
            return original.call();
        }
        synchronized (this.world) {
            return original.call();
        }
    }

    @WrapMethod(method = "saveChunks")
    private boolean impetus$lockedSaveChunks(boolean all, Operation<Boolean> original) {
        if (!this.impetus$parallel) {
            return original.call(all);
        }
        synchronized (this.world) {
            return original.call(all);
        }
    }
}
