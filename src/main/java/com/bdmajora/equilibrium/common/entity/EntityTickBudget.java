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

// A tick-time governor in the spirit of Tick Dynamic and Immersive Optimization: only once the previous server tick ran long does it start halving the tick rate of ordinary living entities, items and orbs that are far from every player, nearest-first being spared. Nothing near a player, nothing a player rides, no boss, and nothing at normal tick times is ever touched, and a skipped entity is ticked on the next tick regardless
public final class EntityTickBudget {
    // Milliseconds of the last tick above which the governor engages
    private static final double SLOW_TICK_MS = 40.0D;
    private static final double FAR_DISTANCE_SQ = 96.0D * 96.0D;

    private EntityTickBudget() {
    }

    // True when this entity should sit this tick out
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

    private static boolean isBehind(MinecraftServer server) {
        long lastTick = server.tickTimeArray[(server.getTickCounter() + 99) % 100];
        return lastTick / 1.0E6D > SLOW_TICK_MS;
    }

    private static boolean isThrottleable(Entity entity) {
        if (entity instanceof EntityPlayer || entity.isBeingRidden() || entity.isRiding() || !entity.isNonBoss()) {
            return false;
        }
        return entity instanceof EntityLivingBase || entity instanceof EntityItem || entity instanceof EntityXPOrb;
    }

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
