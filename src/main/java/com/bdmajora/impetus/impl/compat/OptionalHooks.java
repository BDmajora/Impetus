package com.bdmajora.impetus.impl.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Binds a public static hook of a mod Impetus has no compile dependency on.
public final class OptionalHooks {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    private OptionalHooks() {
    }

    // Null when the class file is absent, so a missing mod is never loaded or logged; a present but unusable hook logs once.
    // The lookup links the class without initialising it, so the mod's static state only runs on the first real call.
    public static MethodHandle bind(ClassLoader loader, String owner, String name, MethodType type) {
        if (loader == null || owner == null || name == null || type == null
                || loader.getResource(owner.replace('.', '/') + ".class") == null) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName(owner, false, loader), name, type);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[Impetus] {} is present but {}{} could not be bound", owner, name, type, e);
            return null;
        }
    }
}
