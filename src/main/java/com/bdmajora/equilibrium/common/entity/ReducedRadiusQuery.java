package com.bdmajora.equilibrium.common.entity;

import net.minecraft.world.chunk.Chunk;
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

// Entity queries that use a smaller search radius.
public final class ReducedRadiusQuery {
    // Search radius of the base game.
    private static final double VANILLA_RADIUS = 2.0D;
    // Eligibility of each entity class.
    private static final Map<Class<?>, Boolean> ELIGIBLE = new ConcurrentHashMap<>();

    // Prevents instantiation
    private ReducedRadiusQuery() {
    }

    // Returns whether the world radius differs from the base game.
    public static boolean applies() {
        return World.MAX_ENTITY_RADIUS != VANILLA_RADIUS;
    }

    // Returns whether the entity class may use the smaller radius.
    public static boolean isEligible(Entity entity) {
        Class<?> type = entity.getClass();
        Boolean eligible = ELIGIBLE.get(type);
        if (eligible == null) {
            eligible = classify(type);
            ELIGIBLE.put(type, eligible);
        }
        return eligible;
    }

    // Returns whether the class is a base game entity of a small size.
    private static boolean classify(Class<?> type) {
        if (!type.getName().startsWith("net.minecraft.")) {
            return false;
        }
        if (EntityLivingBase.class.isAssignableFrom(type)) {
            return type != EntityWolf.class && !AbstractHorse.class.isAssignableFrom(type) && !EntityPlayer.class.isAssignableFrom(type)
                    && type != EntityDragon.class && type != EntityWither.class;
        }
        return type == EntityItem.class || type == EntityItemFrame.class || type == EntityPainting.class || type == EntityXPOrb.class;
    }

    // Returns the entities in the box found through the loaded chunks only.
    public static List<Entity> getEntitiesInAABBexcluding(World world, Entity entity, AxisAlignedBB box, Predicate<? super Entity> predicate) {
        List<Entity> list = new ArrayList<>();
        int minChunkX = MathHelper.floor((box.minX - VANILLA_RADIUS) / 16.0D);
        int maxChunkX = MathHelper.floor((box.maxX + VANILLA_RADIUS) / 16.0D);
        int minChunkZ = MathHelper.floor((box.minZ - VANILLA_RADIUS) / 16.0D);
        int maxChunkZ = MathHelper.floor((box.maxZ + VANILLA_RADIUS) / 16.0D);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                if (chunk != null) {
                    chunk.getEntitiesWithinAABBForEntity(entity, box, list, predicate);
                }
            }
        }
        return list;
    }
}
