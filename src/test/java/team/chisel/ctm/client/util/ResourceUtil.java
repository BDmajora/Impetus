package team.chisel.ctm.client.util;

import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

// Test stand-in for CTM's resource helpers: a texture has metadata when the test says its file does
public class ResourceUtil {
    public static final Set<ResourceLocation> WITH_METADATA = new HashSet<>();

    // A file named here fails to read, the way a corrupt mcmeta would
    public static final Set<ResourceLocation> UNREADABLE = new HashSet<>();

    public static ResourceLocation spriteToAbsolute(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
    }

    public static Object getMetadata(ResourceLocation file) throws IOException {
        if (UNREADABLE.contains(file)) {
            throw new IOException("unreadable " + file);
        }
        return WITH_METADATA.contains(file) ? "ctm" : null;
    }
}
