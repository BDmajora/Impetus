package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ConcurrentEntityList;
import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.ParallelWorld;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldParallelTickMixin implements ParallelWorld {
    // Reference to the field loaded entity list of the target.
    @Shadow
    @Final
    @Mutable
    public List<Entity> loadedEntityList;

    // Reference to the field player entities of the target.
    @Shadow
    @Final
    @Mutable
    public List<EntityPlayer> playerEntities;

    // Reference to the field is remote of the target.
    @Shadow
    @Final
    public boolean isRemote;

    // Reference to the method on entity removed of the target.
    @Shadow
    public abstract void onEntityRemoved(Entity entity);

    // Reference to the method is chunk loaded of the target.
    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    // Reference to the method get chunk of the target.
    @Shadow
    public abstract Chunk getChunk(int chunkX, int chunkZ);

    // Whether the world ticks entities in parallel.
    @Unique
    private boolean impetus$parallel;

    // Entities collected this tick for the worker threads.
    @Unique
    private List<Entity> impetus$asyncBatch;

    // Entities collected this tick for the main thread.
    @Unique
    private List<Entity> impetus$syncBatch;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void impetus$installConcurrentLists(CallbackInfo ci) {
        if (!ParallelProcessor.INSTALLED || this.isRemote) {
            return;
        }
        this.impetus$parallel = true;
        this.loadedEntityList = new ConcurrentEntityList(this.loadedEntityList);
        this.playerEntities = new CopyOnWriteArrayList<>(this.playerEntities);
    }

    // Returns whether the world ticks entities in parallel.
    @Override
    public boolean impetus$isParallel() {
        return this.impetus$parallel;
    }

    // Runs inside updateEntities.
    @Inject(method = "updateEntities", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=regular"))
    private void impetus$armBatch(CallbackInfo ci) {
        if (ParallelProcessor.isEntityTickingActive((World) (Object) this)) {
            this.impetus$asyncBatch = new ArrayList<>();
            this.impetus$syncBatch = new ArrayList<>();
        }
    }

    // Replaces the call of World.updateEntity in updateEntities.
    @Redirect(method = "updateEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;updateEntity(Lnet/minecraft/entity/Entity;)V"))
    private void impetus$collectOrTick(World world, Entity entity) {
        List<Entity> async = this.impetus$asyncBatch;
        if (async == null) {
            world.updateEntity(entity);
            return;
        }
        if (ParallelProcessor.shouldTickSynchronously(entity)) {
            this.impetus$syncBatch.add(entity);
        } else {
            async.add(entity);
        }
    }

    // Runs inside updateEntities.
    @Inject(method = "updateEntities", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockEntities"))
    private void impetus$runBatch(CallbackInfo ci) {
        List<Entity> async = this.impetus$asyncBatch;
        List<Entity> sync = this.impetus$syncBatch;
        if (async == null) {
            return;
        }
        this.impetus$asyncBatch = null;
        this.impetus$syncBatch = null;

        ParallelProcessor.tickEntities((World) (Object) this, async, sync);
        impetus$removeDead(async, sync);
    }

    // Removes entities that died during the batch.
    @Unique
    private void impetus$removeDead(List<Entity> async, List<Entity> sync) {
        Set<Entity> dead = null;
        for (int pass = 0; pass < 2; pass++) {
            for (Entity entity : pass == 0 ? async : sync) {
                if (entity.isDead) {
                    if (dead == null) {
                        dead = new ReferenceOpenHashSet<>();
                    }
                    dead.add(entity);
                }
            }
        }
        if (dead == null) {
            return;
        }

        Set<Entity> loaded = new ReferenceOpenHashSet<>(((ConcurrentEntityList) this.loadedEntityList).snapshot());
        dead.removeIf(entity -> !loaded.contains(entity));
        if (dead.isEmpty()) {
            return;
        }

        for (Entity entity : dead) {
            int chunkX = entity.chunkCoordX;
            int chunkZ = entity.chunkCoordZ;
            if (entity.addedToChunk && this.isChunkLoaded(chunkX, chunkZ, true)) {
                this.getChunk(chunkX, chunkZ).removeEntity(entity);
            }
        }
        this.loadedEntityList.removeAll(dead);
        for (Entity entity : dead) {
            this.onEntityRemoved(entity);
        }
    }
}
