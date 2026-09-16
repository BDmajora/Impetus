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

// Removes sprites between stitch attempts and resets what a failed vanilla attempt left in the slot list; a dropped sprite ends up on the missing texture, which is what an overflowing atlas cost before too
@Mixin(Stitcher.class)
public abstract class StitcherDropMixin implements DroppingStitcher {
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

    private void coarctatio$resetAttempt() {
        this.stitchSlots.clear();
        this.currentWidth = 0;
        this.currentHeight = 0;
    }
}
