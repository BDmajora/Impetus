package com.bdmajora.impetus.umbra.pbr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

// Reader of the texture format of the resource packs.
public final class TextureFormatLoader {
    // Location of the texture properties file.
    private static final ResourceLocation LOCATION = new ResourceLocation("minecraft", "optifine/texture.properties");

    // Prevents instantiation
    private TextureFormatLoader() {
    }

    // Adds the macros of the declared texture format.
    public static void addFormatMacros(Map<String, String> macros) {
        String format = readDeclaredFormat();
        if (format == null || format.isEmpty()) {
            return;
        }

        String name = format;
        String version = null;
        int lastDash = format.lastIndexOf('-');
        if (lastDash > 0 && lastDash + 1 < format.length() && Character.isDigit(format.charAt(lastDash + 1))) {
            name = format.substring(0, lastDash);
            version = format.substring(lastDash + 1);
        }

        String base = "MC_TEXTURE_FORMAT_" + sanitize(name);
        macros.put(base, "");
        if (version != null) {
            macros.put(base + "_" + sanitize(version), "");
        }
    }

    // Returns a token with only letters and digits and underscores.
    private static String sanitize(String token) {
        return token.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
    }

    // Returns the declared format or null.
    private static String readDeclaredFormat() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            return null;
        }

        try (IResource resource = mc.getResourceManager().getResource(LOCATION)) {
            Properties properties = new Properties();
            properties.load(resource.getInputStream());
            String format = properties.getProperty("format");
            return format != null ? format.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
