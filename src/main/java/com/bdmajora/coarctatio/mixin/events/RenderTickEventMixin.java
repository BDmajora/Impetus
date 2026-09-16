package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

// remap = false, Forge class
@Mixin(value = TickEvent.RenderTickEvent.class, remap = false)
public abstract class RenderTickEventMixin implements RecyclableEvent {
    @Shadow
    @Final
    @Mutable
    public float renderTickTime;

    @Override
    public void coarctatio$refreshRenderTime(float renderTickTime) {
        this.renderTickTime = renderTickTime;
    }
}
