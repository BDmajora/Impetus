package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomImageDefinition;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Image textures a shader pack declares with their image and sampler units.
public class CustomImageManager {
    // Logger of images that cannot be created.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Largest number of images.
    private static final int MAX_DECLARED_IMAGES = 16;
    // Texture unit used while resizing.
    private static final int RESIZE_SCRATCH_UNIT = 32;
    // GL enum of the image unit limit.
    private static final int GL_MAX_IMAGE_UNITS = 0x8D57;
    // GL enum of the minimum level of detail.
    private static final int GL_TEXTURE_MIN_LOD = 0x813A;
    // GL enum of the maximum level of detail.
    private static final int GL_TEXTURE_MAX_LOD = 0x813B;
    // GL enum of the highest mipmap level.
    private static final int GL_TEXTURE_MAX_LEVEL = 0x813D;
    // GL enum of the level of detail bias.
    private static final int GL_TEXTURE_LOD_BIAS = 0x8501;
    // Barrier bit of texture fetches.
    private static final int GL_TEXTURE_FETCH_BARRIER_BIT = 0x00000008;
    // Barrier bit of image access.
    private static final int GL_SHADER_IMAGE_ACCESS_BARRIER_BIT = 0x00000020;
    // Barrier bit of texture updates.
    private static final int GL_TEXTURE_UPDATE_BARRIER_BIT = 0x00000100;
    // Barriers that make a clear visible to every reader.
    private static final int CLEAR_VISIBILITY_BARRIERS =
            GL_TEXTURE_UPDATE_BARRIER_BIT
                    | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                    | GL_TEXTURE_FETCH_BARRIER_BIT;

    // One image texture with its units and formats.
    private static final class Image {
        // Directive of the image.
        final CustomImageDefinition definition;
        // GL name of the texture.
        final int texture;
        // GL texture target.
        final int target;
        // Image unit of the texture.
        final int imageUnit;
        // Texture unit of the sampler or minus one.
        final int samplerUnit;
        // GL internal format.
        final int glInternalFormat;
        // GL pixel format.
        final int glFormat;
        // GL pixel type.
        final int glPixelType;

        // Creates an image.
        Image(CustomImageDefinition definition, int texture, int target, int imageUnit, int samplerUnit,
              int glInternalFormat, int glFormat, int glPixelType) {
            this.definition = definition;
            this.texture = texture;
            this.target = target;
            this.imageUnit = imageUnit;
            this.samplerUnit = samplerUnit;
            this.glInternalFormat = glInternalFormat;
            this.glFormat = glFormat;
            this.glPixelType = glPixelType;
        }
    }

    // Images in unit order.
    private final List<Image> images = new ArrayList<>();
    // Units of the image and sampler uniforms by name.
    private final Map<String, Integer> uniformOverrides = new LinkedHashMap<>();
    // Number of image units the driver allows.
    private final int hardwareImageUnits;
    // Render width of the relative sizes.
    private int renderWidth;
    // Render height of the relative sizes.
    private int renderHeight;

