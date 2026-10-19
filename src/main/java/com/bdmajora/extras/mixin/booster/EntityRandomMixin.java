package com.bdmajora.extras.mixin.booster;

import com.bdmajora.extras.client.booster.FastRandom;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

// Every entity constructs its own java.util.Random; under GPU Booster it gets a FastRandom instead, one per entity so the integrated server's entities and the client's never share state
@Mixin(Entity.class)
public class EntityRandomMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/util/Random", remap = false))
    private Random impetus$fastRandom() {
        return FastRandom.enabled ? new FastRandom() : new Random();
    }
}
