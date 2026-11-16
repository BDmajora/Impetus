package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

// Mixin that changes ActiveRenderInfo.
@Mixin(ActiveRenderInfo.class)
public interface ActiveRenderInfoAccessor {
    // Gives access to the field get projection matrix.
    @Accessor("PROJECTION")
    static FloatBuffer getProjectionMatrix() {
        throw new AssertionError();
    }

    // Gives access to the field get model view matrix.
    @Accessor("MODELVIEW")
    static FloatBuffer getModelViewMatrix() {
        throw new AssertionError();
    }
}
