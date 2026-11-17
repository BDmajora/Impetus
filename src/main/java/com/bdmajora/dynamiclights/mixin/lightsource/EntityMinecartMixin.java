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

// Mixin that changes EntityMinecart.
@Mixin(EntityMinecart.class)
public abstract class EntityMinecartMixin extends Entity implements DynamicLightSource {
    // Reference to the method get display tile of the target.
    @Shadow
    public abstract IBlockState getDisplayTile();

    // Light level the minecart gives off now.
    @Unique
    private int impetus$minecartLuminance;

    // Creates an entity minecart mixin from its world.
    private EntityMinecartMixin(World world) {
        super(world);
    }

    // Runs at the end of onUpdate.
    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void impetus$onMinecartTick(CallbackInfo ci) {
        if (!this.world.isRemote) {
            return;
        }

        this.impetus$tickDynamicLight(this.isDead);
    }

    // Updates the minecart's light from fire and its displayed block capped at 14.
    @Override
    @SuppressWarnings("deprecation")
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

    // Returns the luminance.
    @Override
    public int impetus$getLuminance() {
        return this.impetus$minecartLuminance;
    }
}
