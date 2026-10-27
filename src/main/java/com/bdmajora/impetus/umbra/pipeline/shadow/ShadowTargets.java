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

// Owns the shadow depth and colour textures and the framebuffer that renders into them
public final class ShadowTargets {
    // Empty draw buffer mask that draws no colour at all
    private static final int[] NO_DRAW_BUFFERS = new int[0];

    // Width and height of every shadow texture in pixels
    private final int resolution;
    // Depth of every shadow caster sampled as shadowtex0
    private final DepthTexture depth;
    // Depth copied before translucent casters draw and sampled as shadowtex1
    private final DepthTexture depthNoTranslucents;
    // Mipmap flag per depth texture
    private final boolean[] mipmapDepth;
    // Nearest filtering flag per depth texture
    private final boolean[] nearestDepth;
    // Per buffer format and clear and filtering settings
    private final ShadowColorSettings colorSettings;
    // Texture name per addressable colour buffer with 0 for a buffer that was never allocated
    private final int[] colorTextures;
    // Names of the allocated colour textures that get a mip chain after each pass
    private final int[] mipmappedColors;
    // Framebuffer with every allocated colour buffer and shadowtex0 attached
    private final UmbraFramebuffer framebuffer;
    // Per pass clears of the allocated colour buffers
    private final ShadowColorClears clears;
    // Flag set once destroy has run
    private boolean destroyed;

    // Allocates both depth textures and every colour buffer that is one of the first two or marked as used
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
                // shadowcolor0 and shadowcolor1 always exist and the rest only when referenced
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

    // Returns a copy of a two entry flag array or rejects a null or shorter one
    private static boolean[] depthFlags(boolean[] flags, String name) {
        Objects.requireNonNull(flags, name);
        if (flags.length < 2) {
            throw new IllegalArgumentException(name + " needs one flag per shadow depth texture but has " + flags.length);
        }
        return new boolean[]{flags[0], flags[1]};
    }

    // Deletes the framebuffer and the depth textures and every allocated colour texture that exists
    private static void release(DepthTexture depth, DepthTexture depthNoTranslucents, UmbraFramebuffer framebuffer,
                                int[] colorTextures) {
        if (framebuffer != null) {
            framebuffer.destroy();
        }
        if (depth != null) {
            depth.destroy();
        }
        if (depthNoTranslucents != null) {
            depthNoTranslucents.destroy();
        }
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
        LWJGL.glDeleteTextures(names);
    }

    // Returns the declared draw buffers limited to allocated buffers or shadowcolor0 alone when none remain
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

    // Binds the framebuffer and sets the viewport to the whole shadow map
    public void bind() {
        this.framebuffer.bind();
        LWJGL.glViewport(0, 0, this.resolution, this.resolution);
    }

    // Clears the colour buffers that ask for it and the depth buffer and then selects the draw mask
    public void clear(int[] drawMask) {
        this.clears.run(this.framebuffer);
        LWJGL.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        this.framebuffer.drawBuffers(drawMask);
    }

    // Copies the bound framebuffer depth into shadowtex1
    public void copyDepthWithoutTranslucents() {
        GlTextureUnits.selectScratch(ShadowTextures.SETUP_UNIT);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.depthNoTranslucents.getTextureId());
        LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, this.resolution, this.resolution);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GlTextureUnits.resetToUnit0();
    }

    // Builds the mip chains the pack asked for and leaves unit 0 selected
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

    // Builds one depth mip chain and sets its mipmapped minifying filter on the selected unit
    private static void generateDepthMipmap(DepthTexture texture, boolean nearest) {
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
        LWJGL.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, ShadowTextures.minFilter(true, nearest));
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // Returns the width and height of the shadow map in pixels
    public int resolution() {
        return this.resolution;
    }

    // Returns the shadowtex0 texture name or throws once destroyed
    public int depthTextureId() {
        return this.depth.getTextureId();
    }

    // Returns the shadowtex1 texture name or throws once destroyed
    public int depthNoTranslucentsTextureId() {
        return this.depthNoTranslucents.getTextureId();
    }

    // Returns the texture name of a colour buffer or 0 for an index that was never allocated
    public int colorTextureId(int index) {
        if (this.destroyed) {
            throw new IllegalStateException("Shadow colour textures were destroyed");
        }
        return index >= 0 && index < this.colorTextures.length ? this.colorTextures[index] : 0;
    }

    // Returns the internal format of a colour buffer or 0 for an index outside the addressable buffers
    public int colorInternalFormat(int index) {
        return index >= 0 && index < this.colorTextures.length ? this.colorSettings.format(index).getInternalFormat() : 0;
    }

    // Returns how many colour buffers the pack may address
    public int colorBufferCount() {
        return this.colorTextures.length;
    }

    // Returns the framebuffer that the pass renders into
    public UmbraFramebuffer framebuffer() {
        return this.framebuffer;
    }

    // Deletes the framebuffer and every texture once
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        release(this.depth, this.depthNoTranslucents, this.framebuffer, this.colorTextures);
    }
}
