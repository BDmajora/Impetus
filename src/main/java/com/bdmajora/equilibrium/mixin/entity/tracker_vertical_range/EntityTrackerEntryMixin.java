package com.bdmajora.equilibrium.mixin.entity.tracker_vertical_range;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes EntityTrackerEntry.
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    // Reference to the field encoded pos y of the target.
    @Shadow
    private long encodedPosY;

    // Reference to the field range of the target.
    @Shadow
    private int range;

    // Reference to the field max range of the target.
    @Shadow
    private int maxRange;

    // Changes the return value of isVisibleTo.
    @ModifyReturnValue(method = "isVisibleTo", at = @At("RETURN"))
    private boolean equilibrium$alsoCheckVertical(boolean visible, EntityPlayerMP player) {
        if (!visible) {
            return false;
        }
        int limit = Math.min(this.range, this.maxRange);
        return Math.abs(player.posY - (double) this.encodedPosY / 4096.0D) <= limit;
    }
}
