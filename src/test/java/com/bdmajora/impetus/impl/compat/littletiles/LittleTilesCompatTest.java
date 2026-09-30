package com.bdmajora.impetus.impl.compat.littletiles;

import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.mixin.core.terrain.compat.LittleTilesRenderManagerMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.creativemd.littletiles.client.render.cache.IRenderDataCache;
import com.creativemd.littletiles.client.render.cache.LayeredRenderBufferCache;
import com.creativemd.littletiles.client.render.world.TileEntityRenderManager;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LittleTilesCompatTest {
    private static final BlockPos POS = new BlockPos(3, 70, 12);
    private static final int QUAD_INTS = 28;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // IS_LOADED is read from Forge's mod list when the class first loads
        Mc.forge();
    }

    @AfterEach
    void clearPending() {
        pending().clear();
    }

    private static Long2LongOpenHashMap pending() {
        return Mixins.get(LittleTilesCompat.class, "PENDING_REMESHES");
    }

    // A LittleTiles tile entity whose render manager carries the Impetus mixin
    private static TileEntityLittleTiles tiles() {
        TileEntityLittleTiles tiles = new TileEntityLittleTiles();
        LittleTilesRenderManagerMixin manager = Mixins.instance(LittleTilesRenderManagerMixin.class);
        Mixins.set(manager, "te", tiles);
        tiles.render = (TileEntityRenderManager) (Object) manager;
        return tiles;
    }

    private static int[] quad(int seed) {
        int[] ints = new int[QUAD_INTS];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = seed + i * 7;
        }
        return ints;
    }

    // One baked quad in native layout plus some stray bytes past it, in a buffer flagged with the given order
    private static ByteBuffer baked(int[] quad, int extraBytes, ByteOrder flagged) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(quad.length * 4 + extraBytes);
        buffer.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer().put(quad);
        return buffer.order(flagged);
    }

    private static IRenderDataCache data(ByteBuffer buffer, int length) {
        return new IRenderDataCache() {
            @Override
            public ByteBuffer byteBuffer() {
                return buffer;
            }

            @Override
            public int length() {
                return length;
            }

            @Override
            public int vertexCount() {
                return length / DefaultVertexFormats.BLOCK.getSize();
            }
        };
    }

    private static BufferBuilder begun() {
        BufferBuilder builder = new BufferBuilder(512);
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        return builder;
    }

    private static int[] contents(BufferBuilder builder) {
        IntBuffer ints = builder.getByteBuffer().asIntBuffer();
        int[] out = new int[builder.getVertexCount() * DefaultVertexFormats.BLOCK.getIntegerSize()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ints.get(i);
        }
        return out;
    }

    @Test
    void bakedBuffersAreSplicedAfterTheBlocksOwnModel() {
        Mixins.construct(LittleTilesCompat.class);
        TileEntityLittleTiles tiles = tiles();
        LayeredRenderBufferCache cache = tiles.render.getBufferCache();
        int[] solidQuad = quad(1);
        int[] mippedQuad = quad(500);
        ByteBuffer solid = baked(solidQuad, 5, ByteOrder.nativeOrder());
        solid.position(7);
        // Five stray bytes past the quad are not a quad
        cache.put(BlockRenderLayer.SOLID.ordinal(), data(solid, solid.capacity()));
        // A combined BufferLink is allocated without a byte order though its bytes are native, and a length past its end is held to its capacity
        ByteOrder foreign = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        cache.put(BlockRenderLayer.CUTOUT_MIPPED.ordinal(), data(baked(mippedQuad, 0, foreign), 1000));
        // Uploaded to a VBO elsewhere, and shorter than one quad
        cache.put(BlockRenderLayer.CUTOUT.ordinal(), data(null, 112));
        cache.put(BlockRenderLayer.TRANSLUCENT.ordinal(), data(ByteBuffer.allocateDirect(64), 64));

        VintageChunkBuildContext context = mock(VintageChunkBuildContext.class);
        Map<BlockRenderLayer, BufferBuilder> buffers = new HashMap<>();
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            buffers.put(layer, begun());
            when(context.getBufferForLayer(layer)).thenReturn(buffers.get(layer));
        }
        Map<BlockPos, Integer> light = new HashMap<>();
        IBlockAccess slice = mock(IBlockAccess.class);
        IBlockState air = mock(IBlockState.class);
        when(slice.getBlockState(any())).thenReturn(air);
        when(air.getPackedLightmapCoords(eq(slice), any())).thenAnswer(invocation ->
                light.getOrDefault(invocation.<BlockPos>getArgument(1), 0x00F000F0));
        IBlockState state = Blocks.STONE.getDefaultState();
        Object section = new Object();
        TileEntityRenderManager render = tiles.render;

        LittleTilesCompat.renderTiles(tiles, slice, POS, state, context, section);
        // Each layer that had whole quads is copied verbatim, the cache's own cursor untouched
        assertArrayEquals(solidQuad, contents(buffers.get(BlockRenderLayer.SOLID)));
        assertArrayEquals(mippedQuad, contents(buffers.get(BlockRenderLayer.CUTOUT_MIPPED)));
        assertEquals(0, buffers.get(BlockRenderLayer.CUTOUT).getVertexCount());
        assertEquals(0, buffers.get(BlockRenderLayer.TRANSLUCENT).getVertexCount());
        assertEquals(7, solid.position());
        verify(context).recordVanillaBlockAttribution(BlockRenderLayer.SOLID, state, POS);
        verify(context).recordVanillaBlockAttribution(BlockRenderLayer.CUTOUT_MIPPED, state, POS);
        verify(context, never()).recordVanillaBlockAttribution(eq(BlockRenderLayer.CUTOUT), any(), any());
        verify(context, never()).recordVanillaBlockAttribution(eq(BlockRenderLayer.TRANSLUCENT), any(), any());
        // A first sighting rebakes, since the bake may predate the section's first mesh
        assertEquals(List.of(section), tiles.quadCacheUpdates);
        assertEquals(1, render.lightRebakes);
        assertFalse(render.hasLightChanged);

        // Same light, no rebake; light moving at the block or beside it rebakes
        LittleTilesCompat.renderTiles(tiles, slice, POS, state, context, section);
        assertEquals(1, render.lightRebakes);
        light.put(POS.up(), 0x00F00000);
        LittleTilesCompat.renderTiles(tiles, slice, POS, state, context, section);
        assertEquals(2, render.lightRebakes);
        light.put(POS, 0x00A000F0);
        LittleTilesCompat.renderTiles(tiles, slice, POS, state, context, section);
        assertEquals(3, render.lightRebakes);
        assertEquals(4, tiles.quadCacheUpdates.size());
    }

    @Test
    void onlyLoadedLittleTilesAreTouched() {
        VintageChunkBuildContext context = mock(VintageChunkBuildContext.class);
        IBlockAccess slice = mock(IBlockAccess.class);
        IBlockState state = Blocks.STONE.getDefaultState();
        LittleTilesCompat.renderTiles(new TileEntityChest(), slice, POS, state, context, null);
        // A stand-in or unloaded tile entity has no tiles to draw or bake
        TileEntityLittleTiles tiles = tiles();
        tiles.loaded = false;
        LittleTilesCompat.renderTiles(tiles, slice, POS, state, context, null);
        verifyNoInteractions(context, slice);
        assertTrue(tiles.quadCacheUpdates.isEmpty());
    }

    @Test
    void finishedBakesRemeshTheirSectionOnceTheBurstSettles() {
        Minecraft client = Mc.client();
        WorldClient world = mock(WorldClient.class);
        Mixins.set(client, "world", world);
        ImpetusWorldRenderer renderer = mock(ImpetusWorldRenderer.class);
        TileEntityLittleTiles tiles = tiles();
        tiles.setPos(new BlockPos(-1, 70, -17));
        LittleTilesRenderManagerMixin manager = (LittleTilesRenderManagerMixin) (Object) tiles.render;

        // Not in a world yet, then in an animation's sub-world
        finish(manager, true, 5);
        tiles.setWorld(mock(WorldClient.class));
        finish(manager, true, 5);
        assertTrue(pending().isEmpty());
        // A newer request still outstanding, or a failed bake, remeshes nothing
        tiles.setWorld(world);
        finish(manager, false, 5);
        finish(manager, true, -1);
        assertTrue(pending().isEmpty());

        // Two bakes in one section share one entry, timed from the first
        finish(manager, true, 5);
        TileEntityLittleTiles neighbour = tiles();
        neighbour.setWorld(world);
        neighbour.setPos(new BlockPos(-16, 79, -32));
        long key = PositionUtil.packSection(-1, 4, -2);
        long first = pending().get(key);
        finish((LittleTilesRenderManagerMixin) (Object) neighbour.render, true, 5);
        assertEquals(1, pending().size());
        assertEquals(first, pending().get(key));

        // Too recent to flush, then old enough
        LittleTilesCompat.flushRemeshes(renderer);
        verifyNoInteractions(renderer);
        pending().put(key, first - TimeUnit.SECONDS.toNanos(1));
        LittleTilesCompat.flushRemeshes(renderer);
        verify(renderer).scheduleRebuildForChunk(-1, 4, -2, false);
        assertTrue(pending().isEmpty());
        LittleTilesCompat.flushRemeshes(renderer);
        verifyNoMoreInteractions(renderer);
    }

    private static void finish(LittleTilesRenderManagerMixin manager, boolean done, int renderState) {
        Mixins.call(manager, "impetus$remeshSection", 0, renderState, false, Mixins.cir(done));
    }
}
