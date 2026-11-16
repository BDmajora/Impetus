package com.bdmajora.extras.mixin.particle;

import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Mixin that changes ParticleManager.
@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {
    // Gives access to the field get particle types.
    @Accessor("particleTypes")
    Map<Integer, IParticleFactory> impetus$getParticleTypes();
}
