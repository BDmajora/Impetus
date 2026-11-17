package com.bdmajora.impetus.impl.compat.littletiles;

import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.creativemd.littletiles.client.render.cache.IRenderDataCache;
import com.creativemd.littletiles.client.render.world.TileEntityRenderManager;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

// Compatibility with LittleTiles meshes.
public final class LittleTilesCompat {
    // Mod id of LittleTiles.
    public static final String MODID = "littletiles";
    // Whether LittleTiles is installed.
    public static final boolean IS_LOADED = Loader.isModLoaded(MODID);

    // Remesh delay nanos.
    private static final long REMESH_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    // Quad bytes.
    private static final int QUAD_BYTES = DefaultVertexFormats.BLOCK.getSize() * 4;
    // Due times of pending remeshes by position.
    private static final Long2LongOpenHashMap PENDING_REMESHES = new Long2LongOpenHashMap();

    // Prevents instantiation
    private LittleTilesCompat() {
    }

    // Adds the baked tiles of a block entity to the mesh.
    public static void renderTiles(TileEntity tileEntity, IBlockAccess slice, BlockPos pos, IBlockState state,
                                   // Build context of the section.
                                   VintageChunkBuildContext context, Object section) {
        if (!(tileEntity instanceof TileEntityLittleTiles)) {
            return;
        }
        TileEntityLittleTiles tiles = (TileEntityLittleTiles) tileEntity;
        if (!tiles.hasLoaded()) {
            return;
        }
        int light = lightFingerprint(slice, pos);
        TileEntityRenderManager render = tiles.render;
        synchronized (render) {
            if (((BakedLightTracker) render).impetus$lightChanged(light)) {
                render.hasLightChanged = true;
            }
            tiles.updateQuadCache(section);
            for (BlockRenderLayer layer : VintageChunkBuildContext.LAYERS) {
                IRenderDataCache data = render.getBufferCache().get(layer.ordinal());
                if (data != null && copyQuads(data, context, layer)) {
                    context.recordVanillaBlockAttribution(layer, state, pos);
                }
            }
        }
    }

    // Copies the quads.
    private static boolean copyQuads(IRenderDataCache data, VintageChunkBuildContext context, BlockRenderLayer layer) {
        ByteBuffer buffer = data.byteBuffer();
        if (buffer == null) {
            return false;
        }
        int length = Math.min(data.length(), buffer.capacity());
        length -= length % QUAD_BYTES;
        if (length <= 0) {
            return false;
        }
        ByteBuffer view = buffer.duplicate().order(ByteOrder.nativeOrder());
        view.clear();
        view.limit(length);
        int[] vertices = new int[length >> 2];
        view.asIntBuffer().get(vertices);
        context.getBufferForLayer(layer).addVertexData(vertices);
        return true;
    }

    // Returns a hash of the light around a block.
    static int lightFingerprint(IBlockAccess access, BlockPos pos) {
        int fingerprint = light(access, pos);
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.VALUES) {
            neighbour.setPos(pos.getX() + facing.getXOffset(), pos.getY() + facing.getYOffset(), pos.getZ() + facing.getZOffset());
            fingerprint = fingerprint * 31 + light(access, neighbour);
        }
        return fingerprint;
    }

    // Returns the packed light of a block.
    private static int light(IBlockAccess access, BlockPos pos) {
        return access.getBlockState(pos).getPackedLightmapCoords(access, pos);
    }

    // Schedules a remesh of a block entity.
    public static void queueRemesh(TileEntity tileEntity) {
        World world = tileEntity.getWorld();
        if (world == null || world != Minecraft.getMinecraft().world) {
            return;
        }
        BlockPos pos = tileEntity.getPos();
        long key = PositionUtil.packSection(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        long now = System.nanoTime();
        synchronized (PENDING_REMESHES) {
            if (!PENDING_REMESHES.containsKey(key)) {
                PENDING_REMESHES.put(key, now);
            }
        }
    }

    // Remeshes the due sections.
    public static void flushRemeshes(ImpetusWorldRenderer renderer) {
        synchronized (PENDING_REMESHES) {
            if (PENDING_REMESHES.isEmpty()) {
                return;
            }
            long now = System.nanoTime();
            ObjectIterator<Long2LongMap.Entry> pending = PENDING_REMESHES.long2LongEntrySet().fastIterator();
            while (pending.hasNext()) {
                Long2LongMap.Entry entry = pending.next();
                if (now - entry.getLongValue() >= REMESH_DELAY_NANOS) {
                    long key = entry.getLongKey();
                    renderer.scheduleRebuildForChunk(PositionUtil.unpackSectionX(key), PositionUtil.unpackSectionY(key),
                            PositionUtil.unpackSectionZ(key), false);
                    pending.remove();
                }
            }
        }
    }
}
