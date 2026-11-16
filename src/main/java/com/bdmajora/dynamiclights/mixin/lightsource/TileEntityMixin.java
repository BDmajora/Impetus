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

    // Field luminance added to the target.
    @Unique
    private int impetus$luminance;
    // Field last luminance added to the target.
    @Unique
    private int impetus$lastLuminance;
    // Field last update added to the target.
    @Unique
    private long impetus$lastUpdate;
    // Field tracked lit chunk pos added to the target.
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

    // Handles reset dynamic light.
    @Override
    public void impetus$resetDynamicLight() {
        this.impetus$lastLuminance = 0;
    }

    // Handles dynamic light tick.
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

    // Returns whether update dynamic light.
    @Override
    public boolean impetus$shouldUpdateDynamicLight() {
        long stamp = DynamicLightsEngine.nextUpdateStamp(this.impetus$lastUpdate);
        if (stamp < 0) {
            return false;
        }
        this.impetus$lastUpdate = stamp;
        return true;
    }

    // Handles update dynamic light.
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

        if (this.impetus$trackedLitChunkPos == null) {
            this.impetus$trackedLitChunkPos = new LongOpenHashSet();
            DynamicLightsEngine.walkLitSections(null, this.pos.getX(), this.pos.getY(), this.pos.getZ(),
                    null, this.impetus$trackedLitChunkPos);
        }

        this.impetus$scheduleTrackedChunksRebuild(renderer);
        return true;
    }

    // Handles schedule tracked chunks rebuild.
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
