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

// Mixin that changes TileEntity.
@Mixin(TileEntity.class)
public abstract class TileEntityMixin implements DynamicLightSource {
    // Reference to the field pos of the target.
    @Shadow
    protected BlockPos pos;

    // Reference to the field world of the target.
    @Shadow
    protected World world;

    // Reference to the field tile entity invalid of the target.
    @Shadow
    protected boolean tileEntityInvalid;

    // Light level the block entity gives off now.
    @Unique
    private int impetus$luminance;
    // Light level at the last relight.
    @Unique
    private int impetus$lastLuminance;
    // Time of the last relight.
    @Unique
    private long impetus$lastUpdate;
    // Sections the light reached at the last relight.
    @Unique
    private LongOpenHashSet impetus$trackedLitChunkPos;

    // Runs at the end of invalidate.
    @Inject(method = "invalidate", at = @At("TAIL"))
    private void impetus$onInvalidated(CallbackInfo ci) {
        this.impetus$setDynamicLightEnabled(false);
    }

    // Returns the dynamic light x.
    @Override
    public double impetus$getDynamicLightX() {
        return this.pos.getX() + 0.5D;
    }

    // Returns the dynamic light y.
    @Override
    public double impetus$getDynamicLightY() {
        return this.pos.getY() + 0.5D;
    }

    // Returns the dynamic light z.
    @Override
    public double impetus$getDynamicLightZ() {
        return this.pos.getZ() + 0.5D;
    }

    // Returns the dynamic light world.
    @Override
    public World impetus$getDynamicLightWorld() {
        return this.world;
    }

    // Forgets the last light level.
    @Override
    public void impetus$resetDynamicLight() {
        this.impetus$lastLuminance = 0;
    }

    // Reads the block entity's light level and registers or drops it as a source.
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

    // Returns the luminance.
    @Override
    public int impetus$getLuminance() {
        return this.impetus$luminance;
    }

    // Returns whether the rate limit allows an update now and stamps the time when it does.
    @Override
    public boolean impetus$shouldUpdateDynamicLight() {
        long stamp = DynamicLightsEngine.nextUpdateStamp(this.impetus$lastUpdate);
        if (stamp < 0) {
            return false;
        }
        this.impetus$lastUpdate = stamp;
        return true;
    }

    // Relights the sections in reach when the level changed and returns whether it did.
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

        // Relights the old sections together with the new ones.
        LongOpenHashSet reached = DynamicLightsEngine.relightSections(renderer, this.world,
                this.impetus$trackedLitChunkPos, this.impetus$getDynamicLightX(), this.impetus$getDynamicLightY(),
                this.impetus$getDynamicLightZ(), luminance);
        // Keeps the reached sections when the light is dark.
        if (reached != null) {
            this.impetus$trackedLitChunkPos = reached;
        }
        return true;
    }

    // Rebuilds every section the light last reached.
    @Override
    public void impetus$scheduleTrackedChunksRebuild(RenderGlobal renderer) {
        if (this.impetus$trackedLitChunkPos == null || Minecraft.getMinecraft().world != this.world) {
            return;
        }

        LongIterator positions = this.impetus$trackedLitChunkPos.iterator();
        while (positions.hasNext()) {
            DynamicLightsEngine.scheduleChunkRebuild(renderer, positions.nextLong());
        }
    }
}
