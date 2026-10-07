package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderOptions;
import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.ViewportProvider;
import com.bdmajora.impetus.impl.extensions.VertexFormatExtension;
import com.bdmajora.impetus.impl.render.clouds.SodiumCloudRenderer;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.mixin.core.terrain.compat.FluidCacheMixin;
import com.bdmajora.impetus.mixin.core.terrain.compat.FluidloggedUtilsMixin;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.world.IBlockAccessWrapper;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerrainMixinsTest {
    private Minecraft client;
    private ImpetusGameOptions previousOptions;
    private ImpetusGameOptions options;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
        // setupTerrain asks whether LittleTiles is loaded
        Mc.forge();
    }

    @BeforeEach
    void freshClient() {
        client = Mc.client();
        previousOptions = Statics.get(ImpetusVintage.class, "CONFIG");
        options = ImpetusGameOptions.defaults();
        Statics.set(ImpetusVintage.class, "CONFIG", options);
    }

    @AfterEach
    void restore() {
        Statics.set(ImpetusVintage.class, "CONFIG", previousOptions);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
    }

    private static boolean cancels(Object mixin, String handler, Object... leading) {
        CallbackInfo ci = Mixins.ci();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = ci;
        Mixins.call(mixin, handler, args);
        return ci.isCancelled();
    }

    private static <R> CallbackInfoReturnable<R> returned(Object mixin, String handler, Object... leading) {
        CallbackInfoReturnable<R> cir = Mixins.cir();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = cir;
        Mixins.call(mixin, handler, args);
        return cir;
    }

    // A position, colour, UV and lightmap layout: 7 ints a vertex, colour at 3, UV at 4, light at 6 and no normal
    private static VertexFormatMixin blockFormat() {
        VertexFormatMixin format = Mixins.instance(VertexFormatMixin.class);
        doReturn(7).when(format).getIntegerSize();
        doReturn(12).when(format).getColorOffset();
        doReturn(true).when(format).hasUvOffset(0);
        doReturn(16).when(format).getUvOffsetById(0);
        doReturn(true).when(format).hasUvOffset(1);
        doReturn(24).when(format).getUvOffsetById(1);
        doReturn(-1).when(format).getNormalOffset();
        return format;
    }

    // A flat quad at y = 1 facing up, each vertex with its own colour, UV and light
    private static int[] upQuad() {
        float[][] corners = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        int[] data = new int[28];
        for (int i = 0; i < 4; i++) {
            data[i * 7] = Float.floatToRawIntBits(corners[i][0]);
            data[i * 7 + 1] = Float.floatToRawIntBits(corners[i][1]);
            data[i * 7 + 2] = Float.floatToRawIntBits(corners[i][2]);
            data[i * 7 + 3] = 0xFF000000 | i;
            data[i * 7 + 4] = Float.floatToRawIntBits(0.25F * i);
            data[i * 7 + 5] = Float.floatToRawIntBits(0.5F);
            data[i * 7 + 6] = 0x00F000F0 + i;
        }
        return data;
    }

    private static BakedQuadMixin quad(VertexFormatMixin format, int[] data, EnumFacing face) {
        BakedQuadMixin quad = Mixins.instance(BakedQuadMixin.class);
        doReturn(data).when(quad).getVertexData();
        doReturn(format).when(quad).getFormat();
        Mixins.set(quad, "face", face);
        Mixins.set(quad, "applyDiffuseLighting", true);
        Mixins.set(quad, "tintIndex", 2);
        Mixins.set(quad, "sprite", Mc.uninitialized(TextureAtlasSprite.class));
        return quad;
    }

    @Test
    void theVertexFormatCachesItsIntLayoutUntilItChanges() {
        VertexFormatMixin format = blockFormat();
        VertexFormatExtension layout = format;
        assertEquals(3, layout.impetus$colorIndex());
        assertEquals(4, layout.impetus$uvIndex());
        assertEquals(6, layout.impetus$lightIndex());
        assertEquals(-1, layout.impetus$normalIndex());
        assertEquals(7, layout.impetus$intStride());
        verify(format, times(1)).getIntegerSize();
        // Adding an element or clearing drops the cache, so a mutated format is re-derived
        doReturn(8).when(format).getIntegerSize();
        doReturn(-1).when(format).getColorOffset();
        doReturn(false).when(format).hasUvOffset(0);
        doReturn(false).when(format).hasUvOffset(1);
        doReturn(28).when(format).getNormalOffset();
        returned(format, "impetus$onAddElement", (Object) null);
        assertEquals(8, layout.impetus$intStride());
        assertEquals(-1, layout.impetus$colorIndex());
        assertEquals(0, layout.impetus$uvIndex());
        assertEquals(-1, layout.impetus$lightIndex());
        assertEquals(7, layout.impetus$normalIndex());
        cancels(format, "impetus$onClear");
        assertEquals(8, layout.impetus$intStride());
        verify(format, times(3)).getIntegerSize();
    }

    @Test
    void aBakedQuadReadsItsVerticesThroughTheFormatLayout() {
        int[] data = upQuad();
        BakedQuadMixin quad = quad(blockFormat(), data, EnumFacing.UP);
        assertEquals(4, quad.getVerticesCount());
        assertEquals(1.0F, quad.getX(2));
        assertEquals(1.0F, quad.getY(2));
        assertEquals(1.0F, quad.getZ(1));
        assertEquals(0xFF000002, quad.getColor(2));
        assertEquals(0.75F, quad.getTexU(3));
        assertEquals(0.5F, quad.getTexV(3));
        assertEquals(0x00F000F1, quad.getLight(1));
        assertEquals(0, quad.getForgeNormal(0));
        assertTrue(quad.hasShade());
        assertEquals(2, quad.getColorIndex());
        assertNull(quad.getTransparencyLevel());
        assertSame(Mixins.get(quad, "sprite"), quad.impetus$getSprite());
        // The normal and its face are derived once from the positions, and the flags once from those
        assertEquals(ModelQuadFacing.POS_Y, quad.getNormalFace());
        assertEquals(ModelQuadFacing.POS_Y, quad.getNormalFace());
        int normal = quad.getComputedFaceNormal();
        assertEquals(normal, quad.getComputedFaceNormal());
        assertEquals(ModelQuadFacing.POS_Y, quad.getLightFace());
        quad.addFlags(ModelQuadFlags.IS_VANILLA_SHADED);
        int flags = quad.getFlags();
        assertNotEquals(0, flags & ModelQuadFlags.IS_POPULATED);
        assertNotEquals(0, flags & ModelQuadFlags.IS_VANILLA_SHADED);
        assertEquals(flags, quad.getFlags());

        // A format with no colour, lightmap or normal reads those as zero, and a mod's missing face lights as up
        VertexFormatMixin bare = Mixins.instance(VertexFormatMixin.class);
        doReturn(3).when(bare).getIntegerSize();
        doReturn(-1).when(bare).getColorOffset();
        doReturn(-1).when(bare).getNormalOffset();
        BakedQuadMixin plain = quad(bare, new int[12], null);
        assertEquals(0, plain.getColor(0));
        assertEquals(0, plain.getLight(0));
        assertEquals(ModelQuadFacing.POS_Y, plain.getLightFace());
        VertexFormatMixin withNormal = blockFormat();
        doReturn(20).when(withNormal).getNormalOffset();
        assertEquals(data[5], quad(withNormal, data, EnumFacing.DOWN).getForgeNormal(0));
    }

    @Test
    void theModelBuildersTagTheirQuads() {
        BakedQuadMixin quad = quad(blockFormat(), upQuad(), EnumFacing.UP);
        SimpleBakedModelBuilderMixin builder = Mixins.instance(SimpleBakedModelBuilderMixin.class);
        assertSame(quad, Mixins.call(builder, "setVanillaShadingFlag", quad));
        assertEquals(ModelQuadFlags.IS_VANILLA_SHADED, (int) Mixins.get(quad, "flags"));

        // A plain atlas sprite whose UVs stay inside it can be trusted; an animated or custom sprite, or UVs past the edge, cannot
        BakedQuadFactoryMixin bakery = Mixins.instance(BakedQuadFactoryMixin.class);
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        BakedQuadMixin inside = quad(blockFormat(), upQuad(), EnumFacing.UP);
        assertSame(inside, Mixins.call(bakery, "setMaterialClassification", inside,
                new BlockPartFace(null, -1, "#all", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)), sprite));
        assertEquals(ModelQuadFlags.IS_TRUSTED_SPRITE, (int) Mixins.get(inside, "flags"));
        BakedQuadMixin outside = quad(blockFormat(), upQuad(), EnumFacing.UP);
        Mixins.call(bakery, "setMaterialClassification", outside,
                new BlockPartFace(null, -1, "#all", new BlockFaceUV(new float[] {-1, 0, 16, 17}, 0)), sprite);
        Mixins.call(bakery, "setMaterialClassification", outside,
                new BlockPartFace(null, -1, "#all", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)), new TextureAtlasSprite("custom") {
                });
        assertEquals(0, (int) Mixins.get(outside, "flags"));
    }

    @Test
    void theChunkTrackerFollowsTheClientsColumns() {
        WorldClientMixin world = Mixins.instance(WorldClientMixin.class);
        ChunkTracker tracker = world.impetus$getTracker();
        assertSame(tracker, world.impetus$getTracker());
        ClientChunkManagerMixin chunks = Mixins.instance(ClientChunkManagerMixin.class);
        Mixins.set(chunks, "world", world);
        returned(chunks, "afterLoadChunkFromPacket", 3, -4);
        Map<Long, Integer> status = Mixins.get(tracker, "chunkStatus");
        assertTrue(status.containsKey(ChunkPos.asLong(3, -4)));
        cancels(chunks, "afterUnloadChunk", 3, -4);
        assertFalse(status.containsKey(ChunkPos.asLong(3, -4)));
    }

    @Test
    void fluidloggedReadsGoBackToTheChunkSlice() {
        FluidCacheMixin cache = Mixins.instanceWith(FluidCacheMixin.class, IBlockAccessWrapper.class);
        IBlockAccessWrapper wrapper = (IBlockAccessWrapper) cache;
        // Wrapping the live world, the Fluidlogged reads stand
        when(wrapper.getWrapped()).thenReturn(mock(IBlockAccess.class));
        assertFalse(returned(cache, "impetus$sliceBlockState", 1, 2, 3).isCancelled());
        assertFalse(returned(cache, "impetus$sliceFluidState", 1, 2, 3).isCancelled());
        // Wrapping a chunk slice, both come from the snapshot the mesh is built from
        ImpetusBlockAccess slice = mock(ImpetusBlockAccess.class);
        IBlockState stone = Blocks.STONE.getDefaultState();
        when(slice.getBlockState(1, 2, 3)).thenReturn(stone);
        when(slice.getFluidState(1, 2, 3)).thenReturn(FluidState.EMPTY);
        when(wrapper.getWrapped()).thenReturn(slice);
        assertSame(stone, returned(cache, "impetus$sliceBlockState", 1, 2, 3).getReturnValue());
        assertSame(FluidState.EMPTY, returned(cache, "impetus$sliceFluidState", 1, 2, 3).getReturnValue());
    }

    @Test
    void fluidloggedStaticLookupsSeeTheSlicesGuessedFluid() {
        BlockPos pos = new BlockPos(1, 2, 3);
        IBlockState fence = Blocks.OAK_FENCE.getDefaultState();
        IBlockState water = Blocks.WATER.getDefaultState();
        // The live world, bare or wrapped, keeps Fluidlogged's own chunk reads
        IBlockAccess live = mock(IBlockAccess.class);
        IBlockAccessWrapper liveWrapper = mock(IBlockAccessWrapper.class);
        when(liveWrapper.getWrapped()).thenReturn(live);
        for (IBlockAccess world : new IBlockAccess[] {live, liveWrapper}) {
            assertFalse(returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidState", world, pos).isCancelled());
            assertFalse(returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidOrReal", world, pos).isCancelled());
        }
        // A fence the server never fluidlogged, holding the guess the slice made for it
        ImpetusBlockAccess slice = mock(ImpetusBlockAccess.class);
        when(slice.getBlockState(pos)).thenReturn(fence);
        when(slice.getFluidState(1, 2, 3)).thenReturn(FluidState.of(water));
        assertSame(water, sliceFluid(slice, pos).getState());
        assertSame(water, returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidOrReal", slice, pos).getReturnValue());
        // Through FluidCache's wrapper too, which is how the fluid renderer asks
        IBlockAccessWrapper cache = mock(IBlockAccessWrapper.class);
        when(cache.getWrapped()).thenReturn(slice);
        when(cache.getBlockState(pos)).thenReturn(fence);
        assertSame(water, sliceFluid(cache, pos).getState());
        assertSame(water, returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidOrReal", cache, pos).getReturnValue());
        // A block with nothing in it stays itself, and a fluid block is its own fluid whatever the slice holds
        when(slice.getFluidState(1, 2, 3)).thenReturn(FluidState.EMPTY);
        assertTrue(sliceFluid(slice, pos).isEmpty());
        assertSame(fence, returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidOrReal", slice, pos).getReturnValue());
        when(slice.getBlockState(pos)).thenReturn(water);
        assertSame(water, sliceFluid(slice, pos).getState());
        assertSame(water, returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidOrReal", slice, pos).getReturnValue());
        assertNotNull(Mixins.construct(FluidloggedUtilsMixin.class));
    }

    // What the static getFluidState answers for an access the mixin must take over
    private static FluidState sliceFluid(IBlockAccess world, BlockPos pos) {
        CallbackInfoReturnable<FluidState> cir = returned(FluidloggedUtilsMixin.class, "impetus$sliceFluidState", world, pos);
        assertTrue(cir.isCancelled());
        return cir.getReturnValue();
    }

    @Test
    void biomeTintsBlendOverTheConfiguredRadius() {
        BiomeColorHelper.ColorResolver resolver = mock(BiomeColorHelper.ColorResolver.class);
        IBlockAccess world = mock(IBlockAccess.class);
        when(world.getBiome(any())).thenReturn(Biomes.PLAINS);
        when(resolver.getColorAtPos(any(), any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(1);
            return pos.getX() == 10 && pos.getZ() == 10 ? 0x908070 : 0x000000;
        });
        BlockPos pos = new BlockPos(10, 64, 10);
        options.quality.legacyBiomeBlendRadius = 0;
        assertEquals(0x908070, (int) Mixins.call(BiomeColorHelperMixin.class, "getColorAtPos", world, pos, resolver));
        // A radius of one averages the nine columns around the block
        options.quality.legacyBiomeBlendRadius = 1;
        assertEquals(0x100E0C, (int) Mixins.call(BiomeColorHelperMixin.class, "getColorAtPos", world, pos, resolver));
        // A chunk slice has its own blended biome cache
        ImpetusBlockAccess slice = mock(ImpetusBlockAccess.class);
        when(slice.getBlockTint(pos, resolver)).thenReturn(0x123456);
        assertEquals(0x123456, (int) Mixins.call(BiomeColorHelperMixin.class, "getColorAtPos", slice, pos, resolver));
        assertNotNull(Mixins.construct(BiomeColorHelperMixin.class));
    }

    @Test
    void terrainProgramsAreSwappedForThePacksWhileShadersRun() {
        MixinShaderChunkRenderer renderer = Mixins.instance(MixinShaderChunkRenderer.class);
        Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs = new HashMap<>();
        Mixins.set(renderer, "programs", programs);
        ChunkShaderOptions solid = new ChunkShaderOptions(List.of(), null);
        @SuppressWarnings("unchecked")
        GlProgram<ChunkShaderInterface> override = mock(GlProgram.class);
        @SuppressWarnings("unchecked")
        GlProgram<ChunkShaderInterface> shadow = mock(GlProgram.class);
        try (MockedStatic<UmbraTerrainProgramOverride> overrides = Mockito.mockStatic(UmbraTerrainProgramOverride.class)) {
            // Shaders off: Impetus compiles its own
            assertFalse(returned(renderer, "impetus$overrideTerrainProgram", solid).isCancelled());
            overrides.when(UmbraTerrainProgramOverride::areShadersActive).thenReturn(true);
            // A pack without a terrain program leaves Impetus's in place
            assertFalse(returned(renderer, "impetus$overrideTerrainProgram", solid).isCancelled());
            overrides.when(() -> UmbraTerrainProgramOverride.getProgramOverride(solid)).thenReturn(override);
            assertSame(override, returned(renderer, "impetus$overrideTerrainProgram", solid).getReturnValue());
            assertSame(override, programs.get(solid));
            assertSame(override, returned(renderer, "impetus$overrideTerrainProgram", solid).getReturnValue());
            overrides.verify(() -> UmbraTerrainProgramOverride.getProgramOverride(solid), times(2));
            // The shadow pass has its own cache
            Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
            overrides.when(() -> UmbraTerrainProgramOverride.getShadowProgramOverride(solid)).thenReturn(shadow);
            assertSame(shadow, returned(renderer, "impetus$overrideTerrainProgram", solid).getReturnValue());
        }
    }

    @Test
    void theProjectionAccessorsArePlaceholdersTheGameFillsIn() {
        assertThrows(AssertionError.class, () -> Mixins.call(ActiveRenderInfoAccessor.class, "getProjectionMatrix"));
        assertThrows(AssertionError.class, () -> Mixins.call(ActiveRenderInfoAccessor.class, "getModelViewMatrix"));
    }

    // RenderGlobal with the Impetus renderer swapped for a mock and just enough of the client behind it
    private static final class Scene {
        final RenderGlobalMixin global = Mixins.instance(RenderGlobalMixin.class);
        final ImpetusWorldRenderer renderer = mock(ImpetusWorldRenderer.class);
        final WorldClient world = mock(WorldClient.class);
        final RenderManager renderManager = mock(RenderManager.class);
        final GameSettings settings = mock(GameSettings.class);
        final EntityRenderer entityRenderer = mock(EntityRenderer.class);

        Scene(Minecraft client) {
            Mixins.set(global, "mc", client);
            Mixins.set(global, "renderer", renderer);
            Mixins.set(global, "world", world);
            Mixins.set(global, "renderManager", renderManager);
            Mixins.set(global, "renderEngine", mock(TextureManager.class));
            Mixins.set(global, "setTileEntities", new HashSet<TileEntity>());
            Mixins.set(global, "damagedBlocks", new HashMap<>());
            Mixins.set(client, "gameSettings", settings);
            Mixins.set(client, "profiler", new Profiler());
            client.entityRenderer = entityRenderer;
        }
    }

    @Test
    void renderGlobalHandsTerrainToTheImpetusRenderer() {
        Devices.active().makeInactive();
        Scene scene = new Scene(client);
        RenderGlobalMixin global = scene.global;
        ImpetusWorldRenderer renderer = scene.renderer;
        // Vanilla allocates no chunk storage, and leaves follow the Impetus leaves option
        assertEquals(0, (int) Mixins.call(global, "nullifyBuiltChunkStorage", scene.settings));
        BlockLeaves leaves = mock(BlockLeaves.class);
        options.quality.leavesQuality = ImpetusGameOptions.GraphicsQuality.FAST;
        Mixins.call(global, "useConfiguredLeavesGraphicsLevel", leaves, true);
        verify(leaves).setGraphicsLevel(false);
        // The renderer is built alongside RenderGlobal
        cancels(global, "init", client);
        assertInstanceOf(ImpetusWorldRenderer.class, global.impetus$getWorldRenderer());
        assertSame(global.impetus$getWorldRenderer(), SimpleWorldRenderer.Provider.getWorldRenderer(global));
        Mixins.set(global, "renderer", renderer);

        // A world change rebuilds once, skipping the reload vanilla does for the world being left; Umbra hears the dimension first, nothing for a world without a provider or no world at all
        cancels(global, "impetus$beginWorldChange", (Object) null);
        assertNull(Mixins.get(com.bdmajora.impetus.umbra.Umbra.class, "dimensionName"));
        WorldProvider nether = mock(WorldProvider.class);
        when(nether.getDimensionType()).thenReturn(net.minecraft.world.DimensionType.NETHER);
        Mixins.set(scene.world, "provider", nether);
        cancels(global, "impetus$beginWorldChange", scene.world);
        assertEquals("the_nether", Mixins.get(com.bdmajora.impetus.umbra.Umbra.class, "dimensionName"));
        assertEquals(-1, (int) Mixins.<Integer>get(com.bdmajora.impetus.umbra.Umbra.class, "dimensionId"));
        when(nether.getDimensionType()).thenReturn(null);
        cancels(global, "impetus$beginWorldChange", scene.world);
        Mixins.set(scene.world, "provider", null);
        cancels(global, "impetus$beginWorldChange", scene.world);
        cancels(global, "onReload");
        verify(renderer, never()).reload();
        cancels(global, "onWorldChanged", scene.world);
        verify(renderer).setWorld(scene.world);
        cancels(global, "onReload");
        verify(renderer).reload();

        when(renderer.getVisibleChunkCount()).thenReturn(42);
        assertEquals(42, global.getRenderedChunks());
        when(renderer.isTerrainRenderComplete()).thenReturn(true);
        assertTrue(global.hasNoChunkUpdates());
        cancels(global, "onTerrainUpdateScheduled");
        verify(renderer).scheduleTerrainUpdate();
        Mixins.call(global, "markBlocksForUpdate", 1, 2, 3, 4, 5, 6, true);
        verify(renderer).scheduleRebuildForBlockArea(1, 2, 3, 4, 5, 6, true);
        assertFalse((boolean) Mixins.call(global, "alwaysHaveBuilders", (Object) null));
        assertTrue((boolean) Mixins.call(global, "alwaysHaveNoTasks", new HashSet<>()));
        when(renderer.getChunksDebugString()).thenReturn("C: 1/2");
        assertEquals("C: 1/2", global.getDebugInfoRenders());

        // Each layer draws at the interpolated camera with the atlas and lightmap bound
        int defaultTexUnit = OpenGlHelper.defaultTexUnit;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            TextureMap atlas = mock(TextureMap.class);
            when(client.getTextureMapBlocks()).thenReturn(atlas);
            Entity camera = mock(Entity.class);
            camera.lastTickPosX = 0;
            camera.posX = 10;
            camera.lastTickPosY = 60;
            camera.posY = 64;
            camera.lastTickPosZ = -2;
            camera.posZ = 2;
            assertEquals(1, global.renderBlockLayer(BlockRenderLayer.CUTOUT, 0.5, 0, camera));
            verify(renderer).drawChunkLayer(BlockRenderLayer.CUTOUT, 5.0, 62.0, 0.0);
            verify(scene.entityRenderer).enableLightmap();
            verify(scene.entityRenderer).disableLightmap();

            // Terrain setup takes the camera's viewport, or one that culls nothing when the pack turns frustum culling off
            when(client.getRenderViewEntity()).thenReturn(camera);
            Viewport viewport = new Viewport((minX, minY, minZ, maxX, maxY, maxZ) -> false, new Vector3d(1, 2, 3));
            ICamera frustum = mock(ICamera.class, Mockito.withSettings().extraInterfaces(ViewportProvider.class));
            when(((ViewportProvider) frustum).impetus$createViewport()).thenReturn(viewport);
            global.setupTerrain(camera, 0.5, frustum, 7, false);
            verify(renderer).setupTerrain(eq(viewport), any(), eq(7), eq(false), eq(false));
            UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
            Mixins.set(Umbra.class, "renderingPipeline", pipeline);
            global.setupTerrain(camera, 0.5, frustum, 8, true);
            when(pipeline.shouldDisableFrustumCulling()).thenReturn(true);
            List<Viewport> seen = new ArrayList<>();
            Mockito.doAnswer(invocation -> seen.add(invocation.getArgument(0))).when(renderer)
                    .setupTerrain(any(), any(), eq(9), anyBoolean(), anyBoolean());
            global.setupTerrain(camera, 0.5, frustum, 9, false);
            assertTrue(seen.get(0).isBoxVisible(1.0e6, 1.0e6, 1.0e6, 1.0e6 + 1, 1.0e6 + 1, 1.0e6 + 1));
        } finally {
            OpenGlHelper.defaultTexUnit = defaultTexUnit;
        }
    }

    @Test
    void cloudsUseTheFaceCulledMeshWhereTheyCan() {
        Scene scene = new Scene(client);
        RenderGlobalMixin global = scene.global;
        Mixins.set(Extras.class, "config", new ExtrasConfig());
        WorldProvider provider = mock(WorldProvider.class);
        Mixins.set(scene.world, "provider", provider);
        options.quality.cloudHeight = 192;
        options.quality.cloudDistance = 4;
        try (MockedStatic<SodiumCloudRenderer> clouds = Mockito.mockStatic(SodiumCloudRenderer.class)) {
            // Clouds off
            assertTrue(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            when(scene.settings.shouldRenderClouds()).thenReturn(2);
            // The faster clouds option off, a non-surface world, a mod's own cloud renderer, or no cloud texture: vanilla draws them
            options.performance.useFasterClouds = false;
            assertFalse(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            options.performance.useFasterClouds = true;
            assertFalse(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            when(provider.isSurfaceWorld()).thenReturn(true);
            when(provider.getCloudRenderer()).thenReturn(mock(net.minecraftforge.client.IRenderHandler.class));
            assertFalse(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            when(provider.getCloudRenderer()).thenReturn(null);
            assertFalse(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            clouds.when(() -> SodiumCloudRenderer.isReady(client)).thenReturn(true);
            // The mesh draws at the configured height and a radius no smaller than vanilla's, under gbuffers_clouds with a pack
            scene.settings.renderDistanceChunks = 12;
            clouds.when(() -> SodiumCloudRenderer.render(any(), any(), any(), anyInt(), anyFloat(), anyInt(), anyDouble(),
                    anyDouble(), anyDouble(), anyBoolean(), anyInt(), anyFloat(), anyFloat())).thenReturn(true);
            assertTrue(cancels(global, "impetus$renderCloudsSodium", 0.5F, 2, 0.0, 0.0, 0.0));
            clouds.verify(() -> SodiumCloudRenderer.render(eq(client), eq(scene.world), any(), anyInt(), eq(0.5F), eq(2),
                    eq(0.0), eq(0.0), eq(0.0), eq(true), eq(32), eq(192.0F), anyFloat()));
            UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
            Mixins.set(Umbra.class, "renderingPipeline", pipeline);
            clouds.when(() -> SodiumCloudRenderer.render(any(), any(), any(), anyInt(), anyFloat(), anyInt(), anyDouble(),
                    anyDouble(), anyDouble(), anyBoolean(), anyInt(), anyFloat(), anyFloat())).thenReturn(false);
            assertFalse(cancels(global, "impetus$renderCloudsSodium", 0.5F, 1, 0.0, 0.0, 0.0));
            verify(pipeline).setPhase(ProgramId.Clouds);
            verify(pipeline).setPhase(null);
        } finally {
            Mixins.set(Extras.class, "config", null);
        }
        // A far cloud distance reaches out past vanilla's extent, but never beyond the cloud projection
        options.quality.cloudDistance = 64;
        assertEquals(64, (int) Mixins.call(global, "impetus$cloudRadiusCells", 12.0F));
        scene.settings.renderDistanceChunks = 2;
        assertEquals(32, (int) Mixins.call(global, "impetus$cloudRadiusCells", 12.0F));
        // The vanilla fallback keeps the configured height
        assertEquals(192.0F, (float) Mixins.call(global, "getConfiguredFastCloudHeight", (Object) null));
        assertEquals(192.0F, (float) Mixins.call(global, "getConfiguredFancyCloudHeight", (Object) null));
    }

    @Test
    void blockEntitiesAndEntitiesDrawFromTheVisibleSections() {
        Scene scene = new Scene(client);
        RenderGlobalMixin global = scene.global;
        ImpetusWorldRenderer renderer = scene.renderer;
        int renderPass = Statics.get(ForgeHooksClient.class, "renderPass");
        double distanceWeight = Statics.get(Entity.class, "renderDistanceWeight");
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class); MockedStatic<GL13> gl13 = Mockito.mockStatic(GL13.class)) {
            // Block entities come from the renderer, plus any a mod put straight into RenderGlobal's own set
            CallbackInfo ci = Mixins.ci();
            Mixins.call(global, "impetus$renderTileEntities", null, null, 0.5F, ci, 0);
            assertTrue(ci.isCancelled());
            verify(renderer).renderBlockEntities(any());
            TileEntity skipped = mock(TileEntity.class);
            Set<TileEntity> modded = Mixins.get(global, "setTileEntities");
            modded.add(skipped);
            TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
            Mixins.set(dispatcher, "renderEngine", mock(TextureManager.class));
            try {
                Mixins.call(global, "impetus$renderTileEntities", null, null, 0.5F, Mixins.ci(), 0);
            } finally {
                Mixins.set(dispatcher, "renderEngine", null);
            }
            verify(skipped).shouldRenderInPass(0);

            // Entities are gathered once per frame at pass 0 and filtered by vanilla's checks and section visibility
            Statics.set(ForgeHooksClient.class, "renderPass", 0);
            EntityPlayerSP player = mock(EntityPlayerSP.class);
            when(player.shouldRenderInPass(0)).thenReturn(true);
            client.player = player;
            Entity visible = entity(64);
            Entity hidden = entity(64);
            Entity occluded = entity(64);
            Entity riding = entity(-5);
            Entity unloaded = entity(64);
            when(riding.isRidingOrBeingRiddenBy(player)).thenReturn(true);
            for (Entity entity : List.of(visible, occluded, unloaded)) {
                when(scene.renderManager.shouldRender(eq(entity), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(true);
            }
            when(renderer.isEntityVisible(visible)).thenReturn(true);
            when(renderer.isEntityVisible(unloaded)).thenReturn(true);
            when(scene.world.isBlockLoaded(any())).thenAnswer(invocation -> ((BlockPos) invocation.getArgument(0)).getX() == 0);
            unloaded.posX = 40;
            when(scene.renderManager.isRenderMultipass(visible)).thenReturn(true);
            Mixins.stub(global, "isOutlineActive", invocation -> invocation.getArgument(0) == visible);
            List<Entity> loaded = new ArrayList<>(List.of(visible, hidden, occluded, riding, unloaded, player));
            Mixins.set(scene.world, "loadedEntityList", loaded);
            List<Entity> outlines = new ArrayList<>();
            List<Entity> multipass = new ArrayList<>();
            Mixins.call(global, "renderEntities", player, null, 0.5F, Mixins.ci(), outlines, multipass, 0.0, 64.0, 0.0);
            verify(scene.renderManager).renderEntityStatic(visible, 0.5F, false);
            verify(scene.renderManager).renderEntityStatic(riding, 0.5F, false);
            verify(scene.renderManager, never()).renderEntityStatic(hidden, 0.5F, false);
            verify(scene.renderManager, never()).renderEntityStatic(occluded, 0.5F, false);
            verify(scene.renderManager, never()).renderEntityStatic(unloaded, 0.5F, false);
            verify(scene.renderManager, never()).renderEntityStatic(player, 0.5F, false);
            assertEquals(List.of(visible), outlines);
            assertEquals(List.of(visible), multipass);
            assertEquals(2, (int) Mixins.get(global, "countEntitiesRendered"));

            // The translucent pass reuses the gathered list, drawing in the pack's translucent entity phase
            Statics.set(ForgeHooksClient.class, "renderPass", 1);
            UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
            Mixins.set(Umbra.class, "renderingPipeline", pipeline);
            when(pipeline.isRenderingPostDeferredTranslucents()).thenReturn(true);
            when(pipeline.getTranslucentEntityPhase()).thenReturn(ProgramId.EntitiesTrans);
            Mixins.call(global, "renderEntities", player, null, 0.5F, Mixins.ci(), outlines, multipass, 0.0, 64.0, 0.0);
            verify(pipeline).setPhase(ProgramId.EntitiesTrans, 11);
            // In third person, or asleep, the player's own body draws too
            Statics.set(ForgeHooksClient.class, "renderPass", 0);
            scene.settings.thirdPersonView = 1;
            Mixins.call(global, "renderEntities", player, null, 0.5F, Mixins.ci(), outlines, multipass, 0.0, 64.0, 0.0);
            verify(scene.renderManager).renderEntityStatic(player, 0.5F, false);
            scene.settings.thirdPersonView = 0;
            when(player.isPlayerSleeping()).thenReturn(true);
            Mixins.call(global, "renderEntities", player, null, 0.5F, Mixins.ci(), outlines, multipass, 0.0, 64.0, 0.0);
            verify(scene.renderManager, times(2)).renderEntityStatic(player, 0.5F, false);
        } finally {
            Statics.set(ForgeHooksClient.class, "renderPass", renderPass);
            Statics.set(Entity.class, "renderDistanceWeight", distanceWeight);
        }
    }

    // A mob at the origin column and the given height, drawn in the opaque pass
    private static Entity entity(double y) {
        Entity entity = mock(Entity.class);
        entity.posY = y;
        when(entity.shouldRenderInPass(0)).thenReturn(true);
        return entity;
    }
}
