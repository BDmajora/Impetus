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

// Mixin that changes Stitcher.
@Mixin(Stitcher.class)
public abstract class StitcherMixin {
    // Reference to the field set stitch holders of the target.
    @Shadow
    @Final
    private Set<Stitcher.Holder> setStitchHolders;

    // Reference to the field stitch slots of the target.
    @Shadow
    @Final
    private List<Stitcher.Slot> stitchSlots;

    // Reference to the field current width of the target.
    @Shadow
    private int currentWidth;

    // Reference to the field current height of the target.
    @Shadow
    private int currentHeight;

    // Reference to the field max width of the target.
    @Shadow
    @Final
    private int maxWidth;

    // Reference to the field max height of the target.
    @Shadow
    @Final
    private int maxHeight;

    // Runs at the start of doStitch and can cancel it.
    @Inject(method = "doStitch", at = @At("HEAD"), cancellable = true)
    private void coarctatio$shelfStitch(CallbackInfo ci) {
        Stitcher.Holder[] holders = this.setStitchHolders.toArray(new Stitcher.Holder[0]);
        ShelfStitcher.Result result = ShelfStitcher.stitch(holders, this.maxWidth, this.maxHeight);
        this.stitchSlots.clear();
        for (ShelfStitcher.Placement placement : result.placements) {
            Stitcher.Holder holder = placement.holder;
            Stitcher.Slot slot = new Stitcher.Slot(placement.x, placement.y, holder.getWidth(), holder.getHeight());
            slot.addSlot(holder);
            this.stitchSlots.add(slot);
        }
        this.currentWidth = result.width;
        this.currentHeight = result.height;
        ci.cancel();
    }
}
