package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorRenderBridge;
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

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkLightingMixin {
    // Reference to the field x of the target.
    @Shadow
    @Final
    public int x;

    // Reference to the field z of the target.
    @Shadow
    @Final
    public int z;

    // Reference to the field is light populated of the target.
    @Shadow
    private boolean isLightPopulated;

    // Reference to the field is terrain populated of the target.
    @Shadow
    private boolean isTerrainPopulated;

    // Reference to the field height map of the target.
    @Shadow
    @Final
    private int[] heightMap;

    // Reference to the field precipitation height map of the target.
    @Shadow
    @Final
    private int[] precipitationHeightMap;

    // Reference to the field height map minimum of the target.
    @Shadow
    private int heightMapMinimum;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Reference to the method get block storage array of the target.
    @Shadow
    public abstract ExtendedBlockStorage[] getBlockStorageArray();

    // Reference to the method get top filled segment of the target.
    @Shadow
    public abstract int getTopFilledSegment();

    // Reference to the method get block state of the target.
    @Shadow
    public abstract IBlockState getBlockState(int x, int y, int z);

    // Reference to the method mark dirty of the target.
    @Shadow
    public abstract void markDirty();

    // Position reused for contextual opacity lookups.
    @Unique
    private final BlockPos.MutableBlockPos fulgor$lookupPos = new BlockPos.MutableBlockPos();

    // Runs at the start of generateSkylightMap and can cancel it.
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

    // Runs at the start of relightBlock and can cancel it.
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

    // Returns the opacity at a position including a fluidlogged fluid.
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

    // Seeds a column's vanilla sky light down from the top until it is absorbed.
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
                FulgorRenderBridge.markLightChanged(section);
            }
            if (skyLevel <= 0) {
                break;
            }
        }
    }

    // Runs at the start of recheckGaps and can cancel it.
    @Inject(method = "recheckGaps", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipRecheckGaps(boolean onlyOne, CallbackInfo ci) {
        ci.cancel();
    }

    // Runs at the start of propagateSkylightOcclusion and can cancel it.
    @Inject(method = "propagateSkylightOcclusion", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipSkylightOcclusion(int x, int z, CallbackInfo ci) {
        ci.cancel();
    }

    // Runs at the start of enqueueRelightChecks and can cancel it.
    @Inject(method = "enqueueRelightChecks", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipRelightChecks(CallbackInfo ci) {
        ci.cancel();
    }

    // Runs at the start of checkLight and can cancel it.
    @Inject(method = "checkLight()V", at = @At("HEAD"), cancellable = true)
    private void fulgor$skipCheckLight(CallbackInfo ci) {
        this.isTerrainPopulated = true;
        this.isLightPopulated = true;
        ci.cancel();
    }
}
