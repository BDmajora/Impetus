package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFarPlaneMixin {
    // Reference to the field far plane distance of the target.
    @Shadow
    private float farPlaneDistance;

    // Runs inside setupCameraTransform.
    @Inject(method = "setupCameraTransform", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;farPlaneDistance:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void impetus$extendFarPlane(float partialTicks, int pass, CallbackInfo ci) {
        this.farPlaneDistance = MeshTerrainRenderer.extendFarPlane(this.farPlaneDistance);
    }
}
