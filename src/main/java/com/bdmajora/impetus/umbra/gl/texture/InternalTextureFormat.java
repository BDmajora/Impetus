package com.bdmajora.impetus.umbra.gl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import java.util.Locale;
import java.util.Optional;

// Every colour buffer format a pack can request via formatN with its (pixelFormat, pixelType) pair; integer formats need *_INTEGER client formats, and names match OptiFine's spelling for valueOf
public enum InternalTextureFormat {
    // Default, OptiFine's implicit format, resolved to SIZED RGBA8 like Umbra: glBindImageTexture only accepts sized formats, so base GL_RGBA raises GL_INVALID_VALUE and every imageStore is silently dropped (Clarity's composite2)
    RGBA(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // 8-bit normalized
    R8(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, false),
    RG8(GL30.GL_RG8, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, false),
    RGB8(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    RGBA8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // 8-bit signed normalized
    R8_SNORM(GL31.GL_R8_SNORM, GL11.GL_RED, GL11.GL_BYTE, false),
    RG8_SNORM(GL31.GL_RG8_SNORM, GL30.GL_RG, GL11.GL_BYTE, false),
    RGB8_SNORM(GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_BYTE, false),
    RGBA8_SNORM(GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_BYTE, false),

    // 16-bit normalized
    R16(GL30.GL_R16, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, false),
    RG16(GL30.GL_RG16, GL30.GL_RG, GL11.GL_UNSIGNED_SHORT, false),
    RGB16(GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_SHORT, false),
    RGBA16(GL11.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, false),

    // 16-bit signed normalized
    R16_SNORM(GL31.GL_R16_SNORM, GL11.GL_RED, GL11.GL_SHORT, false),
    RG16_SNORM(GL31.GL_RG16_SNORM, GL30.GL_RG, GL11.GL_SHORT, false),
    RGB16_SNORM(GL31.GL_RGB16_SNORM, GL11.GL_RGB, GL11.GL_SHORT, false),
    RGBA16_SNORM(GL31.GL_RGBA16_SNORM, GL11.GL_RGBA, GL11.GL_SHORT, false),

    // 16-bit float
    R16F(GL30.GL_R16F, GL11.GL_RED, GL11.GL_FLOAT, false),
    RG16F(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT, false),
    RGB16F(GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    RGBA16F(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // 32-bit float
    R32F(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, false),
    RG32F(GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT, false),
    RGB32F(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    RGBA32F(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // 8-bit integer
    R8I(GL30.GL_R8I, GL30.GL_RED_INTEGER, GL11.GL_BYTE, true),
    RG8I(GL30.GL_RG8I, GL30.GL_RG_INTEGER, GL11.GL_BYTE, true),
    RGB8I(GL30.GL_RGB8I, GL30.GL_RGB_INTEGER, GL11.GL_BYTE, true),
    RGBA8I(GL30.GL_RGBA8I, GL30.GL_RGBA_INTEGER, GL11.GL_BYTE, true),
    R8UI(GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    RG8UI(GL30.GL_RG8UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    RGB8UI(GL30.GL_RGB8UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    RGBA8UI(GL30.GL_RGBA8UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE, true),

    // 16-bit integer
    R16I(GL30.GL_R16I, GL30.GL_RED_INTEGER, GL11.GL_SHORT, true),
    RG16I(GL30.GL_RG16I, GL30.GL_RG_INTEGER, GL11.GL_SHORT, true),
    RGB16I(GL30.GL_RGB16I, GL30.GL_RGB_INTEGER, GL11.GL_SHORT, true),
    RGBA16I(GL30.GL_RGBA16I, GL30.GL_RGBA_INTEGER, GL11.GL_SHORT, true),
    R16UI(GL30.GL_R16UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    RG16UI(GL30.GL_RG16UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    RGB16UI(GL30.GL_RGB16UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    RGBA16UI(GL30.GL_RGBA16UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_SHORT, true),

    // 32-bit integer
    R32I(GL30.GL_R32I, GL30.GL_RED_INTEGER, GL11.GL_INT, true),
    RG32I(GL30.GL_RG32I, GL30.GL_RG_INTEGER, GL11.GL_INT, true),
    RGB32I(GL30.GL_RGB32I, GL30.GL_RGB_INTEGER, GL11.GL_INT, true),
    RGBA32I(GL30.GL_RGBA32I, GL30.GL_RGBA_INTEGER, GL11.GL_INT, true),
    R32UI(GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, true),
    RG32UI(GL30.GL_RG32UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, true),
    RGB32UI(GL30.GL_RGB32UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_INT, true),
    RGBA32UI(GL30.GL_RGBA32UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT, true),

    // Mixed / packed
    RGB5_A1(GL11.GL_RGB5_A1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    RGB10_A2(GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    R11F_G11F_B10F(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    RGB9_E5(GL30.GL_RGB9_E5, GL11.GL_RGB, GL11.GL_FLOAT, false),

    // Low-precision legacy formats; Umbra accepts them, Body Camera v1.6.1 requests `colortex4Format = RGBA2`, and packs sometimes rely on the quantisation, so silently substituting RGBA8 is a divergence
    RGBA2(GL11.GL_RGBA2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    RGBA4(GL11.GL_RGBA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    R3_G3_B2(GL11.GL_R3_G3_B2, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    RGB565(GL41.GL_RGB565, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Packed integer: needs an *_INTEGER client format, and the only legal type for the 2-10-10-10 packing.
    RGB10_A2UI(GL33.GL_RGB10_A2UI, GL30.GL_RGBA_INTEGER, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, true);

    private final int internalFormat;
    private final int pixelFormat;
    private final int pixelType;
    private final boolean integer;

    InternalTextureFormat(int internalFormat, int pixelFormat, int pixelType, boolean integer) {
        this.internalFormat = internalFormat;
        this.pixelFormat = pixelFormat;
        this.pixelType = pixelType;
        this.integer = integer;
    }

    // Sized GL internal format
    public int getInternalFormat() {
        return this.internalFormat;
    }

    // A client format legal with the internal one
    public int getPixelFormat() {
        return this.pixelFormat;
    }

    // A client type legal with the internal one
    public int getPixelType() {
        return this.pixelType;
    }

    // Integer formats need *_INTEGER client formats
    public boolean isInteger() {
        return this.integer;
    }

    // valueOf without the exception, for pack-supplied names
    public static Optional<InternalTextureFormat> fromString(String name) {
        try {
            return Optional.of(InternalTextureFormat.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
