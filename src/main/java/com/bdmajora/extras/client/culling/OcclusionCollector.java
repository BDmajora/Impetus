package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.mixin.core.terrain.ChunkProviderClientAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

// Gathers the entities and block entities of the client world for the worker each tick.
public final class OcclusionCollector {
    // Margin added around every entity box.
    static final double ENTITY_MARGIN = 0.25D;
    // Ticks of movement an entity box is stretched ahead.
    static final double MOTION_LOOKAHEAD = 2.0D;
    // Margin added around every block entity box.
    static final double BLOCK_ENTITY_MARGIN = 0.5D;
    // Largest side of a test box in blocks.
    static final double MAX_TEST_EXTENT = 64.0D;
    // Cached box of a block entity that is never culled.
    static final AxisAlignedBB NEVER = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    // Queue the grid changes go to.
    private final OcclusionInbox inbox;
    // Exchange the snapshots go to.
    private final OcclusionSnapshot.Exchange exchange;
    // World the listener is registered on or null.
    private WorldClient listenedWorld;
    // Listener registered on that world or null.
    private OcclusionWorldListener listener;

    // Creates a collector that feeds the inbox and exchange.
    public OcclusionCollector(OcclusionInbox inbox, OcclusionSnapshot.Exchange exchange) {
        this.inbox = inbox;
        this.exchange = exchange;
    }

    // Returns the world the listener follows.
    WorldClient listenedWorld() {
        return this.listenedWorld;
    }

    // Follows the client world and publishes a snapshot of its targets.
    public void tick(Minecraft client, ExtrasConfig.OcclusionSettings settings) {
        WorldClient world = client.world;
        this.follow(world);
        OcclusionSnapshot snapshot = this.exchange.producer();
        snapshot.clear();
        if (world == null) {
            this.exchange.publish();
            return;
        }
        ModelReach.beginTick(client);
        Entity view = client.getRenderViewEntity();
        Entity player = client.player;
        if (settings.entities) {
            List<Entity> entities = world.loadedEntityList;
            for (int i = 0, n = entities.size(); i < n; i++) {
                Entity entity = entities.get(i);
                if (isCandidate(entity, view, player, settings.maxEntitySize)) {
                    addEntity(snapshot, entity);
                }
            }
        }
        if (settings.blockEntities && view != null) {
            List<TileEntity> blockEntities = world.loadedTileEntityList;
            for (int i = 0, n = blockEntities.size(); i < n; i++) {
                addBlockEntity(snapshot, blockEntities.get(i), view, settings.maxBlockEntitySize);
            }
        }
        this.exchange.publish();
    }

    // Registers the listener on a new world and queues the chunks it already holds.
    void follow(WorldClient world) {
        if (world == this.listenedWorld) {
            return;
        }
        if (this.listenedWorld != null) {
            this.listenedWorld.removeEventListener(this.listener);
        }
        this.listenedWorld = world;
        this.listener = null;
        Long2ObjectOpenHashMap<Chunk> loaded = new Long2ObjectOpenHashMap<>();
        if (world != null) {
            this.listener = new OcclusionWorldListener(world, this.inbox);
            world.addEventListener(this.listener);
            if (world.getChunkProvider() instanceof ChunkProviderClientAccessor provider) {
                for (Chunk chunk : provider.impetus$getLoadedChunks().values()) {
                    loaded.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
                }
            }
        }
        this.inbox.reset(loaded);
    }

    // Returns whether the entity may be culled at all.
    static boolean isCandidate(Entity entity, Entity view, Entity player, int maxSize) {
        if (entity == null || entity == view || entity == player || entity.isDead || entity.ignoreFrustumCheck) {
            return false;
        }
        if (!entity.isNonBoss() || entity.isGlowing() || entity.width > maxSize || entity.height > maxSize) {
            return false;
        }
        return player == null || !entity.isRidingOrBeingRiddenBy(player);
    }

