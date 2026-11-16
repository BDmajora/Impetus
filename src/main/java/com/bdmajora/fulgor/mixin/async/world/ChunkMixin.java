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

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkMixin implements AsyncLitChunk {
    // Reference to the field x of the target.
    @Shadow
    @Final
    public int x;

    // Reference to the field z of the target.
    @Shadow
    @Final
    public int z;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Reference to the field is light populated of the target.
    @Shadow
    private boolean isLightPopulated;

    // Reference to the method get block storage array of the target.
    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    // Field block nibbles added to the target.
    @Unique
    private volatile SWMRNibbleArray[] fulgor$blockNibbles;

    // Field sky nibbles added to the target.
    @Unique
    private volatile SWMRNibbleArray[] fulgor$skyNibbles;

    // Field block emptiness map added to the target.
    @Unique
    private boolean[] fulgor$blockEmptinessMap;

    // Field sky emptiness map added to the target.
    @Unique
    private boolean[] fulgor$skyEmptinessMap;

    // Field light ready added to the target.
    @Unique
    private volatile boolean fulgor$lightReady;

    // Field light usable added to the target.
    @Unique
    private volatile boolean fulgor$lightUsable;

    // Field saved light valid added to the target.
    @Unique
    private volatile boolean fulgor$savedLightValid;

    // Runs before every return of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void fulgor$allocateNibbles(World world, int x, int z, CallbackInfo ci) {
        this.fulgor$blockNibbles = ChunkLightHelper.newNullNibbles();
        this.fulgor$skyNibbles = ChunkLightHelper.newNullNibbles();
    }

    // Runs at the start of getLightFor and can cancel it.
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

    // Runs at the start of onLoad.
    @Inject(method = "onLoad", at = @At("HEAD"))
    private void fulgor$onLoad(CallbackInfo ci) {
        Chunk self = (Chunk) (Object) this;

        if (!this.fulgor$savedLightValid) {
            ChunkLightHelper.importVanillaBlock(this.fulgor$blockNibbles, getBlockStorageArray());
            if (this.world.provider.hasSkyLight()) {
                ChunkLightHelper.importVanillaSky(this.fulgor$skyNibbles, getBlockStorageArray());
            }
        }

        this.isLightPopulated = true;

        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        manager.registerChunk(self);

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

    // Runs at the start of onUnload.
    @Inject(method = "onUnload", at = @At("HEAD"))
    private void fulgor$onUnload(CallbackInfo ci) {
        WorldLightManager manager = ((AsyncLitWorld) this.world).fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        if (!this.world.isRemote) {
            boolean workFinished = manager.awaitPendingWork(this.x, this.z);
            if (!workFinished || manager.hasPendingLightWork(this.x, this.z)) {
                this.fulgor$lightReady = false;
            }
        }
        manager.removeChunkFromQueues(this.x, this.z);
        manager.unregisterChunk(this.x, this.z);
    }

    // Runs before every return of read.
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

    // Returns whether light ready.
    @Override
    public boolean fulgor$isLightReady() {
        return this.fulgor$lightReady;
    }

    // Handles set light ready.
    @Override
    public void fulgor$setLightReady(boolean ready) {
        this.fulgor$lightReady = ready;
    }

    // Returns whether light usable.
    @Override
    public boolean fulgor$isLightUsable() {
        return this.fulgor$lightReady || this.fulgor$lightUsable;
    }

    // Handles set light usable.
    @Override
    public void fulgor$setLightUsable(boolean usable) {
        this.fulgor$lightUsable = usable;
    }

    // Handles set saved light valid.
    @Override
    public void fulgor$setSavedLightValid(boolean valid) {
        this.fulgor$savedLightValid = valid;
    }

    // Returns whether saved light valid.
    @Override
    public boolean fulgor$hasSavedLightValid() {
        return this.fulgor$savedLightValid;
    }

    // Handles sync light to vanilla.
    @Override
    public void fulgor$syncLightToVanilla() {
        ChunkLightHelper.syncBlockToVanilla(this.fulgor$blockNibbles, getBlockStorageArray());
        if (this.world.provider.hasSkyLight()) {
            ChunkLightHelper.syncSkyToVanilla(this.fulgor$skyNibbles, getBlockStorageArray());
        }
    }

    // Returns the block nibbles.
    @Override
    public SWMRNibbleArray[] fulgor$getBlockNibbles() {
        return this.fulgor$blockNibbles;
    }

    // Handles set block nibbles.
    @Override
    public void fulgor$setBlockNibbles(SWMRNibbleArray[] nibbles) {
        this.fulgor$blockNibbles = nibbles;
    }

    // Returns the block emptiness map.
    @Override
    public boolean[] fulgor$getBlockEmptinessMap() {
        return this.fulgor$blockEmptinessMap;
    }

    // Handles set block emptiness map.
    @Override
    public void fulgor$setBlockEmptinessMap(boolean[] emptinessMap) {
        this.fulgor$blockEmptinessMap = emptinessMap;
    }

    // Returns the sky nibbles.
    @Override
    public SWMRNibbleArray[] fulgor$getSkyNibbles() {
        return this.fulgor$skyNibbles;
    }

    // Handles set sky nibbles.
    @Override
    public void fulgor$setSkyNibbles(SWMRNibbleArray[] nibbles) {
        this.fulgor$skyNibbles = nibbles;
    }

    // Returns the sky emptiness map.
    @Override
    public boolean[] fulgor$getSkyEmptinessMap() {
        return this.fulgor$skyEmptinessMap;
    }

    // Handles set sky emptiness map.
    @Override
    public void fulgor$setSkyEmptinessMap(boolean[] emptinessMap) {
        this.fulgor$skyEmptinessMap = emptinessMap;
    }
}
