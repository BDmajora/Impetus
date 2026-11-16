package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.lighting.LightingEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements LightingEngineProvider {
    // Field lighting engine added to the target.
    @Unique
    private LightingEngine fulgor$lightingEngine;

    // Runs before every return of constructor.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void fulgor$createLightingEngine(CallbackInfo ci) {
        this.fulgor$lightingEngine = new LightingEngine((World) (Object) this);
    }

    // Runs at the start of checkLightFor and can cancel it.
    @Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true)
    private void fulgor$scheduleLightUpdate(EnumSkyBlock lightType, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        this.fulgor$lightingEngine.scheduleLightUpdate(lightType, pos);

        cir.setReturnValue(true);
    }

    // Returns the lighting engine.
    @Override
    public LightingEngine fulgor$getLightingEngine() {
        return this.fulgor$lightingEngine;
    }
}
