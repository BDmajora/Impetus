package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.panini.ShaderGroupAccessor;
import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;

// Post effect that applies a Panini projection.
public final class PaniniProjection {
    // Location of the shader chain.
    private static final ResourceLocation CHAIN = new ResourceLocation("impetus", "shaders/post/panini.json");
    // Name of the parameter uniform.
    private static final String CONFIG_UNIFORM = "PaniniParams";
    // Matrix mode of the texture matrix.
    private static final int GL_TEXTURE = 5890;

    // Loaded shader group or null.
    private static ShaderGroup shaderGroup;
    // Width of the framebuffers.
    private static int framebufferWidth;
    // Height of the framebuffers.
    private static int framebufferHeight;
    // Whether loading failed.
    private static boolean failed;

    // Horizontal extent of the view.
    private static float horizontalExtent = 1.0F;
    // Vertical extent of the view.
    private static float verticalExtent = 1.0F;

    // Prevents instantiation
    private PaniniProjection() {
    }

    // Stores the extent of the view from the projection.
    public static void captureProjection(float m00, float m11) {
        if (m00 != 0.0F && m11 != 0.0F) {
            horizontalExtent = Math.abs(1.0F / m00);
            verticalExtent = Math.abs(1.0F / m11);
        }
    }

    // Applies the post effect to the frame.
    public static void render(float partialTicks) {
        if (!shouldApply()) {
            release();
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ensureShaderGroup(minecraft)) {
            return;
        }

        updateUniforms();

        GlStateManager.matrixMode(GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        shaderGroup.render(partialTicks);
        GlStateManager.popMatrix();

        minecraft.getFramebuffer().bindFramebuffer(true);
    }

    // Deletes the shader group.
    public static void release() {
        if (shaderGroup != null) {
            shaderGroup.deleteShaderGroup();
            shaderGroup = null;
        }
    }

    // Returns whether the effect applies.
    private static boolean shouldApply() {
        ExtrasConfig.ExtraSettings settings = Extras.options().extra;

        if (failed
                || !settings.paniniProjection
                || settings.paniniProjectionStrength <= 0
                || Extras.options().render.preventShaders
                || !OpenGlHelper.shadersSupported) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.getRenderViewEntity() == null) {
            return false;
        }

        return Umbra.getRenderingPipeline() == null;
    }

    // Returns whether the shader group is ready and loads it when needed.
    private static boolean ensureShaderGroup(Minecraft minecraft) {
        int width = minecraft.displayWidth;
        int height = minecraft.displayHeight;

        if (width <= 0 || height <= 0) {
            return false;
        }

        if (shaderGroup != null && (width != framebufferWidth || height != framebufferHeight)) {
            release();
        }

        if (shaderGroup == null) {
            try {
                shaderGroup = new ShaderGroup(minecraft.getTextureManager(), minecraft.getResourceManager(),
                        minecraft.getFramebuffer(), CHAIN);
                shaderGroup.createBindFramebuffers(width, height);
                framebufferWidth = width;
                framebufferHeight = height;
            } catch (Exception e) {
                failed = true;
                shaderGroup = null;
                Extras.LOGGER.error("Could not load the Panini projection post effect; disabling it", e);
                return false;
            }
        }

        return true;
    }

    // Stores the strength and extents in the shader.
    private static void updateUniforms() {
        float strength = Extras.options().extra.paniniProjectionStrength / 100.0F;

        for (Shader shader : ((ShaderGroupAccessor) shaderGroup).impetus$getShaders()) {
            ShaderUniform uniform = shader.getShaderManager().getShaderUniform(CONFIG_UNIFORM);
            if (uniform != null) {
                uniform.set(strength, horizontalExtent, verticalExtent, 0.0F);
                return;
            }
        }
    }
}
