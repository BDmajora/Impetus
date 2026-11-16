package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.api.SectionLightInfo;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

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

    // Field light ref count added to the target.
    @Unique
    private int fulgor$lightRefCount = -1;

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

        if (this.fulgor$lightRefCount == -1) {
            this.fulgor$lightRefCount = fulgor$isUniform(this.skyLight, (byte) 0xFF)
                    && fulgor$isUniform(this.blockLight, (byte) 0x00) ? 0 : 1;
        }

        return this.fulgor$lightRefCount == 0;
    }

    // Returns whether no blocks.
    @Override
    public boolean fulgor$hasNoBlocks() {
        return this.blockRefCount == 0;
    }

    // Helper method is uniform added to the target.
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
