package com.bdmajora.impetus.engine.impl.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

// Reflective bridge to whatever shader mod is installed; the engine module has no dependency on the shader subsystem, so it works for both our Umbra pipeline and a third-party Iris build
public class ShaderModBridge {
    // Bound to the shader API instance at class-init and reused every call; null means "no shader mod present"
    private static final MethodHandle SHADERS_ENABLED;
    private static final MethodHandle SHADERS_OPEN_SCREEN;
    private static final MethodHandle NVIDIUM_ENABLED;

    static {
        MethodHandle shadersEnabled = null, shaderOpenScreen = null;
        try {
            // The Iris public API contract; Umbra SHIPS this exact class, package and method names because that is what the ecosystem probes for, and renaming any of them silently drops the shader option pages
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceGetter = irisApiClass.getDeclaredMethod("getInstance");
            // Singleton, so it is fetched once here and the handles are permanently bound to it below
            Object irisApiInstance = instanceGetter.invoke(null);
            shadersEnabled = MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("isShaderPackInUse")).bindTo(irisApiInstance);
            // Takes and returns Object rather than GuiScreen so this module never has to name a client class
            shaderOpenScreen =  MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("openMainIrisScreenObj", Object.class)).bindTo(irisApiInstance);
        } catch (NoSuchMethodException e) {
            // The class was found but a method was not: an API version we do not understand, worth printing
            e.printStackTrace();
        } catch (Throwable ignored) {
            // Everything else — ClassNotFoundException above all — is the ordinary "no shader mod installed" case
        }
        SHADERS_ENABLED = shadersEnabled;
        SHADERS_OPEN_SCREEN = shaderOpenScreen;
        MethodHandle nvidiumEnabled = null;
        try {
            // Nvidium exposes a plain static boolean field instead of an API object, so this is a getter handle
            Class<?> nvidiumClass = Class.forName("me.cortex.nvidium.Nvidium");
            nvidiumEnabled = MethodHandles.lookup().findStaticGetter(nvidiumClass, "IS_ENABLED", boolean.class);
        } catch (Throwable ignored) {
        }
        NVIDIUM_ENABLED = nvidiumEnabled;
    }

    // Nvidium's static flag; false when it is not installed
    public static boolean isNvidiumEnabled() {
        return invokeBoolean(NVIDIUM_ENABLED);
    }

    // Whether a shader pack is loaded RIGHT NOW, not merely installed; used per-frame to pick between the vanilla and shader render paths
    public static boolean areShadersEnabled() {
        return invokeBoolean(SHADERS_ENABLED);
    }

    // invokeExact needs the call site descriptor to match exactly (hence the cast, both handles are ()boolean); any failure answers false so a broken shader mod degrades to "no shaders"
    private static boolean invokeBoolean(MethodHandle handle) {
        if (handle == null) {
            return false;
        }

        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }

    // Whether the API class resolved at class-init; the video options screen gates the shader pack pages on this
    public static boolean isShaderModPresent() {
        return SHADERS_ENABLED != null;
    }

    // Opens the shader mod's pack selection screen with the given parent; invoke (not invokeExact) since the handle's return is taken as Object, null means don't navigate
    public static Object openShaderScreen(Object parentScreen) {
        if(SHADERS_OPEN_SCREEN != null) {
            try {
                return SHADERS_OPEN_SCREEN.invoke(parentScreen);
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
