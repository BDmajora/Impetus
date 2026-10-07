package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.impl.platform.ContextCreation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// The window's context hints and first mitigations; the whole method is wrapped rather than the Display.create call inside, whose owner Cleanroom relocates to lwjglx
@Mixin(Minecraft.class)
public class ContextCreationMixin {
    @WrapMethod(method = "createDisplay")
    private void impetus$createDisplay(Operation<Void> original) {
        ContextCreation.create(original::call);
    }
}
