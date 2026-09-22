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

// Tracks the lid: the moment it leaves or returns to rest the section is rebuilt so the static chest disappears or reappears, and the renderer keeps drawing for a few ticks after rest so the rebuild has landed before it stops
@Mixin(TileEntityChest.class)
public abstract class TileEntityChestAnimationMixin extends TileEntity implements AnimatedBlockEntity {
    @Unique
    private int impetus$lid = LidTracking.INITIAL;

    @Override
    public boolean impetus$isSettled() {
        TileEntityChest self = (TileEntityChest) (Object) this;
        return self.lidAngle == 0.0F && self.prevLidAngle == 0.0F;
    }

    @Override
    public boolean impetus$needsRenderer() {
        return LidTracking.needsRenderer(this.impetus$lid, impetus$isSettled());
    }

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

    // Both halves of a pair share one lid state in the mesh, so both positions are marked
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
            // A pair is always level, so only X and Z can differ
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
