package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.BitSet;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// colortex4..7 (gaux1..4) for the gbuffer stage on OptiFine's aux units 7..10, plus the copies that keep a program that both writes and samples one of them from reading the target it is drawing into (undefined in GL)
public final class GbufferFeedback {
    // GL_CURRENT_PROGRAM, a literal because the generated GL constant classes do not carry it
    static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int UNKNOWN = -1;

    private final UmbraRenderTargets targets;
    // Per linked program, which of colortex4..7 it declares a sampler for as a bit mask, resolved once since glGetUniformLocation is a driver call; program ids are per pipeline and the map dies with it
    private final Int2IntOpenHashMap sampledByProgram = new Int2IntOpenHashMap();
    // Scratch read FBO used to snapshot a gbuffer colour target before a program reads and writes it
    private UmbraFramebuffer copyFramebuffer;

    public GbufferFeedback(UmbraRenderTargets targets) {
        this.targets = targets;
        this.sampledByProgram.defaultReturnValue(UNKNOWN);
    }

    // The flips to bind colortex4..7 with for a program writing drawBuffers: a target the BOUND program both writes and samples is copied front->back and read from the copy; the copy is a full-size glCopyTexSubImage2D, so it is skipped for targets the program never samples (dozens of full-screen copies a frame on a pack writing colortex6 everywhere)
    public BitSet prepare(int[] drawBuffers, BitSet activeFlips) {
        BitSet flips = null;
        int sampled = sampledGaux();
        for (int index : drawBuffers) {
            if (SamplerLayout.gbufferColorUnit(index) < 0 || this.targets.get(index) == null
                    || (sampled & (1 << index)) == 0) {
                continue;
            }
            copyFrontToBack(index, activeFlips.get(index));
            if (flips == null) {
                flips = (BitSet) activeFlips.clone();
            }
            flips.flip(index);
        }
        return flips == null ? activeFlips : flips;
    }

    // Which of colortex4..7 (by either name) the bound program samples, as bits 4..7; every bit with no program bound, keeping the copy-everything behaviour for that caller
    int sampledGaux() {
        int program = LWJGL.glGetInteger(GL_CURRENT_PROGRAM);
        if (program == 0) {
            return ~0;
        }
        int sampled = this.sampledByProgram.get(program);
        if (sampled == UNKNOWN) {
            sampled = 0;
            for (int i = SamplerLayout.GAUX_FIRST; i <= SamplerLayout.GAUX_LAST; i++) {
                if (LWJGL.glGetUniformLocation(program, "colortex" + i) != -1
                        || LWJGL.glGetUniformLocation(program, UmbraRenderTargets.LEGACY_COLOR_TARGETS[i]) != -1) {
                    sampled |= 1 << i;
                }
            }
            this.sampledByProgram.put(program, sampled);
        }
        return sampled;
    }

    // Copies the front side into the back at the target's own size; a size.buffer target copied at the screen size was an invalid region
    private void copyFrontToBack(int index, boolean flipped) {
        if (this.copyFramebuffer == null) {
            this.copyFramebuffer = new UmbraFramebuffer();
        }
        this.copyFramebuffer.addColorAttachment(0, 0, GbufferTargets.front(this.targets, flipped, index));
        this.copyFramebuffer.bindAsReadBuffer();
        LWJGL.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int previous = GlState.bindScratchTexture2D(GbufferTargets.back(this.targets, flipped, index));
        try {
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    this.targets.getWidth(index), this.targets.getHeight(index));
        } finally {
            GlState.restoreScratchTexture2D(previous);
        }
    }

    // Binds colortex4..7 for the gbuffer phase (MakeUp reads gaux4 in gbuffers_terrain as the fog colour; a stale unit 7 blew the horizon out and dragged auto-exposure down); units 0..3 (atlas, lightmap, PBR) are left alone
    public void bindColorSamplers(BitSet flips) {
        for (int i = SamplerLayout.GAUX_FIRST; i <= SamplerLayout.GAUX_LAST; i++) {
            UmbraRenderTarget target = this.targets.get(i);
            if (target == null) {
                continue;
            }
            int texture = flips.get(i) ? target.getAltTexture() : target.getMainTexture();
            int unit = SamplerLayout.gbufferColorUnit(i);
            if (unit < GlTextureUnits.CACHED_UNITS) {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager.bindTexture(texture);
            } else {
                GlTextureUnits.selectScratch(unit);
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            }
        }
        // A bare setActiveTexture here can be swallowed after the raw branch; resetToUnit0 steps through unit 1
        GlTextureUnits.resetToUnit0();
    }

    // Frees the copy FBO and forgets the per-program masks
    public void destroy() {
        this.sampledByProgram.clear();
        if (this.copyFramebuffer != null) {
            this.copyFramebuffer.destroy();
            this.copyFramebuffer = null;
        }
    }
}
