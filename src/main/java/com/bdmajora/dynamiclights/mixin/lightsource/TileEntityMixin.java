package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Makes block entities light sources, purely an API surface for mods whose machines glow (vanilla emitters have real block light); a block entity does not move, so the chunk set is computed once and rebuilt only on brightness change
@Mixin(TileEntity.class)
public abstract class TileEntityMixin implements DynamicLightSource {
    @Shadow
    protected BlockPos pos;

    @Shadow
    protected World world;

    @Shadow
    protected boolean tileEntityInvalid;

    @Unique
    private int impetus$luminance;
    @Unique
    private int impetus$lastLuminance;
    @Unique
    private long impetus$lastUpdate;
    // Allocated on first use; see EntityMixin for why these are not eager.
    @Unique
    private LongOpenHashSet impetus$trackedLitChunkPos;

    @Inject(method = "invalidate", at = @At("TAIL"))
    private void impetus$onInvalidated(CallbackInfo ci) {
        this.impetus$setDynamicLightEnabled(false);
    }

    // DynamicLightSource

    @Override
    public double impetus$getDynamicLightX() {
        return this.pos.getX() + 0.5D;
    }

    @Override
    public double impetus$getDynamicLightY() {
        return this.pos.getY() + 0.5D;
    }

    @Override
    public double impetus$getDynamicLightZ() {
        return this.pos.getZ() + 0.5D;
    }

    @Override
    public World impetus$getDynamicLightWorld() {
        return this.world;
    }

    @Override
    public void impetus$resetDynamicLight() {
        this.impetus$lastLuminance = 0;
    }

    @Override
    public void impetus$dynamicLightTick() {
        if (this.world == null || !this.world.isRemote || this.tileEntityInvalid) {
            return;
        }

        this.impetus$luminance = DynamicLightHandlers.getLuminanceFrom((TileEntity) (Object) this);
        DynamicLightsEngine.updateTracking(this);

        if (!this.impetus$isDynamicLightEnabled()) {
            this.impetus$lastLuminance = 0;
        }
    }

    @Override
    public int impetus$getLuminance() {
        return this.impetus$luminance;
    }

    @Override
    public boolean impetus$shouldUpdateDynamicLight() {
        long stamp = DynamicLightsEngine.nextUpdateStamp(this.impetus$lastUpdate);
        if (stamp < 0) {
            return false;
        }
        this.impetus$lastUpdate = stamp;
        return true;
    }

    @Override
    public boolean impetus$updateDynamicLight(RenderGlobal renderer) {
        if (!this.impetus$shouldUpdateDynamicLight()) {
            return false;
        }

        int luminance = this.impetus$getLuminance();
        if (luminance == this.impetus$lastLuminance) {
            return false;
        }

        this.impetus$lastLuminance = luminance;

        // A block entity never moves, so its section set is computed once and only the rebuild below repeats
        if (this.impetus$trackedLitChunkPos == null) {
            this.impetus$trackedLitChunkPos = new LongOpenHashSet();
            DynamicLightsEngine.walkLitSections(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(),
                    null, this.impetus$trackedLitChunkPos);
        }

        this.impetus$scheduleTrackedChunksRebuild(renderer);
        return true;
    }

    @Override
    public void impetus$scheduleTrackedChunksRebuild(RenderGlobal renderer) {
        if (this.impetus$trackedLitChunkPos == null || Minecraft.getMinecraft().world != this.world) {
            return;
        }

        // Explicit primitive iterator: the enhanced for boxes every position through LongIterator.next()
        LongIterator positions = this.impetus$trackedLitChunkPos.iterator();
        while (positions.hasNext()) {
            DynamicLightsEngine.scheduleChunkRebuild(renderer, positions.nextLong());
        }
    }
}
