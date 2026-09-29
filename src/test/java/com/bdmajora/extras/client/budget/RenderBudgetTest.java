package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleDigging;
import net.minecraft.client.particle.ParticleFlame;
import net.minecraft.client.particle.ParticleRain;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenderBudgetTest {
    private ExtrasConfig config;
    private Minecraft client;
    private WorldClient world;
    private EntityPlayer viewer;

    // A particle class whose name marks it as a block particle, and one whose simple name marks it as weather
    static class ModTerrainParticle extends Particle {
        ModTerrainParticle() {
            super(null, 0, 0, 0);
        }
    }

    static class SnowFlurry extends Particle {
        SnowFlurry() {
            super(null, 0, 0, 0);
        }
    }

    // A block entity from a mod, which the budget never touches even when it extends a vanilla one
    static class ModdedChest extends TileEntityChest {
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshController() {
        config = new ExtrasConfig();
        config.renderBudget.enabled = true;
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        world = mock(WorldClient.class);
        Mixins.set(client, "world", world);
        viewer = mock(EntityPlayer.class);
        when(client.getRenderViewEntity()).thenReturn(viewer);
        Mixins.set(RenderBudgetController.class, "lastFrameNanos", 0L);
        Mixins.set(RenderBudgetController.class, "emaFrameMillis", 0.0D);
        Mixins.set(RenderBudgetController.class, "frameIndex", 0L);
        Mixins.set(RenderBudgetController.class, "armedLastTick", false);
        Mixins.set(RenderBudgetController.class, "lastProfile", null);
        Mixins.<Map<?, ?>>get(RenderBudgetController.class, "decisions").clear();
        Mixins.<Map<?, ?>>get(RenderBudgetController.class, "particleCategories").clear();
        Mixins.set(RenderBudgetController.class, "particleCarry", 0.0D);
    }

    @AfterEach
    void neutral() {
        Mixins.set(RenderBudgetController.class, "budget", RenderBudget.NEUTRAL);
        Mixins.set(Extras.class, "config", null);
    }

    private static void tick() {
        RenderBudgetController.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
    }

    private static void frame() {
        RenderBudgetController.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0.0F));
    }

    private static void pressure(double emaMillis) {
        Mixins.set(RenderBudgetController.class, "emaFrameMillis", emaMillis);
    }

    @Test
    void theBudgetFollowsTheProfileThePressureAndAShaderPack() {
        // Off, nothing is limited
        config.renderBudget.enabled = false;
        tick();
        assertSame(RenderBudget.NEUTRAL, RenderBudgetController.budget());
        assertFalse(RenderBudget.NEUTRAL.limitsParticles());
        assertFalse(RenderBudget.NEUTRAL.limitsEntities());
        assertFalse(RenderBudget.NEUTRAL.limitsBlockEntities());

        // Balanced at target is not armed, but reports what would apply
        config.renderBudget.enabled = true;
        pressure(16.0);
        tick();
        RenderBudget balanced = RenderBudgetController.budget();
        assertFalse(balanced.armed);
        assertEquals(0.65, balanced.particleScale, 1e-9);
        assertEquals(96, balanced.entityCullDistance);
        assertTrue(balanced.limitsParticles());
        assertTrue(balanced.limitsEntities());
        assertTrue(balanced.limitsBlockEntities());

        // A quarter over target arms it and tightens it one adaptive step
        pressure(22.0);
        tick();
        RenderBudget pressed = RenderBudgetController.budget();
        assertTrue(pressed.armed);
        assertTrue(pressed.adaptiveActive);
        assertEquals(0.45, pressed.particleScale, 1e-9);
        assertEquals(80, pressed.entityCullDistance);
        // Once armed it stays so while there is any pressure at all
        pressure(16.8);
        tick();
        assertTrue(RenderBudgetController.budget().armed);

        // Quality keeps more, performance keeps less and is always armed
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.QUALITY;
        pressure(0.0);
        tick();
        assertEquals(0.85, RenderBudgetController.budget().particleScale, 1e-9);
        assertEquals(128, RenderBudgetController.budget().entityCullDistance);
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        tick();
        assertTrue(RenderBudgetController.budget().armed);
        assertEquals(0.45, RenderBudgetController.budget().particleScale, 1e-9);
        assertEquals(72, RenderBudgetController.budget().entityCullDistance);
        // Relaxing the profile clamps the frame average to the new target so the old pressure does not linger
        pressure(40.0);
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.QUALITY;
        tick();
        assertEquals(ExtrasConfig.BudgetProfile.QUALITY.targetFrameMillis, RenderBudgetController.budget().emaFrameMillis, 0.01);

        // All particles handed back stays that way, and the entity and block-entity limits can be switched off
        config.renderBudget.particleBudget = 100;
        config.renderBudget.smartEntityCulling = false;
        config.renderBudget.blockEntities = false;
        tick();
        assertFalse(RenderBudgetController.budget().limitsParticles());
        assertFalse(RenderBudgetController.budget().limitsEntities());
        assertFalse(RenderBudgetController.budget().limitsBlockEntities());
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        pressure(40.0);
        tick();
        assertFalse(RenderBudgetController.budget().limitsParticles());

        // A running shader pack loosens everything
        config.renderBudget.particleBudget = 50;
        config.renderBudget.blockEntities = true;
        Mixins.set(Umbra.class, "currentPack", mock(com.bdmajora.impetus.umbra.shaderpack.ShaderPack.class));
        try {
            tick();
            assertEquals(ExtrasConfig.RenderBudgetSettings.BLOCK_ENTITY_DISTANCE_DEFAULT + 32, RenderBudgetController.budget().blockEntityCullDistance);
        } finally {
            Mixins.set(Umbra.class, "currentPack", null);
        }
        assertNotNull(Mixins.construct(RenderBudgetController.class));
    }

    @Test
    void theFrameAverageFollowsRenderTicksAndStaleDecisionsAreSwept() {
        RenderBudgetController.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0.0F));
        frame();
        frame();
        assertTrue(Mixins.<Double>get(RenderBudgetController.class, "emaFrameMillis") >= 0.0);
        frame();

        // A decision nobody asked about for a long while is dropped on the next sweep
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        tick();
        EntityPig far = pig(500);
        RenderBudgetController.shouldCullLivingEntity(far);
        assertFalse(Mixins.<Map<?, ?>>get(RenderBudgetController.class, "decisions").isEmpty());
        Mixins.set(RenderBudgetController.class, "frameIndex", 4_980L - 1);
        frame();
        assertTrue(Mixins.<Map<?, ?>>get(RenderBudgetController.class, "decisions").isEmpty());
        // A sweep with nothing cached costs nothing
        Mixins.set(RenderBudgetController.class, "frameIndex", 6_000L - 1);
        frame();
        // The start of a client tick changes nothing
        RenderBudgetController.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
    }

    private EntityPig pig(double x) {
        EntityPig pig = mock(EntityPig.class);
        Mixins.set(pig, "posX", x);
        Mixins.set(pig, "world", world);
        when(pig.getEntityId()).thenReturn(7);
        when(world.getEntityByID(7)).thenReturn(pig);
        return pig;
    }

    @Test
    void aFarMobIsDroppedOnlyAfterStayingFarAndComesBackWhenItNears() {
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        tick();
        EntityPig pig = pig(200);
        // Several frames in a row far away before it is actually dropped
        for (int i = 0; i < 5; i++) {
            frame();
            assertFalse(RenderBudgetController.shouldCullLivingEntity(pig));
        }
        frame();
        assertTrue(RenderBudgetController.shouldCullLivingEntity(pig));
        assertTrue(RenderBudgetController.wasCulledThisFrame(pig));
        frame();
        assertTrue(RenderBudgetController.shouldCullLivingEntity(pig));
        tick();
        assertTrue(RenderBudgetController.entitiesSkipped() > 0);

        // Coming inside the exit line brings it straight back
        Mixins.set(pig, "posX", 40.0);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(pig));
        assertFalse(RenderBudgetController.wasCulledThisFrame(pig));
        // A mob seen far, then skipped for a frame, starts its run over
        Mixins.set(pig, "posX", 200.0);
        frame();
        RenderBudgetController.shouldCullLivingEntity(pig);
        frame();
        frame();
        assertFalse(RenderBudgetController.shouldCullLivingEntity(pig));
        // Coming inside the entry line while not yet dropped forgets it
        Mixins.set(pig, "posX", 60.0);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(pig));

        // Players, the viewer itself, and having no viewer are never dropped
        EntityPlayer player = mock(EntityPlayer.class);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(player));
        when(client.getRenderViewEntity()).thenReturn(null);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(pig));
        assertFalse(RenderBudgetController.wasCulledThisFrame(mock(EntityPig.class)));
    }

    @Test
    void mobsPeopleCareAboutAreProtected() {
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        config.renderBudget.entityCullDistance = 16;
        tick();
        // Close enough to be protected though past the configured line
        EntityPig close = pig(30);
        for (int i = 0; i < 8; i++) {
            frame();
            assertFalse(RenderBudgetController.shouldCullLivingEntity(close));
        }
        EntityPig riding = pig(100);
        when(riding.isRiding()).thenReturn(true);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(riding));
        EntityPig elsewhere = pig(100);
        Mixins.set(elsewhere, "world", mock(WorldClient.class));
        assertFalse(RenderBudgetController.shouldCullLivingEntity(elsewhere));
        EntityPig unregistered = pig(100);
        when(world.getEntityByID(anyInt())).thenReturn(null);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(unregistered));
        EntityPig preview = pig(100);
        Mixins.set(client, "currentScreen", mock(GuiScreen.class));
        assertFalse(RenderBudgetController.shouldCullLivingEntity(preview));
        Mixins.set(client, "currentScreen", null);
        EntityPig glowing = pig(100);
        when(glowing.isGlowing()).thenReturn(true);
        assertFalse(RenderBudgetController.shouldCullLivingEntity(glowing));
        tick();
        assertTrue(RenderBudgetController.entitiesProtected() > 0);
    }

    @Test
    void onlyVanillaDecorativeBlockEntitiesAreSkippedAndNeverTheOneLookedAt() {
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        tick();
        TileEntityChest near = new TileEntityChest();
        near.setPos(new BlockPos(10, 0, 0));
        assertFalse(RenderBudgetController.shouldCullBlockEntity(near));
        TileEntityChest far = new TileEntityChest();
        far.setPos(new BlockPos(500, 0, 0));
        assertTrue(RenderBudgetController.shouldCullBlockEntity(far));
        // Cached per class from then on
        assertTrue(RenderBudgetController.shouldCullBlockEntity(far));
        TileEntityFurnace furnace = new TileEntityFurnace();
        furnace.setPos(new BlockPos(500, 0, 0));
        assertFalse(RenderBudgetController.shouldCullBlockEntity(furnace));
        ModdedChest modded = new ModdedChest();
        modded.setPos(new BlockPos(500, 0, 0));
        assertFalse(RenderBudgetController.shouldCullBlockEntity(modded));
        Mixins.set(client, "objectMouseOver", new RayTraceResult(Vec3d.ZERO, EnumFacing.UP, far.getPos()));
        assertFalse(RenderBudgetController.shouldCullBlockEntity(far));
        Mixins.set(client, "objectMouseOver", null);
        tick();
        assertTrue(RenderBudgetController.blockEntitiesSkipped() > 0);
        assertTrue(RenderBudgetController.blockEntitiesProtected() > 0);

        // Disarmed or unlimited, nothing is skipped
        Mixins.set(RenderBudgetController.class, "budget", RenderBudget.NEUTRAL);
        assertFalse(RenderBudgetController.shouldCullBlockEntity(far));
    }

    @Test
    void itemFramesPeopleLookAtFromAfarAreKept() {
        config.renderBudget.profile = ExtrasConfig.BudgetProfile.PERFORMANCE;
        tick();
        EntityItemFrame plain = mock(EntityItemFrame.class);
        Mixins.set(plain, "posX", 500.0);
        when(plain.getDisplayedItem()).thenReturn(new ItemStack(Items.APPLE));
        assertTrue(RenderBudgetController.shouldCullItemFrame(plain));
        EntityItemFrame map = mock(EntityItemFrame.class);
        Mixins.set(map, "posX", 500.0);
        when(map.getDisplayedItem()).thenReturn(new ItemStack(Items.FILLED_MAP));
        assertFalse(RenderBudgetController.shouldCullItemFrame(map));
        EntityItemFrame near = mock(EntityItemFrame.class);
        Mixins.set(near, "posX", 5.0);
        assertFalse(RenderBudgetController.shouldCullItemFrame(near));
        when(client.getRenderViewEntity()).thenReturn(plain);
        assertFalse(RenderBudgetController.shouldCullItemFrame(plain));
        tick();
        assertEquals(1, RenderBudgetController.itemFramesSkipped());
        assertEquals(1, RenderBudgetController.itemFramesProtected());
        Mixins.set(RenderBudgetController.class, "budget", RenderBudget.NEUTRAL);
        assertFalse(RenderBudgetController.shouldCullItemFrame(map));
    }

    @Test
    void cosmeticParticlesAreThinnedEvenlyAndTheRestProtected() {
        config.renderBudget.particleBudget = 50;
        tick();
        Particle flame = Mc.uninitialized(ParticleFlame.class);
        int refused = 0;
        for (int i = 0; i < 10; i++) {
            if (RenderBudgetController.shouldCullParticle(flame)) {
                refused++;
            }
        }
        assertEquals(5, refused);
        assertFalse(RenderBudgetController.shouldCullParticle(Mc.uninitialized(ParticleRain.class)));
        assertFalse(RenderBudgetController.shouldCullParticle(Mc.uninitialized(ParticleDigging.class)));
        tick();
        assertEquals(5, RenderBudgetController.particlesSkipped());
        assertEquals(2, RenderBudgetController.particlesProtected());
        Mixins.set(RenderBudgetController.class, "budget", RenderBudget.NEUTRAL);
        assertFalse(RenderBudgetController.shouldCullParticle(flame));
    }

    @Test
    void particlesAreSortedByWhatTheyShow() {
        assertEquals(ParticleCategory.WEATHER, ParticleCategory.classify(ParticleRain.class));
        assertEquals(ParticleCategory.CRITICAL, ParticleCategory.classify(ParticleDigging.class));
        assertEquals(ParticleCategory.CRITICAL, ParticleCategory.classify(ModTerrainParticle.class));
        assertEquals(ParticleCategory.WEATHER, ParticleCategory.classify(SnowFlurry.class));
        assertEquals(ParticleCategory.COSMETIC, ParticleCategory.classify(ParticleFlame.class));
    }
}
