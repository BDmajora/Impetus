package com.bdmajora.equilibrium.mixin.chunk.tile_entity_map;

import com.bdmajora.equilibrium.common.collections.BlockPosLongMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Shadow
    @Final
    @Mutable
    private Map<BlockPos, TileEntity> tileEntities;

    // Value cubic chunks.
    private static final boolean CUBIC_CHUNKS = net.minecraftforge.fml.common.Loader.isModLoaded("cubicchunks");

    // Runs before every return of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void equilibrium$useLongKeyedMap(World world, int x, int z, CallbackInfo ci) {
        if (!CUBIC_CHUNKS) {
            this.tileEntities = new BlockPosLongMap<>();
        }
    }
}
