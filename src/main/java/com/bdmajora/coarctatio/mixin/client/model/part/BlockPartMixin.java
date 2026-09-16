package com.bdmajora.coarctatio.mixin.client.model.part;

import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.BlockPartRotation;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Map;

// The deserializer hands every element a HashMap of at most six faces; an EnumMap is a six-slot array, smaller and faster for the baker's per-face lookups (StellarCore)
@Mixin(BlockPart.class)
public abstract class BlockPartMixin {
    @Shadow
    @Final
    @Mutable
    public Map<EnumFacing, BlockPartFace> mapFaces;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void coarctatio$compactFaces(Vector3f from, Vector3f to, Map<EnumFacing, BlockPartFace> faces,
                                         BlockPartRotation rotation, boolean shade, CallbackInfo ci) {
        if (this.mapFaces != null && !(this.mapFaces instanceof EnumMap) && !this.mapFaces.isEmpty()) {
            this.mapFaces = new EnumMap<>(this.mapFaces);
        }
    }
}
