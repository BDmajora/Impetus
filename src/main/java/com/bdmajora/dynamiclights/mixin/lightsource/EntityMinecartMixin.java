package com.bdmajora.dynamiclights.mixin.lightsource;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lights a minecart by its display tile (glowstone by command lights the tunnel); EntityMinecart overrides onUpdate without calling up so it needs its own hook, ported from SodiumDynamicLights
@Mixin(EntityMinecart.class)
public abstract class EntityMinecartMixin extends Entity implements DynamicLightSource {
    @Shadow
    public abstract IBlockState getDisplayTile();

    @Unique
    private int impetus$minecartLuminance;

    private EntityMinecartMixin(World world) {
        super(world);
    }

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onMinecartTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    @Override
    public void impetus$dynamicLightTick() {
        Entity self = (Entity) (Object) this;

        if (!DynamicLights.options().entitiesLightSource || !DynamicLightHandlers.canEntityLightUp(self)) {
            this.impetus$minecartLuminance = 0;
            return;
        }

        IBlockState displayed = this.getDisplayTile();
        int displayLight = displayed == null ? 0 : displayed.getLightValue();

        this.impetus$minecartLuminance = Math.min(14, Math.max(
                Math.max(this.isBurning() ? 14 : 0, displayLight),
                DynamicLightHandlers.getLuminanceFrom(self)));
    }

    @Override
    public int impetus$getLuminance() {
        return this.impetus$minecartLuminance;
    }
}
