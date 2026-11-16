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

// Color attachments of the gbuffer framebuffers and the mapping from targets to attachment points.
public final class GbufferTargets {
    // Logger of attachment shortages.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Draw buffers of fixed function draws.
    public static final int[] FIXED_FUNCTION_MASK = {0};

    // Color targets of the pack.
    private final UmbraRenderTargets targets;
    // Targets attached in point order.
    private final int[] attachments;
    // Attachment point of each target or minus one.
    private final int[] pointByIndex = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Number of draw buffers the driver allows.
    private final int maxDrawBuffers;
    // Number of color attachments the driver allows.
    private final int maxAttachments;
    // Scratch of the attachment points of a draw.
    private int[] physicalScratch = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Targets already reported as unattached.
    private int warnedUnattached;
    // Reported state of too many draw buffers.
    private boolean warnedTooMany;
    // Gbuffer framebuffer of the current pass.
    UmbraFramebuffer current;

    // Attaches every target a gbuffer program writes up to the driver limit.
    public GbufferTargets(ShaderPack pack, GbufferPrograms programs, Map<String, String> defines, UmbraRenderTargets targets) {
        this.targets = targets;
        int reportedDrawBuffers = LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS);
        int reportedAttachments = LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        this.maxDrawBuffers = reportedDrawBuffers > 0 ? reportedDrawBuffers : Integer.MAX_VALUE;
        this.maxAttachments = reportedAttachments > 0 ? reportedAttachments : UmbraRenderTargets.MAX_COLOR_BUFFERS;
        BitSet union = new BitSet();
        union.set(0);
        mark(union, programDrawBuffers(pack, ProgramId.Terrain, "gbuffers_terrain", defines));
        mark(union, programDrawBuffers(pack, ProgramId.Water, "gbuffers_water", defines));
        for (GbufferPrograms.Entry entry : programs.entries()) {
            mark(union, entry.drawBuffersReadOnly());
        }
        int[] all = union.stream().toArray();
        if (all.length > this.maxAttachments) {
            LOGGER.warn("[Umbra] Gbuffer programs write {} colour targets but this GPU attaches {}; dropping colortex{}",
                    all.length, this.maxAttachments, Arrays.toString(Arrays.copyOfRange(all, this.maxAttachments, all.length)));
        }
        this.attachments = Arrays.copyOf(all, Math.min(all.length, this.maxAttachments));
        Arrays.fill(this.pointByIndex, -1);
        for (int point = 0; point < this.attachments.length; point++) {
            int index = this.attachments[point];
            this.pointByIndex[index] = point;
            if (targets.hasCustomSize(index)) {
                LOGGER.warn("[Umbra] colortex{} declares its own size but is also a gbuffer output; the world pass "
                        + "would be clipped to {}x{}", index, targets.getWidth(index), targets.getHeight(index));
            }
        }
    }

    // Adds draw buffers to a set.
    private static void mark(BitSet union, int[] buffers) {
        for (int buffer : buffers) {
            union.set(buffer);
        }
    }

    // Returns the draw buffers of a program or the default ones.
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

    // Returns the draw buffers limited to the gbuffer attachments.
    public static int[] sanitize(int[] drawBuffers) {
        return DrawBuffers.sanitize(drawBuffers, SamplerLayout.GBUFFER_ATTACHMENT_LIMIT);
    }

    // Returns a copy of the attached targets.
    public int[] attachments() {
        return this.attachments.clone();
    }

    // Returns the texture a target reads from.
    static int front(UmbraRenderTargets targets, boolean flipped, int index) {
        UmbraRenderTarget target = targets.getOrCreate(index);
        return flipped ? target.getAltTexture() : target.getMainTexture();
    }

    // Returns the texture a target writes to.
    static int back(UmbraRenderTargets targets, boolean flipped, int index) {
        return front(targets, !flipped, index);
    }

    // Creates a gbuffer framebuffer under the current flips and destroys it when incomplete.
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

    // Selects the attachment points of logical draw buffers and discards unattached ones.
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
        framebuffer.retainColorAttachments(written);
        framebuffer.drawBuffers(physical, count);
    }

    // Reports an unattached target once.
    private void warnUnattached(int index, int slot) {
        int bit = index >= 0 && index < Integer.SIZE ? 1 << index : 0;
        if (bit != 0 && (this.warnedUnattached & bit) != 0) {
            return;
        }
        this.warnedUnattached |= bit;
        LOGGER.warn("[Umbra] Gbuffer draw buffer colortex{} is not attached; discarding output slot {}", index, slot);
    }

    // Creates a Distant Horizons framebuffer up to the driver limits.
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
