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

// The GL state hand-offs between the pipeline and vanilla: main framebuffer, fixed-function matrices, blend, vertex arrays, scratch texture binds and error checks
public final class GlState {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final int GL_BACK_BUFFER = 0x0405;
    // Fixed-function matrix modes, not in the core GL wrapper used elsewhere
    static final int GL_MODELVIEW_MODE = 0x1700;
    static final int GL_PROJECTION_MODE = 0x1701;
    static final int GL_TEXTURE_MODE = 0x1702;
    // gl_Vertex/gl_Normal/gl_Color/gl_MultiTexCoord0..7 all alias generic slots below this
    private static final int VANILLA_ALIASED_ATTRIBUTE_SLOTS = 16;
    // GL keeps at most one flag per error kind, so a context still reporting after this many reads is stuck (lost) and draining stops instead of spinning
    private static final int MAX_DRAINED_ERRORS = 64;

    private GlState() {
    }

    // Back to vanilla's framebuffer
    public static void bindMainRenderTarget(Minecraft mc) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            LWJGL.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            LWJGL.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    // Resets draw and read buffers after an FBO with custom ones
    public static void restoreMainDrawReadBuffers() {
        int buffer = OpenGlHelper.isFramebufferEnabled() ? GL30.GL_COLOR_ATTACHMENT0 : GL_BACK_BUFFER;
        LWJGL.glDrawBuffers(buffer);
        LWJGL.glReadBuffer(buffer);
    }

    // The ortho projection mapping the [0,1] quad to NDC (Umbra's composite gl_ProjectionMatrix) with identity modelview and texture, so `gl_Position = ftransform()` (Complementary/BSL) resolves; saved for popFixedFunctionMatrices
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

    // Restores the matrices pushFullscreenMatrices saved
    static void popFixedFunctionMatrices() {
        GlStateManager.matrixMode(GL_PROJECTION_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_TEXTURE_MODE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL_MODELVIEW_MODE);
        GlStateManager.popMatrix();
    }

    // Hands the fixed-function vertex pipeline back to vanilla: it submits through client arrays aliasing generic slots (0 gl_Vertex, 2 gl_Normal, 3 gl_Color, 8..15 texcoords), and a leftover generic array wins and flattens the attribute (a constant gl_MultiTexCoord0 gave a flat green arm), baked into ModelRenderer's display list on first compile
    public static void resetVanillaVertexArrayState() {
        LWJGL.glBindVertexArray(0);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        for (int slot = 0; slot < VANILLA_ALIASED_ATTRIBUTE_SLOTS; slot++) {
            LWJGL.glDisableVertexAttribArray(slot);
        }
    }

    // Unit 0 back to vanilla in a cache-trustworthy state: raw scratch binds desync GlStateManager's active-unit and texture caches, after which the skin bind no-ops and the arm samples the block atlas; stepping through unit 1 and a cached unbind leave real GL and the cache both on 0
    static void resyncTextureUnitZero() {
        GlTextureUnits.resetToUnit0();
        GlStateManager.bindTexture(0);
    }

    // Turns blending off per attachment after a pass that set it per attachment; maxDrawBuffers is the context's GL_MAX_DRAW_BUFFERS, read once per pipeline rather than queried per pass
    static void disableIndexedBlend(int drawBufferSlots, int maxDrawBuffers) {
        if (!LWJGL.supportsBufferBlending()) {
            return;
        }
        int slots = Math.min(drawBufferSlots, maxDrawBuffers);
        for (int slot = 0; slot < slots; slot++) {
            LWJGL.glDisablei(GL11.GL_BLEND, slot);
        }
    }

    // Blend state for the opaque terrain pass on every attachment
    static void opaqueGbufferBlend(int drawBufferSlots, int maxDrawBuffers) {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        disableIndexedBlend(drawBufferSlots, maxDrawBuffers);
    }

    // OptiFine's beginWater() contract for translucent terrain: blending AND depth writes on, since composites find water by comparing depthtex0 with depthtex1; slots past the GPU's draw buffers would be GL_INVALID_VALUE
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

    // Binds a texture on the scratch unit and returns the previous binding; custom textures may live on that unit, so it is put back
    static int bindScratchTexture2D(int texture) {
        GlTextureUnits.selectScratch(SamplerLayout.DEPTH_COPY_SCRATCH_UNIT);
        int previous = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        return previous;
    }

    // Rebinds the saved texture and hands the selector back
    static void restoreScratchTexture2D(int texture) {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlTextureUnits.resetToUnit0();
    }

    // Fails loudly with the purpose and attachments named, since an incomplete FBO renders nothing silently
    static void checkComplete(UmbraFramebuffer framebuffer, String purpose, int[] buffers) {
        int status = framebuffer.getStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Umbra " + purpose + " framebuffer for buffers "
                    + Arrays.toString(buffers) + ": status=" + status);
        }
    }

    // The block-atlas size for the atlasSize/terrainTextureSize uniforms
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

    // Clears pending GL errors so the next check is attributable
    public static void drainGlError() {
        for (int i = 0; i < MAX_DRAINED_ERRORS && LWJGL.glGetError() != 0; i++) {
            // discard
        }
    }

    // Logs a pending GL error with the call site
    public static void reportGlError(String where) {
        int error = LWJGL.glGetError();
        if (error != 0) {
            LOGGER.warn("[Umbra] GL error 0x{} ({}) at: {}", Integer.toHexString(error), error, where);
        }
    }
}
