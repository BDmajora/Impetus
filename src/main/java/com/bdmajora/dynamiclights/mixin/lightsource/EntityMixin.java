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

// Makes every entity a potential light source: burning entities and registered handlers glow here, subclass mixins override impetus$dynamicLightTick() and keep their own luminance field (a @Unique field cannot be shadowed across mixins) plus impetus$getLuminance()
@Mixin(Entity.class)
public abstract class EntityMixin implements DynamicLightSource {
    @Shadow
    public World world;

    @Shadow
    public double posX;

    @Shadow
    public double posY;

    @Shadow
    public double posZ;

    @Shadow
    public boolean isDead;

    @Shadow
    public abstract boolean isBurning();

    @Shadow
    public abstract float getEyeHeight();

    @Unique
    private int impetus$luminance;
    @Unique
    private int impetus$lastLuminance;
    @Unique
    private long impetus$lastUpdate;
    @Unique
    private double impetus$prevX;
    @Unique
    private double impetus$prevY;
    @Unique
    private double impetus$prevZ;
    // Sections this entity currently lights, allocated on first use; upstream allocates eagerly, which on a busy client is thousands of empty hash sets
    @Unique
    private LongOpenHashSet impetus$trackedLitChunkPos;

    // Recomputes luminance once per tick from onEntityUpdate, the shared tail every entity's tick runs through except those overriding onUpdate without calling up (EntityHanging, EntityMinecart carry their own)
    @Inject(method = "onEntityUpdate", at = @At("TAIL"))
    private void impetus$onTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    // Lights the entity's own model by the brighter of its own glow and the light where it stands.
    @Inject(method = "getBrightnessForRender", at = @At("RETURN"), cancellable = true)
    private void impetus$brightnessForRender(CallbackInfoReturnable<Integer> cir) {
        if (!DynamicLights.options().mode.isEnabled()) {
            return;
        }

        cir.setReturnValue(DynamicLights.engine()
                .getLightmapWithDynamicLight((Entity) (Object) this, cir.getReturnValueI()));
    }

    @Inject(method = "onRemovedFromWorld", at = @At("TAIL"))
    private void impetus$onRemoved(CallbackInfo ci) {
        if (this.world.isRemote) {
            this.impetus$setDynamicLightEnabled(false);
        }
    }

    // DynamicLightSource

    @Override
    public double impetus$getDynamicLightX() {
        return this.posX;
    }

    // Eye height, not feet: a held torch is at head level, and the falloff is measured from it.
    @Override
    public double impetus$getDynamicLightY() {
        return this.posY + this.getEyeHeight();
    }

    @Override
    public double impetus$getDynamicLightZ() {
        return this.posZ;
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
        Entity self = (Entity) (Object) this;

        if (!DynamicLights.options().entitiesLightSource || !DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$luminance = 0;
            return;
        }

        int burning = this.isBurning() ? 14 : 0;
        this.impetus$luminance = Math.max(burning, DynamicLightHandlers.getLuminanceFrom(self));
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

    // Re-lights surrounding chunks if the entity moved 0.1+ blocks (keeps a standing player from re-meshing every frame) or changed brightness
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

        // Whatever is left in the old set is a chunk this source moved away from and still needs rebuilding to lose the light
        this.impetus$scheduleTrackedChunksRebuild(renderer);
        this.impetus$trackedLitChunkPos = newPos;
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
