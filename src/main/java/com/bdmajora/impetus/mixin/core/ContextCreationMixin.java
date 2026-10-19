package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.impl.platform.ContextCreation;
import com.cleanroommc.client.sdl.Window;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class ContextCreationMixin {
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lcom/cleanroommc/client/sdl/Window$Builder;build()Lcom/cleanroommc/client/sdl/Window;", remap = false))
    private Window impetus$createWindow(Window.Builder builder, Operation<Window> original) {
        return ContextCreation.create(() -> original.call(builder));
    }
}
