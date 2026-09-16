package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

// remap = false, Forge class
@Mixin(value = TickEvent.PlayerTickEvent.class, remap = false)
public abstract class PlayerTickEventMixin implements RecyclableEvent {
    @Shadow
    @Final
    @Mutable
    public EntityPlayer player;

    @Override
    public void coarctatio$refreshPlayer(EntityPlayer player) {
        this.player = player;
    }
}
