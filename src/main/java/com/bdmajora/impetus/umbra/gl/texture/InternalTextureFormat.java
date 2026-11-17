package com.bdmajora.impetus.umbra.gl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import java.util.Locale;
import java.util.Optional;

// Texture formats a pack can name with the format and type that allocate each.
public enum InternalTextureFormat {
    // Four 8 bit unsigned normalized channels.
    RGBA(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // One 8-bit unsigned normalized channel.
    R8(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, false),
    // Two 8-bit unsigned normalized channels.
    RG8(GL30.GL_RG8, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, false),
    // Three 8-bit unsigned normalized channels.
    RGB8(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Four 8-bit unsigned normalized channels.
    RGBA8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // One 8-bit signed normalized channel.
    R8_SNORM(GL31.GL_R8_SNORM, GL11.GL_RED, GL11.GL_BYTE, false),
    // Two 8-bit signed normalized channels.
    RG8_SNORM(GL31.GL_RG8_SNORM, GL30.GL_RG, GL11.GL_BYTE, false),
    // Three 8-bit signed normalized channels.
    RGB8_SNORM(GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_BYTE, false),
    // Four 8-bit signed normalized channels.
    RGBA8_SNORM(GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_BYTE, false),

    // One 16-bit unsigned normalized channel.
    R16(GL30.GL_R16, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, false),
    // Two 16-bit unsigned normalized channels.
    RG16(GL30.GL_RG16, GL30.GL_RG, GL11.GL_UNSIGNED_SHORT, false),
    // Three 16-bit unsigned normalized channels.
    RGB16(GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_SHORT, false),
    // Four 16-bit unsigned normalized channels.
    RGBA16(GL11.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, false),

    // One 16-bit signed normalized channel.
    R16_SNORM(GL31.GL_R16_SNORM, GL11.GL_RED, GL11.GL_SHORT, false),
    // Two 16-bit signed normalized channels.
    RG16_SNORM(GL31.GL_RG16_SNORM, GL30.GL_RG, GL11.GL_SHORT, false),
    // Three 16-bit signed normalized channels.
    RGB16_SNORM(GL31.GL_RGB16_SNORM, GL11.GL_RGB, GL11.GL_SHORT, false),
    // Four 16-bit signed normalized channels.
    RGBA16_SNORM(GL31.GL_RGBA16_SNORM, GL11.GL_RGBA, GL11.GL_SHORT, false),

    // One 16-bit floating point channel.
    R16F(GL30.GL_R16F, GL11.GL_RED, GL11.GL_FLOAT, false),
    // Two 16-bit floating point channels.
    RG16F(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT, false),
    // Three 16-bit floating point channels.
    RGB16F(GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Four 16-bit floating point channels.
    RGBA16F(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // One 32-bit floating point channel.
    R32F(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, false),
    // Two 32-bit floating point channels.
    RG32F(GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT, false),
    // Three 32-bit floating point channels.
    RGB32F(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Four 32-bit floating point channels.
    RGBA32F(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // One 8-bit signed integer channel.
    R8I(GL30.GL_R8I, GL30.GL_RED_INTEGER, GL11.GL_BYTE, true),
    // Two 8-bit signed integer channels.
    RG8I(GL30.GL_RG8I, GL30.GL_RG_INTEGER, GL11.GL_BYTE, true),
    // Three 8-bit signed integer channels.
    RGB8I(GL30.GL_RGB8I, GL30.GL_RGB_INTEGER, GL11.GL_BYTE, true),
    // Four 8-bit signed integer channels.
    RGBA8I(GL30.GL_RGBA8I, GL30.GL_RGBA_INTEGER, GL11.GL_BYTE, true),
    // One 8-bit unsigned integer channel.
    R8UI(GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Two 8-bit unsigned integer channels.
    RG8UI(GL30.GL_RG8UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Three 8-bit unsigned integer channels.
    RGB8UI(GL30.GL_RGB8UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Four 8-bit unsigned integer channels.
    RGBA8UI(GL30.GL_RGBA8UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE, true),

    // One 16-bit signed integer channel.
    R16I(GL30.GL_R16I, GL30.GL_RED_INTEGER, GL11.GL_SHORT, true),
    // Two 16-bit signed integer channels.
    RG16I(GL30.GL_RG16I, GL30.GL_RG_INTEGER, GL11.GL_SHORT, true),
    // Three 16-bit signed integer channels.
    RGB16I(GL30.GL_RGB16I, GL30.GL_RGB_INTEGER, GL11.GL_SHORT, true),
    // Four 16-bit signed integer channels.
    RGBA16I(GL30.GL_RGBA16I, GL30.GL_RGBA_INTEGER, GL11.GL_SHORT, true),
    // One 16-bit unsigned integer channel.
    R16UI(GL30.GL_R16UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Two 16-bit unsigned integer channels.
    RG16UI(GL30.GL_RG16UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Three 16-bit unsigned integer channels.
    RGB16UI(GL30.GL_RGB16UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Four 16-bit unsigned integer channels.
    RGBA16UI(GL30.GL_RGBA16UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_SHORT, true),

    // One 32-bit signed integer channel.
    R32I(GL30.GL_R32I, GL30.GL_RED_INTEGER, GL11.GL_INT, true),
    // Two 32-bit signed integer channels.
    RG32I(GL30.GL_RG32I, GL30.GL_RG_INTEGER, GL11.GL_INT, true),
    // Three 32-bit signed integer channels.
    RGB32I(GL30.GL_RGB32I, GL30.GL_RGB_INTEGER, GL11.GL_INT, true),
    // Four 32-bit signed integer channels.
    RGBA32I(GL30.GL_RGBA32I, GL30.GL_RGBA_INTEGER, GL11.GL_INT, true),
    // One 32-bit unsigned integer channel.
    R32UI(GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Two 32-bit unsigned integer channels.
    RG32UI(GL30.GL_RG32UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Three 32-bit unsigned integer channels.
    RGB32UI(GL30.GL_RGB32UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Four 32-bit unsigned integer channels.
    RGBA32UI(GL30.GL_RGBA32UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT, true),

    // Three 5-bit colour channels and 1-bit alpha.
    RGB5_A1(GL11.GL_RGB5_A1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Three 10-bit colour channels and 2-bit alpha.
    RGB10_A2(GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Packed unsigned floats with 11 bits for red and green and 10 for blue.
    R11F_G11F_B10F(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Three 9-bit mantissas sharing a 5-bit exponent.
    RGB9_E5(GL30.GL_RGB9_E5, GL11.GL_RGB, GL11.GL_FLOAT, false),

    // Four 2-bit unsigned normalized channels.
    RGBA2(GL11.GL_RGBA2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Four 4-bit unsigned normalized channels.
    RGBA4(GL11.GL_RGBA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // 3-bit red and green with 2-bit blue.
    R3_G3_B2(GL11.GL_R3_G3_B2, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // 5 bit red and 6 bit green and 5 bit blue.
    RGB565(GL41.GL_RGB565, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Three 10-bit and one 2-bit unsigned integer channels.
    RGB10_A2UI(GL33.GL_RGB10_A2UI, GL30.GL_RGBA_INTEGER, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, true);

    // GL internal format.
    private final int internalFormat;
    // GL pixel format.
    private final int pixelFormat;
    // GL pixel type.
    private final int pixelType;
    // Whether shaders read the format as integers.
    private final boolean integer;

    // Creates a format from its GL formats and integer flag.
    InternalTextureFormat(int internalFormat, int pixelFormat, int pixelType, boolean integer) {
        this.internalFormat = internalFormat;
        this.pixelFormat = pixelFormat;
        this.pixelType = pixelType;
        this.integer = integer;
    }

    // Returns the internal format.
    public int getInternalFormat() {
        return this.internalFormat;
    }

    // Returns the pixel format.
    public int getPixelFormat() {
        return this.pixelFormat;
    }

    // Returns the pixel type.
    public int getPixelType() {
        return this.pixelType;
    }

    // Returns whether shaders read the format as integers.
    public boolean isInteger() {
        return this.integer;
    }

    // Returns the format a pack directive names ignoring case or empty.
    public static Optional<InternalTextureFormat> fromString(String name) {
        try {
            return Optional.of(InternalTextureFormat.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
