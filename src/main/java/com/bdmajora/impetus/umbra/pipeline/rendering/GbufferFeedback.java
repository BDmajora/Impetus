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

// Copies gbuffer targets that a program samples while writing them so it reads the previous contents.
public final class GbufferFeedback {
    // GL enum of the current program.
    static final int GL_CURRENT_PROGRAM = 0x8B8D;
    // Marker of a program not inspected yet.
    private static final int UNKNOWN = -1;

    // Color targets of the pack.
    private final UmbraRenderTargets targets;
    // Bits of the gaux targets each program samples.
    private final Int2IntOpenHashMap sampledByProgram = new Int2IntOpenHashMap();
    // Framebuffer that reads the copied target or null.
    private UmbraFramebuffer copyFramebuffer;

    // Creates feedback for the targets of a pack.
    public GbufferFeedback(UmbraRenderTargets targets) {
        this.targets = targets;
        this.sampledByProgram.defaultReturnValue(UNKNOWN);
    }

    // Copies each written target the bound program samples and returns the flips with those targets flipped.
    public BitSet prepare(int[] drawBuffers, BitSet activeFlips) {
        return prepare(LWJGL.glGetInteger(GL_CURRENT_PROGRAM), drawBuffers, activeFlips);
    }

    // Same as prepare for a program the caller just bound, without asking GL.
    public BitSet prepare(int program, int[] drawBuffers, BitSet activeFlips) {
        BitSet flips = null;
        int sampled = sampledGaux(program);
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

    // Returns the bits of the gaux targets a program samples, every bit for no program, and inspects each once.
    int sampledGaux(int program) {
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

    // Copies the read texture of a target into its write texture.
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

    // Binds the gaux textures under the current flips to their units.
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
        GlTextureUnits.resetToUnit0();
    }

    // Forgets the inspected programs and destroys the copy framebuffer.
    public void destroy() {
        this.sampledByProgram.clear();
        if (this.copyFramebuffer != null) {
            this.copyFramebuffer.destroy();
            this.copyFramebuffer = null;
        }
    }
}
