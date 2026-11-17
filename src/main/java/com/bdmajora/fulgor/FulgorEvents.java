package com.bdmajora.fulgor;

import com.bdmajora.fulgor.async.AsyncLitWorld;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// Handler of world events for the async light engine.
public final class FulgorEvents {
    // Stops the light workers of an unloading world.
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof AsyncLitWorld) {
            ((AsyncLitWorld) event.getWorld()).fulgor$shutdownLightManager();
        }
    }
}
