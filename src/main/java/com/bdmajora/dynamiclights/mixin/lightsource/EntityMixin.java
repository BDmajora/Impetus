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

    // Reference to the field last tick pos x of the target.
    @Shadow
    public double lastTickPosX;

    // Reference to the field last tick pos y of the target.
    @Shadow
    public double lastTickPosY;

    // Reference to the field last tick pos z of the target.
    @Shadow
    public double lastTickPosZ;

    // Reference to the field is dead of the target.
    @Shadow
    public boolean isDead;

    // Reference to the method is burning of the target.
    @Shadow
    public abstract boolean isBurning();

    // Reference to the method get eye height of the target.
    @Shadow
    public abstract float getEyeHeight();

    // Light level the entity gives off now.
    @Unique
    private int impetus$luminance;
    // Light level at the last relight.
    @Unique
    private int impetus$lastLuminance;
    // Time of the last relight.
    @Unique
    private long impetus$lastUpdate;
    // X of the last relight.
    @Unique
    private double impetus$prevX;
    // Y of the last relight.
    @Unique
    private double impetus$prevY;
    // Z of the last relight.
    @Unique
    private double impetus$prevZ;
    // Sections the light reached at the last relight.
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

    // Returns the dynamic light x where the entity is drawn this frame.
    @Override
    public double impetus$getDynamicLightX(float partialTicks) {
        return this.lastTickPosX + (this.posX - this.lastTickPosX) * partialTicks;
    }

    // Returns the dynamic light y where the entity's eyes are drawn this frame.
    @Override
    public double impetus$getDynamicLightY(float partialTicks) {
        return this.lastTickPosY + (this.posY - this.lastTickPosY) * partialTicks + this.getEyeHeight();
    }

    // Returns the dynamic light z where the entity is drawn this frame.
    @Override
    public double impetus$getDynamicLightZ(float partialTicks) {
        return this.lastTickPosZ + (this.posZ - this.lastTickPosZ) * partialTicks;
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

    // Updates the entity's light to 14 while burning and to its handlers' level otherwise.
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

    // Relights the sections in reach after a move of a tenth of a block or a level change.
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

        // Relights the sections the light reaches now and the ones it reached before.
        this.impetus$trackedLitChunkPos = DynamicLightsEngine.relightSections(renderer, this.world,
                this.impetus$trackedLitChunkPos, this.posX, this.posY + this.getEyeHeight(), this.posZ, luminance);
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
