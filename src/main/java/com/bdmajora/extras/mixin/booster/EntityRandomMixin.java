package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastRandom;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

// Mixin that changes Entity.
@Mixin(Entity.class)
public class EntityRandomMixin {
    // Replaces the creation of Random in constructor.
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/util/Random", remap = false))
    private Random impetus$fastRandom() {
        return FastRandom.enabled ? new FastRandom() : new Random();
    }
}
