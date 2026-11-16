package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityMixin implements DynamicLightSource {
    // Reference to the field world of the target.
    @Shadow
    public World world;

    // Reference to the field pos x of the target.
    @Shadow
    public double posX;

    // Reference to the field pos y of the target.
    @Shadow
    public double posY;

    // Reference to the field pos z of the target.
    @Shadow
    public double posZ;

    // Reference to the field is dead of the target.
    @Shadow
    public boolean isDead;

    // Reference to the method is burning of the target.
    @Shadow
    public abstract boolean isBurning();

    // Reference to the method get eye height of the target.
    @Shadow
    public abstract float getEyeHeight();

    // Field luminance added to the target.
    @Unique
    private int impetus$luminance;
    // Field last luminance added to the target.
    @Unique
    private int impetus$lastLuminance;
    // Field last update added to the target.
    @Unique
    private long impetus$lastUpdate;
    // Field prev x added to the target.
    @Unique
    private double impetus$prevX;
    // Field prev y added to the target.
    @Unique
    private double impetus$prevY;
    // Field prev z added to the target.
    @Unique
    private double impetus$prevZ;
    // Field tracked lit chunk pos added to the target.
    @Unique
    private LongOpenHashSet impetus$trackedLitChunkPos;

    // Runs at the end of onEntityUpdate.
    @Inject(method = "onEntityUpdate", at = @At("TAIL"))
    private void impetus$onTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    // Runs before every return of getBrightnessForRender and can cancel it.
    @Inject(method = "getBrightnessForRender", at = @At("RETURN"), cancellable = true)
    private void impetus$brightnessForRender(CallbackInfoReturnable<Integer> cir) {
        if (!DynamicLights.options().mode.isEnabled()) {
            return;
        }

        cir.setReturnValue(DynamicLights.engine()
                .getLightmapWithDynamicLight((Entity) (Object) this, cir.getReturnValueI()));
    }

    // Runs at the end of onRemovedFromWorld.
    @Inject(method = "onRemovedFromWorld", at = @At("TAIL"))
    private void impetus$onRemoved(CallbackInfo ci) {
        if (this.world.isRemote) {
            this.impetus$setDynamicLightEnabled(false);
        }
    }

    // Returns the dynamic light x.
    @Override
    public double impetus$getDynamicLightX() {
        return this.posX;
    }

    // Returns the dynamic light y.
    @Override
    public double impetus$getDynamicLightY() {
        return this.posY + this.getEyeHeight();
    }

    // Returns the dynamic light z.
    @Override
    public double impetus$getDynamicLightZ() {
        return this.posZ;
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
        Entity self = (Entity) (Object) this;

        if (!DynamicLights.options().entitiesLightSource || !DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$luminance = 0;
            return;
        }

        int burning = this.isBurning() ? 14 : 0;
        this.impetus$luminance = Math.max(burning, DynamicLightHandlers.getLuminanceFrom(self));
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

        double deltaX = this.posX - this.impetus$prevX;
        double deltaY = this.posY - this.impetus$prevY;
        double deltaZ = this.posZ - this.impetus$prevZ;
        int luminance = this.impetus$getLuminance();

        boolean moved = Math.abs(deltaX) > 0.1D || Math.abs(deltaY) > 0.1D || Math.abs(deltaZ) > 0.1D;
        if (!moved && luminance == this.impetus$lastLuminance) {
            return false;
        }

        this.impetus$prevX = this.posX;
        this.impetus$prevY = this.posY;
        this.impetus$prevZ = this.posZ;
        this.impetus$lastLuminance = luminance;

        LongOpenHashSet newPos = null;
        if (luminance > 0) {
            newPos = new LongOpenHashSet();
            DynamicLightsEngine.walkLitSections(renderer, this.posX, this.posY + this.getEyeHeight(), this.posZ,
                    this.impetus$trackedLitChunkPos, newPos);
        }

        this.impetus$scheduleTrackedChunksRebuild(renderer);
        this.impetus$trackedLitChunkPos = newPos;
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
