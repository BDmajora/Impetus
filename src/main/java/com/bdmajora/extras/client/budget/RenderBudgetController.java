package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
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

// Adaptive client render budgeting, a 1.12.2 reimplementation of GpuShift 1.2.8 by orf (MIT): an EMA of frame time against a per-profile target yields a pressure figure, which tightens a particle spawn budget and the distances past which idle mobs, decorative block entities and item frames are skipped; particles are refused evenly by a carry accumulator, mobs by a per-entity hysteresis so nothing flickers at the boundary. Every per-frame path is O(1) with no allocation: the only map is keyed by entity identity and swept on a fixed cadence
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class RenderBudgetController {
    private static final double EMA_ALPHA = 0.12;
    // Frames over budget before an entity is actually dropped, so one hitch cannot pop a mob out
    private static final int CULL_CONFIRMATION_FRAMES = 6;
    private static final long CACHE_MAX_AGE_FRAMES = 1200;
    private static final long CACHE_SWEEP_INTERVAL_FRAMES = 60;
    // Nothing inside this many blocks is ever skipped regardless of the configured distances
    private static final double CLOSE_PROTECTION_DISTANCE = 32.0;
    private static final double CLOSE_PROTECTION_DISTANCE_SQ = CLOSE_PROTECTION_DISTANCE * CLOSE_PROTECTION_DISTANCE;
    private static final double HYSTERESIS_DISTANCE = 12.0;
    private static final double ADAPTIVE_PRESSURE_THRESHOLD = 0.2;
    // Skipping arms once frames run 3% over target and disarms only when back at or under it, so a machine sitting exactly on its frame cap cannot toggle every tick
    private static final double ARM_PRESSURE = 0.03;
    // With a pack running the block-entity line moves out, since shaders make far chests cheap relative to everything else
    private static final int SHADER_BLOCK_ENTITY_BONUS = 32;

    private static long lastFrameNanos;
    private static double emaFrameMillis;
    private static long frameIndex;
    private static boolean armedLastTick;
    private static volatile RenderBudget budget = RenderBudget.NEUTRAL;

    // Last-applied settings, compared each tick so an options change resets the caches instead of bleeding stale decisions through
    private static boolean lastEnabled;
    private static ExtrasConfig.BudgetProfile lastProfile;
    private static boolean lastAdaptive;
    private static int lastParticleBudget;
    private static int lastEntityCullDistance;
    private static boolean lastSmartEntityCulling;

    private static final Map<Entity, Decision> decisions = new IdentityHashMap<>();
    // Concurrent, and the carry below guarded, because addEffect runs on the particle pool too: a firework's starter and a huge explosion spawn their children from their own tick
    private static final Map<Class<?>, ParticleCategory> particleCategories = new ConcurrentHashMap<>();
    private static final Object PARTICLE_CARRY_LOCK = new Object();
    // Boolean.TRUE for the vanilla decorative renderers that may be skipped, FALSE for everything else, so the per-block-entity cost is one identity lookup
    private static final Map<Class<?>, Boolean> budgetableBlockEntities = new HashMap<>();
    private static double particleCarry;

    // Counted over the tick in progress, then copied into the shown fields at tick end so the overlay reads whole ticks
    private static int entitiesSkipped;
    private static int entitiesProtected;
    private static int particlesSkipped;
    private static int particlesProtected;
    private static int blockEntitiesSkipped;
    private static int blockEntitiesProtected;
    private static int itemFramesSkipped;
    private static int itemFramesProtected;
    private static int shownEntitiesSkipped;
    private static int shownEntitiesProtected;
    private static int shownParticlesSkipped;
    private static int shownParticlesProtected;
    private static int shownBlockEntitiesSkipped;
    private static int shownBlockEntitiesProtected;
    private static int shownItemFramesSkipped;
    private static int shownItemFramesProtected;

    private RenderBudgetController() {
    }

    // Feeds the frame-time EMA once per frame and sweeps stale entity decisions on a fixed cadence
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos != 0 && now > lastFrameNanos) {
            double frameMillis = (now - lastFrameNanos) / 1_000_000.0;
            emaFrameMillis = emaFrameMillis <= 0.0
                    ? frameMillis
                    : emaFrameMillis + EMA_ALPHA * (frameMillis - emaFrameMillis);
        }
        lastFrameNanos = now;

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

    // Resolves the budget for the coming tick and rolls the overlay counters
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

    // Any change drops the caches; relaxing the profile also clamps the EMA to the new target, otherwise the old pressure would keep the tighter budget in force for seconds after the user asked for less
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

    // Profile clamps the configured values into its band, pressure past the threshold tightens them one step, and an active shader pack loosens them since the frame time is then mostly the pack's and skipping mobs would buy little
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
        // 100% means the user handed particles to something else (or wants them all); never re-enable it from here
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

        boolean adaptive = settings.adaptive && pressure >= ADAPTIVE_PRESSURE_THRESHOLD;
        if (adaptive) {
            if (!particlesUnlimited) {
                particleScale = Math.max(0.25, particleScale - 0.2);
            }
            entityDistance = Math.max(48, entityDistance - 16);
        }

        boolean shaderPack = Umbra.isShaderPackInUse();
        if (shaderPack) {
            if (!particlesUnlimited) {
                particleScale = Math.min(1.0, particleScale + 0.1);
            }
            entityDistance = Math.max(entityDistance, 64);
        }

        // Performance skips distant things unconditionally; the others only while actually over target, with hysteresis on the arming itself
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
                adaptive);
    }

    // The budget in force for the current tick
    public static RenderBudget budget() {
        return budget;
    }

    // Whether this living entity's model should be skipped this frame; the pure-arithmetic distance gate runs before anything that touches the entity's data manager, so close mobs cost a few multiplies and at most one map lookup
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
            // Not seen last frame: it was off screen or culled elsewhere, so the run of far frames starts over
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

    // Squared distance from the view entity (the player when there is none) to the entity, or -1 when there is no viewer or the entity IS the viewer, which the callers never cull
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

    // Whether shouldCullLivingEntity dropped this entity in the current frame; lets the shadow go with the model rather than hover over empty ground
    public static boolean wasCulledThisFrame(Entity entity) {
        if (decisions.isEmpty()) {
            return false;
        }
        Decision decision = decisions.get(entity);
        return decision != null && decision.cull && decision.lastSeenFrame == frameIndex;
    }

    // Close, mounted, glowing, named, and anything not actually registered in the client world (GUI previews, inventory mannequins, freshly spawned mobs behind a screen) stay drawn; ordered cheapest first, with the data-manager reads (glowing, name) last
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

    // Whether this block entity's special renderer should be skipped this frame: only vanilla's decorative ones (chests, signs, heads, banners, beds, shulker boxes, enchanting tables) past the line, never the one under the crosshair, never anything modded
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

    // Exact vanilla classes only, so a modded chest that extends the vanilla one keeps rendering; the result is cached per class
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

    // Whether this item frame should be skipped this frame; glowing, named and map frames are kept since those are the ones people look at from afar
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

    // Whether this particle should be refused; the carry accumulator keeps exactly the budgeted fraction and spaces the refusals evenly, so a 65% budget never drops three in a row
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

    // Overlay readouts from the last whole tick

    public static int entitiesSkipped() {
        return shownEntitiesSkipped;
    }

    public static int entitiesProtected() {
        return shownEntitiesProtected;
    }

    public static int particlesSkipped() {
        return shownParticlesSkipped;
    }

    public static int particlesProtected() {
        return shownParticlesProtected;
    }

    public static int blockEntitiesSkipped() {
        return shownBlockEntitiesSkipped;
    }

    public static int blockEntitiesProtected() {
        return shownBlockEntitiesProtected;
    }

    public static int itemFramesSkipped() {
        return shownItemFramesSkipped;
    }

    public static int itemFramesProtected() {
        return shownItemFramesProtected;
    }

    // Per-entity hysteresis state, mutated in place since it is touched every frame for every far mob
    private static final class Decision {
        boolean cull;
        int farFrames;
        long lastSeenFrame;
    }
}
