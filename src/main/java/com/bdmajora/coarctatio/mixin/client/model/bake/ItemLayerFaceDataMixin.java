package com.bdmajora.coarctatio.mixin.client.model.bake;

import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;

// The per-face edge bitmaps of a generated item model as one int array instead of four BitSets in an EnumMap, read once per pixel per face while the quads are built. remap = false, Forge class
@Mixin(targets = "net/minecraftforge/client/model/ItemLayerModel$FaceData", remap = false)
public abstract class ItemLayerFaceDataMixin {
    @Shadow
    @Final
    private int vMax;

    private int[] coarctatio$bits;
    private int coarctatio$wordsPerFace;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;put(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object coarctatio$skipBitSets(EnumMap<EnumFacing, Object> map, Enum<?> key, Object value) {
        return null;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$allocate(int uMax, int vMax, CallbackInfo ci) {
        this.coarctatio$wordsPerFace = (uMax * vMax + 31) / 32;
        this.coarctatio$bits = new int[4 * this.coarctatio$wordsPerFace];
    }

    private static int coarctatio$faceIndex(EnumFacing facing) {
        switch (facing) {
            case WEST:
                return 0;
            case EAST:
                return 1;
            case UP:
                return 2;
            case DOWN:
                return 3;
            default:
                throw new IllegalArgumentException("Unexpected facing " + facing);
        }
    }

    /**
     * @author embeddedt, bdmajora
     * @reason Bit in the flat array
     */
    @Overwrite
    public void set(EnumFacing facing, int u, int v) {
        int index = v * this.vMax + u;
        this.coarctatio$bits[coarctatio$faceIndex(facing) * this.coarctatio$wordsPerFace + (index >> 5)] |= 1 << (index & 31);
    }

    /**
     * @author embeddedt, bdmajora
     * @reason Bit in the flat array
     */
    @Overwrite
    public boolean get(EnumFacing facing, int u, int v) {
        int index = v * this.vMax + u;
        return (this.coarctatio$bits[coarctatio$faceIndex(facing) * this.coarctatio$wordsPerFace + (index >> 5)] & (1 << (index & 31))) != 0;
    }
}
