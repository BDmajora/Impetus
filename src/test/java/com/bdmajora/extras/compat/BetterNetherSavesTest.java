package com.bdmajora.extras.compat;

import com.bdmajora.extras.mixin.compat.BetterNetherStructureManagerMixin;
import com.bdmajora.extras.mixin.misc.MinecraftServerMixin;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// BetterNether 1.12.2's StructureManager save, moved off the server thread and skipped when nothing generated.
class BetterNetherSavesTest {
    @TempDir
    Path save;
    private World world;
    private long previousTimeout;

    // Shape of BN's StructureManager: protected name field and protected toNBT on the abstract base.
    abstract static class FakeStructureManager {
        protected String name;
        // Live arrays, as BN's StructureChunk hands its own arrays to setIntArray
        final int[] ids = new int[4096];
        final byte[] metas = new byte[4096];
        int toNbtCalls;
        Thread toNbtThread;

        FakeStructureManager(String name) {
            this.name = name;
        }

        protected NBTTagCompound toNBT() {
            this.toNbtCalls++;
            this.toNbtThread = Thread.currentThread();
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList structures = new NBTTagList();
            NBTTagCompound chunk = new NBTTagCompound();
            chunk.setIntArray("id", this.ids);
            chunk.setByteArray("meta", this.metas);
            structures.appendTag(chunk);
            root.setTag("structures", structures);
            return root;
        }
    }

    // Shape of CityStructureManager: the subclass BN actually instantiates.
    static final class FakeCityManager extends FakeStructureManager {
        FakeCityManager() {
            super("city");
        }
    }

