package com.bdmajora.extras.async;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// WorldEntitySpawner.findChunksForSpawning with the per-chunk attempt loop spread over the pool: the eligibility sweep and per-type caps stay serial and identical to vanilla, each chunk's placement search runs as one task, and spawns funnel through the world lock like any other
public final class ParallelSpawner {
    private static final int MOB_COUNT_DIV = 17 * 17;

    // Vanilla mixes world.rand into every attempt; a per-thread source avoids 24 threads hammering one atomic seed
    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(Random::new);

    private ParallelSpawner() {
    }

    public static int findChunksForSpawning(WorldServer world, boolean spawnHostileMobs, boolean spawnPeacefulMobs, boolean spawnOnSetTickRate) {
        if (!spawnHostileMobs && !spawnPeacefulMobs) {
            return 0;
        }

        Set<ChunkPos> eligible = new HashSet<>();
        int chunkCount = 0;

        for (EntityPlayer player : world.playerEntities) {
            if (player.isSpectator()) {
                continue;
            }
            int chunkX = MathHelper.floor(player.posX / 16.0D);
            int chunkZ = MathHelper.floor(player.posZ / 16.0D);

            for (int dx = -8; dx <= 8; ++dx) {
                for (int dz = -8; dz <= 8; ++dz) {
                    boolean edge = dx == -8 || dx == 8 || dz == -8 || dz == 8;
                    ChunkPos pos = new ChunkPos(dx + chunkX, dz + chunkZ);

                    if (!eligible.contains(pos)) {
                        ++chunkCount;

                        if (!edge && world.getWorldBorder().contains(pos)) {
                            PlayerChunkMapEntry entry = world.getPlayerChunkMap().getEntry(pos.x, pos.z);

                            if (entry != null && entry.isSentToPlayers()) {
                                eligible.add(pos);
                            }
                        }
                    }
                }
            }
        }

        AtomicInteger spawned = new AtomicInteger();
        BlockPos spawnPoint = world.getSpawnPoint();

        for (EnumCreatureType type : EnumCreatureType.values()) {
            if ((type.getPeacefulCreature() && !spawnPeacefulMobs) || (!type.getPeacefulCreature() && !spawnHostileMobs)
                    || (type.getAnimal() && !spawnOnSetTickRate)) {
                continue;
            }

            int current = world.countEntities(type, true);
            int cap = type.getMaxNumberOfCreature() * chunkCount / MOB_COUNT_DIV;

            if (current > cap) {
                continue;
            }

            List<ChunkPos> shuffled = new ArrayList<>(eligible);
            Collections.shuffle(shuffled);

            ParallelProcessor.forEachParallel(shuffled, ParallelProcessor.SPAWN_COST,
                    chunk -> spawned.addAndGet(spawnInChunk(world, type, chunk, spawnPoint)));
        }

        return spawned.get();
    }

    // One chunk's attempts, a straight transcription of the loop body; returns how many entities it placed
    private static int spawnInChunk(WorldServer world, EnumCreatureType type, ChunkPos chunkPos, BlockPos spawnPoint) {
        Random rand = RANDOM.get();
        BlockPos start = getRandomChunkPosition(world, chunkPos.x, chunkPos.z, rand);
        int startX = start.getX();
        int startY = start.getY();
        int startZ = start.getZ();
        IBlockState state = world.getBlockState(start);

        if (state.isNormalCube()) {
            return 0;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int packSize = 0;
        int total = 0;

        for (int group = 0; group < 3; ++group) {
            int x = startX;
            int y = startY;
            int z = startZ;
            Biome.SpawnListEntry entry = null;
            IEntityLivingData livingData = null;
            int attempts = MathHelper.ceil(rand.nextDouble() * 4.0D);

            for (int attempt = 0; attempt < attempts; ++attempt) {
                x += rand.nextInt(6) - rand.nextInt(6);
                z += rand.nextInt(6) - rand.nextInt(6);
                cursor.setPos(x, y, z);
                float fx = (float) x + 0.5F;
                float fz = (float) z + 0.5F;

                if (world.isAnyPlayerWithinRangeAt(fx, y, fz, 24.0D) || spawnPoint.distanceSq(fx, y, fz) < 576.0D) {
                    continue;
                }

                if (entry == null) {
                    entry = world.getSpawnListEntryForTypeAt(type, cursor);

                    if (entry == null) {
                        break;
                    }
                }

                if (!world.canCreatureTypeSpawnHere(type, entry, cursor)
                        || !WorldEntitySpawner.canCreatureTypeSpawnAtLocation(EntitySpawnPlacementRegistry.getPlacementForEntity(entry.entityClass), world, cursor)) {
                    continue;
                }

                EntityLiving living;

                try {
                    living = entry.newInstance(world);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    return total;
                }

                living.setLocationAndAngles(fx, y, fz, rand.nextFloat() * 360.0F, 0.0F);

                Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(living, world, fx, y, fz, false);
                if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && living.getCanSpawnHere() && living.isNotColliding())) {
                    if (!ForgeEventFactory.doSpecialSpawn(living, world, fx, y, fz)) {
                        livingData = living.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(living)), livingData);
                    }

                    if (living.isNotColliding()) {
                        ++packSize;
                        world.spawnEntity(living);
                    } else {
                        living.setDead();
                    }

                    if (packSize >= ForgeEventFactory.getMaxSpawnPackSize(living)) {
                        return total;
                    }
                }

                // Vanilla adds the running pack count on every successful placement, so a pack of three counts 1 + 2 + 3; kept for parity with its return value
                total += packSize;
            }
        }

        return total;
    }

    // Vanilla's private helper with the random source made explicit
    private static BlockPos getRandomChunkPosition(WorldServer world, int chunkX, int chunkZ, Random rand) {
        Chunk chunk = world.getChunk(chunkX, chunkZ);
        int x = chunkX * 16 + rand.nextInt(16);
        int z = chunkZ * 16 + rand.nextInt(16);
        int height = MathHelper.roundUp(chunk.getHeight(new BlockPos(x, 0, z)) + 1, 16);
        int y = rand.nextInt(height > 0 ? height : chunk.getTopFilledSegment() + 16 - 1);
        return new BlockPos(x, y, z);
    }
}
