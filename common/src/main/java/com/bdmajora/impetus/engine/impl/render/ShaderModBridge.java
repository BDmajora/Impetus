package com.bdmajora.impetus.engine.impl.render;

import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

// Reflective bridge to the installed shader mod.
public class ShaderModBridge {
    // Logger of the bridge.
    private static final Logger LOGGER = LogManager.getLogger(ShaderModBridge.class);

    // Handle bound to the shader API instance or null without a shader mod.
    private static final MethodHandle SHADERS_ENABLED;
    private static final MethodHandle SHADERS_OPEN_SCREEN;
    private static final MethodHandle NVIDIUM_ENABLED;

    static {
        MethodHandle shadersEnabled = null, shaderOpenScreen = null;
        try {
            // Looks up the Iris API class Umbra ships.
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceGetter = irisApiClass.getDeclaredMethod("getInstance");
            // Fetches the API singleton.
            Object irisApiInstance = instanceGetter.invoke(null);
            shadersEnabled = MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("isShaderPackInUse")).bindTo(irisApiInstance);
            // Takes and returns Object rather than GuiScreen so this module never has to name a client class
            shaderOpenScreen =  MethodHandles.lookup().unreflect(irisApiClass.getDeclaredMethod("openMainIrisScreenObj", Object.class)).bindTo(irisApiInstance);
        } catch (NoSuchMethodException e) {
            // Logs an API version without the expected methods.
            LOGGER.warn("Shader mod API found without the expected methods, shader options stay hidden", e);
        } catch (Throwable ignored) {
            // Treats any other failure as no shader mod.
        }
        SHADERS_ENABLED = shadersEnabled;
        SHADERS_OPEN_SCREEN = shaderOpenScreen;
        MethodHandle nvidiumEnabled = null;
        try {
            // Looks up Nvidium's static enabled field.
            Class<?> nvidiumClass = Class.forName("me.cortex.nvidium.Nvidium");
            nvidiumEnabled = MethodHandles.lookup().findStaticGetter(nvidiumClass, "IS_ENABLED", boolean.class);
        } catch (Throwable ignored) {
        }
        NVIDIUM_ENABLED = nvidiumEnabled;
    }

    // Returns whether terrain is drawn by mesh shaders.
    public static boolean isNvidiumEnabled() {
        return MeshTerrainRenderer.isActive() || invokeBoolean(NVIDIUM_ENABLED);
    }

    // Returns whether a shader pack is loaded now.
    public static boolean areShadersEnabled() {
        return invokeBoolean(SHADERS_ENABLED);
    }

    // Invokes a boolean handle and returns false on a null handle or any failure.
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

    // Returns whether the shader API class resolved.
    public static boolean isShaderModPresent() {
        return SHADERS_ENABLED != null;
    }

    // Opens the shader mod's pack selection screen or returns null.
    public static Object openShaderScreen(Object parentScreen) {
        if(SHADERS_OPEN_SCREEN != null) {
            try {
                return SHADERS_OPEN_SCREEN.invoke(parentScreen);
            } catch(Throwable e) {
                LOGGER.error("Could not open the shader pack screen", e);
            }
        }
        return null;
    }
}
