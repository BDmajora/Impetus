package com.bdmajora.coarctatio.mixin.client.texture;

import com.bdmajora.coarctatio.client.texture.ShelfStitcher;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

// Hands doStitch to the shelf packer and expresses its layout as vanilla Slots, one exact-size slot per sprite, so getStichSlots and every mod reading the stitcher's output see the same structures they always did
@Mixin(Stitcher.class)
public abstract class StitcherMixin {
    @Shadow
    @Final
    private Set<Stitcher.Holder> setStitchHolders;

    @Shadow
    @Final
    private List<Stitcher.Slot> stitchSlots;

    @Shadow
    private int currentWidth;

    @Shadow
    private int currentHeight;

    @Shadow
    @Final
    private int maxWidth;

    @Shadow
    @Final
    private int maxHeight;

    @Inject(method = "doStitch", at = @At("HEAD"), cancellable = true)
    private void coarctatio$shelfStitch(CallbackInfo ci) {
        Stitcher.Holder[] holders = this.setStitchHolders.toArray(new Stitcher.Holder[0]);
        ShelfStitcher.Result result = ShelfStitcher.stitch(holders, this.maxWidth, this.maxHeight);
        this.stitchSlots.clear();
        for (ShelfStitcher.Placement placement : result.placements) {
            Stitcher.Holder holder = placement.holder;
            Stitcher.Slot slot = new Stitcher.Slot(placement.x, placement.y, holder.getWidth(), holder.getHeight());
            // An exact-size slot takes the holder directly, no subdivision
            slot.addSlot(holder);
            this.stitchSlots.add(slot);
        }
        this.currentWidth = result.width;
        this.currentHeight = result.height;
        ci.cancel();
    }
}
