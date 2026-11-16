package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.gl.program.DrawBuffers;
import com.bdmajora.impetus.umbra.targets.DepthTexture;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Depth and color textures and the framebuffer of the shadow map.
public final class ShadowTargets {
    // Empty draw buffer list.
    private static final int[] NO_DRAW_BUFFERS = new int[0];

    // Side of the shadow map in pixels.
    private final int resolution;
    // Depth texture of every caster.
    private final DepthTexture depth;
    // Depth texture without translucent casters.
    private final DepthTexture depthNoTranslucents;
    // Mipmapping of each depth texture.
    private final boolean[] mipmapDepth;
    // Nearest filtering of each depth texture.
    private final boolean[] nearestDepth;
    // Formats and filters of the color textures.
    private final ShadowColorSettings colorSettings;
    // GL names of the color textures or zero for unused ones.
    private final int[] colorTextures;
    // GL names of the mipmapped color textures.
    private final int[] mipmappedColors;
    // Framebuffer of the shadow pass.
    private final UmbraFramebuffer framebuffer;
    // Clear operations of the color textures.
    private final ShadowColorClears clears;
    // Released state of the targets.
    private boolean destroyed;

    // Creates the textures and the framebuffer and releases them when creation fails.
    public ShadowTargets(int resolution, boolean[] hardwareFiltering, boolean[] mipmapDepth, boolean[] nearestDepth,
                         boolean separateHardwareSamplers, ShadowColorSettings colorSettings, BitSet usedColorBuffers) {
        this.resolution = ShadowTextures.checkResolution(resolution);
        this.mipmapDepth = depthFlags(mipmapDepth, "mipmapDepth");
        this.nearestDepth = depthFlags(nearestDepth, "nearestDepth");
        boolean[] filtering = depthFlags(hardwareFiltering, "hardwareFiltering");
        this.colorSettings = Objects.requireNonNull(colorSettings, "colorSettings");
        Objects.requireNonNull(usedColorBuffers, "usedColorBuffers");
        this.colorTextures = new int[colorSettings.count()];

        DepthTexture depth = null;
        DepthTexture depthNoTranslucents = null;
        UmbraFramebuffer framebuffer = null;
        try {
            depth = ShadowTextures.createDepth(resolution, filtering[0], this.mipmapDepth[0], this.nearestDepth[0],
                    separateHardwareSamplers);
            depthNoTranslucents = ShadowTextures.createDepth(resolution, filtering[1], this.mipmapDepth[1],
                    this.nearestDepth[1], separateHardwareSamplers);
            framebuffer = new UmbraFramebuffer();
            int[] indices = new int[this.colorTextures.length];
            int[] mipmapped = new int[this.colorTextures.length];
            int allocatedCount = 0;
            int mipmappedCount = 0;
            for (int index = 0; index < this.colorTextures.length; index++) {
                if (index >= ShadowColorSettings.OPTIFINE_BUFFERS && !usedColorBuffers.get(index)) {
                    continue;
                }
                int texture = ShadowTextures.createColor(resolution, colorSettings.format(index),
                        colorSettings.mipmap(index), colorSettings.nearest(index));
                this.colorTextures[index] = texture;
                framebuffer.addColorAttachment(index, texture);
                indices[allocatedCount++] = index;
                if (colorSettings.mipmap(index)) {
                    mipmapped[mipmappedCount++] = texture;
                }
            }
            int[] allocated = Arrays.copyOf(indices, allocatedCount);
            this.mipmappedColors = Arrays.copyOf(mipmapped, mipmappedCount);
            framebuffer.addDepthAttachment(depth.getTextureId());
            framebuffer.drawBuffers(allocated);
            this.clears = new ShadowColorClears(colorSettings, allocated, this.colorTextures,
                    LWJGL.isOpenGLVersionSupported(4, 4));
        } catch (Throwable failure) {
            release(depth, depthNoTranslucents, framebuffer, this.colorTextures);
            throw failure;
        }
        this.depth = depth;
        this.depthNoTranslucents = depthNoTranslucents;
        this.framebuffer = framebuffer;
    }

    // Returns a copy of the first two flags and rejects a null or short array.
    private static boolean[] depthFlags(boolean[] flags, String name) {
        Objects.requireNonNull(flags, name);
        if (flags.length < 2) {
            throw new IllegalArgumentException(name + " needs one flag per shadow depth texture but has " + flags.length);
        }
        return new boolean[]{flags[0], flags[1]};
    }

