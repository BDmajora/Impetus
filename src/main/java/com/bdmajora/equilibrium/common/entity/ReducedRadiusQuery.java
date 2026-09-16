package com.bdmajora.equilibrium.common.entity;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// World.MAX_ENTITY_RADIUS is a Forge global that any mod with a big entity raises for everyone, and every AABB query pads by it, so a mob's per-tick collision check scans up to 9 chunks instead of 1 once one such mod is present (Universal Tweaks' "Entity Radius Check"); for the vanilla entities whose own size is known this runs the same query with vanilla's 2-block padding. The class list is Universal Tweaks' and RLTweaker's: no wolves, horses, players, dragon or wither, since those either fight alongside the player or own explosions, and item-like entities are included
public final class ReducedRadiusQuery {
    private static final double VANILLA_RADIUS = 2.0D;
    private static final Map<Class<?>, Boolean> ELIGIBLE = new ConcurrentHashMap<>();

    private ReducedRadiusQuery() {
    }

    // False while no mod has touched the global, so the vanilla path is used untouched
    public static boolean applies() {
        return World.MAX_ENTITY_RADIUS != VANILLA_RADIUS;
    }

    public static boolean isEligible(Entity entity) {
        Class<?> type = entity.getClass();
        Boolean eligible = ELIGIBLE.get(type);
        if (eligible == null) {
            eligible = classify(type);
            ELIGIBLE.put(type, eligible);
        }
        return eligible;
    }

    private static boolean classify(Class<?> type) {
        // Modded entities are left alone; their size is unknown here and they may be exactly why the radius was raised
        if (!type.getName().startsWith("net.minecraft.")) {
            return false;
        }
        if (EntityLivingBase.class.isAssignableFrom(type)) {
            return type != EntityWolf.class && !AbstractHorse.class.isAssignableFrom(type) && !EntityPlayer.class.isAssignableFrom(type)
                    && type != EntityDragon.class && type != EntityWither.class;
        }
        return type == EntityItem.class || type == EntityItemFrame.class || type == EntityPainting.class || type == EntityXPOrb.class;
    }

    // Vanilla's getEntitiesInAABBexcluding with the padding constant fixed at 2
    public static List<Entity> getEntitiesInAABBexcluding(World world, Entity entity, AxisAlignedBB box, Predicate<? super Entity> predicate) {
        List<Entity> list = new ArrayList<>();
        int minChunkX = MathHelper.floor((box.minX - VANILLA_RADIUS) / 16.0D);
        int maxChunkX = MathHelper.floor((box.maxX + VANILLA_RADIUS) / 16.0D);
        int minChunkZ = MathHelper.floor((box.minZ - VANILLA_RADIUS) / 16.0D);
        int maxChunkZ = MathHelper.floor((box.maxZ + VANILLA_RADIUS) / 16.0D);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk != null) {
                    chunk.getEntitiesWithinAABBForEntity(entity, box, list, predicate);
                }
            }
        }
        return list;
    }
}
