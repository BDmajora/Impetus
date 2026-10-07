package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

// Per-buffer shadowcolor directives (Iris's PackShadowDirectives sampling settings): format, per-frame clear, clear colour, mipmaps and nearest filtering for each of up to eight shadow colour buffers
public final class ShadowColorSettings {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // OptiFine's two; a pack declaring HIGHER_SHADOWCOLOR gets Iris's eight
    public static final int OPTIFINE_BUFFERS = 2;
    public static final int IRIS_BUFFERS = 8;

    private final InternalTextureFormat[] formats;
    private final boolean[] clear;
    private final float[][] clearColors;
    private final boolean[] mipmap;
    private final boolean[] nearest;

    private ShadowColorSettings(int count) {
        this.formats = new InternalTextureFormat[count];
        this.clear = new boolean[count];
        this.clearColors = new float[count][];
        this.mipmap = new boolean[count];
        this.nearest = new boolean[count];

        // White, cleared every frame, RGBA8, linear, no mips: untinted light wherever nothing casts
        Arrays.fill(this.formats, InternalTextureFormat.RGBA);
        Arrays.fill(this.clear, true);
        for (int i = 0; i < count; i++) {
            this.clearColors[i] = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
    }

    // Reads every directive for buffers 0..count-1, in Iris's order: the shared mipmap switch first, then per-buffer overrides
    public static ShadowColorSettings parse(ConstDirectives consts, int count) {
        ShadowColorSettings settings = new ShadowColorSettings(count);

        consts.getOptionalBool("generateShadowColorMipmap").ifPresent(value -> Arrays.fill(settings.mipmap, value));

        for (int i = 0; i < count; i++) {
            final int index = i;
            String name = "shadowcolor" + i;

            consts.getIntToken(name + "Format").ifPresent(token -> InternalTextureFormat.fromString(token.trim()).ifPresentOrElse(
                    format -> settings.formats[index] = format,
                    () -> LOGGER.warn("[Umbra] Unknown format {} for {}Format; keeping RGBA8", token, name)));
            consts.getOptionalBool(name + "Clear").ifPresent(value -> settings.clear[index] = value);
            consts.getVec4(name + "ClearColor").ifPresent(color -> settings.clearColors[index] = color);

            consts.getOptionalBool(name + "Mipmap").ifPresent(value -> settings.mipmap[index] = value);
            consts.getOptionalBool("shadowColor" + i + "Mipmap").ifPresent(value -> settings.mipmap[index] = value);

            consts.getOptionalBool(name + "Nearest").ifPresent(value -> settings.nearest[index] = value);
            consts.getOptionalBool("shadowColor" + i + "Nearest").ifPresent(value -> settings.nearest[index] = value);
            consts.getOptionalBool("shadowColor" + i + "MinMagNearest").ifPresent(value -> settings.nearest[index] = value);
        }

        return settings;
    }

    // How many buffers the pack may address
    public int count() {
        return this.formats.length;
    }

    public InternalTextureFormat format(int index) {
        return this.formats[index];
    }

    public boolean clear(int index) {
        return this.clear[index];
    }

    public float[] clearColor(int index) {
        return this.clearColors[index];
    }

    public boolean mipmap(int index) {
        return this.mipmap[index];
    }

    // Integer formats cannot be filtered at all, so they are always nearest whatever the pack says
    public boolean nearest(int index) {
        return this.nearest[index] || this.formats[index].isInteger();
    }
}
