package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EntityDataManagerTypeGuardMixinTest {
    // The crash: slot 11 is the player's ABSORPTION float, every EntityLiving puts AI_FLAGS (byte) in slot 11
    private static final DataParameter<Float> ABSORPTION = new DataParameter<>(11, DataSerializers.FLOAT);
    private static final DataParameter<Byte> AI_FLAGS = new DataParameter<>(11, DataSerializers.BYTE);
    private static final DataParameter<Integer> SCORE = new DataParameter<>(12, DataSerializers.VARINT);

    private EntityDataManagerTypeGuardMixin guard;
    private Map<Integer, EntityDataManager.DataEntry<?>> entries;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void setUp() {
        guard = Mixins.instance(EntityDataManagerTypeGuardMixin.class);
        entries = new HashMap<>();
        entries.put(11, new EntityDataManager.DataEntry<>(ABSORPTION, 4.0F));
        entries.put(12, new EntityDataManager.DataEntry<>(SCORE, 7));
        Mixins.set(guard, "entries", entries);
        Mixins.set(guard, "entity", mock(EntityPlayer.class));
        Mixins.<Set<String>>get(EntityDataManagerTypeGuardMixin.class, "impetus$reported").clear();
    }

    @Test
    void aSetWithAClashingKeyIsCancelledAndMatchingOrUnknownKeysPass() {
        CallbackInfo clash = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, clash);
        assertTrue(clash.isCancelled());
        // Reported once per entity class and slot, a repeat stays quiet
        CallbackInfo again = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 2, again);
        assertTrue(again.isCancelled());
        assertEquals(1, Mixins.<Set<String>>get(EntityDataManagerTypeGuardMixin.class, "impetus$reported").size());

        CallbackInfo same = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", ABSORPTION, 2.0F, same);
        assertFalse(same.isCancelled());
        // An unregistered slot is left to vanilla's own error
        CallbackInfo unknown = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", new DataParameter<>(40, DataSerializers.BYTE), (byte) 0, unknown);
        assertFalse(unknown.isCancelled());
    }

    @Test
    void aSyncedBatchKeepsTheSameListWhenEveryTypeMatches() {
        List<EntityDataManager.DataEntry<?>> batch = new ArrayList<>();
        batch.add(new EntityDataManager.DataEntry<>(ABSORPTION, 1.0F));
        batch.add(new EntityDataManager.DataEntry<>(SCORE, 3));
        batch.add(new EntityDataManager.DataEntry<>(new DataParameter<>(30, DataSerializers.BYTE), (byte) 0));
        assertSame(batch, Mixins.call(guard, "impetus$dropMismatchedEntries", batch));
    }

    @Test
    void aSyncedBatchDropsOnlyTheClashingEntriesInOrder() {
        EntityDataManager.DataEntry<?> first = new EntityDataManager.DataEntry<>(SCORE, 3);
        EntityDataManager.DataEntry<?> mob = new EntityDataManager.DataEntry<>(AI_FLAGS, (byte) 1);
        EntityDataManager.DataEntry<?> last = new EntityDataManager.DataEntry<>(ABSORPTION, 2.0F);
        List<EntityDataManager.DataEntry<?>> batch = List.of(first, mob, last);
        List<EntityDataManager.DataEntry<?>> kept = Mixins.call(guard, "impetus$dropMismatchedEntries", batch);
        assertEquals(List.of(first, last), kept);
        // The incoming list is never written to (packets can hand in immutable lists)
        assertEquals(3, batch.size());
        // A clash in the first slot copies nothing before it
        assertEquals(List.of(last), Mixins.call(guard, "impetus$dropMismatchedEntries", List.of(mob, last)));
    }

    @Test
    void theRealPlayerAbsorptionAndMobAiFlagKeysShareSlotElevenWithDifferentTypes() throws ReflectiveOperationException {
        // The crash's premise checked against the game: every mob's AI flags (byte) and the player's absorption (float) are slot 11
        DataParameter<?> absorption = key(net.minecraft.entity.player.EntityPlayer.class, "ABSORPTION");
        DataParameter<?> aiFlags = key(net.minecraft.entity.EntityLiving.class, "AI_FLAGS");
        assertEquals(absorption.getId(), aiFlags.getId());
        assertSame(DataSerializers.FLOAT, absorption.getSerializer());
        assertSame(DataSerializers.BYTE, aiFlags.getSerializer());
        // So a mob's metadata entry reaching the player is dropped and absorption keeps its float
        Map<Integer, EntityDataManager.DataEntry<?>> player = new HashMap<>();
        player.put(absorption.getId(), new EntityDataManager.DataEntry<>(cast(absorption), 0.0F));
        Mixins.set(guard, "entries", player);
        List<EntityDataManager.DataEntry<?>> mob = List.of(new EntityDataManager.DataEntry<>(cast(aiFlags), (Object) (byte) 0));
        assertEquals(List.of(), Mixins.call(guard, "impetus$dropMismatchedEntries", mob));
    }

    @Test
    void aMatchingSetOfNullAndAnEmptyBatchPassUntouched() {
        DataParameter<Integer> nullable = new DataParameter<>(13, DataSerializers.VARINT);
        entries.put(13, new EntityDataManager.DataEntry<>(nullable, 1));
        CallbackInfo ci = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", nullable, null, ci);
        assertFalse(ci.isCancelled());
        List<EntityDataManager.DataEntry<?>> empty = List.of();
        assertSame(empty, Mixins.call(guard, "impetus$dropMismatchedEntries", empty));
        assertTrue(reported().isEmpty());
    }

    @Test
    void anEmptyEntryMapDropsNothing() {
        // A data manager before entityInit registered anything: nothing to compare against, vanilla skips unknown ids itself
        entries.clear();
        List<EntityDataManager.DataEntry<?>> batch = List.of(new EntityDataManager.DataEntry<>(AI_FLAGS, (byte) 1));
        assertSame(batch, Mixins.call(guard, "impetus$dropMismatchedEntries", batch));
        CallbackInfo ci = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, ci);
        assertFalse(ci.isCancelled());
    }

    @Test
    void aBatchWhereEveryEntryClashesBecomesEmptyAndReportsEachSlotOnce() {
        DataParameter<Byte> scoreAsByte = new DataParameter<>(12, DataSerializers.BYTE);
        List<EntityDataManager.DataEntry<?>> batch = List.of(new EntityDataManager.DataEntry<>(AI_FLAGS, (byte) 1),
                new EntityDataManager.DataEntry<>(scoreAsByte, (byte) 2), new EntityDataManager.DataEntry<>(AI_FLAGS, (byte) 3));
        assertEquals(List.of(), Mixins.call(guard, "impetus$dropMismatchedEntries", batch));
        assertEquals(2, reported().size());
    }

    @Test
    void reportsAreKeptPerEntityClassAndPerSlot() {
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, new CallbackInfo("set", true));
        // Another entity class, same slot: a second report
        Mixins.set(guard, "entity", Mc.uninitialized(EntityPig.class));
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, new CallbackInfo("set", true));
        // Same class, another slot: a third
        Mixins.call(guard, "impetus$refuseMismatchedSet", new DataParameter<>(12, DataSerializers.BYTE), (byte) 1, new CallbackInfo("set", true));
        assertEquals(3, reported().size());
    }

    @Test
    void manyThreadsHittingTheSameClashReportItOnce() throws InterruptedException {
        try (Captured log = new Captured()) {
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
            for (int i = 0; i < threads; i++) {
                Thread worker = new Thread(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < 200; n++) {
                            CallbackInfo ci = new CallbackInfo("set", true);
                            Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) n, ci);
                            if (!ci.isCancelled()) {
                                failures.add(new AssertionError("a clashing set got through"));
                            }
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join();
            }
            assertEquals(List.of(), failures);
            assertEquals(1, log.events.size());
        }
    }

    @Test
    void aLocalSetLogsTheCallersStackAndASyncedValueDoesNot() {
        try (Captured log = new Captured()) {
            Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, new CallbackInfo("set", true));
            Mixins.call(guard, "impetus$dropMismatchedEntries",
                    List.of(new EntityDataManager.DataEntry<>(new DataParameter<>(12, DataSerializers.BYTE), (byte) 1)));
            assertEquals(2, log.events.size());
            LogEvent local = log.events.get(0);
            LogEvent synced = log.events.get(1);
            assertEquals(Level.WARN, local.getLevel());
            // slot and both type names make it into the text; only the local write carries the caller's stack
            String localText = local.getMessage().getFormattedMessage();
            assertTrue(localText.contains("write for data slot 11"), localText);
            assertTrue(localText.contains("holds Float but the value is Byte"), localText);
            assertTrue(localText.contains(EntityPlayer.class.getSimpleName()) || localText.contains("Mock"), localText);
            assertNotNull(local.getThrown());
            assertEquals("data key caller", local.getThrown().getMessage());
            String syncedText = synced.getMessage().getFormattedMessage();
            assertTrue(syncedText.contains("synced value for data slot 12"), syncedText);
            assertTrue(syncedText.contains("holds Integer but the value is Byte"), syncedText);
            assertTrue(syncedText.endsWith("or the entity ids clash"), syncedText);
            assertNull(synced.getThrown());
        }
    }

    // Collects what the Extras logger emits at any level for the duration of a test (no static-final swap: the JIT folds those).
    private static final class Captured implements AutoCloseable {
        final List<LogEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) Extras.LOGGER;
        private final Level level = logger.getLevel();
        private final Appender appender = mock(Appender.class);

        Captured() {
            Mockito.when(appender.getName()).thenReturn("type-guard-test");
            Mockito.when(appender.isStarted()).thenReturn(true);
            Mockito.doAnswer(invocation -> events.add(((LogEvent) invocation.getArgument(0)).toImmutable()))
                    .when(appender).append(Mockito.any());
            logger.addAppender(appender);
            logger.setLevel(Level.ALL);
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            logger.setLevel(level);
        }
    }

    @Test
    void worksOverCoarctatiosCompactIntMap() {
        // Coarctatio swaps the entries map for an Int2ObjectOpenHashMap; boxed lookups must still find the slot
        Map<Integer, EntityDataManager.DataEntry<?>> compact = new Int2ObjectOpenHashMap<>();
        compact.putAll(entries);
        Mixins.set(guard, "entries", compact);
        CallbackInfo ci = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, ci);
        assertTrue(ci.isCancelled());
    }

    @Test
    void aStoredEntryWithANullValueReportsNullNotAnNpe() {
        entries.put(11, new EntityDataManager.DataEntry<>(ABSORPTION, null));
        CallbackInfo ci = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, (byte) 1, ci);
        assertTrue(ci.isCancelled());
    }

    private static Set<String> reported() {
        return Mixins.get(EntityDataManagerTypeGuardMixin.class, "impetus$reported");
    }

    private static DataParameter<?> key(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (DataParameter<?>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static DataParameter<Object> cast(DataParameter<?> key) {
        return (DataParameter<Object>) key;
    }

    @Test
    void aNullEntityStillReports() {
        Mixins.set(guard, "entity", null);
        CallbackInfo clash = new CallbackInfo("set", true);
        Mixins.call(guard, "impetus$refuseMismatchedSet", AI_FLAGS, null, clash);
        assertTrue(clash.isCancelled());
        assertTrue(Mixins.<Set<String>>get(EntityDataManagerTypeGuardMixin.class, "impetus$reported").contains("null#11"));
    }
}
