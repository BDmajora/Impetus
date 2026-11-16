package com.bdmajora.equilibrium.mixin.entity.xp_orb_merging;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin that changes EntityXPOrb.
@Mixin(EntityXPOrb.class)
public abstract class EntityXPOrbMixin {
    // Ticks between merge attempts.
    private static final int MERGE_INTERVAL = 20;
    // Distance within which orbs merge.
    private static final double MERGE_REACH = 1.0D;

    // Reference to the field xp value of the target.
    @Shadow
    private int xpValue;

    // Runs before every return of onUpdate.
    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void equilibrium$absorbNeighbours(CallbackInfo ci) {
        EntityXPOrb self = (EntityXPOrb) (Object) this;
        World world = self.world;
        if (world.isRemote || self.isDead || self.ticksExisted % MERGE_INTERVAL != 0) {
            return;
        }
        List<EntityXPOrb> nearby = world.getEntitiesWithinAABB(EntityXPOrb.class, self.getEntityBoundingBox().grow(MERGE_REACH),
                other -> other != self && !other.isDead);
        if (nearby.isEmpty()) {
            return;
        }
        int total = this.xpValue;
        for (EntityXPOrb other : nearby) {
            total += ((EntityXPOrbMixin) (Object) other).xpValue;
            other.setDead();
        }
        this.xpValue = total;
        self.xpOrbAge = Math.min(self.xpOrbAge, oldestAge(nearby, self.xpOrbAge));
    }

    // Handles oldest age.
    private static int oldestAge(List<EntityXPOrb> orbs, int own) {
        int youngest = own;
        for (EntityXPOrb orb : orbs) {
            youngest = Math.min(youngest, orb.xpOrbAge);
        }
        return youngest;
    }

    // Runs at the start of onCollideWithPlayer.
    @Inject(method = "onCollideWithPlayer", at = @At("HEAD"))
    private void equilibrium$noPickupCooldown(EntityPlayer player, CallbackInfo ci) {
        if (!player.world.isRemote && player.xpCooldown > 0) {
            player.xpCooldown = 0;
        }
    }
}
