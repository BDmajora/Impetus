package com.bdmajora.testing;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.mockito.Mockito;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A Cleanroom launch as Impetus' coremod sees it: Foundation's LaunchClassLoader over the given sources, the blackboard's tweaker lists, a scratch game directory, and a mock Mixin service so Mixin's statics initialise without CleanMix booting a transformer in the test JVM
public final class LaunchEnvironment {
    private static LaunchClassLoader previousLoader;
    private static Map<String, Object> previousBlackboard;
    private static File previousHome;
    private static Object previousService;

    private LaunchEnvironment() {}

    public static LaunchClassLoader install(Path home, URL... sources) {
        previousLoader = Launch.classLoader;
        previousBlackboard = Launch.blackboard;
        previousHome = Launch.minecraftHome;
        LaunchClassLoader loader = new LaunchClassLoader(sources);
        Launch.classLoader = loader;
        Map<String, Object> blackboard = new HashMap<>();
        List<ITweaker> tweaks = new ArrayList<>();
        tweaks.add(Mockito.mock(ITweaker.class));
        blackboard.put("Tweaks", tweaks);
        blackboard.put("TweakClasses", new ArrayList<String>());
        blackboard.put("forgeLaunchArgs", new HashMap<String, String>());
        Launch.blackboard = blackboard;
        Launch.minecraftHome = home.toFile();
        Object mixinService = Statics.call(MixinService.class, "getInstance");
        previousService = Mixins.get(mixinService, "service");
        Mixins.set(mixinService, "service", Mockito.mock(IMixinService.class, Mockito.RETURNS_MOCKS));
        return loader;
    }

    public static void restore() {
        Mixins.set(Statics.call(MixinService.class, "getInstance"), "service", previousService);
        Launch.classLoader = previousLoader;
        Launch.blackboard = previousBlackboard;
        Launch.minecraftHome = previousHome;
    }
}
