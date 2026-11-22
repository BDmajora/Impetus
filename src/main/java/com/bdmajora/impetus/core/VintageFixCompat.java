package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

// Tells VintageFix that Impetus replaces the chunk renderer, so its vanilla renderer patches stay off.
public final class VintageFixCompat {
    // Logger of the compatibility check.
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    // VintageFix coremod that holds the renderer flag.
    static final String VINTAGEFIX_CORE = "org.embeddedt.vintagefix.core.VintageFixCore";

    // Prevents instantiation
    private VintageFixCompat() {
    }

    // Claims the chunk renderer through the game class loader, which holds the coremods.
    public static void claimChunkRenderer() {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : VintageFixCompat.class.getClassLoader();
        claimChunkRenderer(loader);
    }

    // Sets VintageFixCore.VINTAGIUM, the flag VintageFix sets for Celeritas, so it skips chunk_rendering and entity_disappearing.
    static boolean claimChunkRenderer(ClassLoader loader) {
        if (loader == null || loader.getResource(VINTAGEFIX_CORE.replace('.', '/') + ".class") == null) {
            return false;
        }
        try {
            // VintageFix sets the flag in getMixinConfigs and reads it in getMixins; every config's onLoad runs between the two.
            Field flag = Class.forName(VINTAGEFIX_CORE, false, loader).getField("VINTAGIUM");
            if (!flag.getBoolean(null)) {
                flag.setBoolean(null, true);
                LOGGER.info("[Impetus] Told VintageFix that Impetus renders terrain; its vanilla chunk render and entity loop patches stay off");
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[Impetus] Could not tell VintageFix that Impetus renders terrain; entities may draw twice", e);
            return false;
        }
    }
}
