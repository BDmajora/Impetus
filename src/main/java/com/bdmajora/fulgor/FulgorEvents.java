package com.bdmajora.fulgor;

import com.bdmajora.fulgor.async.AsyncLitWorld;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// Forge bus listener for the async engine's lifecycle, registered from ImpetusVintage's construction
public final class FulgorEvents {
    // A world going away takes its two lane threads with it
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof AsyncLitWorld) {
            ((AsyncLitWorld) event.getWorld()).fulgor$shutdownLightManager();
        }
    }
}
