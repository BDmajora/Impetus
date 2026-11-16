package com.bdmajora.equilibrium.common.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.List;

// Decides which distant entities skip a tick while the server lags.
public final class EntityTickBudget {
    // Tick length that counts as lagging.
    private static final double SLOW_TICK_MS = 40.0D;
    // Squared distance that counts as far from players.
    private static final double FAR_DISTANCE_SQ = 96.0D * 96.0D;

    // Prevents instantiation
    private EntityTickBudget() {
    }

    // Returns whether the entity skips this tick.
    public static boolean shouldSkip(World world, Entity entity) {
        if (world.isRemote || (world.getTotalWorldTime() & 1L) != 0L) {
            return false;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !isBehind(server)) {
            return false;
        }
        if (!isThrottleable(entity)) {
            return false;
        }
        return isFarFromPlayers(world.playerEntities, entity);
    }

    // Returns whether the last tick was slow.
    private static boolean isBehind(MinecraftServer server) {
        long lastTick = server.tickTimeArray[(server.getTickCounter() + 99) % 100];
        return lastTick / 1.0E6D > SLOW_TICK_MS;
    }

    // Returns whether the entity type may skip ticks.
    private static boolean isThrottleable(Entity entity) {
        if (entity instanceof EntityPlayer || entity.isBeingRidden() || entity.isRiding() || !entity.isNonBoss()) {
            return false;
        }
        return entity instanceof EntityLivingBase || entity instanceof EntityItem || entity instanceof EntityXPOrb;
    }

    // Returns whether every player is far from the entity.
    private static boolean isFarFromPlayers(List<EntityPlayer> players, Entity entity) {
        for (int i = 0, n = players.size(); i < n; i++) {
            EntityPlayer player = players.get(i);
            if (player.getDistanceSq(entity) < FAR_DISTANCE_SQ) {
                return false;
            }
        }
        return true;
    }
}
