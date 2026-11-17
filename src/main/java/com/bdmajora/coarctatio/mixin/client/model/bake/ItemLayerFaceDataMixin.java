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

// Mixin that changes FaceData.
@Mixin(targets = "net/minecraftforge/client/model/ItemLayerModel$FaceData", remap = false)
public abstract class ItemLayerFaceDataMixin {
    // Reference to the field v max of the target.
    @Shadow
    @Final
    private int vMax;

    // Packed opacity bits of every face.
    private int[] coarctatio$bits;
    // Number of words per face.
    private int coarctatio$wordsPerFace;

    // Replaces the call of EnumMap.put in constructor.
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;put(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object coarctatio$skipBitSets(EnumMap<EnumFacing, Object> map, Enum<?> key, Object value) {
        return null;
    }

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$allocate(int uMax, int vMax, CallbackInfo ci) {
        // Sizes each face for vMax rows of the wider dimension.
        this.coarctatio$wordsPerFace = (vMax * Math.max(uMax, vMax) + 31) / 32;
        this.coarctatio$bits = new int[4 * this.coarctatio$wordsPerFace];
    }

    // Returns the slot of one of the four faces a layer edge can have.
    private static int coarctatio$faceIndex(EnumFacing facing) {
        return switch (facing) {
            case WEST -> 0;
            case EAST -> 1;
            case UP -> 2;
            case DOWN -> 3;
            default -> throw new IllegalArgumentException("Unexpected facing " + facing);
        };
    }

    // Replaces the whole method set.
    @Overwrite
    public void set(EnumFacing facing, int u, int v) {
        int index = v * this.vMax + u;
        this.coarctatio$bits[coarctatio$faceIndex(facing) * this.coarctatio$wordsPerFace + (index >> 5)] |= 1 << (index & 31);
    }

    // Replaces the whole method get.
    @Overwrite
    public boolean get(EnumFacing facing, int u, int v) {
        int index = v * this.vMax + u;
        return (this.coarctatio$bits[coarctatio$faceIndex(facing) * this.coarctatio$wordsPerFace + (index >> 5)] & (1 << (index & 31))) != 0;
    }
}
