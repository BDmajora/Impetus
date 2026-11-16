package com.bdmajora.extras.mixin.particle;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ParticleManager.
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    // Runs at the start of registerParticle.
    @Inject(method = "registerParticle", at = @At("HEAD"))
    private void impetus$captureFactoryMod(int id, IParticleFactory particleFactory, CallbackInfo ci) {
        if (particleFactory == null) {
            return;
        }

        String modId = null;
        try {
            ModContainer container = Loader.instance().activeModContainer();
            if (container != null) {
                modId = container.getModId();
            }
        } catch (Throwable t) {
        }

        ParticleClassRegistry.getInstance().registerFactoryMod(particleFactory, modId);
    }

    // Runs at the start of addBlockDestroyEffects and can cancel it.
    @Inject(method = "addBlockDestroyEffects", at = @At("HEAD"), cancellable = true)
    private void impetus$blockDestroyEffects(BlockPos pos, IBlockState state, CallbackInfo ci) {
        ExtrasConfig.ParticleSettings settings = Extras.options().particle;
        if (!settings.all || !settings.blockBreak) {
            ci.cancel();
        }
    }

    // Runs at the start of addBlockHitEffects and can cancel it.
    @Inject(
            method = "addBlockHitEffects(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void impetus$blockHitEffects(BlockPos pos, EnumFacing side, CallbackInfo ci) {
        ExtrasConfig.ParticleSettings settings = Extras.options().particle;
        if (!settings.all || !settings.blockBreaking) {
            ci.cancel();
        }
    }

    // Runs at the start of addEffect and can cancel it.
    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void impetus$filterEffect(Particle effect, CallbackInfo ci) {
        if (effect == null) {
            return;
        }

        ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
        registry.recordClass(effect.getClass());

        if (!Extras.options().particle.all) {
            ci.cancel();
            return;
        }

        if (!registry.isEmptyDisabled() && registry.isClassDisabled(effect.getClass().getName())) {
            ci.cancel();
            return;
        }

        if (RenderBudgetController.shouldCullParticle(effect)) {
            ci.cancel();
        }
    }
}
