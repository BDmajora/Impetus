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

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityMixin implements FlagCache {
    // Reference to the field flags of the target.
    @Shadow
    @Final
    protected static DataParameter<Byte> FLAGS;

    // Reference to the field data manager of the target.
    @Shadow
    protected EntityDataManager dataManager;

    // Cached entity flags.
    private byte equilibrium$flags;
    // Flag set while the cached flags are current.
    private boolean equilibrium$flagsValid;

    // Runs at the start of getFlag and can cancel it.
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

    // Drops the cached flags after a data watcher write.
    @Override
    public void equilibrium$invalidateFlags() {
        this.equilibrium$flagsValid = false;
    }
}
