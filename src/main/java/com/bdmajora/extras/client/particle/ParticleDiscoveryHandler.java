package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.Extras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// Scans the particle factories when a manager appears.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class ParticleDiscoveryHandler {
    // Manager that was scanned last.
    private static ParticleManager lastScanned;

    // Prevents instantiation
    private ParticleDiscoveryHandler() {
    }

    // Scans a new particle manager.
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ParticleManager effectRenderer = Minecraft.getMinecraft().effectRenderer;
        if (effectRenderer == null || effectRenderer == lastScanned) {
            return;
        }
        lastScanned = effectRenderer;

        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.pruneDiscoveredCache();
        registry.scanFactories(effectRenderer);
        flushIfDirty(registry);
    }

    // Saves the settings when the data changed.
    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() == null || !event.getWorld().isRemote) {
            return;
        }
        flushIfDirty(ParticleClassRegistry.getInstance());
    }

    // Saves the settings when the registry changed.
    private static void flushIfDirty(ParticleClassRegistry registry) {
        if (registry.isDirty()) {
            Extras.save();
        }
    }
}
