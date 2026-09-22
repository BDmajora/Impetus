package com.bdmajora.impetus.umbra.gl.framebuffer;

import com.bdmajora.impetus.umbra.gl.GlResource;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL30;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.util.Arrays;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import java.nio.IntBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A pipeline-owned framebuffer (not vanilla's single-attachment one) since a pack needs many colour attachments with independent draw-buffer masks; logical colortex indices map onto free slots, and every call binds this FBO since the LWJGL abstraction has no DSA
public class UmbraFramebuffer extends GlResource {
    // Indexed by logical colortex number: the texture (0 = none) and the physical attachment point (-1 = none); arrays rather than boxed maps since retainColorAttachments runs on every gbuffer phase switch
    private final int[] colorAttachments = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    private final int[] logicalAttachmentPoints = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Indexed by physical attachment point: the logical index bound there, -1 when free
    private final int[] attachmentLogicalIndices;
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;

    public UmbraFramebuffer() {
        setHandle(LWJGL.glGenFramebuffers());
        this.maxDrawBuffers = LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        this.attachmentLogicalIndices = new int[Math.max(0, this.maxColorAttachments)];
        Arrays.fill(this.logicalAttachmentPoints, -1);
        Arrays.fill(this.attachmentLogicalIndices, -1);
    }

    // GL_FRAMEBUFFER
    public void bind() {
        LWJGL.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getGlId());
    }

    // GL_READ_FRAMEBUFFER, for blits
    public void bindAsReadBuffer() {
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, getGlId());
    }

    // GL_DRAW_FRAMEBUFFER, for blits
    public void bindAsDrawBuffer() {
        LWJGL.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, getGlId());
    }

    // Attaches at the next free slot
    public void addColorAttachment(int index, int texture) {
        addColorAttachment(index, index, texture);
    }

    // Attaches at a specific slot and records the logical mapping
    public void addColorAttachment(int logicalIndex, int attachmentIndex, int texture) {
        if (logicalIndex < 0 || logicalIndex >= this.colorAttachments.length) {
            throw new IllegalArgumentException("Logical color attachment index out of range: " + logicalIndex);
        }
        validateColorAttachmentIndex(attachmentIndex);

        int previousAttachment = this.logicalAttachmentPoints[logicalIndex];
        if (previousAttachment >= 0 && previousAttachment != attachmentIndex) {
            throw new IllegalArgumentException("Logical color attachment index " + logicalIndex
                    + " is already bound to physical attachment " + previousAttachment);
        }

        int previousLogical = this.attachmentLogicalIndices[attachmentIndex];
        if (previousLogical >= 0 && previousLogical != logicalIndex) {
            throw new IllegalArgumentException("Physical color attachment " + attachmentIndex
                    + " is already bound to logical colortex" + previousLogical);
        }

        bind();
        LWJGL.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + attachmentIndex,
                GL11.GL_TEXTURE_2D, texture, 0);
        this.colorAttachments[logicalIndex] = texture;
        this.logicalAttachmentPoints[logicalIndex] = attachmentIndex;
        this.attachmentLogicalIndices[attachmentIndex] = logicalIndex;
    }

    // One depth texture; replaces any previous
    public void addDepthAttachment(int texture) {
        bind();
        LWJGL.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        this.hasDepthAttachment = true;
    }

    // Bitmask form (bit N keeps logical attachment N), the allocation-free spelling the per-phase gbuffer path uses; narrows the FBO's LIVE colour attachments to exactly the given logical set, as Iris gives each gbuffer program a framebuffer of only what it writes; a program SAMPLING a colortex it does not write (gbuffers_terrain reading gaux4 for fog) must not have it attached, or the feedback loop returned in-progress colortex1 and blew the horizon white
    public void retainColorAttachments(int keepLogicalMask) {
        bind();
        int[] points = this.logicalAttachmentPoints;
        for (int logicalIndex = 0; logicalIndex < points.length; logicalIndex++) {
            int point = points[logicalIndex];
            if (point < 0) {
                continue;
            }
            int texture = (keepLogicalMask & (1 << logicalIndex)) != 0 ? this.colorAttachments[logicalIndex] : 0;
            LWJGL.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + point,
                    GL11.GL_TEXTURE_2D, texture, 0);
        }
    }

    // Depth-only rendering, for the shadow pass
    public void noDrawBuffers() {
        bind();
        LWJGL.glDrawBuffers(GL11.GL_NONE);
    }

    // Sets the draw-buffer mask from colour attachment indices; a negative entry writes GL_NONE rather than being skipped, keeping the slot numbering DENSE so an unavailable optional target does not renumber every later gl_FragData write
    public void drawBuffers(int[] colorIndices) {
        drawBuffers(colorIndices, colorIndices == null ? 0 : colorIndices.length);
    }

    // The first count entries of colorIndices, so a caller can pass a reused scratch array
    public void drawBuffers(int[] colorIndices, int count) {
        if (count > this.maxDrawBuffers) {
            throw new IllegalArgumentException("Cannot write to more than " + this.maxDrawBuffers + " draw buffers on this GPU");
        }
        bind();
        if (count == 0) {
            LWJGL.glDrawBuffers(GL11.GL_NONE);
            return;
        }
        try (MemoryStack stack = LWJGL.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
            // Attachment points are bounded by the GL limit (8 on every real driver), so a mask replaces a per-call table
            long seen = 0L;
            for (int i = 0; i < count; i++) {
                int colorIndex = colorIndices[i];
                if (colorIndex < 0) {
                    buffer.put(GL11.GL_NONE);
                    continue;
                }
                validateColorAttachmentIndex(colorIndex);
                long bit = colorIndex < 64 ? 1L << colorIndex : 0L;
                if ((seen & bit) != 0) {
                    throw new IllegalArgumentException("Color attachment index " + colorIndex
                            + " appears more than once in one draw-buffer mask");
                }
                seen |= bit;
                validateAttachedColorAttachmentIndex(colorIndex);
                buffer.put(GL30.GL_COLOR_ATTACHMENT0 + colorIndex);
            }
            buffer.flip();
            LWJGL.glDrawBuffers(buffer);
        }
    }

    // Selects which logical attachment glReadPixels and blits read
    public void readBuffer(int colorIndex) {
        validateColorAttachmentIndex(colorIndex);
        validateAttachedColorAttachmentIndex(colorIndex);
        bind();
        LWJGL.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + colorIndex);
    }

    // Within the driver's attachment limit
    private void validateColorAttachmentIndex(int colorIndex) {
        if (colorIndex < 0) {
            throw new IllegalArgumentException("Color attachment index must be non-negative: " + colorIndex);
        }
        if (colorIndex >= this.maxColorAttachments) {
            throw new IllegalArgumentException("Color attachment index " + colorIndex
                    + " exceeds GPU limit of " + this.maxColorAttachments);
        }
    }

    // Within the limit and actually attached
    private void validateAttachedColorAttachmentIndex(int colorIndex) {
        if (this.attachmentLogicalIndices[colorIndex] < 0) {
            throw new IllegalArgumentException("No color texture is attached to physical color attachment " + colorIndex);
        }
    }

    // Texture at a logical index
    public int getColorAttachment(int index) {
        return index >= 0 && index < this.colorAttachments.length ? this.colorAttachments[index] : 0;
    }

    // Whether addDepthAttachment was called
    public boolean hasDepthAttachment() {
        return this.hasDepthAttachment;
    }

    // glCheckFramebufferStatus
    public int getStatus() {
        bind();
        return LWJGL.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    }

    // Status is GL_FRAMEBUFFER_COMPLETE
    public boolean isComplete() {
        return getStatus() == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    // Deletes the FBO; attached textures are owned elsewhere
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteFramebuffers(getGlId());
    }
}
