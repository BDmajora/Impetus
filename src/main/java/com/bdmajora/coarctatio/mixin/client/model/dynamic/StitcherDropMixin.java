package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.client.model.dynamic.DroppingStitcher;
import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

// Mixin that changes Stitcher.
@Mixin(Stitcher.class)
public abstract class StitcherDropMixin implements DroppingStitcher {
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

    // Drops sprites no model references and resets the stitch attempt.
    @Override
    public void coarctatio$retainSprites(Set<String> referencedNames) {
        this.coarctatio$resetAttempt();
        Iterator<Stitcher.Holder> holders = this.setStitchHolders.iterator();
        int dropped = 0;
        while (holders.hasNext()) {
            String name = holders.next().getAtlasSprite().getIconName();
            if (DynamicModels.isDroppable(name, referencedNames)) {
                holders.remove();
                dropped++;
            }
        }
        Coarctatio.LOGGER.warn("Dropped {} unreferenced sprites from the atlas", dropped);
    }

    // Drops the largest sprite and returns whether one was left to drop.
    @Override
    public boolean coarctatio$dropLargestSprite() {
        this.coarctatio$resetAttempt();
        Stitcher.Holder largest = null;
        for (Stitcher.Holder holder : this.setStitchHolders) {
            if (largest == null || (long) holder.getWidth() * holder.getHeight() > (long) largest.getWidth() * largest.getHeight()) {
                largest = holder;
            }
        }
        if (largest == null) {
            return false;
        }
        this.setStitchHolders.remove(largest);
        Coarctatio.LOGGER.warn("Dropped {}x{} sprite '{}' from the atlas as it is too large", largest.getWidth(), largest.getHeight(), largest.getAtlasSprite().getIconName());
        return true;
    }

    // Clears the slots of a failed stitch.
    private void coarctatio$resetAttempt() {
        this.stitchSlots.clear();
        this.currentWidth = 0;
        this.currentHeight = 0;
    }
}
