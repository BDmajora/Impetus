package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import com.bdmajora.fulgor.async.WorldLightManager;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The chunk's async light storage and lifecycle: SWMR nibbles per lane, registration with the world's manager on load, and getLightFor answered from the visible side once the engine owns usable light
@Mixin(Chunk.class)
public abstract class ChunkMixin implements AsyncLitChunk {
    @Shadow
    @Final
    public int x;

    @Shadow
    @Final
    public int z;

    @Shadow
    @Final
    private World world;

    @Shadow
    private boolean isLightPopulated;

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    @Unique
    private volatile SWMRNibbleArray[] fulgor$blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] fulgor$skyNibbles;

    @Unique
    private boolean[] fulgor$blockEmptinessMap;

    @Unique
    private boolean[] fulgor$skyEmptinessMap;

    @Unique
    private volatile boolean fulgor$lightReady;

    @Unique
    private volatile boolean fulgor$lightUsable;

    @Unique
    private volatile boolean fulgor$savedLightValid;

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void fulgor$allocateNibbles(World world, int x, int z, CallbackInfo ci) {
        this.fulgor$blockNibbles = ChunkLightHelper.newNullNibbles();
        this.fulgor$skyNibbles = ChunkLightHelper.newNullNibbles();
    }

    // Empty sections have no vanilla storage but the engine still holds their values, which is what stops a carved-out cave reading as open sky
    @Inject(method = "getLightFor", at = @At("HEAD"), cancellable = true)
    private void fulgor$getLightFor(EnumSkyBlock lightType, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!fulgor$isLightUsable()) {
            return;
        }
        if (lightType == EnumSkyBlock.SKY) {
            cir.setReturnValue(this.world.provider.hasSkyLight()
                    ? ChunkLightHelper.getSkyLight(this.fulgor$skyNibbles, pos.getX(), pos.getY(), pos.getZ())
                    : 0);
            return;
        }
        cir.setReturnValue(ChunkLightHelper.getBlockLight(this.fulgor$blockNibbles, pos.getX(), pos.getY(), pos.getZ()));
    }

    // Imports or restores the light storage, registers the chunk and queues the server's initial pass (or the cheap init for restored light)
    @Inject(method = "onLoad", at = @At("HEAD"))
    private void fulgor$onLoad(CallbackInfo ci) {
        Chunk self = (Chunk) (Object) this;

        // Restored nibbles carry boundary sections and state markers vanilla storage cannot reproduce, so they are only imported when nothing was restored
        if (!this.fulgor$savedLightValid) {
            ChunkLightHelper.importVanillaBlock(this.fulgor$blockNibbles, getBlockStorageArray());
            if (this.world.provider.hasSkyLight()) {
                ChunkLightHelper.importVanillaSky(this.fulgor$skyNibbles, getBlockStorageArray());
            }
        }

        // PlayerChunkMapEntry is the real readiness gate; vanilla must not refuse the chunk before the async pass has even started
        this.isLightPopulated = true;

        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        manager.registerChunk(self);

        // The client's onLoad precedes packet deserialisation; read() does the import and queueing once storage exists
        if (this.world.isRemote) {
            return;
        }

        Boolean[] emptySections = BfsLightEngine.getEmptySectionsForChunk(self);
        if (this.fulgor$savedLightValid) {
            fulgor$syncLightToVanilla();
            this.fulgor$lightReady = true;
            manager.queueChunkLoadInit(this.x, this.z, self, emptySections);
        } else {
            manager.queueChunkLight(this.x, this.z, self, emptySections);
        }
    }

    // Waits for in-flight work before dropping the chunk's queue entries, in that order, or awaitPendingWork is a no-op while a worker may still be writing
    @Inject(method = "onUnload", at = @At("HEAD"))
    private void fulgor$onUnload(CallbackInfo ci) {
        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        if (!this.world.isRemote) {
            boolean workFinished = manager.awaitPendingWork(this.x, this.z);
            if (!workFinished || manager.hasPendingLightWork(this.x, this.z)) {
                // Readiness off makes the save omit the tag, so the chunk relights rather than keeping a partial snapshot
                this.fulgor$lightReady = false;
            }
        }
        manager.removeChunkFromQueues(this.x, this.z);
        manager.unregisterChunk(this.x, this.z);
    }

    // The client wraps the packet's nibbles without copying and queues only the cheap cache init, since the server already lit the chunk
    @Inject(method = "read", at = @At("RETURN"), require = 0)
    private void fulgor$onRead(PacketBuffer buf, int availableSections, boolean groundUpContinuous, CallbackInfo ci) {
        Chunk self = (Chunk) (Object) this;

        ChunkLightHelper.wrapVanillaBlock(this.fulgor$blockNibbles, getBlockStorageArray());
        if (this.world.provider.hasSkyLight()) {
            ChunkLightHelper.wrapVanillaSky(this.fulgor$skyNibbles, getBlockStorageArray());
        }

        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        manager.registerChunk(self);
        this.fulgor$lightReady = true;
        manager.queueChunkLoadInit(this.x, this.z, self, BfsLightEngine.getEmptySectionsForChunk(self));
    }

    @Override
    public boolean fulgor$isLightReady() {
        return this.fulgor$lightReady;
    }

    @Override
    public void fulgor$setLightReady(boolean ready) {
        this.fulgor$lightReady = ready;
    }

    @Override
    public boolean fulgor$isLightUsable() {
        return this.fulgor$lightReady || this.fulgor$lightUsable;
    }

    @Override
    public void fulgor$setLightUsable(boolean usable) {
        this.fulgor$lightUsable = usable;
    }

    @Override
    public void fulgor$setSavedLightValid(boolean valid) {
        this.fulgor$savedLightValid = valid;
    }

    @Override
    public boolean fulgor$hasSavedLightValid() {
        return this.fulgor$savedLightValid;
    }

    @Override
    public void fulgor$syncLightToVanilla() {
        ChunkLightHelper.syncBlockToVanilla(this.fulgor$blockNibbles, getBlockStorageArray());
        if (this.world.provider.hasSkyLight()) {
            ChunkLightHelper.syncSkyToVanilla(this.fulgor$skyNibbles, getBlockStorageArray());
        }
    }

    @Override
    public SWMRNibbleArray[] fulgor$getBlockNibbles() {
        return this.fulgor$blockNibbles;
    }

    @Override
    public void fulgor$setBlockNibbles(SWMRNibbleArray[] nibbles) {
        this.fulgor$blockNibbles = nibbles;
    }

    @Override
    public boolean[] fulgor$getBlockEmptinessMap() {
        return this.fulgor$blockEmptinessMap;
    }

    @Override
    public void fulgor$setBlockEmptinessMap(boolean[] emptinessMap) {
        this.fulgor$blockEmptinessMap = emptinessMap;
    }

    @Override
    public SWMRNibbleArray[] fulgor$getSkyNibbles() {
        return this.fulgor$skyNibbles;
    }

    @Override
    public void fulgor$setSkyNibbles(SWMRNibbleArray[] nibbles) {
        this.fulgor$skyNibbles = nibbles;
    }

    @Override
    public boolean[] fulgor$getSkyEmptinessMap() {
        return this.fulgor$skyEmptinessMap;
    }

    @Override
    public void fulgor$setSkyEmptinessMap(boolean[] emptinessMap) {
        this.fulgor$skyEmptinessMap = emptinessMap;
    }
}
