package com.bdmajora.extras.mixin.panini;

import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Mixin that changes ShaderGroup.
@Mixin(ShaderGroup.class)
public interface ShaderGroupAccessor {
    // Gives access to the field get shaders.
    @Accessor("listShaders")
    List<Shader> impetus$getShaders();
}
