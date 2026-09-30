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

// LittleTiles 1.5 draws nothing through its block model: its RenderingThread bakes each tile entity into BLOCK-format buffers already offset to the section origin, and its coremod splices them into vanilla's chunk upload, a path the Impetus mesher replaces
public final class LittleTilesCompat {
    public static final String MODID = "littletiles";
    public static final boolean IS_LOADED = Loader.isModLoaded(MODID);

    // A section whose bakes keep finishing (a build streaming in) is remeshed at most this often rather than once per tile entity
    private static final long REMESH_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    // Four BLOCK vertices; a baked buffer is only ever copied in whole quads
    private static final int QUAD_BYTES = DefaultVertexFormats.BLOCK.getSize() * 4;
    // Packed section position to when the first bake waiting on it finished; filled from LittleTiles' threads, drained on the render thread
    private static final Long2LongOpenHashMap PENDING_REMESHES = new Long2LongOpenHashMap();

    private LittleTilesCompat() {
    }

    // From the mesher for every tile entity it meets: LittleTiles' own get their baked buffers spliced in after the block's empty model
    public static void renderTiles(TileEntity tileEntity, IBlockAccess slice, BlockPos pos, IBlockState state,
                                   VintageChunkBuildContext context, Object section) {
        if (!(tileEntity instanceof TileEntityLittleTiles)) {
            return;
        }
        TileEntityLittleTiles tiles = (TileEntityLittleTiles) tileEntity;
        // A stand-in from the slice, or a tile entity whose tiles have not arrived yet or were unloaded
        if (!tiles.hasLoaded()) {
            return;
        }
        int light = lightFingerprint(slice, pos);
        TileEntityRenderManager render = tiles.render;
        // The RenderingThread holds this while it swaps a layer's buffer, so none is read half replaced
        synchronized (render) {
            // Light is baked into the vertices and LittleTiles only hears of it changing through notifyLightSet, which the async light engine never calls
            if (((BakedLightTracker) render).impetus$lightChanged(light)) {
                render.hasLightChanged = true;
            }
            // Queues a bake when light, a neighbour or LittleTiles' render state moved on, as vanilla's upload hook does
            tiles.updateQuadCache(section);
            for (BlockRenderLayer layer : VintageChunkBuildContext.LAYERS) {
                IRenderDataCache data = render.getBufferCache().get(layer.ordinal());
                if (data != null && copyQuads(data, context, layer)) {
                    context.recordVanillaBlockAttribution(layer, state, pos);
                }
            }
        }
    }

    // Appends the whole quads of one baked buffer to the section's vanilla buffer for the layer; false when there were none
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
        // A view, so the cache's own position and limit stay LittleTiles'; duplicate() drops the byte order, and a combined BufferLink never had it
        ByteBuffer view = buffer.duplicate().order(ByteOrder.nativeOrder());
        view.clear();
        view.limit(length);
        int[] vertices = new int[length >> 2];
        view.asIntBuffer().get(vertices);
        context.getBufferForLayer(layer).addVertexData(vertices);
        return true;
    }

    // The packed light a bake reads at the block and across each face, folded into one int
    static int lightFingerprint(IBlockAccess access, BlockPos pos) {
        int fingerprint = light(access, pos);
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (EnumFacing facing : EnumFacing.VALUES) {
            neighbour.setPos(pos.getX() + facing.getXOffset(), pos.getY() + facing.getYOffset(), pos.getZ() + facing.getZOffset());
            fingerprint = fingerprint * 31 + light(access, neighbour);
        }
        return fingerprint;
    }

    // Through the state as the bake reads it, so dynamic light folded into getPackedLightmapCoords counts too
    private static int light(IBlockAccess access, BlockPos pos) {
        return access.getBlockState(pos).getPackedLightmapCoords(access, pos);
    }

    // From LittleTiles' RenderingThread once a bake is final: vanilla LittleTiles marks the RenderChunk instead, but Impetus's vestigial ViewFrustum hands it the same sixteen for the whole world
    public static void queueRemesh(TileEntity tileEntity) {
        World world = tileEntity.getWorld();
        // Tile entities inside animations live in LittleTiles' sub-worlds and draw through its own render chunks
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

    // Once a frame on the render thread: remeshes every section whose first finished bake has waited out the delay
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
