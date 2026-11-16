package com.bdmajora.impetus.umbra.gl.framebuffer;

import com.bdmajora.impetus.umbra.gl.GlResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import java.nio.IntBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Framebuffer of the shader pipeline.
public class UmbraFramebuffer extends GlResource {
    // Texture names of the color attachments.
    private final int[] colorAttachments = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Attachment points by logical index.
    private final int[] logicalAttachmentPoints = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Logical indices by attachment point.
    private final int[] attachmentLogicalIndices;
    // Draw buffer limit of the driver.
    private final int maxDrawBuffers;
    // Color attachment limit of the driver.
    private final int maxColorAttachments;
    // Has depth attachment.
    private boolean hasDepthAttachment;

    // Creates an umbra framebuffer with default state.
    public UmbraFramebuffer() {
        setHandle(LWJGL.glGenFramebuffers());
        this.maxDrawBuffers = LWJGL.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = LWJGL.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        this.attachmentLogicalIndices = new int[Math.max(0, this.maxColorAttachments)];
        Arrays.fill(this.logicalAttachmentPoints, -1);
        Arrays.fill(this.attachmentLogicalIndices, -1);
    }

    // Binds the framebuffer.
    public void bind() {
        LWJGL.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getGlId());
    }

    // Binds the framebuffer for reading.
    public void bindAsReadBuffer() {
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, getGlId());
    }

    // Binds the framebuffer for drawing.
    public void bindAsDrawBuffer() {
        LWJGL.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, getGlId());
    }

    // Attaches a texture at a logical index.
    public void addColorAttachment(int index, int texture) {
        addColorAttachment(index, index, texture);
    }

    // Attaches a texture at a logical index and attachment point.
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

    // Attaches a depth texture.
    public void addDepthAttachment(int texture) {
        bind();
        LWJGL.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        this.hasDepthAttachment = true;
    }

    // Detaches the color attachments outside a mask.
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

    // Disables every draw buffer.
    public void noDrawBuffers() {
        bind();
        LWJGL.glDrawBuffers(GL11.GL_NONE);
    }

    // Sets the draw buffers of logical indices.
    public void drawBuffers(int[] colorIndices) {
        drawBuffers(colorIndices, colorIndices == null ? 0 : colorIndices.length);
    }

    // Sets the draw buffers of the first logical indices.
    public void drawBuffers(int[] colorIndices, int count) {
        if (count > this.maxDrawBuffers) {
            throw new IllegalArgumentException("Cannot write to more than " + this.maxDrawBuffers + " draw buffers on this GPU");
        }
        bind();
        if (count == 0) {
            LWJGL.glDrawBuffers(GL11.GL_NONE);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
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

    // Sets the read buffer to a logical index.
    public void readBuffer(int colorIndex) {
        validateColorAttachmentIndex(colorIndex);
        validateAttachedColorAttachmentIndex(colorIndex);
        bind();
        LWJGL.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + colorIndex);
    }

    // Rejects a logical index outside the limits.
    private void validateColorAttachmentIndex(int colorIndex) {
        if (colorIndex < 0) {
            throw new IllegalArgumentException("Color attachment index must be non-negative: " + colorIndex);
        }
        if (colorIndex >= this.maxColorAttachments) {
            throw new IllegalArgumentException("Color attachment index " + colorIndex
                    + " exceeds GPU limit of " + this.maxColorAttachments);
        }
    }

    // Rejects a logical index without an attachment.
    private void validateAttachedColorAttachmentIndex(int colorIndex) {
        if (this.attachmentLogicalIndices[colorIndex] < 0) {
            throw new IllegalArgumentException("No color texture is attached to physical color attachment " + colorIndex);
        }
    }

    // Returns the color attachment.
    public int getColorAttachment(int index) {
        return index >= 0 && index < this.colorAttachments.length ? this.colorAttachments[index] : 0;
    }

    // Returns whether depth attachment.
    public boolean hasDepthAttachment() {
        return this.hasDepthAttachment;
    }

    // Returns the status.
    public int getStatus() {
        bind();
        return LWJGL.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
    }

    // Returns whether complete.
    public boolean isComplete() {
        return getStatus() == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    // Deletes the framebuffer.
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteFramebuffers(getGlId());
    }
}
