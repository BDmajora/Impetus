package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.mixin.particle.ParticleManagerAccessor;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Remembers every particle class seen and which ones are switched off.
public final class ParticleClassRegistry {
    // Single instance.
    private static final ParticleClassRegistry INSTANCE = new ParticleClassRegistry();

    // Display name of each class name.
    private final ConcurrentHashMap<String, String> discoveredClasses = new ConcurrentHashMap<>();

    // Class names that are switched off.
    private final Set<String> disabledClasses = ConcurrentHashMap.newKeySet();

    // Classes that were recorded.
    private final Set<Class<?>> seenClasses = ConcurrentHashMap.newKeySet();

    // Whether the data changed since the last save.
    private volatile boolean dirty;

    // Prevents instantiation
    private ParticleClassRegistry() {
    }

    // Returns the single instance.
    public static ParticleClassRegistry getInstance() {
        return INSTANCE;
    }

    // Records a class the first time it is seen.
    public void recordClass(Class<?> clazz) {
        if (clazz == null || !seenClasses.add(clazz)) {
            return;
        }
        if (discoveredClasses.putIfAbsent(clazz.getName(), simpleNameOf(clazz)) == null) {
            dirty = true;
        }
    }

    // Records the particle classes of every factory.
    public void scanFactories(ParticleManager particleManager) {
        if (particleManager == null) {
            return;
        }

        Map<Integer, IParticleFactory> factories;
        try {
            factories = ((ParticleManagerAccessor) particleManager).impetus$getParticleTypes();
        } catch (Throwable t) {
            return;
        }

        if (factories == null) {
            return;
        }

        for (Map.Entry<Integer, IParticleFactory> entry : factories.entrySet()) {
            IParticleFactory factory = entry.getValue();
            if (factory == null) {
                continue;
            }

            Class<?> factoryClass = factory.getClass();
            try {
                Class<?> enclosing = factoryClass.getEnclosingClass();
                if (enclosing != null && Particle.class.isAssignableFrom(enclosing)) {
                    recordClass(enclosing);
                    continue;
                }

                for (Method method : factoryClass.getDeclaredMethods()) {
                    Class<?> returnType = method.getReturnType();
                    if (returnType != Particle.class && Particle.class.isAssignableFrom(returnType)) {
                        recordClass(returnType);
                        break;
                    }
                }
            } catch (Throwable t) {
            }
        }
    }

    // Forgets classes that no longer exist.
    public void pruneDiscoveredCache() {
        ClassLoader loader = ParticleClassRegistry.class.getClassLoader();

        for (String name : new ArrayList<>(discoveredClasses.keySet())) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                discoveredClasses.remove(name);
                dirty = true;
            } catch (Throwable t) {
            }
        }
    }

    // Returns whether the class is switched off.
    public boolean isClassDisabled(String fullClassName) {
        return disabledClasses.contains(fullClassName);
    }

    // Returns whether no class is switched off.
    public boolean isEmptyDisabled() {
        return disabledClasses.isEmpty();
    }

    // Replaces the switched off classes and ignores null and empty names.
    public void loadDisabledClasses(String[] classes) {
        disabledClasses.clear();
        if (classes == null) {
            return;
        }
        for (String name : classes) {
            if (name != null && !name.isEmpty()) {
                disabledClasses.add(name);
            }
        }
    }

    // Returns the sorted switched off class names.
    public String[] getDisabledClassesArray() {
        return disabledClasses.stream().sorted().toArray(String[]::new);
    }

    // Returns whether the data changed since the last save.
    public boolean isDirty() {
        return dirty;
    }

    // Marks the data as saved.
    public void markClean() {
        dirty = false;
    }

    // Adds the stored classes and ignores null and empty entries.
    public void loadDiscoveredClasses(String[] entries) {
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            int separator = entry.indexOf('|');
            String fullName = separator > 0 ? entry.substring(0, separator) : entry;
            String simpleName = separator > 0 ? entry.substring(separator + 1) : "";

            if (fullName.isEmpty()) {
                continue;
            }

            if (simpleName.isEmpty()) {
                simpleName = toSimpleName(fullName);
            }
            if (simpleName.isEmpty()) {
                simpleName = fullName;
            }

            discoveredClasses.putIfAbsent(fullName, simpleName);
        }
    }

    // Returns the classes as sorted text entries.
    public String[] getDiscoveredClassesArray() {
        return discoveredClasses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "|" + entry.getValue())
                .toArray(String[]::new);
    }

    // Returns the name without package and outer class.
    private static String toSimpleName(String fullName) {
        String name = fullName.substring(fullName.lastIndexOf('.') + 1);
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }

    // Returns the display name of the class.
    private static String simpleNameOf(Class<?> clazz) {
        String name;
        try {
            name = clazz.getSimpleName();
        } catch (Throwable t) {
            name = "";
        }

        if (name == null || name.isEmpty()) {
            name = toSimpleName(clazz.getName());
        }
        if (name.isEmpty()) {
            name = clazz.getName();
        }

        return name;
    }
}