    // Releases the framebuffer and every texture and continues after a failure.
    private static void release(DepthTexture depth, DepthTexture depthNoTranslucents, UmbraFramebuffer framebuffer,
                                int[] colorTextures) {
        try {
            if (framebuffer != null) {
                framebuffer.destroy();
            }
        } finally {
            try {
                if (depth != null) {
                    depth.destroy();
                }
            } finally {
                try {
                    if (depthNoTranslucents != null) {
                        depthNoTranslucents.destroy();
                    }
                } finally {
                    releaseColors(colorTextures);
                }
            }
        }
    }

    // Deletes every created color texture in one call and forgets their names.
    private static void releaseColors(int[] colorTextures) {
        int count = 0;
        for (int texture : colorTextures) {
            if (texture != 0) {
                count++;
            }
        }
        if (count == 0) {
            return;
        }
        int[] names = new int[count];
        int slot = 0;
        for (int texture : colorTextures) {
            if (texture != 0) {
                names[slot++] = texture;
            }
        }
        Arrays.fill(colorTextures, 0);
        LWJGL.glDeleteTextures(names);
    }

    // Returns the declared draw buffers that have a texture or the first one.
    public int[] drawMask(int[] declared) {
        int[] sanitized = DrawBuffers.sanitize(declared, this.colorTextures.length);
        int[] kept = new int[sanitized.length];
        int count = 0;
        for (int buffer : sanitized) {
            if (buffer < this.colorTextures.length && this.colorTextures[buffer] != 0) {
                kept[count++] = buffer;
            }
        }
        if (count > 0) {
            return Arrays.copyOf(kept, count);
        }
        return this.colorTextures.length > 0 && this.colorTextures[0] != 0 ? new int[]{0} : NO_DRAW_BUFFERS;
    }

    // Binds the framebuffer and sets the viewport to the map.
    public void bind() {
        this.framebuffer.bind();
        LWJGL.glViewport(0, 0, this.resolution, this.resolution);
    }

    // Clears the color and depth textures and selects the draw buffers.
    public void clear(int[] drawMask) {
        this.clears.run(this.framebuffer);
        LWJGL.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        this.framebuffer.drawBuffers(drawMask);
    }

    // Copies the depth into the texture without translucents.
    public void copyDepthWithoutTranslucents() {
        GlTextureUnits.selectScratch(ShadowTextures.SETUP_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.depthNoTranslucents.getTextureId());
        LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, this.resolution, this.resolution);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Generates the mipmaps of the mipmapped textures.
    public void generateMipmaps() {
        boolean depthMips = this.mipmapDepth[0] || this.mipmapDepth[1];
        if (depthMips || this.mipmappedColors.length > 0) {
            GlTextureUnits.selectScratch(ShadowTextures.SETUP_UNIT);
            if (this.mipmapDepth[0]) {
                generateDepthMipmap(this.depth, this.nearestDepth[0]);
            }
            if (this.mipmapDepth[1]) {
                generateDepthMipmap(this.depthNoTranslucents, this.nearestDepth[1]);
            }
            for (int texture : this.mipmappedColors) {
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                LWJGL.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
        }
        GlTextureUnits.resetToUnit0();
    }

    // Generates the mipmaps of a depth texture and sets its filter.
    private static void generateDepthMipmap(DepthTexture texture, boolean nearest) {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
        LWJGL.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, ShadowTextures.minFilter(true, nearest));
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Returns the side of the shadow map.
    public int resolution() {
        return this.resolution;
    }

    // Returns the GL name of the depth texture.
    public int depthTextureId() {
        return this.depth.getTextureId();
    }

    // Returns the GL name of the depth texture without translucents.
    public int depthNoTranslucentsTextureId() {
        return this.depthNoTranslucents.getTextureId();
    }

    // Returns the GL name of a color texture or zero and rejects released targets.
    public int colorTextureId(int index) {
        if (this.destroyed) {
            throw new IllegalStateException("Shadow colour textures were destroyed");
        }
        return index >= 0 && index < this.colorTextures.length ? this.colorTextures[index] : 0;
    }

    // Returns the internal format of a color texture or zero.
    public int colorInternalFormat(int index) {
        return index >= 0 && index < this.colorTextures.length ? this.colorSettings.format(index).getInternalFormat() : 0;
    }

    // Returns the number of color textures.
    public int colorBufferCount() {
        return this.colorTextures.length;
    }

    // Returns the shadow framebuffer.
    public UmbraFramebuffer framebuffer() {
        return this.framebuffer;
    }

    // Releases every resource once.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        release(this.depth, this.depthNoTranslucents, this.framebuffer, this.colorTextures);
    }
}
