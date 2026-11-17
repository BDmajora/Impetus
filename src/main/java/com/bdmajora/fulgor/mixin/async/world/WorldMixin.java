package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.ClientWorldCheck;
import com.bdmajora.fulgor.async.WorldLightManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements AsyncLitWorld {
    // Light manager created on first use.
    @Unique
    private volatile WorldLightManager fulgor$lightManager;

    // Set once the constructor returned.
    @Unique
    private volatile boolean fulgor$ready;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void fulgor$markReady(CallbackInfo ci) {
        this.fulgor$ready = true;
    }

    // Runs at the start of checkLightFor and can cancel it.
    @Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true)
    private void fulgor$queueLightUpdate(EnumSkyBlock lightType, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!this.fulgor$ready) {
            return;
        }
        WorldLightManager manager = fulgor$getLightManager();
        if (manager == null) {
            return;
        }
        World self = (World) (Object) this;
        if (self.isRemote) {
            Chunk chunk = fulgor$getAnyChunkImmediately(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !((AsyncLitChunk) chunk).fulgor$isLightReady()) {
                cir.setReturnValue(false);
                return;
            }
        }
        manager.queueBlockChange(pos.getX(), pos.getY(), pos.getZ());
        cir.setReturnValue(true);
    }

    // Returns the light manager.
    @Override
    public WorldLightManager fulgor$getLightManager() {
        WorldLightManager manager = this.fulgor$lightManager;
        if (manager != null) {
            return manager;
        }
        synchronized (this) {
            manager = this.fulgor$lightManager;
            if (manager != null) {
                return manager;
            }
            World self = (World) (Object) this;
            if (!fulgor$isRealWorld(self)) {
                return null;
            }
            this.fulgor$lightManager = manager = new WorldLightManager(self, self.provider.hasSkyLight());
        }
        return manager;
    }

    // Returns whether the world is a server world or the client's current one.
    @Unique
    private static boolean fulgor$isRealWorld(World world) {
        if (world instanceof WorldServer) {
            return true;
        }
        return world.isRemote && FMLCommonHandler.instance().getSide() == Side.CLIENT && ClientWorldCheck.isCurrentClientWorld(world);
    }

    // Returns a loaded chunk without loading or generating it.
    @Override
    public Chunk fulgor$getAnyChunkImmediately(int chunkX, int chunkZ) {
        WorldLightManager manager = this.fulgor$lightManager;
        return manager == null ? null : manager.getLoadedChunk(chunkX, chunkZ);
    }

    // Returns whether a chunk still has light work queued.
    @Override
    public boolean fulgor$hasChunkPendingLight(int chunkX, int chunkZ) {
        WorldLightManager manager = this.fulgor$lightManager;
        return manager != null && manager.hasChunkPendingLight(chunkX, chunkZ);
    }

    // Stops the light workers and drops the manager.
    @Override
    public void fulgor$shutdownLightManager() {
        WorldLightManager manager = this.fulgor$lightManager;
        if (manager != null) {
            manager.shutdown();
            this.fulgor$lightManager = null;
        }
    }
}
