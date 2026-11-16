package com.bdmajora.coarctatio.mixin.world;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkMixin {
    // Runs before every return of setStorageArrays.
    @Inject(method = "setStorageArrays", at = @At("RETURN"))
    private void coarctatio$dropEmptySections(ExtendedBlockStorage[] sections, CallbackInfo ci) {
        if (sections == null) {
            return;
        }

        for (int i = 0; i < sections.length; i++) {
            ExtendedBlockStorage section = sections[i];

            if (section != null && section.isEmpty() && coarctatio$hasReproducibleLight(section)) {
                sections[i] = Chunk.NULL_BLOCK_STORAGE;
            }
        }
    }

    // Returns whether a section has light that can be recomputed.
    private static boolean coarctatio$hasReproducibleLight(ExtendedBlockStorage section) {
        if (!coarctatio$isUniform(section.getBlockLight(), 0)) {
            return false;
        }

        NibbleArray skyLight = section.getSkyLight();

        return skyLight == null
                || coarctatio$isUniform(skyLight, 0)
                || coarctatio$isUniform(skyLight, 15);
    }

    // Returns whether every value of an array equals one value.
    private static boolean coarctatio$isUniform(NibbleArray array, int value) {
        if (array == null) {
            return true;
        }

        byte expected = (byte) ((value & 0xF) | ((value & 0xF) << 4));

        for (byte b : array.getData()) {
            if (b != expected) {
                return false;
            }
        }

        return true;
    }
}
