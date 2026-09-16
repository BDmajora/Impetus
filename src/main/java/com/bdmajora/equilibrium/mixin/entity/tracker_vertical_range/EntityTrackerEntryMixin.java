package com.bdmajora.equilibrium.mixin.entity.tracker_vertical_range;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla decides whether a player tracks an entity on horizontal distance alone, so a player at bedrock receives every mob update from the surface and vice versa (StellarCore's entitytrackerIncludeY); applying the same range vertically stops that. Off by default since a player can no longer see an entity more than its tracking range above or below them, which shows on tall builds
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    @Shadow
    private long encodedPosY;

    @Shadow
    private int range;

    @Shadow
    private int maxRange;

    @ModifyReturnValue(method = "isVisibleTo", at = @At("RETURN"))
    private boolean equilibrium$alsoCheckVertical(boolean visible, EntityPlayerMP player) {
        if (!visible) {
            return false;
        }
        int limit = Math.min(this.range, this.maxRange);
        return Math.abs(player.posY - (double) this.encodedPosY / 4096.0D) <= limit;
    }
}
