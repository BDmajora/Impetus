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

// Folds nearby experience orbs into one (Clumps' idea): a mob farm drops dozens of orbs per kill and each is an entity with a full tick, a tracker entry and a pickup that costs the player a two-tick cooldown, so they pile up for minutes. Once a second an orb absorbs every orb within a block and the survivor hands its whole value over in one pickup. Off by default since one orb worth 500 points repairs Mending gear in one go and vanilla parity is gone
@Mixin(EntityXPOrb.class)
public abstract class EntityXPOrbMixin {
    private static final int MERGE_INTERVAL = 20;
    private static final double MERGE_REACH = 1.0D;

    @Shadow
    private int xpValue;

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
        // The oldest orb would otherwise despawn first and take everything it absorbed with it
        self.xpOrbAge = Math.min(self.xpOrbAge, oldestAge(nearby, self.xpOrbAge));
    }

    private static int oldestAge(List<EntityXPOrb> orbs, int own) {
        int youngest = own;
        for (EntityXPOrb orb : orbs) {
            youngest = Math.min(youngest, orb.xpOrbAge);
        }
        return youngest;
    }

    // The pickup cooldown exists to pace many small orbs; with one merged orb it only delays the pickup
    @Inject(method = "onCollideWithPlayer", at = @At("HEAD"))
    private void equilibrium$noPickupCooldown(EntityPlayer player, CallbackInfo ci) {
        if (!player.world.isRemote && player.xpCooldown > 0) {
            player.xpCooldown = 0;
        }
    }
}
