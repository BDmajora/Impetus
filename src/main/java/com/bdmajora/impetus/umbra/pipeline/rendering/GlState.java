package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Saver and restorer of the GL state around pipeline passes.
public final class GlState {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Gl back buffer.
    private static final int GL_BACK_BUFFER = 0x0405;
    // Gl modelview mode.
    static final int GL_MODELVIEW_MODE = 0x1700;
    // Gl projection mode.
    static final int GL_PROJECTION_MODE = 0x1701;
    // Gl texture mode.
    static final int GL_TEXTURE_MODE = 0x1702;
    // Vanilla aliased attribute slots.
    private static final int VANILLA_ALIASED_ATTRIBUTE_SLOTS = 16;
    // Max drained errors.
    private static final int MAX_DRAINED_ERRORS = 64;

    // Prevents instantiation
    private GlState() {
    }

    // Binds the vanilla framebuffer.
    public static void bindMainRenderTarget(Minecraft mc) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            LWJGL.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            LWJGL.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    // Restores the draw and read buffers of the vanilla framebuffer.
    public static void restoreMainDrawReadBuffers() {
        int buffer = OpenGlHelper.isFramebufferEnabled() ? GL30.GL_COLOR_ATTACHMENT0 : GL_BACK_BUFFER;
        LWJGL.glDrawBuffers(buffer);
        LWJGL.glReadBuffer(buffer);
    }

    // Pushes identity matrices for a fullscreen draw.
    static void pushFullscreenMatrices() {
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
    }

    // Pops the fixed function matrices.
    static void popFixedFunctionMatrices() {
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.popMatrix();
    }

    // Turns off the vanilla vertex arrays.
    public static void resetVanillaVertexArrayState() {
        LWJGL.glBindVertexArray(0);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        for (int slot = 0; slot < VANILLA_ALIASED_ATTRIBUTE_SLOTS; slot++) {
            LWJGL.glDisableVertexAttribArray(slot);
        }
    }

    // Reselects unit zero in GL and the state manager.
    static void resyncTextureUnitZero() {
        GlTextureUnits.resetToUnit0();
        GlStateManager.bindTexture(0);
    }

    // Turns off blending on every draw buffer.
    static void disableIndexedBlend(int drawBufferSlots, int maxDrawBuffers) {
        if (!LWJGL.supportsBufferBlending()) {
            return;
        }
        int slots = Math.min(drawBufferSlots, maxDrawBuffers);
        for (int slot = 0; slot < slots; slot++) {
            LWJGL.glDisablei(GL11.GL_BLEND, slot);
        }
    }

    // Sets opaque blending on the draw buffers.
    static void opaqueGbufferBlend(int drawBufferSlots, int maxDrawBuffers) {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        disableIndexedBlend(drawBufferSlots, maxDrawBuffers);
    }

    // Sets translucent blending on the draw buffers.
    static void translucentGbufferBlend(int drawBufferSlots, int maxDrawBuffers) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(true);
        if (!LWJGL.supportsBufferBlending()) {
            return;
        }
        int slots = Math.min(drawBufferSlots, maxDrawBuffers);
        for (int slot = 0; slot < slots; slot++) {
            LWJGL.glEnablei(GL11.GL_BLEND, slot);
            LWJGL.glBlendFuncSeparatei(slot, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    // Binds a texture on the scratch unit and returns the old one.
    static int bindScratchTexture2D(int texture) {
        GlTextureUnits.selectScratch(SamplerLayout.DEPTH_COPY_SCRATCH_UNIT);
        int previous = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        return previous;
    }

    // Rebinds the old texture on the scratch unit.
    static void restoreScratchTexture2D(int texture) {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlTextureUnits.resetToUnit0();
    }

    // Logs a framebuffer that is not complete.
    static void checkComplete(UmbraFramebuffer framebuffer, String purpose, int[] buffers) {
        int status = framebuffer.getStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Umbra " + purpose + " framebuffer for buffers "
                    + Arrays.toString(buffers) + ": status=" + status);
        }
    }

    // Records the size of the block atlas.
    static void captureAtlasSize(Minecraft mc) {
        ITextureObject atlas = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (atlas == null) {
            return;
        }
        int previous = bindScratchTexture2D(atlas.getGlTextureId());
        try {
            int width = LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            CapturedRenderingState.INSTANCE.setAtlasSize(width, height);
        } finally {
            restoreScratchTexture2D(previous);
        }
    }

    // Clears pending GL errors.
    public static void drainGlError() {
        for (int i = 0; i < MAX_DRAINED_ERRORS && LWJGL.glGetError() != 0; i++) {
        }
    }

    // Logs pending GL errors with a place.
    public static void reportGlError(String where) {
        int error = LWJGL.glGetError();
        if (error != 0) {
            LOGGER.warn("[Umbra] GL error 0x{} ({}) at: {}", Integer.toHexString(error), error, where);
        }
    }
}
