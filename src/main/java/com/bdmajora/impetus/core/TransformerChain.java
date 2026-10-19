package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.outlands.foundation.boot.TransformerHolder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Foundation walks its one transformer LinkedList unlocked on every class load, so Forge registering ModAPITransformer on the client thread mid loadMods kills whatever class the splash thread is loading right then (seen: SDL_TouchFingerEvent, a ConcurrentModificationException inside NoClassDefFoundError)
final class TransformerChain {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    // Cleanroom's stand-in for LaunchClassLoader.transformers, handed to mods that reflect on it; copied from the holder on each lookup, so one made before the swap still names the old list
    static final String CLEANROOM_MIRROR = "com.cleanroommc.cleanroom.compatibility.CompatibilityHooks$TransformerFields";

    private TransformerChain() {
    }

    // From the coremod constructor, before Impetus or the splash screen starts a thread that loads classes
    static void makeConcurrent() {
        List<IClassTransformer> chain;
        try {
            chain = TransformerHolder.transformers;
        } catch (LinkageError e) {
            LOGGER.warn("Foundation's transformer chain moved; class loading stays unguarded against concurrent registration", e);
            return;
        }
        if (chain == null || chain instanceof CopyOnWriteArrayList) {
            return;
        }
        // The JDK's own list, never a subclass: every class load walks this, so a class of ours on that path would itself load through the walk (an anonymous iterator did: ClassCircularityError on the next class)
        CopyOnWriteArrayList<IClassTransformer> snapshot = new CopyOnWriteArrayList<>(chain);
        TransformerHolder.transformers = snapshot;
        repointMirror(CLEANROOM_MIRROR, chain, snapshot);
    }

    // Only a mirror still holding the replaced list moves, so a newer Cleanroom that keeps its own copy is left alone
    static void repointMirror(String mirrorClass, List<IClassTransformer> replaced, List<IClassTransformer> replacement) {
        try {
            Field mirror = Class.forName(mirrorClass, false, TransformerChain.class.getClassLoader()).getDeclaredField("transformers");
            mirror.setAccessible(true);
            if (mirror.get(null) == replaced) {
                mirror.set(null, replacement);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("No Cleanroom transformer mirror to follow the swap: {}", e.toString());
        }
    }
}
