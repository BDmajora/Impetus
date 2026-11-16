package com.bdmajora.impetus.umbra.shaderpack.texture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Image directive of a shader pack.
public final class CustomImageDefinition {
    // Logger of malformed directives.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Name of the image.
    public final String name;
    // Name of the sampler that reads the image.
    public final String samplerName;
    // Name of the pixel format.
    public final String format;
    // Name of the internal format.
    public final String internalFormat;
    // Name of the pixel type.
    public final String pixelType;
    // Clear state of the image each frame.
    public final boolean clear;
    // Screen relative state of the size.
    public final boolean relative;
    // Width in pixels.
    public final int sizeX;
    // Height in pixels.
    public final int sizeY;
    // Depth in pixels or zero for a 2D image.
    public final int sizeZ;
    // Width as a share of the screen.
    public final float relativeX;
    // Height as a share of the screen.
    public final float relativeY;

    // Creates a definition.
    private CustomImageDefinition(String name, String samplerName, String format, String internalFormat,
                                  String pixelType, boolean clear, boolean relative, int sizeX, int sizeY, int sizeZ,
                                  float relativeX, float relativeY) {
        this.name = name;
        this.samplerName = samplerName;
        this.format = format;
        this.internalFormat = internalFormat;
        this.pixelType = pixelType;
        this.clear = clear;
        this.relative = relative;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
    }

    // Parses a directive and returns null and logs a malformed one.
    public static CustomImageDefinition parse(String name, String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 8 || parts.length > 9) {
            warnMalformed(name, value, parts);
            return null;
        }
        try {
            boolean clear = Boolean.parseBoolean(parts[4]);
            if (Boolean.parseBoolean(parts[5])) {
                float relativeX = Float.parseFloat(parts[6]);
                float relativeY = Float.parseFloat(parts[7]);
                // Rejects a relative size that is not a positive finite number
                if (!(relativeX > 0.0f && relativeX < Float.POSITIVE_INFINITY && relativeY > 0.0f && relativeY < Float.POSITIVE_INFINITY)) {
                    warnMalformed(name, value, parts);
                    return null;
                }
                return new CustomImageDefinition(name, parts[0], parts[1], parts[2], parts[3],
                        clear, true, 0, 0, 0, relativeX, relativeY);
            }
            int sizeX = Integer.parseInt(parts[6]);
            int sizeY = Integer.parseInt(parts[7]);
            int sizeZ = parts.length == 9 ? Integer.parseInt(parts[8]) : 0;
            // Rejects a width or height that is not positive or a negative depth
            if (sizeX <= 0 || sizeY <= 0 || sizeZ < 0) {
                warnMalformed(name, value, parts);
                return null;
            }
            return new CustomImageDefinition(name, parts[0], parts[1], parts[2], parts[3],
                    clear, false, sizeX, sizeY, sizeZ, 0.0f, 0.0f);
        } catch (NumberFormatException e) {
            warnMalformed(name, value, parts);
            return null;
        }
    }

    // Logs a malformed directive.
    private static void warnMalformed(String name, String value, String[] parts) {
        String samplerName = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "(unknown)";
        LOGGER.error("[Umbra] Malformed image directive image.{} = {}. The image is NOT created and sampler '{}' is "
                        + "left on texture unit 0, which fails every draw in any program that declares it with "
                        + "GL_INVALID_OPERATION on a strict driver",
                name, value, samplerName);
    }
}