    // Adds an entity with its render box grown by its equipment models and its motion.
    static void addEntity(OcclusionSnapshot snapshot, Entity entity) {
        AxisAlignedBB box = entity.getRenderBoundingBox();
        if (box == null || box.hasNaN()) {
            box = entity.getEntityBoundingBox();
        }
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        if (entity instanceof EntityLivingBase living && entity instanceof ModelReachHolder holder) {
            double reach = ModelReach.radius(living, holder);
            if (reach > 0.0D) {
                double pivotY = entity.posY + ModelReach.pivotHeight(living);
                minX = Math.min(minX, entity.posX - reach);
                minY = Math.min(minY, pivotY - reach);
                minZ = Math.min(minZ, entity.posZ - reach);
                maxX = Math.max(maxX, entity.posX + reach);
                maxY = Math.max(maxY, pivotY + reach);
                maxZ = Math.max(maxZ, entity.posZ + reach);
            }
        }
        double moveX = (entity.posX - entity.lastTickPosX) * MOTION_LOOKAHEAD;
        double moveY = (entity.posY - entity.lastTickPosY) * MOTION_LOOKAHEAD;
        double moveZ = (entity.posZ - entity.lastTickPosZ) * MOTION_LOOKAHEAD;
        minX += Math.min(moveX, 0.0D) - ENTITY_MARGIN;
        minY += Math.min(moveY, 0.0D) - ENTITY_MARGIN;
        minZ += Math.min(moveZ, 0.0D) - ENTITY_MARGIN;
        maxX += Math.max(moveX, 0.0D) + ENTITY_MARGIN;
        maxY += Math.max(moveY, 0.0D) + ENTITY_MARGIN;
        maxZ += Math.max(moveZ, 0.0D) + ENTITY_MARGIN;
        if (!(maxX - minX <= MAX_TEST_EXTENT && maxY - minY <= MAX_TEST_EXTENT && maxZ - minZ <= MAX_TEST_EXTENT)) {
            return;
        }
        snapshot.add((Cullable) entity, OcclusionSnapshot.ENTITY, minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Adds a block entity in drawing range with its cached box.
    static void addBlockEntity(OcclusionSnapshot snapshot, TileEntity blockEntity, Entity view, int maxSize) {
        if (blockEntity == null || blockEntity.isInvalid()) {
            return;
        }
        OcclusionBoxHolder holder = (OcclusionBoxHolder) blockEntity;
        AxisAlignedBB box = holder.impetus$occlusionBox();
        if (box == null) {
            box = measure(blockEntity);
            holder.impetus$setOcclusionBox(box);
        }
        if (box == NEVER) {
            return;
        }
        double margin = BLOCK_ENTITY_MARGIN * 2.0D;
        if (box.maxX - box.minX - margin > maxSize || box.maxY - box.minY - margin > maxSize || box.maxZ - box.minZ - margin > maxSize) {
            return;
        }
        if (blockEntity.getDistanceSq(view.posX, view.posY, view.posZ) >= blockEntity.getMaxRenderDistanceSquared()) {
            return;
        }
        snapshot.add((Cullable) blockEntity, OcclusionSnapshot.BLOCK_ENTITY, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // Returns the test box of a block entity or NEVER when it has no renderer or an endless box.
    static AxisAlignedBB measure(TileEntity blockEntity) {
        if (TileEntityRendererDispatcher.instance.getRenderer(blockEntity) == null) {
            return NEVER;
        }
        AxisAlignedBB render = blockEntity.getRenderBoundingBox();
        if (render == null || render == TileEntity.INFINITE_EXTENT_AABB || render.hasNaN()
                || render.maxX - render.minX > MAX_TEST_EXTENT || render.maxY - render.minY > MAX_TEST_EXTENT
                || render.maxZ - render.minZ > MAX_TEST_EXTENT) {
            return NEVER;
        }
        return render.union(new AxisAlignedBB(blockEntity.getPos())).grow(BLOCK_ENTITY_MARGIN);
    }
}
