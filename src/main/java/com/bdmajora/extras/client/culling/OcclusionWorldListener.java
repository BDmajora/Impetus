package com.bdmajora.extras.client.culling;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;

// World listener that queues opacity changes and chunk loads for the occlusion grid.
public final class OcclusionWorldListener implements IWorldEventListener {
    // Largest number of chunks one range update re-reads.
    static final int MAX_RANGE_CHUNKS = 64;

    // World the listener is registered on.
    private final World world;
    // Queue the changes go to.
    private final OcclusionInbox inbox;

    // Creates a listener that feeds the inbox from the world.
    public OcclusionWorldListener(World world, OcclusionInbox inbox) {
        this.world = world;
        this.inbox = inbox;
    }

    // Queues the section of a block whose opacity changed.
    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        if (oldState == newState) {
            return;
        }
        if (oldState == null || newState == null || oldState.isOpaqueCube() != newState.isOpaqueCube()) {
            this.inbox.section(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }

    // Does nothing for light changes.
    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    // Re-reads the chunks of a range that covers whole chunks such as a chunk load or unload.
    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (x2 - x1 < 15 || z2 - z1 < 15) {
            return;
        }
        int minX = x1 >> 4;
        int minZ = z1 >> 4;
        int maxX = x2 >> 4;
        int maxZ = z2 >> 4;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_RANGE_CHUNKS) {
            return;
        }
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                this.inbox.chunk(chunkX, chunkZ, this.world.getChunkProvider().getLoadedChunk(chunkX, chunkZ));
            }
        }
    }

    // Does nothing for sounds.
    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
    }

    // Does nothing for records.
    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    // Does nothing for particles.
    @Override
    public void spawnParticle(int particleId, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    // Does nothing for particles.
    @Override
    public void spawnParticle(int particleId, boolean ignoreRange, boolean minimiseLevel, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    // Does nothing for added entities.
    @Override
    public void onEntityAdded(Entity entity) {
    }

    // Does nothing for removed entities.
    @Override
    public void onEntityRemoved(Entity entity) {
    }

    // Does nothing for global sounds.
    @Override
    public void broadcastSound(int soundId, BlockPos pos, int data) {
    }

    // Does nothing for world events.
    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
    }

    // Does nothing for block breaking progress.
    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }
}
