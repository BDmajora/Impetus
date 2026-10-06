package com.bdmajora.coarctatio.util;

import com.bdmajora.coarctatio.Coarctatio;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;

// Defines a class into another class's runtime package for package-private access, through a private lookup on the host; injected classes may reference only their host package and java.*
public final class ClassDefineTool {
    private static boolean warned;

    private ClassDefineTool() {
    }

    // Reads name from our resources and defines it alongside host, borrowing its loader and package; name must be in host's package, and null means the JVM refused, a normal outcome
    public static Class<?> defineClass(Class<?> host, String name) {
        byte[] bytecode = readBytecode(name);

        if (bytecode == null) {
            return null;
        }

        // Already present from a previous attempt (or a duplicate call) — reuse rather than fail.
        try {
            return Class.forName(name, false, host.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Expected on the first call.
        }

        Class<?> defined = defineWithLookup(host, bytecode);

        if (defined == null && !warned) {
            warned = true;
            Coarctatio.LOGGER.warn("Could not define classes into {}'s package; features that need it stay off",
                    host.getName());
        }

        return defined;
    }

    // privateLookupIn works across loaders here because both sides are unnamed modules, which read and open everything; the resulting lookup keeps package access, which is all defineClass needs
    private static Class<?> defineWithLookup(Class<?> host, byte[] bytecode) {
        try {
            return MethodHandles.privateLookupIn(host, MethodHandles.lookup()).defineClass(bytecode);
        } catch (IllegalAccessException | RuntimeException | LinkageError e) {
            // A named, closed host module, or a class already defined under that name by another path
            return null;
        }
    }

    // Loads the class file bytes from our own jar, since the target loader cannot see it
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
