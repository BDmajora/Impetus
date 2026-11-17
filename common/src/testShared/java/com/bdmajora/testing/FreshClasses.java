package com.bdmajora.testing;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

// Loader with its own class copies.
public final class FreshClasses extends ClassLoader {
    private final List<String> roots;

    public FreshClasses(Class<?>... roots) {
        super(FreshClasses.class.getClassLoader());
        this.roots = Arrays.stream(roots).map(Class::getName).toList();
    }

    // Returns this loader's copy.
    public Class<?> load(Class<?> type) throws ClassNotFoundException {
        return Class.forName(type.getName(), false, this);
    }

    // This loader's copy of the class with its static initialiser run
    public Class<?> initialise(Class<?> type) throws ClassNotFoundException {
        return Class.forName(type.getName(), true, this);
    }

    private boolean owns(String name) {
        for (String root : this.roots) {
            if (name.equals(root) || name.startsWith(root + "$")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!owns(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) {
                        throw new ClassNotFoundException(name);
                    }
                    byte[] bytes = in.readAllBytes();
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
