package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Objects;

// Settings of the shadow color group.
public final class ShadowColorSettings {
    // Logger of Umbra.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Shadow colour targets without the HIGHER_SHADOWCOLOR feature.
    public static final int OPTIFINE_BUFFERS = 2;
    // Shadow colour targets with the HIGHER_SHADOWCOLOR feature.
    public static final int IRIS_BUFFERS = 8;

    // Internal format of each shadow colour target.
    private final InternalTextureFormat[] formats;
    // Whether each shadow colour target is cleared every frame.
    private final boolean[] clear;
    // Clear colour of each shadow colour target.
    private final float[][] clearColors;
    // Whether each shadow colour target gets mipmaps.
    private final boolean[] mipmap;
    // Whether each shadow colour target samples with nearest filtering.
    private final boolean[] nearest;

    // Creates a shadow color settings from its count.
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

    // Reads the color target settings from the pack constants.
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

    // Returns the number of color targets.
    public int count() {
        return this.formats.length;
    }

    // Returns the format of a color target.
    public InternalTextureFormat format(int index) {
        return this.formats[index];
    }

    // Returns whether a color target is cleared each frame.
    public boolean clear(int index) {
        return this.clear[index];
    }

    // Returns the clear color of a target.
    public float[] clearColor(int index) {
        return this.clearColors[index];
    }

    // Returns whether a target gets mipmaps.
    public boolean mipmap(int index) {
        return this.mipmap[index];
    }

    // Returns whether a target uses nearest filtering.
    public boolean nearest(int index) {
        return this.nearest[index] || this.formats[index].isInteger();
    }
}
