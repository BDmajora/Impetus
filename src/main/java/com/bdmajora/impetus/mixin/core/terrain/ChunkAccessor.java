package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public interface ChunkAccessor {
    // Gives access to the field get has entities.
    @Accessor("hasEntities")
    boolean impetus$getHasEntities();
}
