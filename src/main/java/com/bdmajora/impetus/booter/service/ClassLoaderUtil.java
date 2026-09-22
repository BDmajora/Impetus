package com.bdmajora.impetus.booter.service;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.service.IClassTracker;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class ClassLoaderUtil implements IClassTracker {

    private static final String CACHED_CLASSES_FIELD = "cachedClasses";
    private static final String INVALID_CLASSES_FIELD = "invalidClasses";
    private static final String CLASS_LOADER_EXCEPTIONS_FIELD = "classLoaderExceptions";
    private static final String TRANSFORMER_EXCEPTIONS_FIELD = "transformerExceptions";

    private final Map<String, Class<?>> cachedClasses;
    private final Set<String> invalidClasses, classLoaderExceptions, transformerExceptions;

    ClassLoaderUtil() {
        this.cachedClasses = getField(CACHED_CLASSES_FIELD);
        this.invalidClasses = getField(INVALID_CLASSES_FIELD);
        this.classLoaderExceptions = getField(CLASS_LOADER_EXCEPTIONS_FIELD);
        this.transformerExceptions = getField(TRANSFORMER_EXCEPTIONS_FIELD);
    }

    // Checks LaunchClassLoader's cache, not the parent, since that is where transformed classes live
    @Override
    public boolean isClassLoaded(String name) {
        return this.cachedClasses.containsKey(name);
    }

    // Reports which exclusion lists a class is on, for Mixin's diagnostics
    @Override
    public String getClassRestrictions(String className) {
        String restrictions = "";
        if (this.isClassClassLoaderExcluded(className, null)) {
            restrictions = "PACKAGE_CLASSLOADER_EXCLUSION";
        }
        if (this.isClassTransformerExcluded(className, null)) {
            restrictions = (!restrictions.isEmpty() ? restrictions + "," : "") + "PACKAGE_TRANSFORMER_EXCLUSION";
        }
        return restrictions;
    }

    // Adds to LaunchClassLoader's invalid set so a failed transform is not retried
    @Override
    public void registerInvalidClass(String name) {
        this.invalidClasses.add(name);
    }

    // Whether the name or transformedName appears in either exclusion list
    boolean isClassExcluded(String name, String transformedName) {
        return this.isClassClassLoaderExcluded(name, transformedName) || this.isClassTransformerExcluded(name, transformedName);
    }

    // Prefix match against the class loader exclusions
    private boolean isClassClassLoaderExcluded(String name, String transformedName) {
        return isPrefixExcluded(this.getClassLoaderExceptions(), name, transformedName);
    }

    // Prefix match against the transformer exclusions
    private boolean isClassTransformerExcluded(String name, String transformedName) {
        return isPrefixExcluded(this.getTransformerExceptions(), name, transformedName);
    }

    // Whether either name starts with any listed package prefix
    private static boolean isPrefixExcluded(Set<String> exceptions, String name, String transformedName) {
        for (final String exception : exceptions) {
            if ((transformedName != null && transformedName.startsWith(exception)) || name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    // Reflective read of LaunchClassLoader's private set
    private Set<String> getClassLoaderExceptions() {
        return this.classLoaderExceptions != null ? this.classLoaderExceptions : Collections.<String>emptySet();
    }

    // Reflective read of LaunchClassLoader's private set
    private Set<String> getTransformerExceptions() {
        return this.transformerExceptions != null ? this.transformerExceptions : Collections.<String>emptySet();
    }

    // Reads a private LaunchClassLoader field; these have been stable across every 1.12.2 launchwrapper
    @SuppressWarnings("unchecked")
    private static <T> T getField(String fieldName) {
        try {
            Field field = LaunchClassLoader.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(Launch.classLoader);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to reflect into LaunchClassLoader", e);
        }
    }

}
