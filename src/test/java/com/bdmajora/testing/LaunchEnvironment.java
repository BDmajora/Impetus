package com.bdmajora.testing;

import com.bdmajora.impetus.booter.service.MixinBooterService;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.mockito.Mockito;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A LaunchWrapper boot as the bundled Mixin booter sees it: a LaunchClassLoader that hands Impetus, Forge and Guava
// to the application loader the way the booter's own exclusions do in the game, the blackboard's tweaker lists, a
// scratch game directory, and the booter's service installed as Mixin's; Mixin's own packages are deliberately left
// unreachable through it, so registering Mixin's proxy transformer cannot boot a real transformer in the test JVM
public final class LaunchEnvironment {
    private static final String[] PROPERTIES = {"mixin.service", "mixin.bootstrapService", "mixinbooter.watchedClasses",
            "impetusbooter.auditTrail", "mixin.debug.verbose", "mixin.debug.export", "mixin.checks.interfaces"};
    private static final Map<String, String> previousProperties = new HashMap<>();
    private static LaunchClassLoader previousLoader;
    private static Map<String, Object> previousBlackboard;
    private static File previousHome;
    private static Object previousService;

    private LaunchEnvironment() {}

    public static LaunchClassLoader install(Path home, URL... sources) {
        previousLoader = Launch.classLoader;
        previousBlackboard = Launch.blackboard;
        previousHome = Launch.minecraftHome;
        for (String property : PROPERTIES) {
            previousProperties.put(property, System.getProperty(property));
        }
        LaunchClassLoader loader = new LaunchClassLoader(sources);
        for (String prefix : new String[] {"com.bdmajora.", "net.minecraftforge.", "com.google."}) {
            loader.addClassLoaderExclusion(prefix);
        }
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
        Mixins.set(mixinService, "service", new MixinBooterService());
        return loader;
    }

    public static void restore() {
        Mixins.set(Statics.call(MixinService.class, "getInstance"), "service", previousService);
        Launch.classLoader = previousLoader;
        Launch.blackboard = previousBlackboard;
        Launch.minecraftHome = previousHome;
        previousProperties.forEach((property, value) -> {
            if (value == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, value);
            }
        });
    }
}
