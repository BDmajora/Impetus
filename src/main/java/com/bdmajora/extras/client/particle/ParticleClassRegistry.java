package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.mixin.particle.ParticleManagerAccessor;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

// Remembers every particle class and its mod.
public final class ParticleClassRegistry {
    // Single instance.
    private static final ParticleClassRegistry INSTANCE = new ParticleClassRegistry();

    // Display name of each class name.
    private final ConcurrentHashMap<String, String> discoveredClasses = new ConcurrentHashMap<>();

    // Mod of each class name.
    private final ConcurrentHashMap<String, String> classModIds = new ConcurrentHashMap<>();

    // Mod of each factory.
    private final Map<IParticleFactory, String> factoryModIds =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Class names that are switched off.
    private final Set<String> disabledClasses = ConcurrentHashMap.newKeySet();

    // Classes that were recorded.
    private final Set<Class<?>> seenClasses = ConcurrentHashMap.newKeySet();

    // Whether the data changed since the last save.
    private volatile boolean dirty;

    // Mod of each source file or null before the first use.
    private volatile Map<File, String> sourceToModId;

    // Prevents instantiation
    private ParticleClassRegistry() {
    }

    // Returns the single instance.
    public static ParticleClassRegistry getInstance() {
        return INSTANCE;
    }

    // Records a class without a factory.
    public void recordClass(Class<?> clazz) {
        recordClass(clazz, null);
    }

    // Records a class and finds its mod.
    public void recordClass(Class<?> clazz, IParticleFactory factory) {
        if (clazz == null) {
            return;
        }

        boolean firstSeen = seenClasses.add(clazz);
        String fullName = clazz.getName();

        if (firstSeen && discoveredClasses.putIfAbsent(fullName, simpleNameOf(clazz)) == null) {
            dirty = true;
        }

        if (!firstSeen && (factory == null || classModIds.containsKey(fullName))) {
            return;
        }

        String modId = resolveModId(clazz, factory);
        if (modId != null) {
            classModIds.putIfAbsent(fullName, modId);
        }
    }

    // Stores the mod of a factory.
    public void registerFactoryMod(IParticleFactory factory, String modId) {
        if (factory != null && modId != null) {
            factoryModIds.put(factory, modId);
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
                    recordClass(enclosing, factory);
                    continue;
                }

                for (Method method : factoryClass.getDeclaredMethods()) {
                    Class<?> returnType = method.getReturnType();
                    if (returnType != Particle.class && Particle.class.isAssignableFrom(returnType)) {
                        recordClass(returnType, factory);
                        break;
                    }
                }
            } catch (Throwable t) {
            }
        }
    }

    // Forgets classes that no longer exist and fills missing mods.
    public void pruneDiscoveredCache() {
        ClassLoader loader = ParticleClassRegistry.class.getClassLoader();

        for (String name : new ArrayList<>(discoveredClasses.keySet())) {
            Class<?> clazz;
            try {
                clazz = Class.forName(name, false, loader);
            } catch (ClassNotFoundException e) {
                discoveredClasses.remove(name);
                classModIds.remove(name);
                dirty = true;
                continue;
            } catch (Throwable t) {
                continue;
            }

            if (!classModIds.containsKey(name)) {
                String modId = resolveModId(clazz, null);
                if (modId != null) {
                    classModIds.put(name, modId);
                }
            }
        }
    }

    // Returns the mod of the class or null.
    private String resolveModId(Class<?> clazz, IParticleFactory factory) {
        String name = clazz.getName();
        if (name.startsWith("net.minecraft.")) {
            return "minecraft";
        }

        if (factory != null) {
            String captured = factoryModIds.get(factory);
            if (captured != null) {
                return captured;
            }
        }

        return modIdFromCodeSource(clazz);
    }

    // Returns the mod that owns the file of the class.
    private String modIdFromCodeSource(Class<?> clazz) {
        try {
            CodeSource source = clazz.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }

            URL location = source.getLocation();
            if (location == null) {
                return null;
            }

            File file;
            try {
                file = new File(location.toURI());
            } catch (Exception e) {
                file = new File(location.getPath());
            }

            return sourceMap().get(canonical(file));
        } catch (Throwable t) {
            return null;
        }
    }

    // Returns the mod of each source file.
    private Map<File, String> sourceMap() {
        Map<File, String> map = sourceToModId;
        if (map == null) {
            map = new HashMap<>();
            try {
                for (ModContainer container : Loader.instance().getActiveModList()) {
                    File source = container.getSource();
                    if (source != null) {
                        map.put(canonical(source), container.getModId());
                    }
                }
            } catch (Throwable t) {
            }
            sourceToModId = map;
        }
        return map;
    }

    // Returns the mod of the class name or null.
    public String getModId(String fullClassName) {
        String modId = classModIds.get(fullClassName);
        if (modId != null) {
            return modId;
        }
        return fullClassName.startsWith("net.minecraft.") ? "minecraft" : null;
    }

    // Returns whether the class is switched off.
    public boolean isClassDisabled(String fullClassName) {
        return disabledClasses.contains(fullClassName);
    }

    // Returns whether no class is switched off.
    public boolean isEmptyDisabled() {
        return disabledClasses.isEmpty();
    }

    // Switches a class on or off.
    public void setClassEnabled(String fullClassName, boolean enabled) {
        boolean changed = enabled
                ? disabledClasses.remove(fullClassName)
                : disabledClasses.add(fullClassName);
        if (changed) {
            dirty = true;
        }
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

    // Returns a read only view of the classes.
    public Map<String, String> getDiscoveredClasses() {
        return Collections.unmodifiableMap(discoveredClasses);
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

    // Returns the canonical file or the absolute file.
    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
