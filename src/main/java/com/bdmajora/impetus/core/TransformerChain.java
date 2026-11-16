package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.outlands.foundation.boot.TransformerHolder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Copy on write list of class transformers.
final class TransformerChain {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    // Cleanroom class that mirrors the transformer list.
    static final String CLEANROOM_MIRROR = "com.cleanroommc.cleanroom.compatibility.CompatibilityHooks$TransformerFields";

    // Prevents instantiation
    private TransformerChain() {
    }

    // Swaps the transformer list for a copy on write list.
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
        CopyOnWriteArrayList<IClassTransformer> snapshot = new CopyOnWriteArrayList<>(chain);
        TransformerHolder.transformers = snapshot;
        repointMirror(CLEANROOM_MIRROR, chain, snapshot);
    }

    // Points a mirror class at the new list.
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
