package com.bdmajora.impetus.umbra.targets;

import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Color targets and depth textures of a shader pack with the framebuffers built on them.
public class UmbraRenderTargets {
    // Number of color targets.
    public static final int MAX_COLOR_BUFFERS = 16;
    // Legacy names of the first eight color targets.
    public static final String[] LEGACY_COLOR_TARGETS =
            {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};

    // Returns the index of a color target name or null for an unknown or out of range name.
    public static Integer colorTargetIndex(String name) {
        if (name.startsWith("colortex")) {
            try {
                int index = Integer.parseInt(name.substring("colortex".length()));
                return index >= 0 && index < MAX_COLOR_BUFFERS ? index : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        for (int i = 0; i < LEGACY_COLOR_TARGETS.length; i++) {
            if (LEGACY_COLOR_TARGETS[i].equals(name)) {
                return i;
            }
        }
        return null;
    }

    // Color targets or null before first use.
    private final UmbraRenderTarget[] targets = new UmbraRenderTarget[MAX_COLOR_BUFFERS];
    // Internal format of each target.
    private final InternalTextureFormat[] formats = new InternalTextureFormat[MAX_COLOR_BUFFERS];
    // Custom size of each target or null.
    private final float[][] sizeOverrides = new float[MAX_COLOR_BUFFERS][];
    // Screen relative state of each custom size component.
    private final boolean[][] sizeRelative = new boolean[MAX_COLOR_BUFFERS][];

    // Depth texture of the main pass.
    private DepthTexture depthTexture;
    // Depth texture without translucents.
    private DepthTexture noTranslucents;
    // Depth texture without the hand.
    private DepthTexture noHand;

    // Framebuffers to destroy with the targets.
    private final List<UmbraFramebuffer> ownedFramebuffers = new ArrayList<>();
    // Flip state of the targets.
    private final BufferFlipper flipper = new BufferFlipper();

    // Screen width in pixels.
    private int width;
    // Screen height in pixels.
    private int height;
    // Released state of the targets.
    private boolean destroyed;

    // Creates the depth textures at a screen size and deletes them when one fails.
    public UmbraRenderTargets(int width, int height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < MAX_COLOR_BUFFERS; i++) {
            this.formats[i] = InternalTextureFormat.RGBA;
        }
        // Deletes the depth textures already created when a later one fails
        try {
            this.depthTexture = createDepthTexture();
            this.noTranslucents = createDepthTexture();
            this.noHand = createDepthTexture();
        } catch (RuntimeException | Error e) {
            if (this.depthTexture != null) {
                this.depthTexture.destroy();
            }
            if (this.noTranslucents != null) {
                this.noTranslucents.destroy();
            }
            throw e;
        }
    }

    // Creates a depth texture at the screen size.
    private DepthTexture createDepthTexture() {
        return new DepthTexture(this.width, this.height,
                GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
    }

    // Stores the custom size of a target and rejects a target that exists.
    public void setColorSize(int index, float x, float y, boolean[] relative) {
        requireValid();
        if (this.targets[index] != null) {
            throw new IllegalStateException("Color buffer " + index + " has already been created; cannot resize it");
        }
        this.sizeOverrides[index] = new float[]{x, y};
        this.sizeRelative[index] = relative.clone();
    }

    // Returns the width of a target.
    public int getWidth(int index) {
        float[] override = this.sizeOverrides[index];
        if (override == null) {
            return this.width;
        }
        return Math.max(1, this.sizeRelative[index][0] ? (int) (this.width * override[0]) : (int) override[0]);
    }

    // Returns the height of a target.
    public int getHeight(int index) {
        float[] override = this.sizeOverrides[index];
        if (override == null) {
            return this.height;
        }
        return Math.max(1, this.sizeRelative[index][1] ? (int) (this.height * override[1]) : (int) override[1]);
    }

    // Returns whether a target has a custom size.
    public boolean hasCustomSize(int index) {
        return this.sizeOverrides[index] != null;
    }

    // Stores the format of a target and rejects a target that exists.
    public void setColorFormat(int index, InternalTextureFormat format) {
        requireValid();
        if (this.targets[index] != null) {
            throw new IllegalStateException("Color buffer " + index + " has already been created; cannot change its format");
        }
        this.formats[index] = format;
    }

    // Returns a target and creates it on first use.
    public UmbraRenderTarget getOrCreate(int index) {
        requireValid();
        if (this.targets[index] == null) {
            this.targets[index] = new UmbraRenderTarget(this.formats[index], getWidth(index), getHeight(index));
        }
        return this.targets[index];
    }

    // Returns a target or null.
    public UmbraRenderTarget get(int index) {
        return this.targets[index];
    }

    // Returns the flip state.
    public BufferFlipper getBufferFlipper() {
        return this.flipper;
    }

    // Returns the depth texture of the main pass.
    public DepthTexture getDepthTexture() {
        return this.depthTexture;
    }

    // Returns the depth texture without translucents.
    public DepthTexture getDepthTextureNoTranslucents() {
        return this.noTranslucents;
    }

    // Returns the depth texture without the hand.
    public DepthTexture getDepthTextureNoHand() {
        return this.noHand;
    }

    // Returns the screen width.
    public int getWidth() {
        return this.width;
    }

    // Returns the screen height.
    public int getHeight() {
        return this.height;
    }

    // Creates a framebuffer that draws into targets and owns it.
    public UmbraFramebuffer createColorFramebuffer(int[] drawBuffers) {
        requireValid();
        if (drawBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one draw buffer");
        }
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        this.ownedFramebuffers.add(framebuffer);

        int[] densePoints = new int[drawBuffers.length];
        for (int i = 0; i < drawBuffers.length; i++) {
            densePoints[i] = i;
            UmbraRenderTarget target = getOrCreate(drawBuffers[i]);
            int texture = this.flipper.isFlipped(drawBuffers[i]) ? target.getMainTexture() : target.getAltTexture();
            framebuffer.addColorAttachment(drawBuffers[i], i, texture);
        }

        framebuffer.addDepthAttachment(this.depthTexture.getTextureId());
        framebuffer.drawBuffers(densePoints);
        framebuffer.readBuffer(0);
        checkFramebufferComplete(framebuffer, "color", drawBuffers);
        return framebuffer;
    }

    // Creates a framebuffer that clears the main or alternate textures and owns it.
    public UmbraFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
        requireValid();
        if (clearBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one clear buffer");
        }
        UmbraFramebuffer framebuffer = new UmbraFramebuffer();
        this.ownedFramebuffers.add(framebuffer);

        int[] densePoints = new int[clearBuffers.length];
        for (int i = 0; i < clearBuffers.length; i++) {
            densePoints[i] = i;
            UmbraRenderTarget target = getOrCreate(clearBuffers[i]);
            framebuffer.addColorAttachment(clearBuffers[i], i, alt ? target.getAltTexture() : target.getMainTexture());
        }
        framebuffer.drawBuffers(densePoints);
        checkFramebufferComplete(framebuffer, "clear", clearBuffers);
        return framebuffer;
    }

    // Rejects an incomplete framebuffer.
    private static void checkFramebufferComplete(UmbraFramebuffer framebuffer, String purpose, int[] drawBuffers) {
        int status = framebuffer.getStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Umbra " + purpose + " framebuffer for draw buffers "
                    + Arrays.toString(drawBuffers) + ": status=" + status);
        }
    }

    // Resizes every target and depth texture to a new screen size.
    public void resize(int newWidth, int newHeight) {
        requireValid();
        if (newWidth == this.width && newHeight == this.height) {
            return;
        }
        this.width = newWidth;
        this.height = newHeight;
        for (int i = 0; i < this.targets.length; i++) {
            if (this.targets[i] != null) {
                this.targets[i].resize(getWidth(i), getHeight(i));
            }
        }
        this.depthTexture.resize(newWidth, newHeight);
        this.noTranslucents.resize(newWidth, newHeight);
        this.noHand.resize(newWidth, newHeight);
    }

    // Releases every framebuffer and texture once and rethrows the first failure.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        // First failure of a release step
        RuntimeException[] failure = new RuntimeException[1];
        for (UmbraFramebuffer framebuffer : this.ownedFramebuffers) {
            release(failure, framebuffer::destroy);
        }
        this.ownedFramebuffers.clear();
        for (int i = 0; i < this.targets.length; i++) {
            UmbraRenderTarget target = this.targets[i];
            this.targets[i] = null;
            if (target != null) {
                release(failure, target::destroy);
            }
        }
        release(failure, this.depthTexture::destroy);
        release(failure, this.noTranslucents::destroy);
        release(failure, this.noHand::destroy);
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    // Runs one release step and keeps its failure when it is the first.
    private static void release(RuntimeException[] failure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            if (failure[0] == null) {
                failure[0] = e;
            } else if (e != failure[0]) {
                failure[0].addSuppressed(e);
            }
        }
    }

    // Rejects released targets.
    private void requireValid() {
        if (this.destroyed) {
            throw new IllegalStateException("Tried to use destroyed UmbraRenderTargets");
        }
    }
}
