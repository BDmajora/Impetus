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

// Mixin that changes ChunkProviderServer.
@Mixin(ChunkProviderServer.class)
public abstract class ChunkProviderServerParallelMixin {
    // Reference to the field loaded chunks of the target.
    @Shadow
    @Final
    @Mutable
    public Long2ObjectMap<Chunk> loadedChunks;

    // Reference to the field world of the target.
    @Shadow
    @Final
    public WorldServer world;

    // Reference to the method get loaded chunk of the target.
    @Shadow
    public abstract Chunk getLoadedChunk(int x, int z);

    // Whether the provider serves a parallel ticked world.
    @Unique
    private boolean impetus$parallel;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$installConcurrentMap(CallbackInfo ci) {
        if (!ParallelProcessor.INSTALLED) {
            return;
        }
        this.impetus$parallel = true;
        this.loadedChunks = new ConcurrentLong2ObjectMap<>(this.loadedChunks);
    }

    // Returns a loaded chunk without locking or loads it under the world and worldgen locks.
    @WrapMethod(method = "provideChunk")
    private Chunk impetus$lockedProvideChunk(int x, int z, Operation<Chunk> original) {
        if (!this.impetus$parallel) {
            return original.call(x, z);
        }
        Chunk loaded = this.getLoadedChunk(x, z);
        if (loaded != null) {
            return loaded;
        }
        synchronized (this.world) {
            synchronized (ParallelProcessor.WORLDGEN_LOCK) {
                return original.call(x, z);
            }
        }
    }

    // Returns a loaded chunk without locking or loads it under the world lock.
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

    // Queues a chunk for unload under the world lock.
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

    // Queues every chunk for unload under the world lock.
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

    // Unloads queued chunks under the world lock.
    @WrapMethod(method = "tick")
    private boolean impetus$lockedTick(Operation<Boolean> original) {
        if (!this.impetus$parallel) {
            return original.call();
        }
        synchronized (this.world) {
            return original.call();
        }
    }

    // Saves chunks under the world lock.
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
