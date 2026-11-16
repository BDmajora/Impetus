package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.LidTracking;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityChest.
@Mixin(TileEntityChest.class)
public abstract class TileEntityChestAnimationMixin extends TileEntity implements AnimatedBlockEntity {
    // Field lid added to the target.
    @Unique
    private int impetus$lid = LidTracking.INITIAL;

    // Returns whether settled.
    @Override
    public boolean impetus$isSettled() {
        TileEntityChest self = (TileEntityChest) (Object) this;
        return self.lidAngle == 0.0F && self.prevLidAngle == 0.0F;
    }

    // Returns whether renderer.
    @Override
    public boolean impetus$needsRenderer() {
        return LidTracking.needsRenderer(this.impetus$lid, impetus$isSettled());
    }

    // Runs at the end of update.
    @Inject(method = "update", at = @At("TAIL"))
    private void impetus$trackLid(CallbackInfo ci) {
        if (this.world == null || !this.world.isRemote) {
            return;
        }
        int next = LidTracking.tick(this.impetus$lid, impetus$isSettled());
        if (LidTracking.restChanged(this.impetus$lid, next)) {
            impetus$rebuildPair();
        }
        this.impetus$lid = next;
    }

    // Helper method rebuild pair added to the target.
    @Unique
    private void impetus$rebuildPair() {
        TileEntityChest self = (TileEntityChest) (Object) this;
        int minX = this.pos.getX(), minZ = this.pos.getZ();
        int maxX = minX, maxZ = minZ;
        TileEntityChest[] neighbours = {self.adjacentChestXNeg, self.adjacentChestXPos, self.adjacentChestZNeg, self.adjacentChestZPos};
        for (TileEntityChest neighbour : neighbours) {
            if (neighbour == null) {
                continue;
            }
            BlockPos other = neighbour.getPos();
            minX = Math.min(minX, other.getX());
            maxX = Math.max(maxX, other.getX());
            minZ = Math.min(minZ, other.getZ());
            maxZ = Math.max(maxZ, other.getZ());
        }
        int y = this.pos.getY();
        this.world.markBlockRangeForRenderUpdate(minX, y, minZ, maxX, y, maxZ);
    }
}
