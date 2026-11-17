package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

// Mixin that changes RenderTickEvent.
@Mixin(value = TickEvent.RenderTickEvent.class, remap = false)
public abstract class RenderTickEventMixin implements RecyclableEvent {
    // Reference to the field render tick time of the target.
    @Shadow
    @Final
    @Mutable
    public float renderTickTime;

    // Points the reused event at a new render time.
    @Override
    public void coarctatio$refreshRenderTime(float renderTickTime) {
        this.renderTickTime = renderTickTime;
    }
}
