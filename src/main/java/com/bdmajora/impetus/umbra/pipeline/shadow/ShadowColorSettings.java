package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Objects;

// Format and clear and filtering settings of each shadow colour buffer from the pack const directives
public final class ShadowColorSettings {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Number of shadow colour buffers without the HIGHER_SHADOWCOLOR feature
    public static final int OPTIFINE_BUFFERS = 2;
    // Number of shadow colour buffers with the HIGHER_SHADOWCOLOR feature
    public static final int IRIS_BUFFERS = 8;

    // Internal format per buffer
    private final InternalTextureFormat[] formats;
    // Clear switch per buffer for the start of every pass
    private final boolean[] clear;
    // Clear colour per buffer as four components
    private final float[][] clearColors;
    // Mipmap switch per buffer
    private final boolean[] mipmap;
    // Nearest filtering switch per buffer
    private final boolean[] nearest;

    // Creates the defaults of white RGBA8 cleared every pass with linear filtering and no mipmaps
    private ShadowColorSettings(int count) {
        this.formats = new InternalTextureFormat[count];
        this.clear = new boolean[count];
        this.clearColors = new float[count][];
        this.mipmap = new boolean[count];
        this.nearest = new boolean[count];
        Arrays.fill(this.formats, InternalTextureFormat.RGBA);
        Arrays.fill(this.clear, true);
        for (int i = 0; i < count; i++) {
            this.clearColors[i] = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
    }

    // Reads the shared mipmap switch and then the directives of each buffer between 0 and count minus 1
    public static ShadowColorSettings parse(ConstDirectives consts, int count) {
        Objects.requireNonNull(consts, "consts");
        if (count < 0) {
            throw new IllegalArgumentException("Shadow colour buffer count must not be negative: " + count);
        }
        ShadowColorSettings settings = new ShadowColorSettings(count);
        consts.getOptionalBool("generateShadowColorMipmap").ifPresent(value -> Arrays.fill(settings.mipmap, value));
        for (int i = 0; i < count; i++) {
            final int index = i;
            String name = "shadowcolor" + i;
            String alias = "shadowColor" + i;
            consts.getIntToken(name + "Format").ifPresent(token -> InternalTextureFormat.fromString(token.trim()).ifPresentOrElse(
                    format -> settings.formats[index] = format,
                    () -> LOGGER.warn("[Umbra] Unknown format {} for {}Format; keeping RGBA8", token, name)));
            consts.getOptionalBool(name + "Clear").ifPresent(value -> settings.clear[index] = value);
            consts.getVec4(name + "ClearColor").ifPresent(color -> settings.clearColors[index] = color);
            consts.getOptionalBool(name + "Mipmap").ifPresent(value -> settings.mipmap[index] = value);
            consts.getOptionalBool(alias + "Mipmap").ifPresent(value -> settings.mipmap[index] = value);
            consts.getOptionalBool(name + "Nearest").ifPresent(value -> settings.nearest[index] = value);
            consts.getOptionalBool(alias + "Nearest").ifPresent(value -> settings.nearest[index] = value);
            consts.getOptionalBool(alias + "MinMagNearest").ifPresent(value -> settings.nearest[index] = value);
        }
        return settings;
    }

    // Returns how many buffers the pack may address
    public int count() {
        return this.formats.length;
    }

    // Returns the internal format of a buffer
    public InternalTextureFormat format(int index) {
        return this.formats[index];
    }

    // Returns whether a buffer clears at the start of every pass
    public boolean clear(int index) {
        return this.clear[index];
    }

    // Returns the live clear colour array of a buffer
    public float[] clearColor(int index) {
        return this.clearColors[index];
    }

    // Returns whether a buffer gets a mip chain after every pass
    public boolean mipmap(int index) {
        return this.mipmap[index];
    }

    // Returns whether a buffer filters nearest which integer formats always do
    public boolean nearest(int index) {
        return this.nearest[index] || this.formats[index].isInteger();
    }
}
