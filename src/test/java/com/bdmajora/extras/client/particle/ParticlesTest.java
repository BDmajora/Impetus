package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.particle.ParticleManagerAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFlame;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParticlesTest {
    private final ParticleClassRegistry registry = ParticleClassRegistry.getInstance();
    private ExtrasConfig config;

    // A particle class outside net.minecraft, which is what modded means to the ticker and the registry
    static class ModdedParticle extends Particle {
        ModdedParticle(World world) {
            super(world, 0, 0, 0);
        }
    }

    static class OtherModdedParticle extends Particle {
        OtherModdedParticle(World world) {
            super(world, 0, 0, 0);
        }
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshOptions() {
        Mc.client();
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        // The registry is one per client, so each test starts it from nothing
        for (String field : new String[] {"discoveredClasses", "classModIds", "factoryModIds", "disabledClasses", "seenClasses"}) {
            Object value = Mixins.get(registry, field);
            if (value instanceof Map<?, ?> map) {
                map.clear();
            } else {
                ((java.util.Collection<?>) value).clear();
            }
        }
        Mixins.set(registry, "sourceToModId", null);
        registry.markClean();
    }

    @AfterEach
    void forgetOptions() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(ParticleDiscoveryHandler.class, "lastScanned", null);
        registry.loadDisabledClasses(new String[0]);
        ParticleTicker.moddedParticles = false;
    }

    @Test
    void namedSwitchesFilterParticlesByVanillaId() {
        // Everything passes with the defaults, including ids no switch governs and ids past the table
        for (EnumParticleTypes type : EnumParticleTypes.values()) {
            assertTrue(ParticleFilters.isAllowed(type.getParticleID()), type.name());
        }
        assertTrue(ParticleFilters.isAllowed(-1));
        assertTrue(ParticleFilters.isAllowed(10_000));

        config.animation.explosion = false;
        config.animation.smoke = false;
        config.animation.flame = false;
        config.animation.redstone = false;
        config.particle.portalParticles = false;
        config.particle.waterParticles = false;
        config.particle.voidParticles = false;
        config.particle.potionParticles = false;
        config.particle.drippingWaterLava = false;
        config.particle.fireworkParticles = false;
        for (EnumParticleTypes type : new EnumParticleTypes[] {EnumParticleTypes.EXPLOSION_HUGE, EnumParticleTypes.SMOKE_LARGE,
                EnumParticleTypes.FLAME, EnumParticleTypes.REDSTONE, EnumParticleTypes.PORTAL, EnumParticleTypes.SUSPENDED,
                EnumParticleTypes.SUSPENDED_DEPTH, EnumParticleTypes.SPELL_WITCH, EnumParticleTypes.DRIP_LAVA,
                EnumParticleTypes.FIREWORKS_SPARK}) {
            assertFalse(ParticleFilters.isAllowed(type.getParticleID()), type.name());
        }
        assertTrue(ParticleFilters.isAllowed(EnumParticleTypes.HEART.getParticleID()));

        // The master switch stops everything
        config.particle.all = false;
        assertFalse(ParticleFilters.isAllowed(EnumParticleTypes.HEART.getParticleID()));
        assertNotNull(Mixins.construct(ParticleFilters.class));
    }

    @Test
    void particleClassesAreDiscoveredNamedAndAttributed() {
        registry.recordClass(null);
        registry.recordClass(ParticleFlame.class);
        assertEquals("ParticleFlame", registry.getDiscoveredClasses().get(ParticleFlame.class.getName()));
        assertEquals("minecraft", registry.getModId(ParticleFlame.class.getName()));

        // The factory a mod registered names that mod, and a later sighting with the same factory changes nothing
        IParticleFactory factory = mock(IParticleFactory.class);
        registry.registerFactoryMod(factory, "examplemod");
        registry.registerFactoryMod(null, "examplemod");
        registry.recordClass(ModdedParticle.class, factory);
        registry.recordClass(ModdedParticle.class, factory);
        registry.recordClass(ModdedParticle.class);
        assertEquals("examplemod", registry.getModId(ModdedParticle.class.getName()));

        // Without a factory the jar the class came from is matched against the loaded mods
        Mixins.set(registry, "sourceToModId", null);
        ModContainer testMod = mock(ModContainer.class);
        when(testMod.getModId()).thenReturn("testmod");
        File source = new File(OtherModdedParticle.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        when(testMod.getSource()).thenReturn(source);
        LoadController controller = mock(LoadController.class);
        when(controller.getActiveModList()).thenReturn(List.of(testMod, mock(ModContainer.class)));
        Object previous = Mixins.get(Loader.instance(), "modController");
        Mixins.set(Loader.instance(), "modController", controller);
        registry.recordClass(OtherModdedParticle.class);
        assertEquals("testmod", registry.getModId(OtherModdedParticle.class.getName()));
        // A class from nowhere known answers null, and so does a name never seen
        assertNull(registry.getModId("com.example.Unknown"));

        // An anonymous class still gets a usable display name
        Object anonymous = new Object() { };
        registry.recordClass(anonymous.getClass());
        assertTrue(Character.isDigit(registry.getDiscoveredClasses().get(anonymous.getClass().getName()).charAt(0)));
        Mixins.set(Loader.instance(), "modController", previous);
    }

    @Test
    void theCacheIsReadBackPrunedAndWrittenSorted() {
        registry.loadDiscoveredClasses(new String[] {
                "com.example.gone.GoneParticle|Gone",
                ParticleFlame.class.getName(),
                OtherModdedParticle.class.getName() + "|",
                "|nameless", "", null});
        assertEquals("Gone", registry.getDiscoveredClasses().get("com.example.gone.GoneParticle"));
        assertEquals("ParticleFlame", registry.getDiscoveredClasses().get(ParticleFlame.class.getName()));
        assertEquals("OtherModdedParticle", registry.getDiscoveredClasses().get(OtherModdedParticle.class.getName()));

        // A class whose mod is gone is dropped; one that still loads is kept and attributed
        registry.pruneDiscoveredCache();
        assertFalse(registry.getDiscoveredClasses().containsKey("com.example.gone.GoneParticle"));
        assertTrue(registry.getDiscoveredClasses().containsKey(ParticleFlame.class.getName()));

        String[] written = registry.getDiscoveredClassesArray();
        assertTrue(List.of(written).contains(ParticleFlame.class.getName() + "|ParticleFlame"));
        registry.markClean();
        assertFalse(registry.isDirty());
    }

    @Test
    void theUserCanSwitchSingleClassesOff() {
        registry.loadDisabledClasses(new String[] {"b.Second", "", null, "a.First"});
        assertFalse(registry.isEmptyDisabled());
        assertArrayEquals(new String[] {"a.First", "b.Second"}, registry.getDisabledClassesArray());
        registry.markClean();
        // Only a real change marks the config dirty
        registry.setClassEnabled("c.Third", true);
        assertFalse(registry.isDirty());
        registry.setClassEnabled("a.First", true);
        assertTrue(registry.isDirty());
        assertFalse(registry.isClassDisabled("a.First"));
        registry.setClassEnabled("a.First", false);
        assertTrue(registry.isClassDisabled("a.First"));
        registry.loadDisabledClasses(new String[0]);
        assertTrue(registry.isEmptyDisabled());
    }

    @Test
    void factoriesAreScannedForTheClassesTheyMake() {
        // No manager, or one that cannot be read, is simply nothing to scan
        registry.scanFactories(null);
        registry.scanFactories(mock(ParticleManager.class));
        ParticleManager manager = Mc.mock(ParticleManager.class, ParticleManagerAccessor.class);
        registry.scanFactories(manager);

        Map<Integer, IParticleFactory> factories = new HashMap<>();
        factories.put(0, new ParticleFlame.Factory());
        factories.put(1, null);
        // A factory declaring a covariant return type names its class that way
        factories.put(2, new IParticleFactory() {
            @Override
            public ModdedParticle createParticle(int id, World world, double x, double y, double z, double dx, double dy, double dz, int... args) {
                return null;
            }
        });
        // A lambda says nothing about what it makes
        factories.put(3, (id, world, x, y, z, dx, dy, dz, args) -> null);
        when(((ParticleManagerAccessor) manager).impetus$getParticleTypes()).thenReturn(factories);
        registry.scanFactories(manager);
        assertTrue(registry.getDiscoveredClasses().containsKey(ParticleFlame.class.getName()));
        assertTrue(registry.getDiscoveredClasses().containsKey(ModdedParticle.class.getName()));
    }

    @Test
    void eachNewParticleManagerIsScannedOnceAndDiscoveriesSaved() {
        Minecraft client = Minecraft.getMinecraft();
        ParticleDiscoveryHandler.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        // Before a world there is no manager
        ParticleDiscoveryHandler.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));

        ParticleManager manager = Mc.mock(ParticleManager.class, ParticleManagerAccessor.class);
        when(((ParticleManagerAccessor) manager).impetus$getParticleTypes()).thenReturn(Map.of(0, new ParticleFlame.Factory()));
        Mixins.set(client, "effectRenderer", manager);
        registry.setClassEnabled("z.Dirty", false);
        ParticleDiscoveryHandler.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        assertSame(manager, Mixins.get(ParticleDiscoveryHandler.class, "lastScanned"));
        // The same manager is not scanned twice
        ParticleDiscoveryHandler.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));

        // Leaving a client world persists what the session gathered; a server world is not ours to save for
        WorldClient world = Mc.uninitialized(WorldClient.class);
        Mixins.set(world, "isRemote", true);
        ParticleDiscoveryHandler.onWorldUnload(new WorldEvent.Unload(world));
        World server = Mc.uninitialized(net.minecraft.world.WorldServer.class);
        ParticleDiscoveryHandler.onWorldUnload(new WorldEvent.Unload(server));
        assertNotNull(Mixins.construct(ParticleDiscoveryHandler.class));
        Mixins.set(client, "effectRenderer", null);
    }

    private static Particle alive(Particle particle, boolean alive) {
        Mixins.set(particle, "isExpired", !alive);
        return particle;
    }

    @Test
    void aLayerIsTickedAcrossThePoolWithModdedParticlesKeptOnTheCallingThread() {
        ParticleTicker.beginTick();
        Queue<Particle> small = new ArrayDeque<>();
        assertFalse(ParticleTicker.shouldParallelize(small));

        Queue<Particle> layer = new java.util.concurrent.ConcurrentLinkedQueue<>();
        for (int i = 0; i < 300; i++) {
            layer.add(alive(Mc.uninitialized(ParticleFlame.class), i % 2 == 0));
        }
        ModdedParticle modded = (ModdedParticle) alive(Mc.uninitialized(ModdedParticle.class), true);
        layer.add(modded);
        ParticleTicker.enabled = true;
        assertTrue(ParticleTicker.shouldParallelize(layer));
        ParticleTicker.enabled = false;

        Thread caller = Thread.currentThread();
        AtomicInteger ticked = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Thread> moddedThread = new java.util.concurrent.atomic.AtomicReference<>();
        ParticleTicker.tickLayer(layer, particle -> {
            ticked.incrementAndGet();
            if (particle == modded) {
                moddedThread.set(Thread.currentThread());
            }
        });
        assertEquals(301, ticked.get());
        assertSame(caller, moddedThread.get());
        // The dead are dropped afterwards, as vanilla's iterator did
        assertEquals(151, layer.size());
        assertTrue(ParticleTicker.isModded(modded));
        assertFalse(ParticleTicker.isModded(layer.peek()));

        // A particle that throws fails the whole tick with its own exception, a crash report as it was
        ParticleTicker.moddedParticles = true;
        assertThrows(IllegalStateException.class, () -> ParticleTicker.tickLayer(layer, particle -> {
            throw new IllegalStateException("bad particle");
        }));
        ReportedException reported = new ReportedException(CrashReport.makeCrashReport(new RuntimeException(), "ticking"));
        assertSame(reported, assertThrows(ReportedException.class, () -> ParticleTicker.tickLayer(layer, particle -> {
            throw reported;
        })));
        assertThrows(RuntimeException.class, () -> ParticleTicker.tickLayer(layer, particle -> {
            throw new AssertionError("not an exception");
        }));
        assertNotNull(Mixins.construct(ParticleTicker.class));
    }
}
