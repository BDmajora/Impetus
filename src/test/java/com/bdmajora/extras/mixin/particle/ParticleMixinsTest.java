package com.bdmajora.extras.mixin.particle;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.budget.RenderBudget;
import com.bdmajora.extras.client.budget.RenderBudgetController;
import com.bdmajora.extras.client.particle.LightCachedParticle;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.extras.client.particle.ParticleTicker;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFlame;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticleMixinsTest {
    private ExtrasConfig config;

    // A particle class from outside net.minecraft, which the culling leaves alone
    static class ModParticle extends ParticleFlame {
        ModParticle() {
            super(null, 0, 0, 0, 0, 0, 0);
        }
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        ParticleClassRegistry.getInstance().loadDisabledClasses(new String[0]);
        // The shipped defaults turn these on; each test switches on what it exercises
        ParticleTicker.collisionCache = false;
        ParticleTicker.lightCache = false;
        ParticleTicker.cullOffscreen = false;
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        ParticleTicker.apply(new ExtrasConfig.ParticleSettings());
        ParticleTicker.enabled = false;
        ShadowStubs.clear();
        Mixins.set(RenderBudgetController.class, "budget", RenderBudget.NEUTRAL);
    }

    @Test
    void theSwitchesStopTheirParticlesAtTheSource() {
        EntityRendererRainSplashMixin renderer = Mixins.instance(EntityRendererRainSplashMixin.class);
        CallbackInfo rain = Mixins.ci();
        Mixins.call(renderer, "impetus$addRainParticles", rain);
        assertFalse(rain.isCancelled());
        config.particle.rainSplash = false;
        CallbackInfo off = Mixins.ci();
        Mixins.call(renderer, "impetus$addRainParticles", off);
        assertTrue(off.isCancelled());

        ParticleManagerMixin manager = Mixins.instance(ParticleManagerMixin.class);
        CallbackInfo destroy = Mixins.ci();
        Mixins.call(manager, "impetus$blockDestroyEffects", BlockPos.ORIGIN, null, destroy);
        assertFalse(destroy.isCancelled());
        CallbackInfo hit = Mixins.ci();
        Mixins.call(manager, "impetus$blockHitEffects", BlockPos.ORIGIN, EnumFacing.UP, hit);
        assertFalse(hit.isCancelled());
        config.particle.blockBreak = false;
        config.particle.blockBreaking = false;
        CallbackInfo noDestroy = Mixins.ci();
        Mixins.call(manager, "impetus$blockDestroyEffects", BlockPos.ORIGIN, null, noDestroy);
        assertTrue(noDestroy.isCancelled());
        CallbackInfo noHit = Mixins.ci();
        Mixins.call(manager, "impetus$blockHitEffects", BlockPos.ORIGIN, EnumFacing.UP, noHit);
        assertTrue(noHit.isCancelled());

        // Vanilla particle ids go through the named filters
        RenderGlobalParticleMixin global = Mixins.instance(RenderGlobalParticleMixin.class);
        CallbackInfoReturnable<Particle> flame = Mixins.cir();
        Mixins.call(global, "impetus$filterParticle", EnumParticleTypes.FLAME.getParticleID(), false, false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, new int[0], flame);
        assertFalse(flame.isCancelled());
        config.particle.all = false;
        CallbackInfoReturnable<Particle> none = Mixins.cir();
        Mixins.call(global, "impetus$filterParticle", EnumParticleTypes.FLAME.getParticleID(), false, false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, new int[0], none);
        assertTrue(none.isCancelled());
        assertNull(none.getReturnValue());
    }

    @Test
    void everyParticleIsRecordedThenFilteredByMasterClassAndBudget() {
        ParticleManagerMixin manager = Mixins.instance(ParticleManagerMixin.class);
        // Registration names the mod that was active, when there is one
        IParticleFactory factory = mock(IParticleFactory.class);
        LoadController controller = mock(LoadController.class);
        ModContainer mod = mock(ModContainer.class);
        when(mod.getModId()).thenReturn("examplemod");
        when(controller.activeContainer()).thenReturn(mod);
        Object previous = Mixins.get(Loader.instance(), "modController");
        Mixins.set(Loader.instance(), "modController", controller);
        try {
            Mixins.call(manager, "impetus$captureFactoryMod", 0, factory, Mixins.ci());
            Mixins.call(manager, "impetus$captureFactoryMod", 1, null, Mixins.ci());
            when(controller.activeContainer()).thenReturn(null);
            Mixins.call(manager, "impetus$captureFactoryMod", 2, factory, Mixins.ci());
        } finally {
            Mixins.set(Loader.instance(), "modController", previous);
        }

        Particle flame = Mc.uninitialized(ParticleFlame.class);
        CallbackInfo nothing = Mixins.ci();
        Mixins.call(manager, "impetus$filterEffect", null, nothing);
        assertFalse(nothing.isCancelled());
        CallbackInfo allowed = Mixins.ci();
        Mixins.call(manager, "impetus$filterEffect", flame, allowed);
        assertFalse(allowed.isCancelled());
        assertTrue(ParticleClassRegistry.getInstance().getDiscoveredClasses().containsKey(ParticleFlame.class.getName()));

        // A class the user switched off
        ParticleClassRegistry.getInstance().setClassEnabled(ParticleFlame.class.getName(), false);
        CallbackInfo disabled = Mixins.ci();
        Mixins.call(manager, "impetus$filterEffect", flame, disabled);
        assertTrue(disabled.isCancelled());
        ParticleClassRegistry.getInstance().setClassEnabled(ParticleFlame.class.getName(), true);
        // One the budget refuses
        config.renderBudget.enabled = true;
        config.renderBudget.particleBudget = 0;
        RenderBudgetController.onClientTick(new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(
                net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));
        boolean refused = false;
        for (int i = 0; i < 4 && !refused; i++) {
            CallbackInfo budgeted = Mixins.ci();
            Mixins.call(manager, "impetus$filterEffect", flame, budgeted);
            refused = budgeted.isCancelled();
        }
        assertTrue(refused);
        // And everything, with the master off
        config.particle.all = false;
        CallbackInfo master = Mixins.ci();
        Mixins.call(manager, "impetus$filterEffect", flame, master);
        assertTrue(master.isCancelled());
    }

    @Test
    void aParticleRemembersWhetherItsCellHasAnythingToHit() {
        ParticleCollisionCacheMixin particle = Mixins.instance(ParticleCollisionCacheMixin.class);
        Mixins.set(particle, "impetus$cellX", Integer.MIN_VALUE);
        World world = mock(World.class);
        AxisAlignedBB inside = new AxisAlignedBB(0.2, 0.2, 0.2, 0.4, 0.4, 0.4);
        AxisAlignedBB across = new AxisAlignedBB(0.8, 0.2, 0.2, 1.2, 0.4, 0.4);

        // Off, every sweep is vanilla's
        Mc.Recorded<List<AxisAlignedBB>> empty = Mc.operation(List.of());
        Mixins.call(particle, "impetus$cachedCollisions", world, null, inside, empty);
        assertEquals(1, empty.count());

        ParticleTicker.collisionCache = true;
        // A sweep crossing cells cannot use the cached answer
        Mixins.call(particle, "impetus$cachedCollisions", world, null, across, empty);
        assertEquals(2, empty.count());
        // Inside one cell, the cell is asked once and an empty one answers every later sweep
        for (int i = 0; i < 5; i++) {
            assertTrue(Mixins.<List<AxisAlignedBB>>call(particle, "impetus$cachedCollisions", world, null, inside, empty).isEmpty());
        }
        assertEquals(3, empty.count());
        // Every so often it is asked again, since the world can change
        for (int i = 0; i < 10; i++) {
            Mixins.call(particle, "impetus$cachedCollisions", world, null, inside, empty);
        }
        assertEquals(4, empty.count());

        // A cell with something in it sends the sweep on to vanilla
        AxisAlignedBB block = new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);
        Mc.Recorded<List<AxisAlignedBB>> solid = Mc.operation(List.of(block));
        AxisAlignedBB elsewhere = new AxisAlignedBB(5.2, 0.2, 0.2, 5.4, 0.4, 0.4);
        assertEquals(List.of(block), Mixins.call(particle, "impetus$cachedCollisions", world, null, elsewhere, solid));
        assertEquals(2, solid.count());
    }

    @Test
    void aParticleRendersWithTheLightItSampledThisTick() {
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
        ParticleLightCacheMixin particle = Mixins.instance(ParticleLightCacheMixin.class);
        Mixins.set(particle, "impetus$cachedLightTick", -1);
        World world = mock(World.class);
        when(world.isBlockLoaded(any(BlockPos.class))).thenReturn(true);
        when(world.getCombinedLight(any(), Mockito.anyInt())).thenReturn(0x00F000A0);
        Mixins.set(particle, "world", world);

        // Nothing sampled yet, so vanilla samples
        ParticleTicker.lightCache = true;
        CallbackInfoReturnable<Integer> unsampled = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, unsampled);
        assertFalse(unsampled.isCancelled());

        particle.impetus$sampleLight();
        CallbackInfoReturnable<Integer> sampled = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, sampled);
        assertEquals(0x00F000A0, sampled.getReturnValue());
        // Without dynamic lights the sample is used as is
        DynamicLights.options().mode = com.bdmajora.dynamiclights.DynamicLightsMode.OFF;
        CallbackInfoReturnable<Integer> plain = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, plain);
        assertEquals(0x00F000A0, plain.getReturnValue());

        // A sample from an earlier tick, or with the cache off, is not trusted
        ParticleTicker.beginTick();
        CallbackInfoReturnable<Integer> stale = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, stale);
        assertFalse(stale.isCancelled());
        ParticleTicker.lightCache = false;
        particle.impetus$sampleLight();
        CallbackInfoReturnable<Integer> off = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, off);
        assertFalse(off.isCancelled());

        // An unloaded block reads as dark
        when(world.isBlockLoaded(any(BlockPos.class))).thenReturn(false);
        ParticleTicker.lightCache = true;
        particle.impetus$sampleLight();
        CallbackInfoReturnable<Integer> dark = Mixins.cir();
        Mixins.call(particle, "impetus$useCachedLight", 0.0F, dark);
        assertEquals(0, dark.getReturnValue());
        Mixins.set(DynamicLights.class, "config", null);
    }

    @Test
    void offscreenVanillaParticlesAreNotDrawn() {
        ParticleManagerCullingMixin manager = Mixins.instance(ParticleManagerCullingMixin.class);
        Entity viewer = mock(Entity.class);
        // Off, there is no frustum to test against
        Mixins.call(manager, "impetus$buildFrustum", viewer, 0.5F, Mixins.ci());
        assertNull(Mixins.get(manager, "impetus$frustum"));
        ParticleTicker.cullOffscreen = true;
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            Mixins.call(manager, "impetus$buildFrustum", viewer, 0.5F, Mixins.ci());
        }
        assertNotNull(Mixins.get(manager, "impetus$frustum"));

        Frustum frustum = mock(Frustum.class);
        Mixins.set(manager, "impetus$frustum", frustum);
        Particle vanilla = Mc.uninitialized(ParticleFlame.class);
        Mixins.set(vanilla, "boundingBox", new AxisAlignedBB(0, 0, 0, 0.1, 0.1, 0.1));
        Mc.Recorded<Void> draw = Mc.operation();
        Mixins.call(manager, "impetus$cullParticle", vanilla, null, viewer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, draw);
        assertEquals(0, draw.count());
        when(frustum.isBoxInFrustum(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
        Mixins.call(manager, "impetus$cullParticle", vanilla, null, viewer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, draw);
        assertEquals(1, draw.count());
        // Modded particles are drawn regardless, since their boxes cannot be trusted
        Mixins.set(manager, "impetus$frustum", mock(Frustum.class));
        Mixins.call(manager, "impetus$cullParticle", Mc.uninitialized(ModParticle.class), null, viewer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, draw);
        assertEquals(2, draw.count());
        // And so is everything once culling is off
        Mixins.set(manager, "impetus$frustum", null);
        Mixins.call(manager, "impetus$cullParticle", vanilla, null, viewer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, draw);
        assertEquals(3, draw.count());
    }

    @Test
    void largeLayersAreTickedOnThePool() {
        ParticleManagerParallelMixin manager = Mixins.instance(ParticleManagerParallelMixin.class);
        Mixins.set(manager, "queue", new ArrayDeque<Particle>());
        Mixins.call(manager, "impetus$concurrentQueue", Mixins.ci());
        assertInstanceOf(ConcurrentLinkedQueue.class, Mixins.get(manager, "queue"));
        int tick = ParticleTicker.tickCounter;
        Mixins.call(manager, "impetus$beginTick", Mixins.ci());
        assertEquals(tick + 1, ParticleTicker.tickCounter);

        AtomicInteger ticked = new AtomicInteger();
        ShadowStubs.on(manager, "tickParticle", args -> {
            ticked.incrementAndGet();
            return null;
        });
        Queue<Particle> layer = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 300; i++) {
            Particle particle = Mc.uninitialized(ParticleFlame.class);
            Mixins.set(particle, "isExpired", true);
            layer.add(particle);
        }
        // Small layers, or the pool switched off, keep vanilla's loop
        CallbackInfo serial = Mixins.ci();
        Mixins.call(manager, "impetus$tickLayerParallel", layer, serial);
        assertFalse(serial.isCancelled());
        ParticleTicker.enabled = true;
        CallbackInfo pooled = Mixins.ci();
        Mixins.call(manager, "impetus$tickLayerParallel", layer, pooled);
        assertTrue(pooled.isCancelled());
        assertEquals(300, ticked.get());
        assertTrue(layer.isEmpty());
        // The placeholder body is what Mixin replaces with the real private method
        assertThrows(AssertionError.class, () -> Mixins.call(manager, "tickParticle", Mc.uninitialized(ParticleFlame.class)));

        // After each tick a living particle samples its light for the render pass
        Particle living = Mc.mock(ParticleFlame.class, LightCachedParticle.class);
        when(living.isAlive()).thenReturn(true);
        Mixins.call(manager, "impetus$sampleLight", living, Mixins.ci());
        ParticleTicker.lightCache = true;
        Mixins.call(manager, "impetus$sampleLight", living, Mixins.ci());
        verify((LightCachedParticle) living).impetus$sampleLight();
        Particle dead = Mc.mock(ParticleFlame.class, LightCachedParticle.class);
        Mixins.call(manager, "impetus$sampleLight", dead, Mixins.ci());
        verify((LightCachedParticle) dead, Mockito.never()).impetus$sampleLight();
    }
}
