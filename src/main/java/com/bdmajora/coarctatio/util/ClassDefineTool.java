package com.bdmajora.coarctatio.util;

import com.bdmajora.coarctatio.Coarctatio;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;

// Defines extra classes into the package of a host class.
public final class ClassDefineTool {
    // Whether the failure warning was logged.
    private static boolean warned;

    // Prevents instantiation
    private ClassDefineTool() {
    }

    // Returns the class defined next to the host or null when it cannot be defined.
    public static Class<?> defineClass(Class<?> host, String name) {
        byte[] bytecode = readBytecode(name);

        if (bytecode == null) {
            return null;
        }

        try {
            return Class.forName(name, false, host.getClassLoader());
        } catch (ClassNotFoundException ignored) {
        }

        Class<?> defined = defineWithLookup(host, bytecode);

        if (defined == null && !warned) {
            warned = true;
            Coarctatio.LOGGER.warn("Could not define classes into {}'s package; features that need it stay off",
                    host.getName());
        }

        return defined;
    }

    // Returns the class defined through a private lookup or null when access fails.
    private static Class<?> defineWithLookup(Class<?> host, byte[] bytecode) {
        try {
            return MethodHandles.privateLookupIn(host, MethodHandles.lookup()).defineClass(bytecode);
        } catch (IllegalAccessException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    // Returns the bytecode of the named class or null when it cannot be read.
    private static byte[] readBytecode(String name) {
        String path = "/" + name.replace('.', '/') + ".class";
        URL url = ClassDefineTool.class.getResource(path);

        if (url == null) {
            Coarctatio.LOGGER.warn("Could not find bytecode for {}", name);
            return null;
        }

        try {
            return IOUtils.toByteArray(url);
        } catch (IOException e) {
            Coarctatio.LOGGER.warn("Could not read bytecode for {}", name, e);
            return null;
        }
    }
}
