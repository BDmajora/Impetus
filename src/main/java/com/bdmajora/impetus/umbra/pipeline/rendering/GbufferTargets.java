package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The gbuffer framebuffers world rendering is redirected into: every gbuffer-stage target's front side plus the shared depth, attached densely; the draw-buffer mask is switched per program OptiFine-style
public final class GbufferTargets {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Draw-buffer mask for fixed-function content with no pack program: plain colour into colortex0 only
    public static final int[] FIXED_FUNCTION_MASK = {0};

    private final UmbraRenderTargets targets;
    // Every colour index attached, the union of all gbuffer-stage DRAWBUFFERS masks plus colortex0, ascending and capped at the driver's attachment count
    private final int[] attachments;
    // Logical colortex index -> physical attachment point, -1 when unattached
    private final int[] pointByIndex = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    private final int maxDrawBuffers;
    private final int maxAttachments;
    // Reused output for drawBuffers, which runs on every phase switch
    private int[] physicalScratch = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Logical indices already reported unattached, so a pack writing one does not log on every phase switch
    private int warnedUnattached;
    private boolean warnedTooMany;
    // The gbuffer FBO the world is rendering into right now (switches after the deferred chain)
    UmbraFramebuffer current;

    public GbufferTargets(ShaderPack pack, GbufferPrograms programs, Map<String, String> defines, UmbraRenderTargets targets) {
        this.targets = targets;
        int reportedDrawBuffers = LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS);
        int reportedAttachments = LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        this.maxDrawBuffers = reportedDrawBuffers > 0 ? reportedDrawBuffers : Integer.MAX_VALUE;
        this.maxAttachments = reportedAttachments > 0 ? reportedAttachments : UmbraRenderTargets.MAX_COLOR_BUFFERS;
        BitSet union = new BitSet();
        // colortex0 must exist, it is what final/blit shows
        union.set(0);
        mark(union, programDrawBuffers(pack, ProgramId.Terrain, "gbuffers_terrain", defines));
        mark(union, programDrawBuffers(pack, ProgramId.Water, "gbuffers_water", defines));
        for (GbufferPrograms.Entry entry : programs.entries()) {
            mark(union, entry.drawBuffersReadOnly());
        }
        int[] all = union.stream().toArray();
        if (all.length > this.maxAttachments) {
            // Past the attachment limit the FBO cannot be built at all, which used to fail the whole pipeline; the highest targets are dropped and their outputs discarded instead
            LOGGER.warn("[Umbra] Gbuffer programs write {} colour targets but this GPU attaches {}; dropping colortex{}",
                    all.length, this.maxAttachments, Arrays.toString(Arrays.copyOfRange(all, this.maxAttachments, all.length)));
        }
        this.attachments = Arrays.copyOf(all, Math.min(all.length, this.maxAttachments));
        Arrays.fill(this.pointByIndex, -1);
        for (int point = 0; point < this.attachments.length; point++) {
            int index = this.attachments[point];
            this.pointByIndex[index] = point;
            // A gbuffer FBO mixing attachment sizes renders into their intersection, silently shrinking the world pass; packs size reflection/bloom buffers this way but never gbuffer outputs, so surface it as a pack bug
            if (targets.hasCustomSize(index)) {
                LOGGER.warn("[Umbra] colortex{} declares its own size but is also a gbuffer output; the world pass "
                        + "would be clipped to {}x{}", index, targets.getWidth(index), targets.getHeight(index));
            }
        }
    }

    private static void mark(BitSet union, int[] buffers) {
        for (int buffer : buffers) {
            union.set(buffer);
        }
    }

    // One gbuffer program's mask against the macros it compiles with; a legacy-branch program declares a different one (Sildur's gbuffers_water is 41 with IS_IRIS, 412 without)
    static int[] programDrawBuffers(ShaderPack pack, ProgramId id, String fallbackName, Map<String, String> defines) {
        ProgramSource source = pack.getProgramSet().get(id).orElse(null);
        String fragment = source == null ? null : source.getFragmentSource().orElse(null);
        if (fragment == null) {
            return DrawBuffers.DEFAULT.clone();
        }
        String name = source.getName();
        return DrawBuffers.sanitize(DrawBuffers.parseActive(fragment, ShaderMacros.forProgram(defines, name)),
                SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
    }

    // Gbuffer-program draw buffers: packs address logical colortex indices that the shared gbuffer FBO maps onto dense attachments; an out-of-range index is normally an inactive #ifdef path and is dropped silently. Read-only result
    public static int[] sanitize(int[] drawBuffers) {
        return DrawBuffers.sanitize(drawBuffers, SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
    }

    // The attached logical indices, ascending
    public int[] attachments() {
        return this.attachments.clone();
    }

    // The front texture a pass reads for a colortex under a flip state, materialising the target
    static int front(UmbraRenderTargets targets, boolean flipped, int index) {
        UmbraRenderTarget target = targets.getOrCreate(index);
        return flipped ? target.getAltTexture() : target.getMainTexture();
    }

    // The back texture, the one a pass writes
    static int back(UmbraRenderTargets targets, boolean flipped, int index) {
        return front(targets, !flipped, index);
    }

    // A gbuffer FBO over the current front sides plus depth, colour-only to start; destroyed again if the driver calls it incomplete
    public UmbraFramebuffer create(BufferFlipper flipper) {
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        try {
            for (int point = 0; point < this.attachments.length; point++) {
                int index = this.attachments[point];
                framebuffer.addColorAttachment(index, point, front(this.targets, flipper.isFlipped(index), index));
            }
            framebuffer.addDepthAttachment(this.targets.getDepthTexture().getTextureId());
            drawBuffers(framebuffer, FIXED_FUNCTION_MASK);
            GlState.checkComplete(framebuffer, "gbuffer", this.attachments);
            return framebuffer;
        } catch (RuntimeException e) {
            framebuffer.destroy();
            throw e;
        }
    }

    // Sets the FBO's draw buffers from a program's logical colortex list; an unattached target goes to GL_NONE (naming colortex0 twice is a GL error), and a mask longer than the GPU allows is cut rather than thrown mid-frame
    public void drawBuffers(UmbraFramebuffer framebuffer, int[] logical) {
        int count = logical.length;
        if (count > this.maxDrawBuffers) {
            if (!this.warnedTooMany) {
                this.warnedTooMany = true;
                LOGGER.warn("[Umbra] A gbuffer program writes {} targets but this GPU draws {}; the rest are discarded",
                        count, this.maxDrawBuffers);
            }
            count = this.maxDrawBuffers;
        }
        if (this.physicalScratch.length < count) {
            this.physicalScratch = new int[count];
        }
        int[] physical = this.physicalScratch;
        int written = 0;
        for (int slot = 0; slot < count; slot++) {
            int index = logical[slot];
            int point = index >= 0 && index < this.pointByIndex.length ? this.pointByIndex[index] : -1;
            if (point < 0) {
                warnUnattached(index, slot);
            } else {
                written |= 1 << index;
            }
            physical[slot] = point;
        }
        // Umbra parity: the FBO holds ONLY what the program writes, so gbuffers_terrain sampling gaux4 for fog reads a detached texture rather than a feedback loop's garbage
        framebuffer.retainColorAttachments(written);
        framebuffer.drawBuffers(physical, count);
    }

    private void warnUnattached(int index, int slot) {
        int bit = index >= 0 && index < Integer.SIZE ? 1 << index : 0;
        if (bit != 0 && (this.warnedUnattached & bit) != 0) {
            return;
        }
        this.warnedUnattached |= bit;
        LOGGER.warn("[Umbra] Gbuffer draw buffer colortex{} is not attached; discarding output slot {}", index, slot);
    }

    // A framebuffer for a Distant Horizons LOD pass over the gbuffer targets the DH program writes, front sides of the phase it runs in, attached densely in draw-buffer order; DH's own depth is attached by the compat. Owned by the caller
    public UmbraFramebuffer createDh(int[] drawBuffers, BitSet flips) {
        int[] sanitized = DrawBuffers.sanitize(drawBuffers, SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
        int count = Math.min(sanitized.length, Math.min(this.maxAttachments, this.maxDrawBuffers));
        if (count < sanitized.length) {
            LOGGER.warn("[Umbra] A Distant Horizons program writes {} targets but this GPU attaches {}; the rest are discarded",
                    sanitized.length, count);
        }
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        try {
            int[] dense = new int[count];
            for (int slot = 0; slot < count; slot++) {
                int index = sanitized[slot];
                framebuffer.addColorAttachment(index, slot, front(this.targets, flips.get(index), index));
                dense[slot] = slot;
            }
            framebuffer.drawBuffers(dense);
            return framebuffer;
        } catch (RuntimeException e) {
            framebuffer.destroy();
            throw e;
        }
    }
}
