package com.bdmajora.extras.mixin.models;

import com.bdmajora.extras.Extras;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;
import java.util.List;

// Vanilla positions each model box with up to six GL matrix calls (translate offset, translate pivot, three rotates, translate back), each a JNI crossing; the same transform is one 4x4 multiply built here (Valkyrie's ModelRenderer path): the composite of T(offset + pivot) and Rz*Ry*Rx written straight into column-major form. A box with no transform at all skips even the push/pop
@Mixin(ModelRenderer.class)
public abstract class ModelRendererMatrixMixin {
    @Shadow
    public float rotationPointX;

    @Shadow
    public float rotationPointY;

    @Shadow
    public float rotationPointZ;

    @Shadow
    public float rotateAngleX;

    @Shadow
    public float rotateAngleY;

    @Shadow
    public float rotateAngleZ;

    @Shadow
    public float offsetX;

    @Shadow
    public float offsetY;

    @Shadow
    public float offsetZ;

    @Shadow
    private boolean compiled;

    @Shadow
    private int displayList;

    @Shadow
    public boolean showModel;

    @Shadow
    public boolean isHidden;

    @Shadow
    public List<ModelRenderer> childModels;

    @Shadow
    protected abstract void compileDisplayList(float scale);

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void impetus$renderWithMatrix(float scale, CallbackInfo ci) {
        if (!Extras.options().entityModels.matrixTransforms) {
            return;
        }
        ci.cancel();
        if (this.isHidden || !this.showModel) {
            return;
        }
        if (!this.compiled) {
            this.compileDisplayList(scale);
        }
        if (!this.impetus$hasTransform()) {
            GlStateManager.callList(this.displayList);
            this.impetus$renderChildren(scale);
            return;
        }
        GlStateManager.pushMatrix();
        this.impetus$multiply(scale, true, false);
        GlStateManager.callList(this.displayList);
        this.impetus$renderChildren(scale);
        GlStateManager.popMatrix();
    }

    @Inject(method = "renderWithRotation", at = @At("HEAD"), cancellable = true)
    private void impetus$renderWithRotationMatrix(float scale, CallbackInfo ci) {
        if (!Extras.options().entityModels.matrixTransforms) {
            return;
        }
        ci.cancel();
        if (this.isHidden || !this.showModel) {
            return;
        }
        if (!this.compiled) {
            this.compileDisplayList(scale);
        }
        GlStateManager.pushMatrix();
        // renderWithRotation applies its rotations in Y, X, Z order, unlike render's Z, Y, X
        this.impetus$multiply(scale, false, true);
        GlStateManager.callList(this.displayList);
        GlStateManager.popMatrix();
    }

    // postRender leaves the transform on the stack for whatever the caller draws next (held items, armour), so no push/pop here
    @Inject(method = "postRender", at = @At("HEAD"), cancellable = true)
    private void impetus$postRenderMatrix(float scale, CallbackInfo ci) {
        if (!Extras.options().entityModels.matrixTransforms) {
            return;
        }
        ci.cancel();
        if (this.isHidden || !this.showModel) {
            return;
        }
        if (!this.compiled) {
            this.compileDisplayList(scale);
        }
        if (this.rotateAngleX == 0.0F && this.rotateAngleY == 0.0F && this.rotateAngleZ == 0.0F) {
            if (this.rotationPointX != 0.0F || this.rotationPointY != 0.0F || this.rotationPointZ != 0.0F) {
                GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
            }
            return;
        }
        this.impetus$multiply(scale, false, false);
    }

    private boolean impetus$hasTransform() {
        return this.offsetX != 0.0F || this.offsetY != 0.0F || this.offsetZ != 0.0F
                || this.rotationPointX != 0.0F || this.rotationPointY != 0.0F || this.rotationPointZ != 0.0F
                || this.rotateAngleX != 0.0F || this.rotateAngleY != 0.0F || this.rotateAngleZ != 0.0F;
    }

    private void impetus$renderChildren(float scale) {
        if (this.childModels != null) {
            for (int i = 0, n = this.childModels.size(); i < n; i++) {
                this.childModels.get(i).render(scale);
            }
        }
    }

    // M = T(offset) * T(pivot*scale) * R, i.e. [R | t] with t = offset + pivot*scale, where R is Rz*Ry*Rx (render, postRender) or Ry*Rx*Rz (renderWithRotation); vanilla's rotate calls take degrees of these same radian fields
    private void impetus$multiply(float scale, boolean withOffset, boolean yxzOrder) {
        float tx = this.rotationPointX * scale;
        float ty = this.rotationPointY * scale;
        float tz = this.rotationPointZ * scale;
        if (withOffset) {
            tx += this.offsetX;
            ty += this.offsetY;
            tz += this.offsetZ;
        }
        float ax = this.rotateAngleX;
        float ay = this.rotateAngleY;
        float az = this.rotateAngleZ;
        if (ax == 0.0F && ay == 0.0F && az == 0.0F) {
            GlStateManager.translate(tx, ty, tz);
            return;
        }
        float cx = MathHelper.cos(ax);
        float sx = MathHelper.sin(ax);
        float cy = MathHelper.cos(ay);
        float sy = MathHelper.sin(ay);
        float cz = MathHelper.cos(az);
        float sz = MathHelper.sin(az);
        FloatBuffer m = MATRIX;
        m.clear();
        if (yxzOrder) {
            // Column 0
            m.put(cy * cz + sy * sx * sz).put(cx * sz).put(-sy * cz + cy * sx * sz).put(0.0F);
            // Column 1
            m.put(-cy * sz + sy * sx * cz).put(cx * cz).put(sy * sz + cy * sx * cz).put(0.0F);
            // Column 2
            m.put(sy * cx).put(-sx).put(cy * cx).put(0.0F);
        } else {
            // Column 0
            m.put(cz * cy).put(sz * cy).put(-sy).put(0.0F);
            // Column 1
            m.put(cz * sy * sx - sz * cx).put(sz * sy * sx + cz * cx).put(cy * sx).put(0.0F);
            // Column 2
            m.put(cz * sy * cx + sz * sx).put(sz * sy * cx - cz * sx).put(cy * cx).put(0.0F);
        }
        // Column 3
        m.put(tx).put(ty).put(tz).put(1.0F);
        m.flip();
        GlStateManager.multMatrix(m);
    }
}
