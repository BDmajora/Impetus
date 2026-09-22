package com.bdmajora.impetus.booter;

import net.minecraft.launchwrapper.Launch;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

// Decides whether Impetus supplies the Mixin subsystem itself or stands down for a real MixinBooter; references no org.spongepowered.asm type since it runs before any Mixin implementation is guaranteed to exist
public final class BooterBootstrap {

    // A real MixinBooter (or any other service provider) already owns Mixin; do nothing at all.
    public static final int DEFERRED = 0;
    // No Mixin on the classpath; we extracted our own copy and are responsible for booting it.
    public static final int MIXIN_OWNED = 1;

    // Probed as resources rather than Class.forName so detection never triggers static init on a foreign booter, which would bootstrap Mixin as a side effect of looking
    private static final String FOREIGN_BOOTER = "zone/rong/mixinbooter/MixinBooterPlugin.class";
    private static final String MIXIN_MARKER = "org/spongepowered/asm/launch/MixinBootstrap.class";
    // BooterCore excludes com.bdmajora.impetus.booter.service.* from the LaunchClassLoader, so this class must resolve from the system class loader before we may claim the Mixin service
    private static final String SERVICE_MARKER = "com/bdmajora/impetus/booter/service/MixinServiceBootstrap.class";

    // CleanMix + MixinExtras, shaded and embedded unextracted as a nested jar so that on installs with MixinBooter we contribute zero org.spongepowered.asm classes and the duplicate-class race cannot happen
    private static final String NESTED_LIBS = "/com/bdmajora/impetus/booter/impetus-booter-libs.jar";

    private static int state = -1;

    private BooterBootstrap() { }

    // DEFERRED or MIXIN_OWNED once initialize has run; -1 before
    public static int state() {
        return state;
    }

    // Resolves once and caches; safe to call repeatedly
    public static int initialize() {
        if (state != -1) {
            return state;
        }
        state = resolve();
        return state;
    }

    // The detection ladder: existing service property, foreign booter class, existing Mixin, then extract our own
    private static int resolve() {
        // Someone set mixin.service before us, so a service provider is already committed.
        String service = System.getProperty("mixin.service");
        if (service != null && !service.isEmpty() && !service.startsWith("com.bdmajora.impetus.booter")) {
            log("Deferring to the Mixin service already selected by " + service);
            return DEFERRED;
        }

        if (resource(FOREIGN_BOOTER) != null) {
            log("MixinBooter detected on the classpath; standing down and using it.");
            return DEFERRED;
        }

        // Mixin present without MixinBooter, e.g. the dev runtime or a Sponge install. Nothing to add.
        if (resource(MIXIN_MARKER) != null) {
            log("An existing Mixin implementation was found; standing down.");
            return DEFERRED;
        }

        // BooterCore excludes the service package from the LaunchClassLoader so Mixin resolves it from the parent, but the coremod jar lives only on the LaunchClassLoader; without this MixinService.runBootServices dies with ClassNotFoundException (MixinBooter does the same)
        injectSelfIntoAppClassLoader();

        // Verify rather than assume: if our service classes are unreachable from the system loader, BooterCore's exclusions make Mixin's Class.forName kill the game during coremod construction, and standing down leaves it playable with a clear message
        if (ClassLoader.getSystemClassLoader().getResource(SERVICE_MARKER) == null) {
            log("Impetus' Mixin service could not be exposed to the system class loader; standing down. "
                    + "Install MixinBooter alongside Impetus on this setup.");
            return DEFERRED;
        }

        if (!extractAndAttachLibs()) {
            // Left un-owned on purpose: better to fail loudly on a missing Mixin than to half-boot a subsystem other coremods will build on
            log("No Mixin implementation available and the bundled copy could not be attached.");
            return DEFERRED;
        }

        log("No MixinBooter found; Impetus is supplying Mixin " + Tags.CLEANMIX_VERSION + " itself.");
        return MIXIN_OWNED;
    }

    // Writes the nested libs jar to disk and adds it to both class loaders; false on any failure
    private static boolean extractAndAttachLibs() {
        InputStream in = BooterBootstrap.class.getResourceAsStream(NESTED_LIBS);
        if (in == null) {
            log("Bundled Mixin libraries are missing from the Impetus jar.");
            return false;
        }
        try {
            File target = new File(gameDir(), ".impetus/impetus-booter-libs-" + Tags.VERSION + ".jar");
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                log("Could not create " + parent);
                return false;
            }
            // Re-extract every launch; a truncated jar from a previous crashed boot is worse than the copy cost.
            copy(in, target);
            URL url = target.toURI().toURL();
            Launch.classLoader.addURL(url);
            addToAppClassLoader(url);
            return resource(MIXIN_MARKER) != null;
        } catch (Throwable t) {
            log("Failed to attach the bundled Mixin libraries: " + t);
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    // Puts the Impetus jar itself on the AppClassLoader; harmless if already present (dev runs from a classes directory on it), so deliberately unguarded
    private static void injectSelfIntoAppClassLoader() {
        URL self = selfJarUrl();
        if (self == null) {
            log("Could not locate the Impetus jar; the bundled Mixin service will not be reachable.");
            return;
        }
        addToAppClassLoader(self);
    }

    // LaunchClassLoader's CodeSource points at the jar *entry* (jar:file:/...jar!/...class), useless to URLClassLoader.addURL, so derive the jar root from the resource URL and fall back to CodeSource only in dev
    private static URL selfJarUrl() {
        URL resource = BooterBootstrap.class.getResource("BooterBootstrap.class");
        if (resource != null) {
            String location = resource.toString();
            int separator = location.indexOf("!/");
            if (location.startsWith("jar:") && separator > 0) {
                try {
                    return new URL(location.substring(4, separator));
                } catch (MalformedURLException e) {
                    log("Malformed jar URL " + location + ": " + e);
                }
            }
        }
        CodeSource source = BooterBootstrap.class.getProtectionDomain().getCodeSource();
        return source != null ? source.getLocation() : null;
    }

    // Mixin's service layer resolves through the AppClassLoader, mirroring MixinBooter's injectSelfIntoAppClassLoader; without this the ServiceLoader lookup will not see our jar
    private static void addToAppClassLoader(URL url) {
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();
        if (!(appClassLoader instanceof URLClassLoader)) {
            return;
        }
        try {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(appClassLoader, url);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to add the bundled Mixin libraries to the parent ClassLoader", t);
        }
    }

    // Probes the launch loader first, then the system loader
    private static URL resource(String path) {
        URL url = Launch.classLoader.getResource(path);
        return url != null ? url : ClassLoader.getSystemClassLoader().getResource(path);
    }

    // minecraftHome, or the working directory when a launcher never set it
    private static File gameDir() {
        File home = Launch.minecraftHome;
        return home != null ? home : new File(".");
    }

    // Runs once per launch; REPLACE_EXISTING overwrites a truncated jar from a crashed boot
    private static void copy(InputStream in, File target) throws IOException {
        Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // Swallows close errors; nothing useful can be done this early
    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
            // nothing useful to do this early in boot
        }
    }

    // Mixin's logger does not exist yet at this point, so this is the only channel available.
    private static void log(String message) {
        System.out.println("[" + Tags.MOD_NAME + "] " + message);
    }

}
