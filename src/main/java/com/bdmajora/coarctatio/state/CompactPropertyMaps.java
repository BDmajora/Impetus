package com.bdmajora.coarctatio.state;

import net.minecraft.block.properties.IProperty;
import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.util.ClassDefineTool;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Builds property maps that share one key array between states.
public final class CompactPropertyMaps {
    // Name of the injected map class.
    private static final String MAP_CLASS = "com.google.common.collect.CoarctatioPropertyMap";

    // Count of maps that were compacted.
    private static final AtomicInteger COMPACTED = new AtomicInteger();
    // Count of maps left unchanged.
    private static final AtomicInteger DECLINED = new AtomicInteger();

    // Constructor of the injected map class or null when unavailable.
    private static volatile Constructor<?> constructor;
    // Finished state of the initialisation.
    private static volatile boolean initialised;

    // Prevents instantiation
    private CompactPropertyMaps() {
    }

    // Defines the injected map class once and skips the lock after the first call.
    private static void initialise() {
        if (initialised) {
            return;
        }
        synchronized (CompactPropertyMaps.class) {
            if (initialised) {
                return;
            }
            try {
                constructor = findConstructor();
            } finally {
                initialised = true;
            }
        }
    }

    // Returns the constructor of the injected map class or null when disabled or unavailable.
    private static Constructor<?> findConstructor() {
        if (!CoarctatioConfig.get().compactStateProperties) {
            return null;
        }

        Class<?> defined = ClassDefineTool.defineClass(ImmutableMap.class, MAP_CLASS);

        if (defined == null) {
            return null;
        }

        try {
            return defined.getConstructor(Object[].class, Object[].class);
        } catch (NoSuchMethodException e) {
            Coarctatio.LOGGER.warn("Injected {} has no usable constructor", MAP_CLASS, e);
            return null;
        }
    }

    // Returns a compact copy of the map or the original when its keys differ from the shared keys.
    public static ImmutableMap<IProperty<?>, Comparable<?>> compact(
            Object[] sharedKeys,
            ImmutableMap<IProperty<?>, Comparable<?>> original) {
        initialise();
        Constructor<?> constructor = CompactPropertyMaps.constructor;

        if (constructor == null || sharedKeys == null) {
            DECLINED.incrementAndGet();
            return original;
        }

        Object[] values = new Object[sharedKeys.length];
        int index = 0;

        for (Map.Entry<IProperty<?>, Comparable<?>> entry : original.entrySet()) {
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

    // Returns the number of compacted maps.
    public static long compacted() {
        return COMPACTED.get();
    }

    // Returns a one line report of the results.
    public static String statistics() {
        if (COMPACTED.get() == 0 && DECLINED.get() == 0) {
            return "unused";
        }

        return String.format("%d compacted, %d left on Guava", COMPACTED.get(), DECLINED.get());
    }
}
