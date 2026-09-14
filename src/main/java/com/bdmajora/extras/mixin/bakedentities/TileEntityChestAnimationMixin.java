package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
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
    private static final int RENDERER_GRACE_TICKS = 4;

    @Unique
    private boolean impetus$wasSettled = true;

    @Unique
    private int impetus$rendererGrace;

    @Override
    public boolean impetus$isSettled() {
        TileEntityChest self = (TileEntityChest) (Object) this;
        return self.lidAngle == 0.0F && self.prevLidAngle == 0.0F;
    }

    @Override
    public boolean impetus$needsRenderer() {
        return !impetus$isSettled() || this.impetus$rendererGrace > 0;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void impetus$trackLid(CallbackInfo ci) {
        if (this.world == null || !this.world.isRemote) {
            return;
        }
        boolean settled = impetus$isSettled();
        if (settled != this.impetus$wasSettled) {
            this.impetus$wasSettled = settled;
            impetus$rebuildPair();
        }
        if (!settled) {
            this.impetus$rendererGrace = RENDERER_GRACE_TICKS;
        } else if (this.impetus$rendererGrace > 0) {
            this.impetus$rendererGrace--;
        }
    }

    // Both halves of a pair share one lid state in the mesh, so both positions are marked
    @Unique
    private void impetus$rebuildPair() {
        TileEntityChest self = (TileEntityChest) (Object) this;
        BlockPos min = this.pos;
        BlockPos max = this.pos;
        TileEntityChest[] neighbours = {self.adjacentChestXNeg, self.adjacentChestXPos, self.adjacentChestZNeg, self.adjacentChestZPos};
        for (TileEntityChest neighbour : neighbours) {
            if (neighbour == null) {
                continue;
            }
            BlockPos other = neighbour.getPos();
            min = new BlockPos(Math.min(min.getX(), other.getX()), Math.min(min.getY(), other.getY()), Math.min(min.getZ(), other.getZ()));
            max = new BlockPos(Math.max(max.getX(), other.getX()), Math.max(max.getY(), other.getY()), Math.max(max.getZ(), other.getZ()));
        }
        this.world.markBlockRangeForRenderUpdate(min, max);
    }
}
