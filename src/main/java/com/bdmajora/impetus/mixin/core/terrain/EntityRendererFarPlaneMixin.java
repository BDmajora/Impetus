package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Moves the far plane, and with it vanilla's fog end, out to the mesh backend's region keep distance, so terrain kept on the GPU past the render distance is neither clipped nor fogged away
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFarPlaneMixin {
    @Shadow
    private float farPlaneDistance;

    // Right after setupCameraTransform derives it from the render distance, before the projection and fog read it
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void impetus$extendFarPlane(float partialTicks, int pass, CallbackInfo ci) {
        this.farPlaneDistance = MeshTerrainRenderer.extendFarPlane(this.farPlaneDistance);
    }
}
