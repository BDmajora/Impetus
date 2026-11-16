package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.LidTracking;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityEnderChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityEnderChest.
@Mixin(TileEntityEnderChest.class)
public abstract class TileEntityEnderChestAnimationMixin extends TileEntity implements AnimatedBlockEntity {
    // Field lid added to the target.
    @Unique
    private int impetus$lid = LidTracking.INITIAL;

    // Returns whether settled.
    @Override
    public boolean impetus$isSettled() {
        TileEntityEnderChest self = (TileEntityEnderChest) (Object) this;
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
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
        this.impetus$lid = next;
    }
}
