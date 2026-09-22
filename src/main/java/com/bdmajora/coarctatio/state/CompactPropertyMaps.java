package com.bdmajora.coarctatio.state;

import net.minecraft.block.properties.IProperty;
import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.util.ClassDefineTool;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Replaces each state's property ImmutableMap with a compact one sharing the block's key array; the map class is injected into com.google.common.collect (see ClassDefineTool), and a key-order mismatch just falls back to the original
public final class CompactPropertyMaps {
    private static final String MAP_CLASS = "com.google.common.collect.CoarctatioPropertyMap";

    private static final AtomicInteger COMPACTED = new AtomicInteger();
    private static final AtomicInteger DECLINED = new AtomicInteger();

    private static Constructor<?> constructor;
    private static boolean initialised;

    private CompactPropertyMaps() {
    }

    // Attempts the class injection once; safe to call repeatedly, and a failure just makes compact() a no-op
    private static synchronized void initialise() {
        if (initialised) {
            return;
        }
        initialised = true;

        if (!CoarctatioConfig.get().compactStateProperties) {
            return;
        }

        Class<?> defined = ClassDefineTool.defineClass(ImmutableMap.class, MAP_CLASS);

        if (defined == null) {
            return;
        }

        try {
            constructor = defined.getConstructor(Object[].class, Object[].class);
        } catch (NoSuchMethodException e) {
            Coarctatio.LOGGER.warn("Injected {} has no usable constructor", MAP_CLASS, e);
        }
    }

    // sharedKeys comes from PropertyValueMapper#sharedKeys; returns a compact equivalent of original, or original itself.
    public static ImmutableMap<IProperty<?>, Comparable<?>> compact(
            Object[] sharedKeys,
            ImmutableMap<IProperty<?>, Comparable<?>> original) {
        initialise();

        if (constructor == null || sharedKeys == null) {
            DECLINED.incrementAndGet();
            return original;
        }

        Object[] values = new Object[sharedKeys.length];
        int index = 0;

        for (Map.Entry<IProperty<?>, Comparable<?>> entry : original.entrySet()) {
            // Guarded rather than assumed: if iteration order ever diverges from the shared key array, values would silently line up against the wrong properties
            if (index >= sharedKeys.length || entry.getKey() != sharedKeys[index]) {
                DECLINED.incrementAndGet();
                return original;
            }

            values[index++] = entry.getValue();
        }

        if (index != sharedKeys.length) {
            DECLINED.incrementAndGet();
            return original;
        }

        try {
            @SuppressWarnings("unchecked")
            ImmutableMap<IProperty<?>, Comparable<?>> compacted =
                    (ImmutableMap<IProperty<?>, Comparable<?>>)
                            constructor.newInstance(sharedKeys, values);

            COMPACTED.incrementAndGet();
            return compacted;
        } catch (ReflectiveOperationException | RuntimeException e) {
            DECLINED.incrementAndGet();
            return original;
        }
    }

    // Property maps replaced with the compact implementation.
    public static long compacted() {
        return COMPACTED.get();
    }

    // Shared versus fallen-back counts for /coarctatio
    public static String statistics() {
        if (COMPACTED.get() == 0 && DECLINED.get() == 0) {
            return "unused";
        }

        return String.format("%d compacted, %d left on Guava", COMPACTED.get(), DECLINED.get());
    }
}
