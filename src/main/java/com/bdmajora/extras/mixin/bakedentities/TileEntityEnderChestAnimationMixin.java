package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityEnderChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The ender chest's lid, tracked the same way as a chest's but never paired
@Mixin(TileEntityEnderChest.class)
public abstract class TileEntityEnderChestAnimationMixin extends TileEntity implements AnimatedBlockEntity {
    private static final int RENDERER_GRACE_TICKS = 4;

    @Unique
    private boolean impetus$wasSettled = true;

    @Unique
    private int impetus$rendererGrace;

    @Override
    public boolean impetus$isSettled() {
        TileEntityEnderChest self = (TileEntityEnderChest) (Object) this;
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
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
        if (!settled) {
            this.impetus$rendererGrace = RENDERER_GRACE_TICKS;
        } else if (this.impetus$rendererGrace > 0) {
            this.impetus$rendererGrace--;
        }
    }
}
