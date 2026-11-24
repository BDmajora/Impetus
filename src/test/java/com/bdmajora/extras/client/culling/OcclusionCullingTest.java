package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.culling.EntityArmorStandPoseAccessor;
import com.bdmajora.extras.mixin.culling.EntityCullableMixin;
import com.bdmajora.extras.mixin.culling.EntityLivingBaseInterpolationAccessor;
import com.bdmajora.extras.mixin.culling.RenderGlobalOcclusionMixin;
import com.bdmajora.extras.mixin.culling.RenderLivingBaseCullMixin;
import com.bdmajora.extras.mixin.culling.RenderManagerCullMixin;
import com.bdmajora.extras.mixin.culling.TileEntityCullableMixin;
import com.bdmajora.extras.mixin.culling.TileEntityRendererDispatcherCullMixin;
import com.bdmajora.extras.mixin.culling.WorldDormantTickMixin;
import com.bdmajora.impetus.mixin.core.terrain.ChunkProviderClientAccessor;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Rotations;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcclusionCullingTest {
    private ExtrasConfig config;
    private Minecraft client;
    private TileEntityRendererDispatcher previousDispatcher;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void setUp() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        // Keeps the real worker from starting.
        Mixins.set(OcclusionCullingThread.class, "instance", Mixins.construct(OcclusionCullingThread.class));
        Mixins.set(OcclusionCulling.class, "camera", null);
        OcclusionCulling.INBOX.drain();
        OcclusionCulling.EXCHANGE.take(null);
        previousDispatcher = TileEntityRendererDispatcher.instance;
    }

    @AfterEach
    void tearDown() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        Statics.set(TileEntityRendererDispatcher.class, "instance", previousDispatcher);
        OcclusionCulling.COLLECTOR.follow(null);
        OcclusionCulling.INBOX.drain();
    }

    // Returns an armor stand mock that keeps a real occlusion state.
    private static EntityArmorStand stand(double x, double y, double z) {
        EntityArmorStand stand = Mc.backed(EntityArmorStand.class, Mixins.instance(EntityCullableMixin.class));
        stand.posX = x;
        stand.posY = y;
        stand.posZ = z;
        stand.lastTickPosX = x;
        stand.lastTickPosY = y;
        stand.lastTickPosZ = z;
        stand.width = 0.5F;
        stand.height = 1.975F;
        when(stand.isNonBoss()).thenReturn(true);
        when(stand.getEntityBoundingBox()).thenReturn(new AxisAlignedBB(x - 0.25, y, z - 0.25, x + 0.25, y + 1.975, z + 0.25));
        when(stand.getItemStackFromSlot(any())).thenReturn(ItemStack.EMPTY);
        return stand;
    }

    // Returns a chest mock that keeps a real occlusion state.
    private static TileEntityChest chest(BlockPos pos, double distanceSq) {
        TileEntityChest chest = Mc.backed(TileEntityChest.class, Mixins.instance(TileEntityCullableMixin.class));
        when(chest.getPos()).thenReturn(pos);
        when(chest.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(pos));
        when(chest.getDistanceSq(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble())).thenReturn(distanceSq);
        when(chest.getMaxRenderDistanceSquared()).thenReturn(4096.0D);
        return chest;
    }

    // Returns a quad of four item vertices at the given positions.
    private static BakedQuad quad(VertexFormat format, float[]... corners) {
        int stride = format.getIntegerSize();
        int[] data = new int[stride * corners.length];
        for (int i = 0; i < corners.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                data[i * stride + axis] = Float.floatToRawIntBits(corners[i][axis]);
            }
        }
        return new BakedQuad(data, -1, EnumFacing.UP, null, false, format);
    }

    // Returns a uniform scale matrix with a translation.
    private static Matrix4f scale(float factor, float tx, float ty, float tz) {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.m00 = factor;
        matrix.m11 = factor;
        matrix.m22 = factor;
        matrix.m03 = tx;
        matrix.m13 = ty;
        matrix.m23 = tz;
        return matrix;
    }

    // Installs a render item that answers the given model for every stack.
    private RenderItem renderItem(IBakedModel model) {
        RenderItem items = mock(RenderItem.class);
        ItemModelMesher mesher = mock(ItemModelMesher.class);
        ModelManager manager = mock(ModelManager.class);
        when(items.getItemModelMesher()).thenReturn(mesher);
        when(mesher.getModelManager()).thenReturn(manager);
        when(manager.getMissingModel()).thenReturn(mock(IBakedModel.class));
        when(items.getItemModelWithOverrides(any(), any(), any())).thenReturn(model);
        when(client.getRenderItem()).thenReturn(items);
        return items;
    }

    // Returns a model whose quads reach from minus one to two on the east axis.
    private static IBakedModel wideModel(Matrix4f head) {
        IBakedModel model = mock(IBakedModel.class);
        BakedQuad quad = quad(DefaultVertexFormats.ITEM, new float[] {-1, 0.5F, 0.5F}, new float[] {2, 0.5F, 0.5F},
                new float[] {0.5F, 0.5F, 0.5F}, new float[] {0.5F, 0.5F, 0.5F});
        when(model.getQuads(null, null, 0L)).thenReturn(List.of(quad));
        when(model.handlePerspective(any())).thenAnswer(invocation -> Pair.of(model,
                invocation.getArgument(0) == ItemCameraTransforms.TransformType.HEAD ? head : null));
        return model;
    }

    @Test
    void entitiesAndBlockEntitiesCarryTheirOcclusionState() {
        EntityCullableMixin entity = Mixins.instance(EntityCullableMixin.class);
        entity.impetus$setOccluded(true, 42L);
        entity.impetus$setHiddenSince(7L);
        ModelReachState state = new ModelReachState();
        entity.impetus$setModelReach(state);
        assertTrue(entity.impetus$isOccluded());
        assertEquals(42L, entity.impetus$occlusionStamp());
        assertEquals(7L, entity.impetus$hiddenSince());
        assertSame(state, entity.impetus$modelReach());
        TileEntityCullableMixin blockEntity = Mixins.instance(TileEntityCullableMixin.class);
        blockEntity.impetus$setOccluded(true, 43L);
        blockEntity.impetus$setHiddenSince(8L);
        blockEntity.impetus$setOcclusionBox(OcclusionCollector.NEVER);
        assertTrue(blockEntity.impetus$isOccluded());
        assertEquals(43L, blockEntity.impetus$occlusionStamp());
        assertEquals(8L, blockEntity.impetus$hiddenSince());
        assertSame(OcclusionCollector.NEVER, blockEntity.impetus$occlusionBox());
    }

    @Test
    void hiddenEntitiesSkipDrawingOrKeepOnlyTheirName() {
        EntityArmorStand stand = stand(0, 64, 0);
        Cullable state = (Cullable) stand;
        assertFalse(OcclusionCulling.shouldSkipEntity(stand));
        state.impetus$setOccluded(true, System.nanoTime());
        assertTrue(OcclusionCulling.isHidden(stand));
        assertTrue(OcclusionCulling.shouldSkipEntity(stand));
        assertFalse(OcclusionCulling.isLabelOnly(stand));
        // An always shown name stays drawn through walls.
        when(stand.getAlwaysRenderNameTagForRender()).thenReturn(true);
        assertFalse(OcclusionCulling.shouldSkipEntity(stand));
        assertTrue(OcclusionCulling.isLabelOnly(stand));
        config.occlusion.nameTagsThroughWalls = false;
        assertTrue(OcclusionCulling.shouldSkipEntity(stand));
        assertFalse(OcclusionCulling.isLabelOnly(stand));
        config.occlusion.nameTagsThroughWalls = true;
        // A named item has no name only path so it is skipped whole.
        EntityItem item = Mc.backed(EntityItem.class, Mixins.instance(EntityCullableMixin.class));
        ((Cullable) item).impetus$setOccluded(true, System.nanoTime());
        when(item.getAlwaysRenderNameTagForRender()).thenReturn(true);
        assertTrue(OcclusionCulling.shouldSkipEntity(item));
        // The shadow pass draws everything.
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertFalse(OcclusionCulling.shouldSkipEntity(item));
        assertFalse(OcclusionCulling.isLabelOnly(stand));
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        // Glowing and unculled entities and stale verdicts are drawn.
        when(item.isGlowing()).thenReturn(true);
        assertFalse(OcclusionCulling.isHidden(item));
        when(item.isGlowing()).thenReturn(false);
        item.ignoreFrustumCheck = true;
        assertFalse(OcclusionCulling.isHidden(item));
        item.ignoreFrustumCheck = false;
        ((Cullable) item).impetus$setOccluded(true, System.nanoTime() - 5_000_000_000L);
        assertFalse(OcclusionCulling.isHidden(item));
        config.occlusion.entities = false;
        assertFalse(OcclusionCulling.isHidden(stand));
        config.occlusion.entities = true;
        config.occlusion.enabled = false;
        assertFalse(OcclusionCulling.isHidden(stand));
        config.occlusion.enabled = true;

        // The render manager hook skips only whole hidden entities.
        when(stand.getAlwaysRenderNameTagForRender()).thenReturn(false);
        RenderManagerCullMixin renderManager = Mixins.instance(RenderManagerCullMixin.class);
        CallbackInfoReturnable<Boolean> skipped = Mixins.cir();
        Mixins.call(renderManager, "impetus$skipOccluded", stand, null, 0.0D, 0.0D, 0.0D, skipped);
        assertEquals(false, skipped.getReturnValue());
        CallbackInfoReturnable<Boolean> drawn = Mixins.cir();
        Mixins.call(renderManager, "impetus$skipOccluded", stand(0, 64, 0), null, 0.0D, 0.0D, 0.0D, drawn);
        assertFalse(drawn.isCancelled());

        // The living renderer hook draws only the name of a hidden named entity.
        Mc.textures();
        try (MockedStatic<org.lwjgl.opengl.GL11> gl = Mockito.mockStatic(org.lwjgl.opengl.GL11.class)) {
            Class.forName("net.minecraft.client.renderer.entity.RenderLivingBase");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        RenderLivingBaseCullMixin<?> living = Mixins.instance(RenderLivingBaseCullMixin.class, mock(RenderManager.class));
        AtomicInteger names = new AtomicInteger();
        @SuppressWarnings("unchecked")
        RenderLivingBase<EntityLivingBase> asRenderer = (RenderLivingBase<EntityLivingBase>) (Object) living;
        Mockito.doAnswer(invocation -> {
            names.incrementAndGet();
            return null;
        }).when(asRenderer).renderName(any(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
        CallbackInfo whole = Mixins.ci();
        Mixins.call(living, "impetus$drawOnlyName", stand, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, whole);
        assertFalse(whole.isCancelled());
        when(stand.getAlwaysRenderNameTagForRender()).thenReturn(true);
        CallbackInfo nameOnly = Mixins.ci();
        Mixins.call(living, "impetus$drawOnlyName", stand, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, nameOnly);
        assertTrue(nameOnly.isCancelled());
        assertEquals(1, names.get());
        Mixins.set(living, "renderOutlines", true);
        CallbackInfo outline = Mixins.ci();
        Mixins.call(living, "impetus$drawOnlyName", stand, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, outline);
        assertTrue(outline.isCancelled());
        assertEquals(1, names.get());

        // Block entities are skipped only on fresh verdicts outside the shadow pass.
        TileEntityChest chest = chest(BlockPos.ORIGIN, 0.0D);
        TileEntityRendererDispatcherCullMixin dispatcher = Mixins.instance(TileEntityRendererDispatcherCullMixin.class);
        CallbackInfo visible = Mixins.ci();
        Mixins.call(dispatcher, "impetus$skipOccluded", chest, 0.0F, -1, visible);
        assertFalse(visible.isCancelled());
        ((Cullable) chest).impetus$setOccluded(true, System.nanoTime());
        CallbackInfo hidden = Mixins.ci();
        Mixins.call(dispatcher, "impetus$skipOccluded", chest, 0.0F, -1, hidden);
        assertTrue(hidden.isCancelled());
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertFalse(OcclusionCulling.shouldSkipBlockEntity(chest));
        Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        config.occlusion.blockEntities = false;
        assertFalse(OcclusionCulling.shouldSkipBlockEntity(chest));
        config.occlusion.blockEntities = true;
        config.occlusion.enabled = false;
        assertFalse(OcclusionCulling.shouldSkipBlockEntity(chest));
        assertNotNull(Mixins.construct(OcclusionCulling.class));
    }

    @Test
    void theCameraFollowsTheRenderInfoUnlessItStraysFromTheEye() {
        Entity view = mock(Entity.class);
        view.posX = 10;
        view.posY = 64;
        view.posZ = 10;
        view.lastTickPosX = 8;
        view.lastTickPosY = 64;
        view.lastTickPosZ = 10;
        when(view.getEyeHeight()).thenReturn(1.5F);
        try (MockedStatic<ActiveRenderInfo> info = Mockito.mockStatic(ActiveRenderInfo.class)) {
            info.when(() -> ActiveRenderInfo.projectViewFromEntity(view, 0.5D)).thenReturn(new Vec3d(5, 66, 10));
            OcclusionCulling.captureCamera(view, 0.5F);
            assertEquals(new OcclusionCamera(5, 66, 10), OcclusionCulling.camera);
            // A report far from the eye falls back to the eye.
            info.when(() -> ActiveRenderInfo.projectViewFromEntity(view, 0.5D)).thenReturn(new Vec3d(500, 66, 10));
            OcclusionCulling.captureCamera(view, 0.5F);
            assertEquals(new OcclusionCamera(9, 65.5, 10), OcclusionCulling.camera);
            info.when(() -> ActiveRenderInfo.projectViewFromEntity(view, 0.5D)).thenReturn(null);
            Mixins.set(OcclusionCulling.class, "camera", null);
            RenderGlobalOcclusionMixin hook = Mixins.instance(RenderGlobalOcclusionMixin.class);
            Mixins.call(hook, "impetus$captureCamera", view, null, 0.5F, Mixins.ci());
            assertEquals(new OcclusionCamera(9, 65.5, 10), OcclusionCulling.camera);
            // Shadow passes and later render passes keep the camera.
            Mixins.set(OcclusionCulling.class, "camera", null);
            Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", true);
            OcclusionCulling.captureCamera(view, 0.5F);
            Mixins.set(UmbraShadowRenderer.class, "shadowPassActive", false);
            Statics.set(net.minecraftforge.client.ForgeHooksClient.class, "renderPass", 1);
            OcclusionCulling.captureCamera(view, 0.5F);
            Statics.set(net.minecraftforge.client.ForgeHooksClient.class, "renderPass", -1);
            OcclusionCulling.captureCamera(null, 0.5F);
            assertNull(OcclusionCulling.camera);
        }
    }

    @Test
    void theWorldListenerQueuesOnlyOpacityChangesAndWholeChunks() {
        WorldClient world = mock(WorldClient.class);
        ChunkProviderClient provider = mock(ChunkProviderClient.class);
        Chunk chunk = mock(Chunk.class);
        when(provider.getLoadedChunk(2, 3)).thenReturn(chunk);
        when(world.getChunkProvider()).thenReturn(provider);
        OcclusionInbox inbox = new OcclusionInbox();
        OcclusionWorldListener listener = new OcclusionWorldListener(world, inbox);
        IBlockState stone = Blocks.STONE.getDefaultState();
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState glass = Blocks.GLASS.getDefaultState();
        listener.notifyBlockUpdate(world, new BlockPos(1, 2, 3), stone, stone, 3);
        listener.notifyBlockUpdate(world, new BlockPos(1, 2, 3), glass, air, 3);
        assertNull(inbox.drain());
        listener.notifyBlockUpdate(world, new BlockPos(17, 40, -3), stone, air, 3);
        listener.notifyBlockUpdate(world, new BlockPos(0, 0, 0), null, air, 3);
        OcclusionInbox.Batch changes = inbox.drain();
        assertEquals(1 << 2, changes.sections().get(ChunkPos.asLong(1, -1)));
        assertEquals(1, changes.sections().get(ChunkPos.asLong(0, 0)));
        // Small ranges are light or render updates and change no opacity.
        listener.markBlockRangeForRenderUpdate(0, 0, 0, 3, 3, 3);
        listener.markBlockRangeForRenderUpdate(-500, 0, -500, 500, 255, 500);
        assertNull(inbox.drain());
        listener.markBlockRangeForRenderUpdate(32, 0, 48, 47, 256, 63);
        OcclusionInbox.Batch load = inbox.drain();
        assertSame(chunk, load.chunks().get(ChunkPos.asLong(2, 3)));
        listener.markBlockRangeForRenderUpdate(0, 0, 0, 15, 256, 15);
        assertTrue(inbox.drain().chunks().containsKey(ChunkPos.asLong(0, 0)));
        // The other events change nothing.
        listener.notifyLightSet(BlockPos.ORIGIN);
        listener.playSoundToAllNearExcept(null, null, null, 0, 0, 0, 1, 1);
        listener.playRecord(null, BlockPos.ORIGIN);
        listener.spawnParticle(0, false, 0, 0, 0, 0, 0, 0);
        listener.spawnParticle(0, false, false, 0, 0, 0, 0, 0, 0);
        listener.onEntityAdded(null);
        listener.onEntityRemoved(null);
        listener.broadcastSound(0, BlockPos.ORIGIN, 0);
        listener.playEvent(null, 0, BlockPos.ORIGIN, 0);
        listener.sendBlockBreakProgress(0, BlockPos.ORIGIN, 0);
        assertNull(inbox.drain());
    }

    @Test
    void theCollectorSnapshotsCandidatesWithTheirModelReachAndMotion() {
        WorldClient world = mock(WorldClient.class);
        ChunkProviderClient provider = Mc.mock(ChunkProviderClient.class, ChunkProviderClientAccessor.class);
        Chunk loaded = mock(Chunk.class);
        Mixins.set(loaded, "x", 4);
        Mixins.set(loaded, "z", -2);
        Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
        chunks.put(ChunkPos.asLong(4, -2), loaded);
        when(((ChunkProviderClientAccessor) provider).impetus$getLoadedChunks()).thenReturn(chunks);
        when(world.getChunkProvider()).thenReturn(provider);
        List<Entity> entities = new ArrayList<>();
        List<TileEntity> blockEntities = new ArrayList<>();
        Mixins.set(world, "loadedEntityList", entities);
        Mixins.set(world, "loadedTileEntityList", blockEntities);
        Mixins.set(client, "world", world);
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        Mixins.set(client, "player", player);
        EntityArmorStand view = stand(0, 64, 0);
        when(client.getRenderViewEntity()).thenReturn(view);
        IBakedModel model = wideModel(scale(2, 0, 0, 0));
        renderItem(model);

        EntityArmorStand plain = stand(10, 64, 0);
        EntityArmorStand modelled = stand(20, 64, 0);
        when(modelled.getItemStackFromSlot(EntityEquipmentSlot.HEAD)).thenReturn(new ItemStack(Items.DIAMOND_HOE, 1, 5));
        EntityArmorStand moving = stand(30, 64, 0);
        moving.lastTickPosX = 29;
        EntityArmorStand odd = stand(40, 64, 0);
        when(odd.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(Double.NaN, 0, 0, 0, 0, 0));
        EntityArmorStand endless = stand(50, 64, 0);
        when(endless.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(0, 0, 0, 500, 1, 1));
        EntityArmorStand ridden = stand(60, 64, 0);
        when(ridden.isRidingOrBeingRiddenBy(player)).thenReturn(true);
        EntityArmorStand glowing = stand(70, 64, 0);
        when(glowing.isGlowing()).thenReturn(true);
        EntityArmorStand boss = stand(80, 64, 0);
        when(boss.isNonBoss()).thenReturn(false);
        EntityArmorStand huge = stand(90, 64, 0);
        huge.height = 40.0F;
        EntityArmorStand tall = stand(95, 64, 0);
        tall.width = 40.0F;
        EntityArmorStand dead = stand(100, 64, 0);
        dead.isDead = true;
        EntityArmorStand unculled = stand(110, 64, 0);
        unculled.ignoreFrustumCheck = true;
        entities.addAll(List.of(plain, modelled, moving, odd, endless, ridden, glowing, boss, huge, tall, dead, unculled, view));
        entities.add(null);
        entities.add(player);

        TileEntityRendererDispatcher dispatcher = mock(TileEntityRendererDispatcher.class);
        Statics.set(TileEntityRendererDispatcher.class, "instance", dispatcher);
        TileEntityChest near = chest(new BlockPos(5, 64, 0), 25.0D);
        TileEntityChest far = chest(new BlockPos(500, 64, 0), 250_000.0D);
        TileEntityChest bare = chest(new BlockPos(6, 64, 0), 36.0D);
        TileEntityChest infinite = chest(new BlockPos(7, 64, 0), 49.0D);
        when(infinite.getRenderBoundingBox()).thenReturn(TileEntity.INFINITE_EXTENT_AABB);
        TileEntityChest wide = chest(new BlockPos(8, 64, 0), 64.0D);
        when(wide.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(8, 64, 0, 20, 65, 1));
        TileEntityChest invalid = chest(new BlockPos(9, 64, 0), 81.0D);
        when(invalid.isInvalid()).thenReturn(true);
        TileEntitySpecialRenderer<TileEntity> renderer = mock(TileEntitySpecialRenderer.class);
        for (TileEntity drawn : List.of(near, far, infinite, wide, invalid)) {
            when(dispatcher.getRenderer(drawn)).thenReturn(renderer);
        }
        blockEntities.addAll(List.of(near, far, bare, infinite, wide, invalid));
        blockEntities.add(null);

        config.occlusion.enabled = false;
        OcclusionCulling.tick(client);
        assertNull(OcclusionCulling.EXCHANGE.take(null));
        config.occlusion.enabled = true;
        config.occlusion.maxBlockEntitySize = 32;
        OcclusionCulling.tick(client);
        OcclusionSnapshot snapshot = OcclusionCulling.EXCHANGE.take(null);
        assertNotNull(snapshot);
        List<Cullable> targets = new ArrayList<>();
        for (int i = 0; i < snapshot.count(); i++) {
            targets.add(snapshot.target(i));
        }
        assertEquals(List.of(plain, modelled, moving, odd, near, wide), targets);
        // A plain stand gets its box and margin.
        assertEquals(9.75 - OcclusionCollector.ENTITY_MARGIN, snapshot.bound(0, 0), 1.0E-9);
        assertEquals(64 + 1.975 + OcclusionCollector.ENTITY_MARGIN, snapshot.bound(0, 4), 1.0E-9);
        // A stand wearing a model grows by the model's reach around its head.
        double reach = ModelReach.HEAD_OFFSET + ModelReach.HEAD_SCALE * 3.0D;
        assertEquals(20 - reach - OcclusionCollector.ENTITY_MARGIN, snapshot.bound(1, 0), 1.0E-5);
        assertEquals(64 + 1.5 + reach + OcclusionCollector.ENTITY_MARGIN, snapshot.bound(1, 4), 1.0E-5);
        // A moving stand stretches ahead of its motion.
        assertEquals(30.25 + OcclusionCollector.MOTION_LOOKAHEAD + OcclusionCollector.ENTITY_MARGIN, snapshot.bound(2, 3), 1.0E-9);
        assertEquals(29.75 - OcclusionCollector.ENTITY_MARGIN, snapshot.bound(2, 0), 1.0E-9);
        // A broken render box falls back to the collision box.
        assertEquals(39.75 - OcclusionCollector.ENTITY_MARGIN, snapshot.bound(3, 0), 1.0E-9);
        // The near chest is grown by its margin and kept.
        assertEquals(OcclusionSnapshot.BLOCK_ENTITY, snapshot.kind(4));
        assertEquals(4.5, snapshot.bound(4, 0), 1.0E-9);
        assertSame(OcclusionCollector.NEVER, ((OcclusionBoxHolder) bare).impetus$occlusionBox());
        assertSame(OcclusionCollector.NEVER, ((OcclusionBoxHolder) infinite).impetus$occlusionBox());
        // The wide chest is dropped once the size limit shrinks.
        config.occlusion.maxBlockEntitySize = 4;
        OcclusionCulling.tick(client);
        assertEquals(5, OcclusionCulling.EXCHANGE.take(null).count());
        when(dispatcher.getRenderer(near)).thenReturn(null);
        OcclusionCulling.tick(client);
        assertEquals(5, OcclusionCulling.EXCHANGE.take(null).count());

        // The listener follows the world and queues the chunks it already holds.
        verify(world, times(1)).addEventListener(any(OcclusionWorldListener.class));
        assertSame(world, OcclusionCulling.COLLECTOR.listenedWorld());
        OcclusionInbox.Batch reset = OcclusionCulling.INBOX.drain();
        assertSame(loaded, reset.reset().get(ChunkPos.asLong(4, -2)));
        // Switches off the gathering of each kind.
        config.occlusion.entities = false;
        config.occlusion.blockEntities = false;
        OcclusionCulling.tick(client);
        assertEquals(0, OcclusionCulling.EXCHANGE.take(null).count());
        when(client.getRenderViewEntity()).thenReturn(null);
        config.occlusion.blockEntities = true;
        OcclusionCulling.tick(client);
        assertEquals(0, OcclusionCulling.EXCHANGE.take(null).count());
        // Leaving the world drops the listener and empties the grid.
        Mixins.set(client, "world", null);
        OcclusionCulling.tick(client);
        verify(world).removeEventListener(any(OcclusionWorldListener.class));
        assertTrue(OcclusionCulling.INBOX.drain().reset().isEmpty());
        assertNull(OcclusionCulling.COLLECTOR.listenedWorld());
        // A world without the accessor still gets the listener.
        WorldClient plainWorld = mock(WorldClient.class);
        when(plainWorld.getChunkProvider()).thenReturn(mock(ChunkProviderClient.class));
        OcclusionCulling.COLLECTOR.follow(plainWorld);
        verify(plainWorld).addEventListener(any(OcclusionWorldListener.class));
        assertTrue(OcclusionCulling.INBOX.drain().reset().isEmpty());
    }

    @Test
    void modelReachMeasuresEachModelOnceAndFollowsEquipmentChanges() {
        assertEquals(0.0F, ModelReach.headRadius(null, ItemStack.EMPTY));
        assertEquals(0.0F, ModelReach.handRadius(null, ItemStack.EMPTY));
        assertEquals(ModelReach.WORN_RADIUS, ModelReach.headRadius(null, new ItemStack(Items.SKULL)));
        assertEquals(ModelReach.WORN_RADIUS, ModelReach.headRadius(null, new ItemStack(Items.DIAMOND_HELMET)));
        // Item models are measured through their transform.
        IBakedModel model = wideModel(scale(2, 0, 0, 0));
        RenderItem items = renderItem(model);
        ModelReach.beginTick(client);
        EntityArmorStand stand = stand(0, 64, 0);
        assertEquals(ModelReach.HEAD_OFFSET + ModelReach.HEAD_SCALE * 3.0F, ModelReach.headRadius(stand, new ItemStack(Items.DIAMOND_CHESTPLATE)), 1.0E-5);
        assertEquals(ModelReach.HAND_OFFSET + 1.5F, ModelReach.handRadius(stand, new ItemStack(Items.STICK)), 1.0E-5);
        assertEquals(1.5D, ModelReach.pivotHeight(stand));
        when(stand.isChild()).thenReturn(true);
        assertEquals(0.75D, ModelReach.pivotHeight(stand));
        EntityPig pig = mock(EntityPig.class);
        pig.height = 1.0F;
        assertEquals(0.85D, ModelReach.pivotHeight(pig), 1.0E-9);
        when(stand.isChild()).thenReturn(false);

        // Nothing worn leaves no state behind.
        ModelReachHolder holder = (ModelReachHolder) stand;
        assertEquals(0.0F, ModelReach.radius(stand, holder));
        assertNull(holder.impetus$modelReach());
        ItemStack hoe = new ItemStack(Items.DIAMOND_HOE, 1, 3);
        when(stand.getItemStackFromSlot(EntityEquipmentSlot.HEAD)).thenReturn(hoe);
        float full = ModelReach.radius(stand, holder);
        assertEquals(ModelReach.HEAD_OFFSET + ModelReach.HEAD_SCALE * 3.0F, full, 1.0E-5);
        assertNotNull(holder.impetus$modelReach());
        ModelReach.radius(stand, holder);
        verify(items, times(3)).getItemModelWithOverrides(any(), any(), any());
        // A changed damage or held item or model reload measures again.
        hoe.setItemDamage(4);
        ModelReach.radius(stand, holder);
        verify(items, times(4)).getItemModelWithOverrides(any(), any(), any());
        when(stand.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND)).thenReturn(new ItemStack(Items.STICK));
        assertEquals(ModelReach.HAND_OFFSET + 1.5F, ModelReach.radius(stand, holder), 1.0E-5);
        when(items.getItemModelMesher().getModelManager().getMissingModel()).thenReturn(mock(IBakedModel.class));
        ModelReach.beginTick(client);
        ModelReach.radius(stand, holder);
        verify(items, times(7)).getItemModelWithOverrides(any(), any(), any());
        // Small stands draw their items smaller.
        when(stand.isChild()).thenReturn(true);
        assertEquals(Math.max((ModelReach.HEAD_OFFSET + ModelReach.HEAD_SCALE * 3.0F) * 0.7F, (ModelReach.HAND_OFFSET + 1.5F) * 0.5F),
                ModelReach.radius(stand, holder), 1.0E-5);
        // A failing model lookup falls back to a wide reach.
        when(items.getItemModelWithOverrides(any(), any(), any())).thenThrow(new IllegalStateException());
        assertEquals(ModelReach.HEAD_OFFSET + ModelReach.HEAD_SCALE * ModelReach.FALLBACK_RADIUS,
                ModelReach.headRadius(stand, new ItemStack(Items.IRON_HOE)), 1.0E-5);
        when(client.getRenderItem()).thenReturn(null);
        ModelReach.beginTick(client);
        assertNotNull(Mixins.construct(ModelReach.class));
    }

    @Test
    void modelExtentsCoverEveryTransformAndVertexLayout() {
        // A missing perspective answer reads as no transform.
        IBakedModel plain = mock(IBakedModel.class);
        when(plain.getQuads(null, EnumFacing.UP, 0L)).thenReturn(List.of(quad(DefaultVertexFormats.BLOCK,
                new float[] {0, 0, 0}, new float[] {1, 1, 1})));
        assertEquals(Math.sqrt(0.75D), ModelReach.extent(plain, ItemCameraTransforms.TransformType.HEAD), 1.0E-5);
        // A built in renderer counts as a transformed unit cube.
        IBakedModel builtIn = mock(IBakedModel.class);
        when(builtIn.isBuiltInRenderer()).thenReturn(true);
        when(builtIn.handlePerspective(any())).thenReturn(Pair.of(null, scale(1, 3, 0, 0)));
        assertEquals(Math.sqrt(3.5 * 3.5 + 0.5), ModelReach.extent(builtIn, ItemCameraTransforms.TransformType.HEAD), 1.0E-5);
        // A model that fails or has no usable position falls back.
        IBakedModel broken = mock(IBakedModel.class);
        when(broken.handlePerspective(any())).thenThrow(new IllegalStateException());
        assertEquals(ModelReach.FALLBACK_RADIUS, ModelReach.extent(broken, ItemCameraTransforms.TransformType.HEAD));
        VertexFormat colourOnly = new VertexFormat().addElement(DefaultVertexFormats.COLOR_4UB);
        IBakedModel colourModel = mock(IBakedModel.class);
        when(colourModel.getQuads(null, null, 0L)).thenReturn(List.of(new BakedQuad(new int[] {0}, -1, EnumFacing.UP, null, false, colourOnly)));
        assertEquals(ModelReach.FALLBACK_RADIUS, ModelReach.extent(colourModel, ItemCameraTransforms.TransformType.HEAD));
        assertEquals(-1, ModelReach.positionOffset(colourOnly));
        VertexFormat shortPosition = new VertexFormat().addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.SHORT,
                VertexFormatElement.EnumUsage.POSITION, 3));
        assertEquals(-1, ModelReach.positionOffset(shortPosition));
        VertexFormat late = new VertexFormat().addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.POSITION_3F);
        assertEquals(1, ModelReach.positionOffset(late));
        assertEquals(0, ModelReach.positionOffset(DefaultVertexFormats.ITEM));
        assertEquals(5.0D, ModelReach.distance(scale(1, 3, 4, 0), 0.5, 0.5, 0.5), 1.0E-9);
        assertEquals(0.5D, ModelReach.distance(null, 1.0, 0.5, 0.5), 1.0E-9);
    }

    @Test
    void hiddenArmorStandsTickLightlyAndKeepFollowingTheServer() {
        EntityArmorStand stand = mock(EntityArmorStand.class, Mockito.withSettings().extraInterfaces(EntityArmorStandPoseAccessor.class,
                EntityLivingBaseInterpolationAccessor.class, Cullable.class, ModelReachHolder.class));
        EntityArmorStandPoseAccessor pose = (EntityArmorStandPoseAccessor) stand;
        EntityLivingBaseInterpolationAccessor motion = (EntityLivingBaseInterpolationAccessor) stand;
        Cullable cullable = (Cullable) stand;
        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "isRemote", true);
        stand.world = world;
        assertFalse(DormantTicks.shouldPause(stand));
        when(cullable.impetus$isOccluded()).thenReturn(true);
        when(cullable.impetus$occlusionStamp()).thenReturn(System.nanoTime());
        assertTrue(DormantTicks.shouldPause(stand));
        when(stand.isRiding()).thenReturn(true);
        assertFalse(DormantTicks.shouldPause(stand));
        when(stand.isRiding()).thenReturn(false);
        when(stand.isBeingRidden()).thenReturn(true);
        assertFalse(DormantTicks.shouldPause(stand));
        when(stand.isBeingRidden()).thenReturn(false);
        config.occlusion.pauseHidden = false;
        assertFalse(DormantTicks.shouldPause(stand));
        config.occlusion.pauseHidden = true;
        Mixins.set(world, "isRemote", false);
        assertFalse(DormantTicks.shouldPause(stand));
        stand.world = null;
        assertFalse(DormantTicks.shouldPause(stand));
        assertFalse(DormantTicks.shouldPause(mock(EntityPig.class)));

        // The light tick steps toward the server position and keeps the poses current.
        stand.posX = 0;
        stand.posY = 64;
        stand.posZ = 0;
        stand.rotationYaw = 350;
        stand.prevRotationYaw = 350;
        stand.rotationPitch = 0;
        stand.renderYawOffset = 340;
        stand.rotationYawHead = 30;
        stand.prevRotationYawHead = 250;
        when(motion.impetus$getInterpolationSteps()).thenReturn(2);
        when(motion.impetus$getInterpolationX()).thenReturn(10.0D);
        when(motion.impetus$getInterpolationY()).thenReturn(64.0D);
        when(motion.impetus$getInterpolationZ()).thenReturn(-4.0D);
        when(motion.impetus$getInterpolationYaw()).thenReturn(10.0D);
        when(motion.impetus$getInterpolationPitch()).thenReturn(20.0D);
        EntityDataManager data = mock(EntityDataManager.class);
        when(stand.getDataManager()).thenReturn(data);
        Rotations same = new Rotations(1, 2, 3);
        Rotations moved = new Rotations(4, 5, 6);
        when(data.get(EntityArmorStand.HEAD_ROTATION)).thenReturn(same);
        when(data.get(EntityArmorStand.BODY_ROTATION)).thenReturn(moved);
        when(data.get(EntityArmorStand.LEFT_ARM_ROTATION)).thenReturn(moved);
        when(data.get(EntityArmorStand.RIGHT_ARM_ROTATION)).thenReturn(moved);
        when(data.get(EntityArmorStand.LEFT_LEG_ROTATION)).thenReturn(moved);
        when(data.get(EntityArmorStand.RIGHT_LEG_ROTATION)).thenReturn(moved);
        when(pose.impetus$getHeadPose()).thenReturn(same);
        when(pose.impetus$getBodyPose()).thenReturn(same);
        when(pose.impetus$getLeftArmPose()).thenReturn(same);
        when(pose.impetus$getRightArmPose()).thenReturn(same);
        when(pose.impetus$getLeftLegPose()).thenReturn(same);
        when(pose.impetus$getRightLegPose()).thenReturn(same);
        when(stand.hasMarker()).thenReturn(true);
        DormantTicks.endTick();
        DormantTicks.tick(stand);
        verify(stand).setPosition(5.0D, 64.0D, -2.0D);
        verify(motion).impetus$setInterpolationSteps(1);
        assertEquals(0.0F, stand.rotationYaw, 1.0E-4F);
        assertEquals(10.0F, stand.rotationPitch, 1.0E-4F);
        assertEquals(0.0F, stand.renderYawOffset, 1.0E-4F);
        assertEquals(-10.0F, stand.prevRotationYaw, 1.0E-4F);
        assertEquals(-10.0F, stand.prevRenderYawOffset, 1.0E-4F);
        assertEquals(30.0F, stand.prevRotationYawHead, 1.0E-4F);
        assertEquals(0.0D, stand.prevPosX);
        verify(stand, never()).setHeadRotation(any());
        verify(stand).setBodyRotation(moved);
        verify(stand).setLeftArmRotation(moved);
        verify(stand).setRightArmRotation(moved);
        verify(stand).setLeftLegRotation(moved);
        verify(stand).setRightLegRotation(moved);
        verify(pose).impetus$updateMarkerBox(true);
        verify(pose).impetus$setWasMarker(true);
        assertFalse(stand.preventEntitySpawning);
        // A finished stepping or a steered stand stays put.
        when(pose.impetus$getWasMarker()).thenReturn(true);
        when(motion.impetus$getInterpolationSteps()).thenReturn(0);
        DormantTicks.tick(stand);
        when(motion.impetus$getInterpolationSteps()).thenReturn(3);
        when(stand.canPassengerSteer()).thenReturn(true);
        DormantTicks.tick(stand);
        verify(stand, times(1)).setPosition(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
        verify(pose, times(1)).impetus$updateMarkerBox(true);
        DormantTicks.endTick();
        assertEquals(3, DormantTicks.lastPaused());
        assertEquals(10.0F, DormantTicks.wrapPrevious(20.0F, 10.0F));
        assertEquals(370.0F, DormantTicks.wrapPrevious(380.0F, 10.0F));
        assertEquals(-350.0F, DormantTicks.wrapPrevious(-340.0F, 10.0F));
        assertEquals(730.0F, DormantTicks.wrapPrevious(550.0F, 10.0F));
        assertEquals(10.0F, DormantTicks.wrapPrevious(Float.POSITIVE_INFINITY, 10.0F));
        assertEquals(10.0F, DormantTicks.wrapPrevious(Float.NaN, 10.0F));
        assertNotNull(Mixins.construct(DormantTicks.class));

        // The world hook runs the light tick only for paused stands.
        WorldDormantTickMixin hook = Mixins.instance(WorldDormantTickMixin.class);
        Mc.Recorded<Void> original = Mc.operation();
        stand.world = world;
        Mixins.set(world, "isRemote", true);
        when(stand.canPassengerSteer()).thenReturn(false);
        Mixins.call(hook, "impetus$pauseHidden", stand, original);
        assertEquals(0, original.count());
        EntityPig pig = mock(EntityPig.class);
        Mixins.call(hook, "impetus$pauseHidden", pig, original);
        assertEquals(1, original.count());
        assertSame(pig, original.last()[0]);
    }

    // Returns a chunk whose sections hold a stone wall at x 10.
    private static Chunk wallChunk() {
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
        for (int sy = 0; sy < 16; sy++) {
            sections[sy] = new ExtendedBlockStorage(sy << 4, true);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    sections[sy].set(10, y, z, Blocks.STONE.getDefaultState());
                }
            }
        }
        Chunk chunk = mock(Chunk.class);
        when(chunk.getBlockStorageArray()).thenReturn(sections);
        return chunk;
    }

    @Test
    void theWorkerPassMarksWhatTheWallHidesAfterTheDelay() {
        Long2ObjectOpenHashMap<Chunk> loaded = new Long2ObjectOpenHashMap<>();
        loaded.put(ChunkPos.asLong(0, 0), wallChunk());
        Chunk broken = mock(Chunk.class);
        when(broken.getBlockStorageArray()).thenThrow(new ArrayIndexOutOfBoundsException());
        loaded.put(ChunkPos.asLong(0, 2), broken);
        OcclusionCulling.INBOX.reset(loaded);
        EntityArmorStand hidden = stand(14, 64, 8);
        EntityArmorStand seen = stand(5, 64, 8);
        EntityArmorStand racy = stand(5, 64, 40);
        TileEntityChest chest = chest(new BlockPos(13, 64, 8), 0.0D);
        TileEntityChest openChest = chest(new BlockPos(4, 64, 8), 0.0D);
        OcclusionSnapshot snapshot = OcclusionCulling.EXCHANGE.producer();
        snapshot.clear();
        snapshot.add((Cullable) hidden, OcclusionSnapshot.ENTITY, 13.5, 64, 7.5, 14.5, 66, 8.5);
        snapshot.add((Cullable) seen, OcclusionSnapshot.ENTITY, 4.5, 64, 7.5, 5.5, 66, 8.5);
        snapshot.add((Cullable) racy, OcclusionSnapshot.ENTITY, 4.5, 64, 39.5, 5.5, 66, 40.5);
        snapshot.add((Cullable) chest, OcclusionSnapshot.BLOCK_ENTITY, 12.5, 63.5, 7.5, 14.5, 65.5, 9.5);
        snapshot.add((Cullable) openChest, OcclusionSnapshot.BLOCK_ENTITY, 3.5, 63.5, 7.5, 5.5, 65.5, 9.5);
        OcclusionCulling.EXCHANGE.publish();
        OcclusionCullingThread thread = Mixins.construct(OcclusionCullingThread.class);
        // Nothing happens before a camera exists.
        Mixins.call(thread, "pass", config.occlusion);
        assertFalse(((Cullable) hidden).impetus$isOccluded());
        Mixins.set(OcclusionCulling.class, "camera", new OcclusionCamera(1.5, 65.5, 8.5));
        Mixins.call(thread, "pass", config.occlusion);
        assertEquals(1, ((OcclusionGrid) Mixins.call(thread, "grid")).columnCount() - 1);
        // The first hidden verdict waits for the delay.
        assertFalse(((Cullable) hidden).impetus$isOccluded());
        assertTrue(((Cullable) hidden).impetus$hiddenSince() != 0L);
        config.occlusion.hideDelay = 0;
        Mixins.call(thread, "pass", config.occlusion);
        assertTrue(((Cullable) hidden).impetus$isOccluded());
        assertFalse(((Cullable) seen).impetus$isOccluded());
        assertFalse(((Cullable) racy).impetus$isOccluded());
        assertTrue(((Cullable) chest).impetus$isOccluded());
        assertFalse(((Cullable) openChest).impetus$isOccluded());
        assertEquals(3, OcclusionCullingThread.lastEntities);
        assertEquals(1, OcclusionCullingThread.lastEntitiesHidden);
        assertEquals(2, OcclusionCullingThread.lastBlockEntities);
        assertEquals(1, OcclusionCullingThread.lastBlockEntitiesHidden);
        assertTrue(OcclusionCullingThread.lastRays > 0);
        // A camera inside the wall sees everything.
        Mixins.set(OcclusionCulling.class, "camera", new OcclusionCamera(10.5, 65.5, 8.5));
        Mixins.call(thread, "pass", config.occlusion);
        assertFalse(((Cullable) hidden).impetus$isOccluded());
        assertEquals(0L, ((Cullable) hidden).impetus$hiddenSince());
        // The delay starts at the first hidden verdict.
        Cullable target = (Cullable) stand(0, 0, 0);
        assertFalse(OcclusionCullingThread.settle(target, false, 0L, 100L));
        assertEquals(1L, target.impetus$hiddenSince());
        assertFalse(OcclusionCullingThread.settle(target, false, 50L, 100L));
        assertTrue(OcclusionCullingThread.settle(target, false, 101L, 100L));
        assertFalse(OcclusionCullingThread.settle(target, true, 102L, 100L));
        assertEquals(0L, target.impetus$hiddenSince());

        // The loop runs a pass and stops when interrupted.
        Mixins.set(OcclusionCulling.class, "camera", new OcclusionCamera(1.5, 65.5, 8.5));
        Thread.currentThread().interrupt();
        thread.run();
        assertFalse(Thread.interrupted());
        assertTrue(OcclusionCullingThread.lastPassNanos > 0L);
        config.occlusion.enabled = false;
        Thread.currentThread().interrupt();
        thread.run();
        assertFalse(Thread.interrupted());
        // The overlay line reads the last pass.
        Mixins.set(client, "world", null);
        assertEquals("", OcclusionCulling.status());
        Mixins.set(client, "world", mock(WorldClient.class));
        assertTrue(OcclusionCulling.status().startsWith("Occlusion: "), OcclusionCulling.status());
    }

    // An interrupted worker ends. Without a replacement nothing drains the inbox, which then keeps every chunk ever loaded.
    @Test
    void aWorkerThatEndedIsReplaced() throws InterruptedException {
        OcclusionCullingThread ended = Mixins.construct(OcclusionCullingThread.class);
        ended.start();
        ended.interrupt();
        ended.join(5000L);
        assertEquals(Thread.State.TERMINATED, ended.getState());
        Mixins.set(OcclusionCullingThread.class, "instance", ended);
        OcclusionCullingThread.ensureRunning();
        OcclusionCullingThread replacement = Mixins.get(OcclusionCullingThread.class, "instance");
        try {
            assertNotSame(ended, replacement);
            OcclusionCullingThread.ensureRunning();
            assertSame(replacement, Mixins.get(OcclusionCullingThread.class, "instance"));
        } finally {
            replacement.interrupt();
            replacement.join(5000L);
        }
    }
}
