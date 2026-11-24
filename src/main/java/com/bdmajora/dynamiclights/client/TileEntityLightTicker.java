package com.bdmajora.dynamiclights.client;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

// Ticks the light level of block entities.
public final class TileEntityLightTicker {
    // Prevents instantiation
    private TileEntityLightTicker() {
    }

    // Single instance.
    private static final TileEntityLightTicker INSTANCE = new TileEntityLightTicker();

    // Returns the instance to register on the event bus.
    public static Object instance() {
        return INSTANCE;
    }

    // Recomputes the light level of every loaded block entity at the end of a tick.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!DynamicLights.options().mode.isEnabled()
                || !DynamicLights.options().blockEntitiesLightSource
                || !DynamicLightHandlers.hasTileEntityHandlers()) {
            return;
        }

        // A paused world keeps unloaded block entities in its list, and ticking one would track it again
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null || Minecraft.getMinecraft().isGamePaused()) {
            return;
        }

        List<TileEntity> tileEntities = world.loadedTileEntityList;

        for (int i = 0, size = tileEntities.size(); i < size; i++) {
            TileEntity tileEntity;
            try {
                tileEntity = tileEntities.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }

            if (tileEntity instanceof DynamicLightSource) {
                ((DynamicLightSource) tileEntity).impetus$dynamicLightTick();
            }
        }
    }
}
