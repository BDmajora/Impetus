package com.bdmajora.equilibrium.mixin.util.chunk_access;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ChunkProviderClient.
@Mixin(ChunkProviderClient.class)
public abstract class ChunkProviderClientMixin {
    // Reference to the field loaded chunks of the target.
    @Shadow
    @Final
    private Long2ObjectMap<Chunk> loadedChunks;

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // A full resend replaces a chunk without unloading it. The chunk lookup cache trusts the loaded flag, so a replaced chunk
    // that kept it would go on being returned, and the packet itself would be read into it instead of the new one. Its block
    // entities would stay in the world's lists beside the ones the packet brings.
    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void equilibrium$retireReplacedChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        Chunk replaced = this.loadedChunks.get(ChunkPos.asLong(x, z));
        if (replaced == null) {
            return;
        }
        replaced.markLoaded(false);
        for (TileEntity tileEntity : replaced.getTileEntityMap().values()) {
            this.world.markTileEntityForRemoval(tileEntity);
        }
    }
}
