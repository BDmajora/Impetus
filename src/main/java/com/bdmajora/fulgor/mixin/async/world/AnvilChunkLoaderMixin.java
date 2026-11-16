package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.async.AsyncLightStorage;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes AnvilChunkLoader.
@Mixin(AnvilChunkLoader.class)
public abstract class AnvilChunkLoaderMixin {
    // Runs before every return of readChunkFromNBT.
    @Inject(method = "readChunkFromNBT", at = @At("RETURN"))
    private void fulgor$readLight(World world, NBTTagCompound compound, CallbackInfoReturnable<Chunk> cir) {
        if (cir.getReturnValue() != null) {
            AsyncLightStorage.load(cir.getReturnValue(), compound);
        }
    }

    // Runs before every return of writeChunkToNBT.
    @Inject(method = "writeChunkToNBT", at = @At("RETURN"))
    private void fulgor$writeLight(Chunk chunk, World world, NBTTagCompound compound, CallbackInfo ci) {
        AsyncLightStorage.save(chunk, ((AsyncLitWorld) world).fulgor$getLightManager(), compound);
    }
}
