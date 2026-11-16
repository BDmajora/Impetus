package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.engine.impl.gl.profiling.TimerQueryManager;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.umbra.Umbra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnchantmentTable;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Computes the render budget and decides what to skip.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class RenderBudgetController {
    // Weight of a new frame time.
    private static final double EMA_ALPHA = 0.12;
    // Frames an entity must stay far away before it is skipped.
    private static final int CULL_CONFIRMATION_FRAMES = 6;
    // Frames after which an unseen decision expires.
    private static final long CACHE_MAX_AGE_FRAMES = 1200;
    // Frames between sweeps of old decisions.
    private static final long CACHE_SWEEP_INTERVAL_FRAMES = 60;
    // Distance inside which entities are never skipped.
    private static final double CLOSE_PROTECTION_DISTANCE = 32.0;
    // Square of the protection distance.
    private static final double CLOSE_PROTECTION_DISTANCE_SQ = CLOSE_PROTECTION_DISTANCE * CLOSE_PROTECTION_DISTANCE;
    // Margin that prevents flicker at the limit.
    private static final double HYSTERESIS_DISTANCE = 12.0;
    // Pressure at which adaptive limits start.
    private static final double ADAPTIVE_PRESSURE_THRESHOLD = 0.2;
    // Pressure at which limits apply.
    private static final double ARM_PRESSURE = 0.03;
    // Extra block entity distance while a shader pack runs.
    private static final int SHADER_BLOCK_ENTITY_BONUS = 32;

    // Time of the previous frame.
    private static long lastFrameNanos;
    // Smoothed frame time.
    private static double emaFrameMillis;
    // Number of frames seen.
    private static long frameIndex;
    // Whether limits applied in the previous tick.
    private static boolean armedLastTick;
    // Current budget.
    private static volatile RenderBudget budget = RenderBudget.NEUTRAL;

    // Detector of the bottleneck.
    private static final BottleneckDetector bottleneck = new BottleneckDetector();
    // Graphics timer or null.
    private static TimerQueryManager gpuTimer;
    // Whether a graphics timing is running.
    private static boolean gpuFrameOpen;

    // Setting seen in the previous tick.
    private static boolean lastEnabled;
    // Profile seen in the previous tick.
    private static ExtrasConfig.BudgetProfile lastProfile;
    // Setting seen in the previous tick.
    private static boolean lastAdaptive;
    // Setting seen in the previous tick.
    private static int lastParticleBudget;
    // Setting seen in the previous tick.
    private static int lastEntityCullDistance;
    // Setting seen in the previous tick.
    private static boolean lastSmartEntityCulling;

    // Skip decision of each entity.
    private static final Map<Entity, Decision> decisions = new IdentityHashMap<>();
    // Category of each particle class.
    private static final Map<Class<?>, ParticleCategory> particleCategories = new ConcurrentHashMap<>();
    // Lock that guards the particle carry.
    private static final Object PARTICLE_CARRY_LOCK = new Object();
    // Whether each block entity class may be skipped.
    private static final Map<Class<?>, Boolean> budgetableBlockEntities = new HashMap<>();
    // Fraction of a particle carried to the next one.
    private static double particleCarry;

    // Entities skipped in this tick.
    private static int entitiesSkipped;
    // Entities kept in this tick.
    private static int entitiesProtected;
    // Particles skipped in this tick.
    private static int particlesSkipped;
    // Particles kept in this tick.
    private static int particlesProtected;
    // Block entities skipped in this tick.
    private static int blockEntitiesSkipped;
    // Block entities kept in this tick.
    private static int blockEntitiesProtected;
    // Item frames skipped in this tick.
    private static int itemFramesSkipped;
    // Item frames kept in this tick.
    private static int itemFramesProtected;
    // Count of the previous tick.
    private static int shownEntitiesSkipped;
    // Count of the previous tick.
    private static int shownEntitiesProtected;
    // Count of the previous tick.
    private static int shownParticlesSkipped;
    // Count of the previous tick.
    private static int shownParticlesProtected;
    // Count of the previous tick.
    private static int shownBlockEntitiesSkipped;
    // Count of the previous tick.
    private static int shownBlockEntitiesProtected;
    // Count of the previous tick.
    private static int shownItemFramesSkipped;
    // Count of the previous tick.
    private static int shownItemFramesProtected;

    // Prevents instantiation
    private RenderBudgetController() {
    }

    // Updates the frame time and the graphics timing.
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            closeGpuFrame();
            return;
        }

        long now = System.nanoTime();
        double frameMillis = 0.0;
        if (lastFrameNanos != 0 && now > lastFrameNanos) {
            frameMillis = (now - lastFrameNanos) / 1_000_000.0;
            emaFrameMillis = emaFrameMillis <= 0.0
                    ? frameMillis
                    : emaFrameMillis + EMA_ALPHA * (frameMillis - emaFrameMillis);
        }
        lastFrameNanos = now;
        openGpuFrame(frameMillis);

        frameIndex++;
        if (frameIndex % CACHE_SWEEP_INTERVAL_FRAMES == 0 && !decisions.isEmpty()) {
            Iterator<Decision> iterator = decisions.values().iterator();
            while (iterator.hasNext()) {
                if (frameIndex - iterator.next().lastSeenFrame > CACHE_MAX_AGE_FRAMES) {
                    iterator.remove();
                }
            }
        }
    }

    // Starts the graphics timing of the frame.
    private static void openGpuFrame(double cpuMillis) {
        ExtrasConfig.RenderBudgetSettings settings = Extras.options().renderBudget;
        if (!settings.enabled || !settings.adaptive) {
            stopGpuTiming();
            return;
        }
        if (gpuTimer == null) {
            if (bottleneck.state() == BottleneckDetector.State.UNAVAILABLE) {
                return;
            }
            if (!timerQueriesSupported()) {
                bottleneck.markUnavailable();
                return;
            }
            gpuTimer = new TimerQueryManager();
        }

        closeGpuFrame();
        if (gpuTimer.updateTime()) {
            bottleneck.record(cpuMillis, gpuTimer.getLastTime() / 1_000_000.0, settings.profile.targetFrameMillis);
        }
        gpuTimer.startProfiling();
        gpuFrameOpen = true;
    }

    // Ends the graphics timing of the frame.
    private static void closeGpuFrame() {
        if (gpuFrameOpen) {
            gpuTimer.finishProfiling();
            gpuFrameOpen = false;
        }
    }

    // Releases the graphics timer.
    private static void stopGpuTiming() {
        if (gpuTimer != null) {
            closeGpuFrame();
            gpuTimer.close();
            gpuTimer = null;
            bottleneck.reset();
        }
    }

    // Returns whether the driver supports timer queries.
    private static boolean timerQueriesSupported() {
        try {
            return LWJGL.isOpenGLVersionSupported(3, 3) || LWJGL.isExtensionSupported(GLExtension.ARB_timer_query);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // Recomputes the budget and publishes the counters.
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ExtrasConfig.RenderBudgetSettings settings = Extras.options().renderBudget;
        handleSettingsChange(settings);
        budget = calculate(settings);

        shownEntitiesSkipped = entitiesSkipped;
        shownEntitiesProtected = entitiesProtected;
        shownParticlesSkipped = particlesSkipped;
        shownParticlesProtected = particlesProtected;
        shownBlockEntitiesSkipped = blockEntitiesSkipped;
        shownBlockEntitiesProtected = blockEntitiesProtected;
        shownItemFramesSkipped = itemFramesSkipped;
        shownItemFramesProtected = itemFramesProtected;
        entitiesSkipped = 0;
        entitiesProtected = 0;
        particlesSkipped = 0;
        particlesProtected = 0;
        blockEntitiesSkipped = 0;
        blockEntitiesProtected = 0;
        itemFramesSkipped = 0;
        itemFramesProtected = 0;
    }

    // Resets the state when a setting changed.
    private static void handleSettingsChange(ExtrasConfig.RenderBudgetSettings settings) {
        boolean changed = settings.enabled != lastEnabled
                || settings.profile != lastProfile
                || settings.adaptive != lastAdaptive
                || settings.particleBudget != lastParticleBudget
                || settings.entityCullDistance != lastEntityCullDistance
                || settings.smartEntityCulling != lastSmartEntityCulling;
        if (!changed) {
            return;
        }

        decisions.clear();
        particleCarry = 0.0;
        armedLastTick = false;
        if (lastProfile != null && settings.profile.ordinal() < lastProfile.ordinal() && emaFrameMillis > 0.0) {
            emaFrameMillis = Math.min(emaFrameMillis, settings.profile.targetFrameMillis);
        }

        lastEnabled = settings.enabled;
        lastProfile = settings.profile;
        lastAdaptive = settings.adaptive;
        lastParticleBudget = settings.particleBudget;
        lastEntityCullDistance = settings.entityCullDistance;
        lastSmartEntityCulling = settings.smartEntityCulling;
    }

    // Returns the budget for the settings and the frame time.
    private static RenderBudget calculate(ExtrasConfig.RenderBudgetSettings settings) {
        if (!settings.enabled) {
            armedLastTick = false;
            return RenderBudget.NEUTRAL;
        }

        ExtrasConfig.BudgetProfile profile = settings.profile;
        double target = profile.targetFrameMillis;
        double ema = emaFrameMillis;
        double pressure = ema > 0.0 ? Math.max(0.0, (ema - target) / target) : 0.0;

        double particleScale = settings.particleBudget / 100.0;
        int entityDistance = settings.entityCullDistance;
        boolean particlesUnlimited = particleScale >= 1.0;

        switch (profile) {
            case QUALITY -> {
                if (!particlesUnlimited) {
                    particleScale = Math.max(particleScale, 0.85);
                }
                entityDistance = Math.max(entityDistance, 128);
            }
            case PERFORMANCE -> {
                if (!particlesUnlimited) {
                    particleScale = Math.min(particleScale, 0.45);
                }
                entityDistance = Math.min(entityDistance, 72);
            }
            default -> { }
        }

        BottleneckDetector.State bound = bottleneck.state();
        boolean adaptive = settings.adaptive && pressure >= ADAPTIVE_PRESSURE_THRESHOLD && bound != BottleneckDetector.State.HEADROOM;
        if (adaptive) {
            switch (bound) {
                case CPU_BOUND -> entityDistance = Math.max(48, entityDistance - 16);
                case GPU_BOUND -> {
                    if (!particlesUnlimited) {
                        particleScale = Math.max(0.25, particleScale - 0.2);
                    }
                    entityDistance = Math.max(48, entityDistance - 8);
                }
                default -> {
                    if (!particlesUnlimited) {
                        particleScale = Math.max(0.25, particleScale - 0.2);
                    }
                    entityDistance = Math.max(48, entityDistance - 16);
                }
            }
        }

        boolean shaderPack = Umbra.isShaderPackInUse();
        if (shaderPack) {
            if (!particlesUnlimited) {
                particleScale = Math.min(1.0, particleScale + 0.1);
            }
            entityDistance = Math.max(entityDistance, 64);
        }

        boolean armed = profile == ExtrasConfig.BudgetProfile.PERFORMANCE
                || pressure >= ARM_PRESSURE
                || (armedLastTick && pressure > 0.0);
        armedLastTick = armed;

        int blockEntityDistance = settings.blockEntities
                ? settings.blockEntityDistance + (shaderPack ? SHADER_BLOCK_ENTITY_BONUS : 0)
                : Integer.MAX_VALUE;

        return new RenderBudget(
                Math.round(particleScale * 100.0) / 100.0,
                settings.smartEntityCulling ? entityDistance : Integer.MAX_VALUE,
                blockEntityDistance,
                settings.itemFrames,
                armed,
                target,
                Math.round(ema * 100.0) / 100.0,
                Math.round(pressure * 100.0) / 100.0,
                adaptive,
                bound);
    }

    // Returns the current budget.
    public static RenderBudget budget() {
        return budget;
    }

    // Returns whether the entity is skipped.
    public static boolean shouldCullLivingEntity(EntityLivingBase entity) {
        RenderBudget current = budget;
        if (!current.armed || !current.limitsEntities() || entity instanceof EntityPlayer) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        double distanceSquared = viewerDistanceSquared(minecraft, entity);
        if (distanceSquared < 0.0) {
            return false;
        }
        double maxDistance = current.entityCullDistance;
        double exitDistance = Math.max(CLOSE_PROTECTION_DISTANCE, maxDistance - HYSTERESIS_DISTANCE);
        Decision decision = decisions.get(entity);

        if (decision != null && decision.cull) {
            if (distanceSquared <= exitDistance * exitDistance) {
                decisions.remove(entity);
                return false;
            }
        } else {
            double enterDistance = maxDistance + HYSTERESIS_DISTANCE;
            if (distanceSquared <= enterDistance * enterDistance) {
                if (decision != null) {
                    decisions.remove(entity);
                }
                return false;
            }
        }

        if (isProtected(entity, minecraft, distanceSquared)) {
            if (decision != null) {
                decisions.remove(entity);
            }
            entitiesProtected++;
            return false;
        }

        if (decision != null && decision.cull) {
            decision.lastSeenFrame = frameIndex;
            entitiesSkipped++;
            return true;
        }

        if (decision == null) {
            decision = new Decision();
            decisions.put(entity, decision);
            decision.farFrames = 1;
        } else if (frameIndex - decision.lastSeenFrame > 1) {
            decision.farFrames = 1;
        } else if (frameIndex > decision.lastSeenFrame) {
            decision.farFrames++;
        }
        decision.lastSeenFrame = frameIndex;
        decision.cull = decision.farFrames >= CULL_CONFIRMATION_FRAMES;

        if (decision.cull) {
            entitiesSkipped++;
        }
        return decision.cull;
    }

    // Returns the squared distance to the viewer or minus one.
    private static double viewerDistanceSquared(Minecraft minecraft, Entity entity) {
        Entity viewer = minecraft.getRenderViewEntity();
        if (viewer == null) {
            viewer = minecraft.player;
        }
        if (viewer == null || entity == viewer) {
            return -1.0;
        }
        double dx = entity.posX - viewer.posX;
        double dy = entity.posY - viewer.posY;
        double dz = entity.posZ - viewer.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    // Returns whether the entity was skipped in this frame.
    public static boolean wasCulledThisFrame(Entity entity) {
        if (decisions.isEmpty()) {
            return false;
        }
        Decision decision = decisions.get(entity);
        return decision != null && decision.cull && decision.lastSeenFrame == frameIndex;
    }

    // Returns whether the entity must never be skipped.
    private static boolean isProtected(EntityLivingBase entity, Minecraft minecraft, double distanceSquared) {
        if (distanceSquared <= CLOSE_PROTECTION_DISTANCE_SQ) {
            return true;
        }
        if (entity.isRiding() || entity.isBeingRidden()) {
            return true;
        }

        boolean sameWorld = minecraft.world != null && entity.world == minecraft.world;
        if (!sameWorld || minecraft.world.getEntityByID(entity.getEntityId()) != entity) {
            return true;
        }
        if (minecraft.currentScreen != null && entity.ticksExisted <= 1) {
            return true;
        }
        return entity.isGlowing() || entity.hasCustomName();
    }

    // Returns whether the block entity is skipped.
    public static boolean shouldCullBlockEntity(TileEntity blockEntity) {
        RenderBudget current = budget;
        if (!current.armed || !current.limitsBlockEntities()) {
            return false;
        }

        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        double distanceSquared = blockEntity.getDistanceSq(dispatcher.entityX, dispatcher.entityY, dispatcher.entityZ);
        double maxDistance = current.blockEntityCullDistance;
        if (distanceSquared <= maxDistance * maxDistance) {
            return false;
        }

        Class<?> type = blockEntity.getClass();
        Boolean budgetable = budgetableBlockEntities.get(type);
        if (budgetable == null) {
            budgetable = isBudgetableBlockEntity(blockEntity) ? Boolean.TRUE : Boolean.FALSE;
            budgetableBlockEntities.put(type, budgetable);
        }
        if (!budgetable) {
            blockEntitiesProtected++;
            return false;
        }

        RayTraceResult target = Minecraft.getMinecraft().objectMouseOver;
        if (target != null && target.typeOfHit == RayTraceResult.Type.BLOCK && target.getBlockPos().equals(blockEntity.getPos())) {
            blockEntitiesProtected++;
            return false;
        }

        blockEntitiesSkipped++;
        return true;
    }

    // Returns whether the block entity type may be skipped.
    private static boolean isBudgetableBlockEntity(TileEntity blockEntity) {
        if (!blockEntity.getClass().getName().startsWith("net.minecraft.")) {
            return false;
        }
        return blockEntity instanceof TileEntityChest
                || blockEntity instanceof TileEntityEnderChest
                || blockEntity instanceof TileEntitySign
                || blockEntity instanceof TileEntitySkull
                || blockEntity instanceof TileEntityBanner
                || blockEntity instanceof TileEntityBed
                || blockEntity instanceof TileEntityShulkerBox
                || blockEntity instanceof TileEntityEnchantmentTable;
    }

    // Returns whether the item frame is skipped.
    public static boolean shouldCullItemFrame(EntityItemFrame frame) {
        RenderBudget current = budget;
        if (!current.armed || !current.itemFramesLimited || !current.limitsBlockEntities()) {
            return false;
        }

        double distanceSquared = viewerDistanceSquared(Minecraft.getMinecraft(), frame);
        if (distanceSquared < 0.0) {
            return false;
        }
        double maxDistance = current.blockEntityCullDistance;
        if (distanceSquared <= maxDistance * maxDistance) {
            return false;
        }

        if (frame.getDisplayedItem().getItem() instanceof ItemMap || frame.isGlowing() || frame.hasCustomName()) {
            itemFramesProtected++;
            return false;
        }

        itemFramesSkipped++;
        return true;
    }

    // Returns whether the particle is skipped.
    public static boolean shouldCullParticle(Particle particle) {
        RenderBudget current = budget;
        if (!current.limitsParticles()) {
            return false;
        }

        Class<? extends Particle> type = particle.getClass();
        ParticleCategory category = particleCategories.get(type);
        if (category == null) {
            category = ParticleCategory.classify(type);
            particleCategories.put(type, category);
        }

        if (category != ParticleCategory.COSMETIC) {
            particlesProtected++;
            return false;
        }

        synchronized (PARTICLE_CARRY_LOCK) {
            particleCarry += current.particleScale;
            if (particleCarry >= 1.0) {
                particleCarry -= 1.0;
                return false;
            }
        }

        particlesSkipped++;
        return true;
    }

    // Returns the entities skipped in the previous tick.
    public static int entitiesSkipped() {
        return shownEntitiesSkipped;
    }

    // Returns the entities kept in the previous tick.
    public static int entitiesProtected() {
        return shownEntitiesProtected;
    }

    // Returns the particles skipped in the previous tick.
    public static int particlesSkipped() {
        return shownParticlesSkipped;
    }

    // Returns the particles kept in the previous tick.
    public static int particlesProtected() {
        return shownParticlesProtected;
    }

    // Returns the block entities skipped in the previous tick.
    public static int blockEntitiesSkipped() {
        return shownBlockEntitiesSkipped;
    }

    // Returns the block entities kept in the previous tick.
    public static int blockEntitiesProtected() {
        return shownBlockEntitiesProtected;
    }

    // Returns the item frames skipped in the previous tick.
    public static int itemFramesSkipped() {
        return shownItemFramesSkipped;
    }

    // Returns the item frames kept in the previous tick.
    public static int itemFramesProtected() {
        return shownItemFramesProtected;
    }

    // Skip decision of one entity.
    private static final class Decision {
        // Whether the entity is skipped.
        boolean cull;
        // Consecutive frames the entity stayed far away.
        int farFrames;
        // Frame of the last decision.
        long lastSeenFrame;
    }
}
