package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.engine.FluidLightBridge;
import com.bdmajora.fulgor.async.engine.LightInfo;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Takes over the vanilla chunk lighting bookkeeping that would otherwise write around the async engine: heightmaps stay vanilla's job, every light write is the engine's
@Mixin(Chunk.class)
public abstract class ChunkLightingMixin {
    @Shadow
    @Final
    public int x;

    @Shadow
    @Final
    public int z;

    @Shadow
    private boolean isLightPopulated;

    @Shadow
    private boolean isTerrainPopulated;

    @Shadow
    @Final
    private int[] heightMap;

    @Shadow
    @Final
    private int[] precipitationHeightMap;

    @Shadow
    private int heightMapMinimum;

    @Shadow
    @Final
    private World world;

    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    @Shadow
    public abstract int getTopFilledSegment();

    @Shadow
    public abstract IBlockState getBlockState(int x, int y, int z);

    @Shadow
    public abstract void markDirty();

    @Unique
    private final BlockPos.MutableBlockPos fulgor$lookupPos = new BlockPos.MutableBlockPos();

    // Rebuilds the heightmap and, for a chunk not yet lit, a scalar skylight baseline; the engine owns the real pass, and repainting a lit chunk would overwrite what it propagated
    @Inject(method = "generateSkylightMap", at = @At("HEAD"), cancellable = true)
    private void fulgor$generateSkylightMap(CallbackInfo ci) {
        int topSegment = getTopFilledSegment();
        this.heightMapMinimum = Integer.MAX_VALUE;

        for (int localX = 0; localX < 16; ++localX) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                this.precipitationHeightMap[localX + (localZ << 4)] = -999;
                this.heightMap[localZ << 4 | localX] = 0;

                for (int y = topSegment + 16; y > 0; --y) {
                    if (fulgor$opacityAt(localX, y - 1, localZ) != 0) {
                        this.heightMap[localZ << 4 | localX] = y;
                        if (y < this.heightMapMinimum) {
                            this.heightMapMinimum = y;
                        }
                        break;
                    }
                }
            }
        }

        if (this.world.provider.hasSkyLight() && !((AsyncLitChunk) (Object) this).fulgor$isLightReady()) {
            for (int localX = 0; localX < 16; ++localX) {
                for (int localZ = 0; localZ < 16; ++localZ) {
                    fulgor$fillVanillaSkyColumn(localX, localZ, topSegment);
                }
            }
        }

        this.isLightPopulated = true;
        markDirty();
        ci.cancel();
    }

    // Vanilla's heightmap update minus its skylight writes; the column's light is the engine's to redo when the block change reaches it
    @Inject(method = "relightBlock", at = @At("HEAD"), cancellable = true)
    private void fulgor$relightBlock(int x, int y, int z, CallbackInfo ci) {
        ci.cancel();
        int oldHeight = this.heightMap[z << 4 | x];
        int newHeight = Math.max(oldHeight, y);
        while (newHeight > 0 && fulgor$opacityAt(x, newHeight - 1, z) == 0) {
            --newHeight;
        }
        if (newHeight == oldHeight) {
            return;
        }

        this.world.markBlocksDirtyVertical(x + (this.x << 4), z + (this.z << 4), newHeight, oldHeight);
        this.heightMap[z << 4 | x] = newHeight;
        if (newHeight < this.heightMapMinimum) {
            this.heightMapMinimum = newHeight;
        } else {
            int minimum = Integer.MAX_VALUE;
            for (int height : this.heightMap) {
                if (height < minimum) {
                    minimum = height;
                }
            }
            this.heightMapMinimum = minimum;
        }
        markDirty();
    }

    // Vanilla opacity with Forge's contextual value and, when present, the fluidlogged layer
    @Unique
    private int fulgor$opacityAt(int x, int y, int z) {
        IBlockState state = getBlockState(x, y, z);
        int info = LightInfo.of(state);
        if (LightInfo.hasContextualValues(info)) {
            info = LightInfo.resolveContextual(info, state, this.world, this.fulgor$lookupPos, (this.x << 4) + x, y, (this.z << 4) + z);
        }
        int opacity = LightInfo.opacity(info);
        if (!Fulgor.hasFluidloggedApi()) {
            return opacity;
        }
        return FluidLightBridge.maxOpacityAt((Chunk) (Object) this, opacity, x, y, z, this.fulgor$lookupPos);
    }

    // One column of the pre-pass baseline, top down
    @Unique
    private void fulgor$fillVanillaSkyColumn(int x, int z, int topSegment) {
        ExtendedBlockStorage[] storageArrays = getBlockStorageArray();
        int skyLevel = 15;
        for (int y = topSegment + 15; y >= 0; --y) {
            ExtendedBlockStorage section = storageArrays[y >> 4];
            if (section == null) {
                if (skyLevel != 15) {
                    skyLevel = Math.max(0, skyLevel - 1);
                }
                continue;
            }
            int opacity = fulgor$opacityAt(x, y, z);
            if (opacity == 0 && skyLevel != 15) {
                opacity = 1;
            }
            skyLevel = Math.max(0, skyLevel - opacity);
            NibbleArray skyArray = section.getSkyLight();
            if (skyArray != null) {
                skyArray.set(x, y & 15, z, skyLevel);
            }
            if (skyLevel <= 0) {
                break;
            }
        }
    }

    // The engine's edge reconciliation replaces vanilla's gap checks
    @Inject(method = "recheckGaps", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipRecheckGaps(boolean onlyOne, CallbackInfo ci) {
        ci.cancel();
    }

    // Only ever flagged a column for recheckGaps, which no longer runs
    @Inject(method = "propagateSkylightOcclusion", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipSkylightOcclusion(int x, int z, CallbackInfo ci) {
        ci.cancel();
    }

    // Vanilla's round-robin relight sweep, redundant against a queue that already covers every change
    @Inject(method = "enqueueRelightChecks", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipRelightChecks(CallbackInfo ci) {
        ci.cancel();
    }

    // Vanilla's method also marks terrain and light population complete, which must survive its bypass
    @Inject(method = "checkLight()V", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipCheckLight(CallbackInfo ci) {
        this.isTerrainPopulated = true;
        this.isLightPopulated = true;
        ci.cancel();
    }
}
