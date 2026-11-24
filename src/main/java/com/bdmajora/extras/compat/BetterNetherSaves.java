package com.bdmajora.extras.compat;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// BetterNether 1.12.2 saves its city generator on every Nether WorldEvent.Save, so every autosave. Each city still being placed holds
// a 16 KB int array plus a 4 KB byte array per 16^3 section, and StructureManager.save gzips all of them and writes the file on the
// server thread whether or not anything changed. Cities only leave the list once every chunk they cover generated, so the hitch grew
// as the player explored. Here an unchanged manager skips the save, and a changed one is snapshotted on the server thread and written
// by one background thread through a temp file and an atomic move.
public final class BetterNetherSaves {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Extras");

    static final String MANAGER = "paulevs.betternether.structures.big.StructureManager";

    // Managers whose last queued write matches their contents; a missing entry means unknown, which saves.
    private static final Map<Object, Boolean> CLEAN = Collections.synchronizedMap(new WeakHashMap<>());
    // Only once the generate hook has run is "clean" trusted, so a BN build without that method never skips a save.
    private static volatile boolean generateHookSeen;
    // Set when BN's internals could not be read; BN's own save then runs every time.
    private static volatile boolean unusable;

    private static final Object QUEUE = new Object();
    private static ExecutorService writer;
    private static Future<?> pending = CompletableFuture.completedFuture(null);
    static long flushTimeoutMillis = 30_000L;

    private static volatile Method toNbt;
    private static volatile Field name;

    private BetterNetherSaves() {
    }

    // From the head of StructureManager.generate, the only method that changes what save writes.
    public static void markDirty(Object manager) {
        generateHookSeen = true;
        CLEAN.remove(manager);
    }

    // True when Impetus took this save over and BN's synchronous write must be skipped.
    public static boolean save(Object manager, World world) {
        if (unusable || manager == null || world == null) {
            return false;
        }
        if (generateHookSeen && Boolean.TRUE.equals(CLEAN.get(manager))) {
            return true;
        }
        NBTTagCompound snapshot;
        Path target;
        try {
            NBTTagCompound live = (NBTTagCompound) toNbt(manager.getClass()).invoke(manager);
            // copy() clones every int and byte array, so later generation cannot change what the writer compresses
            snapshot = live.copy();
            // Same path BN builds: <save>/data/bn_<name>.nbt, shared by every dimension's save handler
            target = world.getSaveHandler().getWorldDirectory().toPath().resolve("data")
                    .resolve("bn_" + name(manager.getClass()).get(manager) + ".nbt");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            unusable = true;
            LOGGER.warn("[Impetus] Could not read BetterNether's structure manager; leaving its saves on the server thread", e);
            return false;
        }
        CLEAN.put(manager, Boolean.TRUE);
        queue(manager, target, snapshot);
        return true;
    }

    // Waits for every queued write; from server stop and before BN reads the file back.
    public static void flush() {
        Future<?> last;
        synchronized (QUEUE) {
            last = pending;
        }
        try {
            last.get(flushTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            LOGGER.warn("[Impetus] BetterNether's city data is still being written after {} ms", flushTimeoutMillis);
        } catch (ExecutionException e) {
            LOGGER.error("[Impetus] Writing BetterNether's city data failed", e.getCause());
        }
    }

    private static void queue(Object manager, Path target, NBTTagCompound snapshot) {
        synchronized (QUEUE) {
            if (writer == null) {
                writer = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "Impetus BetterNether saves");
                    // Daemon so a stuck disk never holds the JVM open; server stop flushes before the world goes away
                    thread.setDaemon(true);
                    return thread;
                });
            }
            // One thread, so writes land in the order the saves happened
            pending = writer.submit(() -> write(manager, target, snapshot));
        }
    }

    static void write(Object manager, Path target, NBTTagCompound snapshot) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(temp)) {
                CompressedStreamTools.writeCompressed(snapshot, out);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // Not written, so the next save must try again even if nothing generates meanwhile
            CLEAN.remove(manager);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // The temp file is overwritten by the next attempt anyway
            }
            LOGGER.error("[Impetus] Could not write {}", target, e);
        }
    }

    // StructureManager.toNBT is protected and declared on the base class, so the subclass hierarchy is walked.
    private static Method toNbt(Class<?> type) throws NoSuchMethodException {
        Method method = toNbt;
        if (method == null || !method.getDeclaringClass().isAssignableFrom(type)) {
            method = declared(type, "toNBT");
            method.setAccessible(true);
            toNbt = method;
        }
        return method;
    }

    private static Field name(Class<?> type) throws NoSuchFieldException {
        Field field = name;
        if (field == null || !field.getDeclaringClass().isAssignableFrom(type)) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    field = c.getDeclaredField("name");
                    if (field.getType() == String.class) {
                        field.setAccessible(true);
                        name = field;
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Keep walking up
                }
            }
            throw new NoSuchFieldException("name");
        }
        return field;
    }

    private static Method declared(Class<?> type, String method) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method found = c.getDeclaredMethod(method);
                if (found.getReturnType() == NBTTagCompound.class) {
                    return found;
                }
            } catch (NoSuchMethodException ignored) {
                // Keep walking up
            }
        }
        throw new NoSuchMethodException(method);
    }
}
