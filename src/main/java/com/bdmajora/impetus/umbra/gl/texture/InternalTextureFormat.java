package com.bdmajora.impetus.umbra.gl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import java.util.Locale;
import java.util.Optional;

// Choices of the internal texture format setting.
public enum InternalTextureFormat {
    // Value that selects rgba.
    RGBA(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // Value that selects r8.
    R8(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rg8.
    RG8(GL30.GL_RG8, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgb8.
    RGB8(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgba8.
    RGBA8(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),

    // Value that selects r8 snorm.
    R8_SNORM(GL31.GL_R8_SNORM, GL11.GL_RED, GL11.GL_BYTE, false),
    // Value that selects rg8 snorm.
    RG8_SNORM(GL31.GL_RG8_SNORM, GL30.GL_RG, GL11.GL_BYTE, false),
    // Value that selects rgb8 snorm.
    RGB8_SNORM(GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_BYTE, false),
    // Value that selects rgba8 snorm.
    RGBA8_SNORM(GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_BYTE, false),

    // Value that selects r16.
    R16(GL30.GL_R16, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, false),
    // Value that selects rg16.
    RG16(GL30.GL_RG16, GL30.GL_RG, GL11.GL_UNSIGNED_SHORT, false),
    // Value that selects rgb16.
    RGB16(GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_SHORT, false),
    // Value that selects rgba16.
    RGBA16(GL11.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, false),

    // Value that selects r16 snorm.
    R16_SNORM(GL31.GL_R16_SNORM, GL11.GL_RED, GL11.GL_SHORT, false),
    // Value that selects rg16 snorm.
    RG16_SNORM(GL31.GL_RG16_SNORM, GL30.GL_RG, GL11.GL_SHORT, false),
    // Value that selects rgb16 snorm.
    RGB16_SNORM(GL31.GL_RGB16_SNORM, GL11.GL_RGB, GL11.GL_SHORT, false),
    // Value that selects rgba16 snorm.
    RGBA16_SNORM(GL31.GL_RGBA16_SNORM, GL11.GL_RGBA, GL11.GL_SHORT, false),

    // Value that selects r16 f.
    R16F(GL30.GL_R16F, GL11.GL_RED, GL11.GL_FLOAT, false),
    // Value that selects rg16 f.
    RG16F(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT, false),
    // Value that selects rgb16 f.
    RGB16F(GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Value that selects rgba16 f.
    RGBA16F(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // Value that selects r32 f.
    R32F(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, false),
    // Value that selects rg32 f.
    RG32F(GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT, false),
    // Value that selects rgb32 f.
    RGB32F(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Value that selects rgba32 f.
    RGBA32F(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, false),

    // Value that selects r8 i.
    R8I(GL30.GL_R8I, GL30.GL_RED_INTEGER, GL11.GL_BYTE, true),
    // Value that selects rg8 i.
    RG8I(GL30.GL_RG8I, GL30.GL_RG_INTEGER, GL11.GL_BYTE, true),
    // Value that selects rgb8 i.
    RGB8I(GL30.GL_RGB8I, GL30.GL_RGB_INTEGER, GL11.GL_BYTE, true),
    // Value that selects rgba8 i.
    RGBA8I(GL30.GL_RGBA8I, GL30.GL_RGBA_INTEGER, GL11.GL_BYTE, true),
    // Value that selects r8 ui.
    R8UI(GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Value that selects rg8 ui.
    RG8UI(GL30.GL_RG8UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Value that selects rgb8 ui.
    RGB8UI(GL30.GL_RGB8UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_BYTE, true),
    // Value that selects rgba8 ui.
    RGBA8UI(GL30.GL_RGBA8UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE, true),

    // Value that selects r16 i.
    R16I(GL30.GL_R16I, GL30.GL_RED_INTEGER, GL11.GL_SHORT, true),
    // Value that selects rg16 i.
    RG16I(GL30.GL_RG16I, GL30.GL_RG_INTEGER, GL11.GL_SHORT, true),
    // Value that selects rgb16 i.
    RGB16I(GL30.GL_RGB16I, GL30.GL_RGB_INTEGER, GL11.GL_SHORT, true),
    // Value that selects rgba16 i.
    RGBA16I(GL30.GL_RGBA16I, GL30.GL_RGBA_INTEGER, GL11.GL_SHORT, true),
    // Value that selects r16 ui.
    R16UI(GL30.GL_R16UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Value that selects rg16 ui.
    RG16UI(GL30.GL_RG16UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Value that selects rgb16 ui.
    RGB16UI(GL30.GL_RGB16UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_SHORT, true),
    // Value that selects rgba16 ui.
    RGBA16UI(GL30.GL_RGBA16UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_SHORT, true),

    // Value that selects r32 i.
    R32I(GL30.GL_R32I, GL30.GL_RED_INTEGER, GL11.GL_INT, true),
    // Value that selects rg32 i.
    RG32I(GL30.GL_RG32I, GL30.GL_RG_INTEGER, GL11.GL_INT, true),
    // Value that selects rgb32 i.
    RGB32I(GL30.GL_RGB32I, GL30.GL_RGB_INTEGER, GL11.GL_INT, true),
    // Value that selects rgba32 i.
    RGBA32I(GL30.GL_RGBA32I, GL30.GL_RGBA_INTEGER, GL11.GL_INT, true),
    // Value that selects r32 ui.
    R32UI(GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Value that selects rg32 ui.
    RG32UI(GL30.GL_RG32UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Value that selects rgb32 ui.
    RGB32UI(GL30.GL_RGB32UI, GL30.GL_RGB_INTEGER, GL11.GL_UNSIGNED_INT, true),
    // Value that selects rgba32 ui.
    RGBA32UI(GL30.GL_RGBA32UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT, true),

    // Value that selects rgb5 a1.
    RGB5_A1(GL11.GL_RGB5_A1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgb10 a2.
    RGB10_A2(GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects r11 f g11 f b10 f.
    R11F_G11F_B10F(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT, false),
    // Value that selects rgb9 e5.
    RGB9_E5(GL30.GL_RGB9_E5, GL11.GL_RGB, GL11.GL_FLOAT, false),

    // Value that selects rgba2.
    RGBA2(GL11.GL_RGBA2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgba4.
    RGBA4(GL11.GL_RGBA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects r3 g3 b2.
    R3_G3_B2(GL11.GL_R3_G3_B2, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgb565.
    RGB565(GL41.GL_RGB565, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, false),
    // Value that selects rgb10 a2 ui.
    RGB10_A2UI(GL33.GL_RGB10_A2UI, GL30.GL_RGBA_INTEGER, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, true);

    // GL internal format.
    private final int internalFormat;
    // GL pixel format.
    private final int pixelFormat;
    // GL pixel type.
    private final int pixelType;
    // Flag set for integer formats.
    private final boolean integer;

    // Creates an internal texture format from its internal format and pixel format and pixel type and integer.
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

    // Returns whether integer.
    public boolean isInteger() {
        return this.integer;
    }

    // Creates from the string.
    public static Optional<InternalTextureFormat> fromString(String name) {
        try {
            return Optional.of(InternalTextureFormat.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
