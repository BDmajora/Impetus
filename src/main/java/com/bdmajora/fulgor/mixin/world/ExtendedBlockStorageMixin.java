package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.api.SectionLightInfo;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ExtendedBlockStorage.
@Mixin(ExtendedBlockStorage.class)
public abstract class ExtendedBlockStorageMixin implements SectionLightInfo {
    // Reference to the field block ref count of the target.
    @Shadow
    private int blockRefCount;

    // Reference to the field block light of the target.
    @Shadow
    private NibbleArray blockLight;

    // Reference to the field sky light of the target.
    @Shadow
    private NibbleArray skyLight;

    // Whether the light is uniform (0) or not (1) or unknown (-1).
    @Unique
    private volatile int fulgor$lightRefCount = -1;

    // Forgets the cached answer when the block light array is handed out.
    @Inject(method = "getBlockLight()Lnet/minecraft/world/chunk/NibbleArray;", at = @At("HEAD"))
    private void fulgor$forgetOnBlockLightAccess(CallbackInfoReturnable<NibbleArray> cir) {
        this.fulgor$lightRefCount = -1;
    }

    // Forgets the cached answer when the sky light array is handed out.
    @Inject(method = "getSkyLight()Lnet/minecraft/world/chunk/NibbleArray;", at = @At("HEAD"))
    private void fulgor$forgetOnSkyLightAccess(CallbackInfoReturnable<NibbleArray> cir) {
        this.fulgor$lightRefCount = -1;
    }

    // Replaces the whole method setSkyLight.
    @Overwrite
    public void setSkyLight(int x, int y, int z, int value) {
        this.skyLight.set(x, y, z, value);
        this.fulgor$lightRefCount = -1;
    }

    // Replaces the whole method setBlockLight.
    @Overwrite
    public void setBlockLight(int x, int y, int z, int value) {
        this.blockLight.set(x, y, z, value);
        this.fulgor$lightRefCount = -1;
    }

    // Replaces the whole method setSkyLight.
    @Overwrite
    public void setSkyLight(NibbleArray array) {
        this.skyLight = array;
        this.fulgor$lightRefCount = -1;
    }

    // Replaces the whole method setBlockLight.
    @Overwrite
    public void setBlockLight(NibbleArray array) {
        this.blockLight = array;
        this.fulgor$lightRefCount = -1;
    }

    // Replaces the whole method isEmpty.
    @Overwrite
    public boolean isEmpty() {
        if (this.blockRefCount != 0) {
            return false;
        }

        int cached = this.fulgor$lightRefCount;
        if (cached == -1) {
            cached = fulgor$isUniform(this.skyLight, (byte) 0xFF) && fulgor$isUniform(this.blockLight, (byte) 0x00) ? 0 : 1;
            this.fulgor$lightRefCount = cached;
        }

        return cached == 0;
    }

    // Returns whether the section holds no blocks.
    @Override
    public boolean fulgor$hasNoBlocks() {
        return this.blockRefCount == 0;
    }

    // Drops the cached answer after a light engine finished writing the arrays from another thread.
    @Override
    public void fulgor$markLightChanged() {
        this.fulgor$lightRefCount = -1;
    }

    // Returns whether every byte of a light array has one value and true for a missing array.
    @Unique
    private static boolean fulgor$isUniform(NibbleArray array, byte value) {
        if (array == null) {
            return true;
        }

        for (byte b : array.getData()) {
            if (b != value) {
                return false;
            }
        }

        return true;
    }
}