    // Creates every supported image and deletes them all when one fails.
    public CustomImageManager(List<CustomImageDefinition> definitions, int firstSamplerUnit, int lastSamplerUnit,
                              int renderWidth, int renderHeight) {
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        int reportedImageUnits = LWJGL.glGetInteger(GL_MAX_IMAGE_UNITS);
        this.hardwareImageUnits = reportedImageUnits > 0 ? reportedImageUnits : MAX_DECLARED_IMAGES;
        int imageUnitLimit = Math.min(MAX_DECLARED_IMAGES, this.hardwareImageUnits);
        int nextSamplerUnit = firstSamplerUnit;
        for (CustomImageDefinition definition : definitions) {
            if (this.images.size() >= imageUnitLimit) {
                LOGGER.error("[Umbra] Out of image units for image.{} (max {}); ignoring it. Sampler '{}' stays on "
                                + "texture unit 0 and will fail every draw in any program declaring it with "
                                + "GL_INVALID_OPERATION on a strict driver",
                        definition.name, imageUnitLimit, definition.samplerName);
                continue;
            }
            int internalFormat = glInternalFormat(definition.internalFormat);
            int format = glFormat(definition.format);
            int pixelType = glPixelType(definition.pixelType);
            if (internalFormat == 0 || format == 0 || pixelType == 0) {
                LOGGER.error("[Umbra] Unsupported format for image.{} ({} {} {}); ignoring it. Sampler '{}' stays on "
                                + "texture unit 0 and will fail every draw in any program declaring it with "
                                + "GL_INVALID_OPERATION on a strict driver",
                        definition.name, definition.format, definition.internalFormat, definition.pixelType,
                        definition.samplerName);
                continue;
            }

            boolean is3D = definition.sizeZ > 0;
            int target = is3D ? GL12.GL_TEXTURE_3D : GL11.GL_TEXTURE_2D;
            int filter = isIntegerFormat(definition.internalFormat) ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            int sizeX = relativeSizeX(definition);
            int sizeY = relativeSizeY(definition);

            int texture = LWJGL.glGenTextures();
            // Deletes this texture and every earlier image when the setup fails
            try {
                LWJGL.glBindTexture(target, texture);
                LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, filter);
                LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, filter);
                LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                LWJGL.glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, 0);
                LWJGL.glTexParameteri(target, GL_TEXTURE_MIN_LOD, 0);
                LWJGL.glTexParameteri(target, GL_TEXTURE_MAX_LOD, 0);
                LWJGL.glTexParameterf(target, GL_TEXTURE_LOD_BIAS, 0.0f);
                if (is3D) {
                    LWJGL.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
                    LWJGL.glTexImage3D(target, 0, internalFormat, sizeX, sizeY, definition.sizeZ,
                            0, format, pixelType, (ByteBuffer) null);
                } else {
                    LWJGL.glTexImage2D(target, 0, internalFormat, sizeX, sizeY,
                            0, format, pixelType, (ByteBuffer) null);
                }
                LWJGL.glBindTexture(target, 0);
                clearTexture(texture, format, pixelType);
            } catch (RuntimeException | Error e) {
                LWJGL.glDeleteTextures(texture);
                this.destroy();
                throw e;
            }

            int imageUnit = this.images.size();
            int samplerUnit = -1;
            if (definition.samplerName != null && !definition.samplerName.isEmpty()) {
                if (nextSamplerUnit <= lastSamplerUnit) {
                    samplerUnit = nextSamplerUnit++;
                } else {
                    LOGGER.error("[Umbra] Out of texture units for image sampler '{}' (last usable is {}); image.{} "
                                    + "remains writable on image unit {}, but the sampler stays on texture unit 0 "
                                    + "and will fail every draw in any program declaring it with "
                                    + "GL_INVALID_OPERATION on a strict driver. Per-program sampler allocation "
                                    + "(gl/program/ProgramSamplers) is what removes this shortage.",
                            definition.samplerName, lastSamplerUnit, definition.name, imageUnit);
                }
            }
            this.images.add(new Image(definition, texture, target, imageUnit, samplerUnit,
                    internalFormat, format, pixelType));
            this.uniformOverrides.put(definition.name, imageUnit);
            if (samplerUnit >= 0) {
                this.uniformOverrides.put(definition.samplerName, samplerUnit);
            }
        }
    }

    // Returns the GL internal format of a name or zero.
    private static int glInternalFormat(String name) {
        return InternalTextureFormat.fromString(name).map(InternalTextureFormat::getInternalFormat).orElse(0);
    }

    // Returns whether a format name is an integer format.
    private static boolean isIntegerFormat(String name) {
        return InternalTextureFormat.fromString(name).map(InternalTextureFormat::isInteger).orElse(Boolean.FALSE);
    }

    // Returns the GL pixel format of a name or zero.
    private static int glFormat(String name) {
        switch (name) {
            case "red_integer": return GL30.GL_RED_INTEGER;
            case "rg_integer": return GL30.GL_RG_INTEGER;
            case "rgb_integer": return GL30.GL_RGB_INTEGER;
            case "rgba_integer": return GL30.GL_RGBA_INTEGER;
            case "red": return GL11.GL_RED;
            case "rg": return GL30.GL_RG;
            case "rgb": return GL11.GL_RGB;
            case "rgba": return GL11.GL_RGBA;
            default: return 0;
        }
    }

    // Returns the GL pixel type of a name or zero.
    private static int glPixelType(String name) {
        switch (name) {
            case "unsigned_int": return GL11.GL_UNSIGNED_INT;
            case "unsigned_short": return GL11.GL_UNSIGNED_SHORT;
            case "unsigned_byte": return GL11.GL_UNSIGNED_BYTE;
            case "int": return GL11.GL_INT;
            case "short": return GL11.GL_SHORT;
            case "byte": return GL11.GL_BYTE;
            case "half_float": return GL30.GL_HALF_FLOAT;
            case "float": return GL11.GL_FLOAT;
            default: return 0;
        }
    }

    // Returns the width of an image.
    private int relativeSizeX(CustomImageDefinition definition) {
        return definition.relative ? Math.max(1, (int) (this.renderWidth * definition.relativeX)) : definition.sizeX;
    }

    // Returns the height of an image.
    private int relativeSizeY(CustomImageDefinition definition) {
        return definition.relative ? Math.max(1, (int) (this.renderHeight * definition.relativeY)) : definition.sizeY;
    }

    // Reallocates and clears the relative images at a new render size.
    public void onResize(int width, int height) {
        if (width == this.renderWidth && height == this.renderHeight) {
            return;
        }
        this.renderWidth = width;
        this.renderHeight = height;
        boolean bound = false;
        for (Image image : this.images) {
            if (!image.definition.relative) {
                continue;
            }
            if (!bound) {
                GlTextureUnits.selectScratch(RESIZE_SCRATCH_UNIT);
                bound = true;
            }
            LWJGL.glBindTexture(image.target, image.texture);
            LWJGL.glTexImage2D(image.target, 0, image.glInternalFormat,
                    relativeSizeX(image.definition), relativeSizeY(image.definition),
                    0, image.glFormat, image.glPixelType, (ByteBuffer) null);
            clearTexture(image.texture, image.glFormat, image.glPixelType);
        }
        if (bound) {
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GlTextureUnits.resetToUnit0();
        }
    }

    // Returns whether no image exists.
    public boolean isEmpty() {
        return this.images.isEmpty();
    }

    // Returns the units of the image and sampler uniforms.
    public Map<String, Integer> getUniformOverrides() {
        return this.uniformOverrides;
    }

    // Returns the next free image unit.
    public int getNextAvailableImageUnit() {
        return this.images.size();
    }

    // Returns the number of image units the driver allows.
    public int getHardwareImageUnits() {
        return this.hardwareImageUnits;
    }

    // Returns the size of the first 3D image or null.
    public int[] getFirst3DImageSize() {
        for (Image image : this.images) {
            if (image.definition.sizeZ > 0) {
                return new int[]{image.definition.sizeX, image.definition.sizeY, image.definition.sizeZ};
            }
        }
        return null;
    }

    // Clears the images that clear each frame and makes the clears visible.
    public void clearAll() {
        boolean cleared = false;
        for (Image image : this.images) {
            if (image.definition.clear) {
                clearTexture(image.texture, image.glFormat, image.glPixelType);
                cleared = true;
            }
        }
        if (cleared) {
            LWJGL.glMemoryBarrier(CLEAR_VISIBILITY_BARRIERS);
        }
    }

    // Clears a texture to zero.
    private void clearTexture(int texture, int format, int pixelType) {
        LWJGL.glClearTexImage(texture, 0, format, pixelType, null);
    }

    // Binds every image to its image unit and its sampler unit.
    public void bindAll() {
        for (Image image : this.images) {
            LWJGL.glBindImageTexture(image.imageUnit, image.texture, 0, true, 0, GL15.GL_READ_WRITE,
                    image.glInternalFormat);
            if (image.samplerUnit >= 0) {
                GlTextureUnits.selectScratch(image.samplerUnit);
                LWJGL.glBindTexture(image.target, image.texture);
            }
        }
        GlTextureUnits.resetToUnit0();
    }

    // Unbinds every image from its sampler unit.
    public void unbindAll() {
        for (Image image : this.images) {
            if (image.samplerUnit >= 0) {
                GlTextureUnits.selectScratch(image.samplerUnit);
                LWJGL.glBindTexture(image.target, 0);
            }
        }
        GlTextureUnits.resetToUnit0();
    }

    // Deletes every image and forgets the overrides.
    public void destroy() {
        for (Image image : this.images) {
            LWJGL.glDeleteTextures(image.texture);
        }
        this.images.clear();
        this.uniformOverrides.clear();
    }

}
