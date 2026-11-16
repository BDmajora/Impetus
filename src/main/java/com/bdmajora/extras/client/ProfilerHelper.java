package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

// Adds profiler sections around entity rendering.
public final class ProfilerHelper {
    // Section name of each renderer class.
    private static final Map<Class<?>, String> NAME_CACHE = new WeakHashMap<>();

    // Prevents instantiation
    private ProfilerHelper() {
    }

    // Starts a profiler section for the renderer.
    public static void startSection(World world, Object renderer) {
        if (shouldProfile(world, renderer)) {
            world.profiler.startSection(sectionName(renderer));
        }
    }

    // Ends the profiler section of the renderer.
    public static void endSection(World world, Object renderer) {
        if (shouldProfile(world, renderer)) {
            world.profiler.endSection();
        }
    }

    // Returns whether the renderer is profiled.
    private static boolean shouldProfile(World world, Object renderer) {
        return world != null
                && renderer != null
                && Extras.options().render.profileEntityRendering
                && !sectionName(renderer).isEmpty();
    }

    // Returns the section name of the renderer class.
    private static String sectionName(Object renderer) {
        return NAME_CACHE.computeIfAbsent(renderer.getClass(), Class::getSimpleName);
    }
}
