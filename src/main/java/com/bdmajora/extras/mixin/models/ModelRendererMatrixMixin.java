package com.bdmajora.extras.mixin.models;

import com.bdmajora.extras.Extras;
import com.bdmajora.impetus.umbra.gl.program.ImmediateTessellation;
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

// Mixin that changes ModelRenderer.
@Mixin(ModelRenderer.class)
public abstract class ModelRendererMatrixMixin {
    // Reference to the field rotation point x of the target.
    @Shadow
    public float rotationPointX;

    // Reference to the field rotation point y of the target.
    @Shadow
    public float rotationPointY;

    // Reference to the field rotation point z of the target.
    @Shadow
    public float rotationPointZ;

    // Reference to the field rotate angle x of the target.
    @Shadow
    public float rotateAngleX;

    // Reference to the field rotate angle y of the target.
    @Shadow
    public float rotateAngleY;

    // Reference to the field rotate angle z of the target.
    @Shadow
    public float rotateAngleZ;

    // Reference to the field offset x of the target.
    @Shadow
    public float offsetX;

    // Reference to the field offset y of the target.
    @Shadow
    public float offsetY;

    // Reference to the field offset z of the target.
    @Shadow
    public float offsetZ;

    // Reference to the field compiled of the target.
    @Shadow
    private boolean compiled;

    // Reference to the field display list of the target.
    @Shadow
    private int displayList;

    // Reference to the field show model of the target.
    @Shadow
    public boolean showModel;

    // Reference to the field is hidden of the target.
    @Shadow
    public boolean isHidden;

    // Reference to the field child models of the target.
    @Shadow
    public List<ModelRenderer> childModels;

    // Reference to the method compile display list of the target.
    @Shadow
    protected abstract void compileDisplayList(float scale);

    // Value matrix.
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    // Runs at the start of render and can cancel it.
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
            ImmediateTessellation.callModelList((ModelRenderer) (Object) this, this.displayList, scale);
            this.impetus$renderChildren(scale);
            return;
        }
        GlStateManager.pushMatrix();
        this.impetus$multiply(scale, true, false);
        ImmediateTessellation.callModelList((ModelRenderer) (Object) this, this.displayList, scale);
        this.impetus$renderChildren(scale);
        GlStateManager.popMatrix();
    }

    // Runs at the start of renderWithRotation and can cancel it.
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
        this.impetus$multiply(scale, false, true);
        ImmediateTessellation.callModelList((ModelRenderer) (Object) this, this.displayList, scale);
        GlStateManager.popMatrix();
    }

    // Runs at the start of postRender and can cancel it.
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

    // Returns whether transform.
    private boolean impetus$hasTransform() {
        return this.offsetX != 0.0F || this.offsetY != 0.0F || this.offsetZ != 0.0F
                || this.rotationPointX != 0.0F || this.rotationPointY != 0.0F || this.rotationPointZ != 0.0F
                || this.rotateAngleX != 0.0F || this.rotateAngleY != 0.0F || this.rotateAngleZ != 0.0F;
    }

    // Handles render children.
    private void impetus$renderChildren(float scale) {
        if (this.childModels != null) {
            for (int i = 0, n = this.childModels.size(); i < n; i++) {
                this.childModels.get(i).render(scale);
            }
        }
    }

    // Handles multiply.
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
            m.put(cy * cz + sy * sx * sz).put(cx * sz).put(-sy * cz + cy * sx * sz).put(0.0F);
            m.put(-cy * sz + sy * sx * cz).put(cx * cz).put(sy * sz + cy * sx * cz).put(0.0F);
            m.put(sy * cx).put(-sx).put(cy * cx).put(0.0F);
        } else {
            m.put(cz * cy).put(sz * cy).put(-sy).put(0.0F);
            m.put(cz * sy * sx - sz * cx).put(sz * sy * sx + cz * cx).put(cy * sx).put(0.0F);
            m.put(cz * sy * cx + sz * sx).put(sz * sy * cx - cz * sx).put(cy * cx).put(0.0F);
        }
        m.put(tx).put(ty).put(tz).put(1.0F);
        m.flip();
        GlStateManager.multMatrix(m);
    }
}
