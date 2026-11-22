package com.bdmajora.impetus.impl.world;

import com.bdmajora.impetus.ImpetusVintage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public final class SimulationDistance {
    private SimulationDistance() {
    }

    public static int getLocalDistance() {
        return ImpetusVintage.options().getSimulationDistance();
    }

    public static boolean shouldTick(World world, BlockPos pos) {
        return shouldTick(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean shouldTick(World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer serverWorld) || serverWorld.getMinecraftServer().isDedicatedServer()) {
            return true;
        }

        int radius = getLocalDistance();
        for (EntityPlayer player : world.playerEntities) {
            int playerX = MathHelper.floor(player.posX) >> 4;
            int playerZ = MathHelper.floor(player.posZ) >> 4;
            // long math: an int difference of far-apart chunks wraps, and abs(MIN_VALUE) stays negative, i.e. "in range"
            if (Math.abs((long) chunkX - playerX) <= radius && Math.abs((long) chunkZ - playerZ) <= radius) {
                return true;
            }
        }

        var forced = world.getPersistentChunks();
        return !forced.isEmpty() && forced.containsKey(new ChunkPos(chunkX, chunkZ));
    }
}