    // A manager missing BN's internals, for the give-up path.
    static final class NotAManager {
    }

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        ISaveHandler handler = mock(ISaveHandler.class);
        when(handler.getWorldDirectory()).thenReturn(save.toFile());
        when(world.getSaveHandler()).thenReturn(handler);
        reset();
        previousTimeout = BetterNetherSaves.flushTimeoutMillis;
    }

    @AfterEach
    void tearDown() {
        BetterNetherSaves.flushTimeoutMillis = 30_000L;
        BetterNetherSaves.flush();
        BetterNetherSaves.flushTimeoutMillis = previousTimeout;
        reset();
    }

    private static void reset() {
        Statics.<Map<Object, Boolean>>get(BetterNetherSaves.class, "CLEAN").clear();
        Statics.set(BetterNetherSaves.class, "generateHookSeen", false);
        Statics.set(BetterNetherSaves.class, "unusable", false);
    }

    private Path file() {
        return save.resolve("data").resolve("bn_city.nbt");
    }

    private int[] readIds() throws Exception {
        try (InputStream in = Files.newInputStream(file())) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            return root.getTagList("structures", 10).getCompoundTagAt(0).getIntArray("id");
        }
    }

    // Parks the writer thread until the returned latch opens, so a test can see what a save does before the write runs.
    private static CountDownLatch blockWriter() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch parked = new CountDownLatch(1);
        // Creates the writer if no save has yet
        BetterNetherSaves.save(new FakeCityManager(), mockWorldIn(Files.createTempDirectory("bn-warmup")));
        BetterNetherSaves.flush();
        ExecutorService writer = Statics.get(BetterNetherSaves.class, "writer");
        writer.submit(() -> {
            parked.countDown();
            release.await();
            return null;
        });
        assertTrue(parked.await(5, TimeUnit.SECONDS));
        return release;
    }

    private static World mockWorldIn(Path dir) {
        World other = mock(World.class);
        ISaveHandler handler = mock(ISaveHandler.class);
        when(handler.getWorldDirectory()).thenReturn(dir.toFile());
        when(other.getSaveHandler()).thenReturn(handler);
        return other;
    }

    @Test
    void theFirstSaveWritesBnsOwnFileOffTheCallingThread() throws Exception {
        FakeCityManager manager = new FakeCityManager();
        manager.ids[7] = 42;
        CountDownLatch release = blockWriter();
        assertTrue(BetterNetherSaves.save(manager, world));
        // The snapshot is taken on the caller, the write is not done yet
        assertSame(Thread.currentThread(), manager.toNbtThread);
        assertFalse(Files.exists(file()));
        release.countDown();
        BetterNetherSaves.flush();
        assertEquals(42, readIds()[7]);
        // Exactly BN's path and nothing left behind
        assertTrue(Files.exists(file()));
        assertFalse(Files.exists(file().resolveSibling("bn_city.nbt.tmp")));
    }

    @Test
    void laterGenerationCannotChangeAQueuedWrite() throws Exception {
        FakeCityManager manager = new FakeCityManager();
        manager.ids[0] = 1;
        manager.metas[0] = 1;
        CountDownLatch release = blockWriter();
        assertTrue(BetterNetherSaves.save(manager, world));
        // Generation keeps writing into the live arrays while the write waits
        manager.ids[0] = 99;
        manager.metas[0] = 99;
        release.countDown();
        BetterNetherSaves.flush();
        assertEquals(1, readIds()[0]);
    }

    @Test
    void anUnchangedManagerSkipsTheSaveOnceGenerateIsHooked() throws Exception {
        FakeCityManager manager = new FakeCityManager();
        BetterNetherSaves.markDirty(manager);
        assertTrue(BetterNetherSaves.save(manager, world));
        BetterNetherSaves.flush();
        Files.delete(file());
        // Nothing generated: the save is taken over and skipped, no snapshot and no file
        assertTrue(BetterNetherSaves.save(manager, world));
        BetterNetherSaves.flush();
        assertFalse(Files.exists(file()));
        assertEquals(1, manager.toNbtCalls);
        // One generated chunk later it writes again
        BetterNetherSaves.markDirty(manager);
        assertTrue(BetterNetherSaves.save(manager, world));
        BetterNetherSaves.flush();
        assertTrue(Files.exists(file()));
        assertEquals(2, manager.toNbtCalls);
    }

    @Test
    void withoutTheGenerateHookEverySaveStillWrites() throws Exception {
        // A BN build whose generate the mixin missed must never lose data to a skipped save
        FakeCityManager manager = new FakeCityManager();
        for (int i = 0; i < 3; i++) {
            assertTrue(BetterNetherSaves.save(manager, world));
            BetterNetherSaves.flush();
            assertTrue(Files.exists(file()));
            Files.delete(file());
        }
        assertEquals(3, manager.toNbtCalls);
    }

    @Test
    void eachManagerTracksItsOwnChanges() throws Exception {
        FakeCityManager first = new FakeCityManager();
        FakeCityManager second = new FakeCityManager();
        BetterNetherSaves.markDirty(first);
        BetterNetherSaves.save(first, world);
        BetterNetherSaves.save(second, world);
        BetterNetherSaves.flush();
        // A rejoin builds a new manager: it was never saved, so it saves even though the old one is clean
        FakeCityManager rejoined = new FakeCityManager();
        assertTrue(BetterNetherSaves.save(rejoined, world));
        assertEquals(1, rejoined.toNbtCalls);
        BetterNetherSaves.save(first, world);
        assertEquals(1, first.toNbtCalls);
    }

    @Test
    void writesLandInSaveOrder() throws Exception {
        FakeCityManager manager = new FakeCityManager();
        CountDownLatch release = blockWriter();
        for (int version = 1; version <= 5; version++) {
            manager.ids[0] = version;
            BetterNetherSaves.markDirty(manager);
            assertTrue(BetterNetherSaves.save(manager, world));
        }
        release.countDown();
        BetterNetherSaves.flush();
        assertEquals(5, readIds()[0]);
    }

    @Test
    void aFailedWriteIsRetriedAtTheNextSave() throws Exception {
        FakeCityManager manager = new FakeCityManager();
        BetterNetherSaves.markDirty(manager);
        // data/ is a file, so the directory cannot be made and the write fails
        Files.writeString(save.resolve("data"), "not a directory", StandardCharsets.UTF_8);
        assertTrue(BetterNetherSaves.save(manager, world));
        BetterNetherSaves.flush();
        // No generation since, yet the failure made it dirty again
        Files.delete(save.resolve("data"));
        assertTrue(BetterNetherSaves.save(manager, world));
        BetterNetherSaves.flush();
        assertTrue(Files.exists(file()));
        assertEquals(2, manager.toNbtCalls);
    }

    @Test
    void anOldFileIsReplacedWhole() throws Exception {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[100_000]);
        FakeCityManager manager = new FakeCityManager();
        manager.ids[3] = 3;
        BetterNetherSaves.save(manager, world);
        BetterNetherSaves.flush();
        assertEquals(3, readIds()[3]);
        // A stale temp file from a crash is simply overwritten
        Files.write(file().resolveSibling("bn_city.nbt.tmp"), new byte[] {1, 2, 3});
        BetterNetherSaves.markDirty(manager);
        manager.ids[3] = 4;
        BetterNetherSaves.save(manager, world);
        BetterNetherSaves.flush();
        assertEquals(4, readIds()[3]);
        assertFalse(Files.exists(file().resolveSibling("bn_city.nbt.tmp")));
    }

    @Test
    void anUnreadableManagerHandsEverySaveBackToBn() {
        assertFalse(BetterNetherSaves.save(new NotAManager(), world));
        // Once unusable it stays out of the way, even for a manager it could read
        assertFalse(BetterNetherSaves.save(new FakeCityManager(), world));
        assertFalse(Files.exists(file()));
    }

    @Test
    void aToNbtThatThrowsHandsTheSaveBackToBn() {
        FakeStructureManager broken = new FakeStructureManager("city") {
            @Override
            protected NBTTagCompound toNBT() {
                throw new IllegalStateException("bn");
            }
        };
        assertFalse(BetterNetherSaves.save(broken, world));
    }

    @Test
    void missingArgumentsAreLeftToBn() {
        assertFalse(BetterNetherSaves.save(null, world));
        assertFalse(BetterNetherSaves.save(new FakeCityManager(), null));
        // Neither turned the helper off
        assertTrue(BetterNetherSaves.save(new FakeCityManager(), world));
    }

    @Test
    void aStuckWriteTimesOutTheFlushInsteadOfHanging() throws Exception {
        CountDownLatch release = blockWriter();
        BetterNetherSaves.save(new FakeCityManager(), world);
        BetterNetherSaves.flushTimeoutMillis = 50;
        long start = System.nanoTime();
        assertDoesNotThrow(BetterNetherSaves::flush);
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
        release.countDown();
    }

    @Test
    void anInterruptedFlushKeepsTheInterrupt() throws Exception {
        CountDownLatch release = blockWriter();
        BetterNetherSaves.save(new FakeCityManager(), world);
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            Thread.currentThread().interrupt();
            BetterNetherSaves.flush();
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        waiter.start();
        waiter.join(5000);
        assertEquals(Boolean.TRUE, interrupted.get());
        release.countDown();
    }

    @Test
    void flushWithNothingQueuedReturnsAtOnce() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), BetterNetherSaves::flush);
    }

    @Test
    void theMixinCancelsOnlyWhatImpetusTookOver() {
        BetterNetherStructureManagerMixin mixin = Mixins.instance(BetterNetherStructureManagerMixin.class);
        try (MockedStatic<BetterNetherSaves> saves = Mockito.mockStatic(BetterNetherSaves.class)) {
            saves.when(() -> BetterNetherSaves.save(mixin, world)).thenReturn(true, false);
            CallbackInfo taken = Mixins.ci();
            Mixins.call(mixin, "impetus$saveOffThread", world, taken);
            assertTrue(taken.isCancelled());
            CallbackInfo left = Mixins.ci();
            Mixins.call(mixin, "impetus$saveOffThread", world, left);
            assertFalse(left.isCancelled());
            Mixins.call(mixin, "impetus$markDirty", world, 1, 2, Mixins.ci());
            saves.verify(() -> BetterNetherSaves.markDirty(mixin));
            Mixins.call(mixin, "impetus$finishWritesBeforeLoad", world, Mixins.ci());
            saves.verify(BetterNetherSaves::flush);
        }
    }

    @Test
    void stoppingTheServerFlushesTheWrites() {
        MinecraftServerMixin server = Mixins.instance(MinecraftServerMixin.class);
        try (MockedStatic<BetterNetherSaves> saves = Mockito.mockStatic(BetterNetherSaves.class)) {
            Mixins.call(server, "impetus$flushBetterNether", Mixins.ci());
            saves.verify(BetterNetherSaves::flush);
        }
    }

    @Test
    void theMixinTargetsTheClassTheHelperExpects() throws Exception {
        // @Mixin is invisible at run time, so the class file is read for the target string
        try (InputStream in = BetterNetherStructureManagerMixin.class.getResourceAsStream("BetterNetherStructureManagerMixin.class")) {
            String bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(bytes.contains(BetterNetherSaves.MANAGER), "mixin targets " + BetterNetherSaves.MANAGER);
        }
        try (InputStream in = BetterNetherSavesTest.class.getResourceAsStream("/mixins.extras.json")) {
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"compat.BetterNetherStructureManagerMixin\""));
        }
    }

    // A subclass that hides the base's String name behind a field of another type, as a careless BN fork could.
    static final class ShadowedNameManager extends FakeStructureManager {
        @SuppressWarnings("unused")
        private final int name = 7;

        ShadowedNameManager() {
            super("city");
        }
    }

    // A second hierarchy with its own toNBT, so the cached reflection has to be looked up again.
    static class OtherManager {
        protected String name = "other";
        int calls;

        protected NBTTagCompound toNBT() {
            this.calls++;
            NBTTagCompound root = new NBTTagCompound();
            root.setString("kind", "other");
            return root;
        }
    }

    @Test
    void compressionNeverRunsOnTheServerThread() throws Exception {
        // Static mocks only act on the thread that made them, so any compression on this thread would hit the mock
        try (MockedStatic<CompressedStreamTools> streams = Mockito.mockStatic(CompressedStreamTools.class)) {
            FakeCityManager manager = new FakeCityManager();
            assertTrue(BetterNetherSaves.save(manager, world));
            streams.verifyNoInteractions();
        }
        BetterNetherSaves.flush();
        assertTrue(Files.exists(file()));
    }

    @Test
    void theWriterIsOneNamedDaemonThread() throws Exception {
        BetterNetherSaves.save(new FakeCityManager(), world);
        BetterNetherSaves.flush();
        ExecutorService writer = Statics.get(BetterNetherSaves.class, "writer");
        Thread first = writer.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
        Thread second = writer.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
        assertSame(first, second);
        assertEquals("Impetus BetterNether saves", first.getName());
        // A stuck disk must not keep the game process alive after quit
        assertTrue(first.isDaemon());
        // Later saves reuse it rather than starting another
        BetterNetherSaves.save(new FakeCityManager(), world);
        assertSame(writer, Statics.get(BetterNetherSaves.class, "writer"));
    }

    @Test
    void differentManagersWriteTheirOwnFiles() throws Exception {
        FakeCityManager city = new FakeCityManager();
        city.ids[1] = 11;
        OtherManager other = new OtherManager();
        // Alternating hierarchies swap the cached reflection back and forth
        for (int i = 0; i < 3; i++) {
            assertTrue(BetterNetherSaves.save(city, world));
            assertTrue(BetterNetherSaves.save(other, world));
        }
        BetterNetherSaves.flush();
        assertEquals(11, readIds()[1]);
        try (InputStream in = Files.newInputStream(save.resolve("data").resolve("bn_other.nbt"))) {
            assertEquals("other", CompressedStreamTools.readCompressed(in).getString("kind"));
        }
        assertEquals(3, other.calls);
    }

    @Test
    void eachWorldsSaveGoesToItsOwnFolder() throws Exception {
        // Leaving one singleplayer world and opening another: the queued write must not land in the new world
        Path second = Files.createTempDirectory(save, "second");
        World other = mockWorldIn(second);
        FakeCityManager first = new FakeCityManager();
        first.ids[2] = 1;
        FakeCityManager next = new FakeCityManager();
        next.ids[2] = 2;
        CountDownLatch release = blockWriter();
        BetterNetherSaves.save(first, world);
        BetterNetherSaves.save(next, other);
        release.countDown();
        BetterNetherSaves.flush();
        assertEquals(1, readIds()[2]);
        try (InputStream in = Files.newInputStream(second.resolve("data").resolve("bn_city.nbt"))) {
            assertEquals(2, CompressedStreamTools.readCompressed(in).getTagList("structures", 10).getCompoundTagAt(0).getIntArray("id")[2]);
        }
    }

    @Test
    void aShadowingFieldDoesNotHideBnsName() throws Exception {
        assertTrue(BetterNetherSaves.save(new ShadowedNameManager(), world));
        BetterNetherSaves.flush();
        assertTrue(Files.exists(file()));
        assertFalse(Files.exists(save.resolve("data").resolve("bn_7.nbt")));
    }

    @Test
    void aLargeCityRoundTripsIntact() throws Exception {
        // A sprawling city is hundreds of 16^3 sections; every array must come back bit for bit
        OtherManager big = new OtherManager() {
            @Override
            protected NBTTagCompound toNBT() {
                NBTTagCompound root = new NBTTagCompound();
                NBTTagList chunks = new NBTTagList();
                for (int section = 0; section < 300; section++) {
                    NBTTagCompound chunk = new NBTTagCompound();
                    int[] ids = new int[4096];
                    java.util.Arrays.fill(ids, section);
                    ids[4095] = -1;
                    chunk.setIntArray("id", ids);
                    chunk.setByteArray("meta", new byte[4096]);
                    chunks.appendTag(chunk);
                }
                root.setTag("structures", chunks);
                return root;
            }
        };
        big.name = "city";
        assertTrue(BetterNetherSaves.save(big, world));
        BetterNetherSaves.flush();
        try (InputStream in = Files.newInputStream(file())) {
            NBTTagList chunks = CompressedStreamTools.readCompressed(in).getTagList("structures", 10);
            assertEquals(300, chunks.tagCount());
            for (int section = 0; section < 300; section++) {
                int[] ids = chunks.getCompoundTagAt(section).getIntArray("id");
                assertEquals(4096, ids.length);
                assertEquals(section, ids[0]);
                assertEquals(-1, ids[4095]);
            }
        }
    }

    @Test
    void savesQueuedJustBeforeStopAllLandAfterTheFlush() throws Exception {
        // stopServer saves every world and then flushes: nothing queued before the flush may be lost
        CountDownLatch release = blockWriter();
        Path[] dirs = new Path[5];
        for (int i = 0; i < dirs.length; i++) {
            dirs[i] = Files.createTempDirectory(save, "w" + i);
            BetterNetherSaves.save(new FakeCityManager(), mockWorldIn(dirs[i]));
        }
        Thread opener = new Thread(release::countDown);
        opener.start();
        BetterNetherSaves.flush();
        for (Path dir : dirs) {
            assertTrue(Files.exists(dir.resolve("data").resolve("bn_city.nbt")), dir.toString());
        }
    }
}
