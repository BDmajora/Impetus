package com.bdmajora.equilibrium.mixin.entity.flag_cache;

import com.bdmajora.equilibrium.common.entity.FlagCache;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// isSneaking, isSprinting, isInvisible, isGlowing, isBurning and isElytraFlying all read one synced byte through EntityDataManager.get, which takes a read-write lock and unboxes a Byte on every call, and rendering alone asks several of them per entity per frame (BadOptimizations' entity flag caching); the byte is kept here and refreshed only when the data manager writes it
@Mixin(Entity.class)
public abstract class EntityMixin implements FlagCache {
    @Shadow
    @Final
    protected static DataParameter<Byte> FLAGS;

    @Shadow
    protected EntityDataManager dataManager;

    private byte equilibrium$flags;
    private boolean equilibrium$flagsValid;

    @Inject(method = "getFlag", at = @At("HEAD"), cancellable = true)
    private void equilibrium$readCachedFlag(int flag, CallbackInfoReturnable<Boolean> cir) {
        if (!this.equilibrium$flagsValid) {
            EntityDataManager manager = this.dataManager;
            if (manager == null) {
                return;
            }
            this.equilibrium$flags = manager.get(FLAGS);
            this.equilibrium$flagsValid = true;
        }
        cir.setReturnValue((this.equilibrium$flags & 1 << flag) != 0);
    }

    @Override
    public void equilibrium$invalidateFlags() {
        this.equilibrium$flagsValid = false;
    }
}
