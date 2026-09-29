package com.bdmajora.impetus.impl.render.terrain;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkStatus;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTrackerHolder;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggingInference;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;
import com.bdmajora.impetus.impl.extensions.TextureMapExtension;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.impl.render.terrain.compile.light.VintageDiffuseProvider;
import com.bdmajora.impetus.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import com.bdmajora.impetus.impl.render.terrain.fog.GLStateManagerFogService;
import com.bdmajora.impetus.impl.render.terrain.sprite.SpriteUtil;
import com.bdmajora.impetus.impl.world.WorldSlice;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSection;
import com.bdmajora.impetus.impl.world.cloned.ClonedChunkSectionCache;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.mixin.core.terrain.BakedQuadMixin;
import com.bdmajora.impetus.mixin.core.terrain.BlockColorsAccessor;
import com.bdmajora.impetus.mixin.core.terrain.RenderGlobalMixin;
import com.bdmajora.impetus.mixin.core.terrain.VertexFormatMixin;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Sections;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestFogService;
import com.bdmajora.testing.TestGl;
import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.registries.IRegistryDelegate;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A world of mocked chunks holding real block storage, meshed through the Impetus section manager, world slice and block renderer
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TerrainMeshingTest {
    private static final BlockPos GLASS = new BlockPos(1, 1, 1);
    private static final BlockPos TORCH = new BlockPos(2, 1, 1);
    private static final BlockPos WATER = new BlockPos(3, 1, 1);
    private static final BlockPos CHEST = new BlockPos(4, 1, 1);
    private static final BlockPos BEACON = new BlockPos(5, 1, 1);
    private static final BlockPos LONE_CHEST = new BlockPos(6, 1, 1);
    private static final BlockPos EMITTER = new BlockPos(7, 1, 1);
    private static final BlockPos GRASS = new BlockPos(8, 1, 1);
    private static final BlockPos SLAB = new BlockPos(9, 1, 1);
    private static final CancellationToken RUNNING = token(false);

    // A block drawing nothing yet giving off more light than vanilla's range, the case voxelizeLightBlocks attributes; made once the registries are up
    private static Block EMITTING;
    // Declares a tile entity but cannot make one, as a broken mod block would
    private static Block BROKEN_CONTAINER;
    // Holds a fluid but asks for it not to be drawn
    private static Block HIDING;

    private static final class Hiding extends Block implements IFluidloggable {
        Hiding() {
            super(Material.ROCK);
        }

        @Override
        public boolean shouldFluidRender(IBlockAccess world, BlockPos pos, IBlockState here, FluidState fluid) {
            return false;
        }
    }

    private ImpetusGameOptions previousOptions;
    private ImpetusGameOptions options;
    private TileEntityRendererDispatcher previousBlockEntities;
    private GLRenderDevice device;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // Fluidlogged API asks Forge's mod list as it initialises
        Mc.forge();
        EMITTING = new Block(Material.ROCK) {
            @Override
            public EnumBlockRenderType getRenderType(IBlockState state) {
                return EnumBlockRenderType.INVISIBLE;
            }

            @Override
            public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
                return 300;
            }
        };
        HIDING = new Hiding();
        BROKEN_CONTAINER = new Block(Material.ROCK) {
            @Override
            public boolean hasTileEntity(IBlockState state) {
                return true;
            }

            @Override
            public TileEntity createTileEntity(World world, IBlockState state) {
                throw new IllegalStateException("no tile entity");
            }
        };
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
        }
        Launch.blackboard.putIfAbsent("fml.deobfuscatedEnvironment", true);
    }

    @BeforeEach
    void settle() throws ClassNotFoundException {
        Class.forName(ImpetusVintage.class.getName());
        previousOptions = Statics.get(ImpetusVintage.class, "CONFIG");
        options = ImpetusGameOptions.defaults();
        // Builds run on the calling thread and the graph is walked synchronously, so a frame finishes before it returns
        options.performance.chunkBuilderThreads = -1;
        options.performance.asyncOcclusionMode = AsyncOcclusionMode.NONE;
        options.performance.alwaysDeferChunkUpdates = false;
        Statics.set(ImpetusVintage.class, "CONFIG", options);
        FulgorConfig fulgor = Mc.uninitialized(FulgorConfig.class);
        Mixins.set(FulgorConfig.class, "instance", fulgor);
        Mixins.set(Extras.class, "config", new ExtrasConfig());
        previousBlockEntities = TileEntityRendererDispatcher.instance;
        ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER = true;
        device = Devices.active();
        when(TestGl.gl().glGetUniformLocation(anyInt(), any())).thenReturn(1);
        TestFogService.cutoff = 1_000_000f;
    }

    @AfterEach
    void restore() {
        TestFogService.cutoff = 1f;
        device.makeInactive();
        Statics.set(ImpetusVintage.class, "CONFIG", previousOptions);
        Mixins.set(FulgorConfig.class, "instance", null);
        Mixins.set(Extras.class, "config", null);
        Statics.set(TileEntityRendererDispatcher.class, "instance", previousBlockEntities);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        WorldRenderingSettings.setVoxelizeLightBlocks(false);
        WorldRenderingSettings.setBlockRenderLayers(null);
        BakedEntities.chests = false;
    }

    private static CancellationToken token(boolean cancelled) {
        return new CancellationToken() {
            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public void setCancelled() {}
        };
    }

    // Position, colour, UV and lightmap at 7 ints a vertex, with the first element given
    private static VertexFormat format(VertexFormatElement first) {
        VertexFormatMixin format = Mixins.instance(VertexFormatMixin.class);
        doReturn(7).when(format).getIntegerSize();
        doReturn(12).when(format).getColorOffset();
        doReturn(true).when(format).hasUvOffset(0);
        doReturn(16).when(format).getUvOffsetById(0);
        doReturn(true).when(format).hasUvOffset(1);
        doReturn(24).when(format).getUvOffsetById(1);
        doReturn(-1).when(format).getNormalOffset();
        VertexFormat vertexFormat = (VertexFormat) (Object) format;
        doReturn(first).when(vertexFormat).getElement(0);
        return vertexFormat;
    }

    // Vanilla's corners for each face of a unit cube, wound counter-clockwise from outside
    private static float[][] corners(EnumFacing face) {
        return switch (face) {
            case DOWN -> new float[][] {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
            case UP -> new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case NORTH -> new float[][] {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
            case SOUTH -> new float[][] {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
            case WEST -> new float[][] {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            case EAST -> new float[][] {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
        };
    }

    private static BakedQuad quad(VertexFormat format, EnumFacing face, TextureAtlasSprite sprite, int tint, boolean shade) {
        float[][] corners = corners(face);
        int[] data = new int[28];
        for (int i = 0; i < 4; i++) {
            data[i * 7] = Float.floatToRawIntBits(corners[i][0]);
            data[i * 7 + 1] = Float.floatToRawIntBits(corners[i][1]);
            data[i * 7 + 2] = Float.floatToRawIntBits(corners[i][2]);
            data[i * 7 + 3] = 0xFFFFFFFF;
            data[i * 7 + 4] = Float.floatToRawIntBits(0.25F * i);
            data[i * 7 + 5] = Float.floatToRawIntBits(0.125F * i);
            data[i * 7 + 6] = 0x00100010 * i;
        }
        BakedQuadMixin quad = Mixins.instance(BakedQuadMixin.class);
        doReturn(data).when(quad).getVertexData();
        doReturn(format).when(quad).getFormat();
        Mixins.set(quad, "face", face);
        Mixins.set(quad, "applyDiffuseLighting", shade);
        Mixins.set(quad, "tintIndex", tint);
        Mixins.set(quad, "sprite", sprite);
        return (BakedQuad) (Object) quad;
    }

    private static TextureAtlasSprite sprite(boolean animated, SpriteTransparencyLevel level) {
        TextureAtlasSprite sprite = Mc.mock(TextureAtlasSprite.class, SpriteExtension.class, SpriteTransparencyLevel.Holder.class);
        when(sprite.hasAnimationMetadata()).thenReturn(animated);
        when(((SpriteTransparencyLevel.Holder) sprite).impetus$getTransparencyLevel()).thenReturn(level);
        return sprite;
    }

    // One flat quad along the top of a block, written the way vanilla's fluid and special renderers fill a buffer
    private static void writeQuad(BufferBuilder buffer, BlockPos pos, float u) {
        double x = pos.getX();
        double y = pos.getY() + 0.875;
        double z = pos.getZ();
        buffer.pos(x, y, z).color(255, 255, 255, 255).tex(u, 0.5).lightmap(240, 240).endVertex();
        buffer.pos(x, y, z + 1).color(255, 255, 255, 255).tex(u, 0.6).lightmap(240, 240).endVertex();
        buffer.pos(x + 1, y, z + 1).color(255, 255, 255, 255).tex(u + 0.1, 0.6).lightmap(240, 240).endVertex();
        buffer.pos(x + 1, y, z).color(255, 255, 255, 255).tex(u + 0.1, 0.5).lightmap(240, 240).endVertex();
    }

    // The client, a world of 3x3 chunks around the origin and the models, colours and sprites the mesher asks for
    private static final class Terrain {
        final Minecraft client = Mc.client();
        final WorldClient world = Mc.mock(WorldClient.class, ChunkTrackerHolder.class);
        final WorldProvider dimension = mock(WorldProvider.class);
        // The client's own radius, so a column is only ready once every neighbour is loaded
        final ChunkTracker tracker = new ChunkTracker();
        final Map<Long, Chunk> chunks = new HashMap<>();
        final Map<Long, Map<BlockPos, TileEntity>> tiles = new HashMap<>();
        final BlockRendererDispatcher dispatcher = mock(BlockRendererDispatcher.class);
        final IBakedModel model = mock(IBakedModel.class);
        final TextureMap atlas = Mc.mock(TextureMap.class, TextureMapExtension.class);
        final TextureManager textures = mock(TextureManager.class);
        final TextureAtlasSprite still = sprite(false, SpriteTransparencyLevel.OPAQUE);
        final TextureAtlasSprite animated = sprite(true, SpriteTransparencyLevel.TRANSPARENT);
        final Map<IRegistryDelegate<Block>, IBlockColor> colors = new HashMap<>();
        final List<Runnable> scheduled = new ArrayList<>();
        // What vanilla's dispatcher was asked to draw where; the mesher's position is mutable, so it is copied at the call
        final Map<BlockPos, IBlockState> vanilla = new HashMap<>();
        final GameSettings settings = Mc.uninitialized(GameSettings.class);
        final TileEntityRendererDispatcher blockEntities = mock(TileEntityRendererDispatcher.class);
        final TileEntity chestEntity = mock(TileEntity.class);
        final TileEntity beaconEntity = mock(TileEntity.class);
        final Map<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
        final List<BakedQuad> general;
        final List<BakedQuad> broken;
        final BiomeColorHelper.ColorResolver tint = (biome, pos) -> biome == Biomes.FOREST ? 0x336699 : 0x112233;

        Terrain() {
            when(((ChunkTrackerHolder) world).impetus$getTracker()).thenReturn(tracker);
            when(dimension.hasSkyLight()).thenReturn(true);
            Mixins.set(world, "provider", dimension);
            when(world.getWorldType()).thenReturn(WorldType.DEFAULT);
            when(world.getHeight()).thenReturn(256);
            when(world.getBiomeProvider()).thenReturn(mock(BiomeProvider.class));
            when(world.getChunk(anyInt(), anyInt())).thenAnswer(invocation ->
                    chunks.get(ChunkPos.asLong(invocation.getArgument(0), invocation.getArgument(1))));
            when(world.getChunk(any(BlockPos.class))).thenAnswer(invocation -> {
                BlockPos pos = invocation.getArgument(0);
                return chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            });
            when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> state(invocation.getArgument(0)));
            when(world.isBlockLoaded(any(BlockPos.class))).thenReturn(true);

            Mixins.set(client, "world", world);
            Mixins.set(client, "gameSettings", settings);
            Mixins.set(client, "renderChunksMany", true);
            settings.renderDistanceChunks = 2;
            settings.ambientOcclusion = 2;
            settings.mipmapLevels = 4;
            when(client.getBlockRendererDispatcher()).thenReturn(dispatcher);
            BlockModelShapes shapes = mock(BlockModelShapes.class);
            when(dispatcher.getBlockModelShapes()).thenReturn(shapes);
            when(shapes.getModelForState(any())).thenReturn(model);
            BlockColors blockColors = Mc.mock(BlockColors.class, BlockColorsAccessor.class);
            when(((BlockColorsAccessor) blockColors).getBlockColorMap()).thenReturn(colors);
            when(client.getBlockColors()).thenReturn(blockColors);
            // Grass tints through the slice's own biome blend, which is what the block access hands a colour provider
            colors.put(Blocks.GRASS.delegate, (state, access, pos, index) -> ((ImpetusBlockAccess) access).getBlockTint(pos, tint));
            when(client.getTextureMapBlocks()).thenReturn(atlas);
            when(((TextureMapExtension) atlas).impetus$findFromUV(anyFloat(), anyFloat())).thenAnswer(invocation ->
                    invocation.<Float>getArgument(0) < 0.5F ? animated : still);
            when(textures.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)).thenReturn(atlas);
            when(client.getTextureManager()).thenReturn(textures);
            when(client.addScheduledTask(any(Runnable.class))).thenAnswer(invocation -> {
                scheduled.add(invocation.getArgument(0));
                return null;
            });

            VertexFormat format = format(DefaultVertexFormats.POSITION_3F);
            for (EnumFacing face : EnumFacing.VALUES) {
                faces.put(face, List.of(quad(format, face, still, -1, true)));
            }
            // Two quads on the top face with different shading, so the analyzer compares them
            faces.put(EnumFacing.UP, List.of(quad(format, EnumFacing.UP, still, -1, true), quad(format, EnumFacing.UP, animated, -1, false)));
            general = List.of(quad(format, EnumFacing.UP, animated, 0, true));
            broken = List.of(quad(format(DefaultVertexFormats.COLOR_4UB), EnumFacing.UP, still, -1, true));
            when(model.isAmbientOcclusion(any(IBlockState.class))).thenReturn(true);
            when(model.getQuads(any(), any(), anyLong())).thenAnswer(invocation -> {
                IBlockState state = invocation.getArgument(0);
                EnumFacing side = invocation.getArgument(1);
                if (state.getBlock() == Blocks.OBSIDIAN) {
                    return side == null ? broken : List.of();
                }
                return side == null ? general : faces.get(side);
            });
            // Chests draw nothing into their layer, lava blows up with a crash report, anything else is one quad
            when(dispatcher.renderBlock(any(), any(), any(), any())).thenAnswer(invocation -> {
                IBlockState state = invocation.getArgument(0);
                vanilla.put(invocation.<BlockPos>getArgument(1).toImmutable(), state);
                if (state.getBlock() == Blocks.CHEST) {
                    return false;
                }
                if (state.getBlock() == Blocks.LAVA) {
                    throw new ReportedException(CrashReport.makeCrashReport(new IllegalStateException("lava"), "Rendering lava"));
                }
                writeQuad(invocation.getArgument(3), invocation.getArgument(1), state.getBlock() == Blocks.WATER ? 0.25F : 0.75F);
                return true;
            });

            Statics.set(TileEntityRendererDispatcher.class, "instance", blockEntities);
            TileEntitySpecialRenderer<TileEntity> culled = mock(TileEntitySpecialRenderer.class);
            TileEntitySpecialRenderer<TileEntity> global = mock(TileEntitySpecialRenderer.class);
            when(global.isGlobalRenderer(any())).thenReturn(true);
            doReturn(culled).when(blockEntities).getRenderer(chestEntity);
            doReturn(global).when(blockEntities).getRenderer(beaconEntity);

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    chunk(x, z);
                }
            }
        }

        // A loaded column of empty sections whose biome is forest
        Chunk chunk(int chunkX, int chunkZ) {
            Chunk chunk = mock(Chunk.class);
            when(chunk.getBlockStorageArray()).thenReturn(new ExtendedBlockStorage[16]);
            byte[] biomes = new byte[256];
            Arrays.fill(biomes, (byte) Biome.getIdForBiome(Biomes.FOREST));
            when(chunk.getBiomeArray()).thenReturn(biomes);
            Map<BlockPos, TileEntity> entities = new HashMap<>();
            tiles.put(ChunkPos.asLong(chunkX, chunkZ), entities);
            when(chunk.getTileEntityMap()).thenReturn(entities);
            when(chunk.getBiome(any(), any())).thenReturn(Biomes.FOREST);
            chunks.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
            tracker.onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
            return chunk;
        }

        ExtendedBlockStorage storage(int x, int y, int z) {
            ExtendedBlockStorage[] array = chunks.get(ChunkPos.asLong(x >> 4, z >> 4)).getBlockStorageArray();
            if (array[y >> 4] == null) {
                array[y >> 4] = new ExtendedBlockStorage(y & ~15, true);
            }
            return array[y >> 4];
        }

        void set(BlockPos pos, IBlockState state) {
            storage(pos.getX(), pos.getY(), pos.getZ()).set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
        }

        IBlockState state(BlockPos pos) {
            Chunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (chunk == null || pos.getY() < 0 || pos.getY() > 255) {
                return Blocks.AIR.getDefaultState();
            }
            ExtendedBlockStorage section = chunk.getBlockStorageArray()[pos.getY() >> 4];
            return section == null ? Blocks.AIR.getDefaultState() : section.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }

        void tile(BlockPos pos, TileEntity entity) {
            tiles.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)).put(pos, entity);
        }

        // A stone floor under a row of every kind of block the mesher treats differently, sky lit above and torch lit beside
        Terrain build() {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    set(new BlockPos(x, 0, z), Blocks.STONE.getDefaultState());
                    for (int y = 1; y < 16; y++) {
                        storage(x, y, z).setSkyLight(x, y, z, 15);
                    }
                }
            }
            set(GLASS, Blocks.GLASS.getDefaultState());
            set(TORCH, Blocks.TORCH.getDefaultState());
            storage(3, 1, 1).setBlockLight(3, 1, 1, 13);
            set(WATER, Blocks.WATER.getDefaultState());
            set(CHEST, Blocks.CHEST.getDefaultState());
            tile(CHEST, chestEntity);
            set(BEACON, Blocks.BEACON.getDefaultState());
            tile(BEACON, beaconEntity);
            set(LONE_CHEST, Blocks.CHEST.getDefaultState());
            set(EMITTER, EMITTING.getDefaultState());
            set(GRASS, Blocks.GRASS.getDefaultState());
            set(SLAB, Blocks.STONE_SLAB.getDefaultState());
            // A tile entity above the section is not the section's to copy
            tile(new BlockPos(4, 40, 4), mock(TileEntity.class));
            // The next column over has a floor of its own, so it builds too
            set(new BlockPos(16, 0, 8), Blocks.STONE.getDefaultState());
            return this;
        }

        ChunkRenderContext snapshot(ClonedChunkSectionCache cache) {
            return WorldSlice.prepare(world, new SectionPos(0, 0, 0), cache);
        }
    }

    private static RenderPassConfiguration<BlockRenderLayer> passes() {
        return VintageRenderPassConfigurationBuilder.build(ChunkMeshFormats.COMPACT);
    }

    private static ChunkBuildOutput mesh(Terrain terrain, VintageChunkBuildContext context) {
        ChunkRenderContext snapshot = terrain.snapshot(new ClonedChunkSectionCache(terrain.world));
        return new ChunkBuilderMeshingTask(new RenderSection(null, 0, 0, 0), snapshot, 3, new Vector3d(8, 20, 8)).execute(context, RUNNING);
    }

    @SuppressWarnings("unchecked")
    private static MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> data(ChunkBuildOutput output) {
        return (MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>) output.info;
    }

    @Test
    @Order(1)
    void fluidloggedWaterIsCopiedGuessedAndMeshed() {
        // First in the class: IS_LOADED is a static final, and code compiled while it read false would keep reading false
        Terrain terrain = new Terrain().build();
        IBlockState water = Blocks.WATER.getDefaultState();
        IBlockState fence = Blocks.OAK_FENCE.getDefaultState();
        BlockPos submerged = new BlockPos(12, 1, 5);
        BlockPos buried = new BlockPos(13, 1, 5);
        BlockPos banked = new BlockPos(14, 1, 7);
        BlockPos scorched = new BlockPos(12, 1, 9);
        BlockPos served = new BlockPos(12, 1, 11);
        BlockPos hidden = new BlockPos(12, 1, 13);
        terrain.set(submerged, fence);
        terrain.set(submerged.up(), water);
        terrain.set(buried, Blocks.STONE.getDefaultState());
        terrain.set(buried.up(), water);
        terrain.set(banked, fence);
        terrain.set(banked.west(), water);
        terrain.set(banked.east(), water);
        terrain.set(scorched, fence);
        terrain.set(scorched.up(), Blocks.FLOWING_LAVA.getDefaultState());
        terrain.set(served, fence);
        terrain.set(hidden, HIDING.getDefaultState());
        // The server reports water in two blocks of the section and one far above it
        IFluidStateContainer container = new IFluidStateContainer() {
            @Override
            public FluidState getFluidState(int x, int y, int z, FluidState fallback) {
                return fallback;
            }

            @Override
            public void forEach(ContainerAction action) {
                action.accept((char) (served.getX() | served.getZ() << 4 | served.getY() << 8), new FluidState(water));
                action.accept((char) (hidden.getX() | hidden.getZ() << 4 | hidden.getY() << 8), new FluidState(water));
                action.accept((char) (1 | 1 << 4 | 40 << 8), new FluidState(water));
            }
        };
        IFluidStateCapability.next[0] = y -> container;
        options.quality.inferredFluidlogging = ImpetusGameOptions.FluidloggingGuess.TOUCHING;
        Mixins.set(FluidloggingInference.class, "checkedManager", null);
        Mixins.set(FluidloggingInference.class, "remoteHasMod", false);
        // Initialised before the flag is swapped, or its own initialiser would put it back
        new FluidloggedCompat();
        assertSame(FluidState.EMPTY, FluidloggedCompat.getEmptyFluidState());
        Statics.set(FluidloggedCompat.class, "IS_LOADED", true);
        try {
            ClonedChunkSectionCache cache = new ClonedChunkSectionCache(terrain.world);
            WorldSlice slice = new WorldSlice(terrain.world);
            slice.copyData(terrain.snapshot(cache));
            assertTrue(FluidloggingInference.isActive());
            assertFalse(cache.acquire(0, 0, 0).getFluidData().isEmpty());
            assertTrue(cache.acquire(0, -1, 0).getFluidData().isEmpty());
            // Server data is copied, a fence under or between water sources is guessed full, stone and lava are not
            assertSame(water, slice.getFluidState(served.getX(), served.getY(), served.getZ()).getState());
            assertSame(water, slice.getFluidState(submerged.getX(), submerged.getY(), submerged.getZ()).getState());
            assertSame(water, slice.getFluidState(banked.getX(), banked.getY(), banked.getZ()).getState());
            assertTrue(slice.getFluidState(buried.getX(), buried.getY(), buried.getZ()).isEmpty());
            assertTrue(slice.getFluidState(scorched.getX(), scorched.getY(), scorched.getZ()).isEmpty());
            assertTrue(slice.getFluidState(submerged.getX(), 40, submerged.getZ()).isEmpty());
            assertSame(FluidState.EMPTY, slice.getFluidState(100, 1, 1));

            // The mesher draws each contained fluid through vanilla's fluid renderer, unless the block hides it
            VintageChunkBuildContext context = new VintageChunkBuildContext(terrain.world, passes());
            ChunkBuildOutput output = mesh(terrain, context);
            assertSame(water, terrain.vanilla.get(submerged));
            assertSame(water, terrain.vanilla.get(served));
            assertFalse(terrain.vanilla.containsKey(hidden));
            assertFalse(terrain.vanilla.containsKey(scorched));
            assertFalse(terrain.vanilla.containsKey(buried));
            context.cleanup();
            output.delete();

            // A server running the mod owns the data, so the guess turns off; so does the option
            NetHandlerPlayClient connection = mock(NetHandlerPlayClient.class);
            NetworkManager manager = mock(NetworkManager.class);
            Channel channel = mock(Channel.class);
            Attribute<NetworkDispatcher> attribute = mock(Attribute.class);
            NetworkDispatcher dispatcher = mock(NetworkDispatcher.class);
            when(terrain.client.getConnection()).thenReturn(connection);
            when(connection.getNetworkManager()).thenReturn(manager);
            when(manager.channel()).thenReturn(channel);
            doReturn(attribute).when(channel).attr(any());
            when(attribute.get()).thenReturn(dispatcher);
            when(dispatcher.getModList()).thenReturn(Map.of(FluidloggedCompat.MODID, "3.0.6"));
            FluidloggingInference.refresh();
            assertFalse(FluidloggingInference.isActive());
            FluidloggingInference.refresh();
            assertFalse(FluidloggingInference.isActive());
            options.quality.inferredFluidlogging = ImpetusGameOptions.FluidloggingGuess.OFF;
            FluidloggingInference.refresh();
            assertFalse(FluidloggingInference.isActive());
            FluidloggingInference.apply(slice, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        } finally {
            Statics.set(FluidloggedCompat.class, "IS_LOADED", false);
            IFluidStateCapability.next[0] = null;
            Mixins.set(FluidloggingInference.class, "checkedManager", null);
            Mixins.set(FluidloggingInference.class, "remoteHasMod", false);
            Mixins.set(FluidloggingInference.class, "mode", ImpetusGameOptions.FluidloggingGuess.OFF);
        }
    }

    @Test
    void aSectionMeshesThroughTheVintageBlockRenderer() {
        Terrain terrain = new Terrain().build();
        VintageChunkBuildContext context = new VintageChunkBuildContext(terrain.world, passes());
        ChunkBuildOutput output = mesh(terrain, context);
        MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> data = data(output);
        assertTrue(data.hasBlockGeometry);
        assertFalse(output.meshes.isEmpty());
        // The chest's renderer is culled with the section, the beacon's is global, and the chest missing its entity gets none
        assertEquals(List.of(terrain.chestEntity), data.culledBlockEntities);
        assertEquals(List.of(terrain.beaconEntity), data.globalBlockEntities);
        assertTrue(data.animatedSprites.contains(terrain.animated));
        assertFalse(data.animatedSprites.contains(terrain.still));
        // Water and the chest go through vanilla's dispatcher; modelled blocks never do
        assertSame(Blocks.WATER.getDefaultState(), terrain.vanilla.get(WATER));
        assertSame(Blocks.CHEST.getDefaultState(), terrain.vanilla.get(CHEST));
        assertSame(EMITTING.getDefaultState(), terrain.vanilla.get(EMITTER));
        assertFalse(terrain.vanilla.containsValue(Blocks.STONE.getDefaultState()));
        // The chest without its entity was handed a stand-in and the real one is queued for the main thread
        assertEquals(1, terrain.scheduled.size());
        assertEquals(0, context.getOffX());
        assertNotNull(context.getWorldSlice());
        assertNotNull(context.getBlockRenderer());
        context.cleanup();
        output.delete();

        // A cancelled task stops at the next layer of blocks
        ChunkRenderContext snapshot = terrain.snapshot(new ClonedChunkSectionCache(terrain.world));
        assertNull(new ChunkBuilderMeshingTask(new RenderSection(null, 0, 0, 0), snapshot, 4, new Vector3d()).execute(context, token(true)));
        context.cleanup();
    }

    @Test
    void underAShaderPackEveryQuadCarriesItsBlockAttributes() {
        Terrain terrain = new Terrain().build();
        WorldRenderingSettings.setVoxelizeLightBlocks(true);
        // The pack moves glass into the translucent layer
        WorldRenderingSettings.setBlockRenderLayers(Map.of(Blocks.GLASS, BlockRenderLayer.TRANSLUCENT));
        BakedEntities.chests = true;
        AtomicBoolean shaders = new AtomicBoolean(true);
        try (MockedStatic<UmbraTerrainProgramOverride> overrides = Mockito.mockStatic(UmbraTerrainProgramOverride.class)) {
            overrides.when(UmbraTerrainProgramOverride::areShadersActive).thenAnswer(invocation -> shaders.get());
            VintageChunkBuildContext context = new VintageChunkBuildContext(terrain.world, passes());
            ChunkBuildOutput modelled = mesh(terrain, context);
            assertTrue(data(modelled).hasBlockGeometry);
            // Glass is forced into the translucent layer, and the invisible emitter still reaches vanilla's solid buffer
            assertSame(EMITTING.getDefaultState(), terrain.vanilla.get(EMITTER));
            assertFalse(terrain.vanilla.containsKey(GLASS));
            context.cleanup();
            modelled.delete();

            // With the fast renderer off, every block goes through vanilla's dispatcher and its quads are attributed afterwards
            ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER = false;
            ChunkBuildOutput vanilla = mesh(terrain, context);
            assertTrue(data(vanilla).hasBlockGeometry);
            assertSame(Blocks.STONE.getDefaultState(), terrain.vanilla.get(new BlockPos(0, 0, 0)));
            assertSame(Blocks.GLASS.getDefaultState(), terrain.vanilla.get(GLASS));
            assertTrue(data(vanilla).animatedSprites.contains(terrain.animated));
            context.cleanup();
            vanilla.delete();

            // A quad buffered before the pack came up has no attribution run, so its block is found from its own corners
            shaders.set(false);
            MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> late = new MinecraftBuiltRenderSectionData<>();
            context.buffers.init(late, 0);
            context.setupTranslation(0, 0, 0);
            BlockPos past = new BlockPos(20, 1, 1);
            writeQuad(context.getBufferForLayer(BlockRenderLayer.SOLID), past, 0.25F);
            context.recordVanillaBlockAttribution(BlockRenderLayer.SOLID, Blocks.STONE.getDefaultState(), past);
            shaders.set(true);
            context.recordVanillaBlockAttribution(BlockRenderLayer.CUTOUT, Blocks.STONE.getDefaultState(), past);
            context.convertVanillaDataToImpetusData(context.buffers);
            assertTrue(late.animatedSprites.contains(terrain.animated));
            context.cleanup();
        } finally {
            ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER = true;
        }
    }

    @Test
    void aFailingBlockCrashesWithTheBlockItWasRendering() {
        Terrain terrain = new Terrain().build();
        VintageChunkBuildContext context = new VintageChunkBuildContext(terrain.world, passes());
        // A model whose quads do not start with a position is refused, and the refusal becomes a crash report
        terrain.set(GLASS, Blocks.OBSIDIAN.getDefaultState());
        ReportedException refused = assertThrows(ReportedException.class, () -> mesh(terrain, context));
        assertTrue(refused.getCrashReport().getCompleteReport().contains("Block being rendered"));
        assertInstanceOf(IllegalStateException.class, refused.getCrashReport().getCrashCause());
        context.cleanup();

        // A crash report thrown by the block itself keeps its cause and gains the block
        terrain.set(GLASS, Blocks.GLASS.getDefaultState());
        terrain.set(WATER, Blocks.LAVA.getDefaultState());
        ReportedException lava = assertThrows(ReportedException.class, () -> mesh(terrain, context));
        assertEquals("lava", lava.getCrashReport().getCrashCause().getMessage());
        assertTrue(lava.getCrashReport().getCompleteReport().contains("Chunk section"));
        context.cleanup();
    }

    @Test
    void theWorldRendererDrivesTheVintageSectionManager() {
        Terrain terrain = new Terrain().build();
        ImpetusWorldRenderer renderer = new ImpetusWorldRenderer();
        RenderGlobalMixin global = Mixins.instance(RenderGlobalMixin.class);
        Mixins.set(global, "renderer", renderer);
        Mixins.set(terrain.client, "renderGlobal", global);
        assertSame(renderer, ImpetusWorldRenderer.instance());
        assertEquals(0, renderer.getMinimumBuildHeight());
        assertEquals(2, renderer.getEffectiveRenderDistance());
        FloatBuffer identity = FloatBuffer.wrap(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "PROJECTION").clear();
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "PROJECTION").put(identity).flip();
        identity.rewind();
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "MODELVIEW").clear();
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "MODELVIEW").put(identity).flip();

        renderer.setWorld(terrain.world);
        assertEquals(256, renderer.getMaximumBuildHeight());
        VintageRenderSectionManager manager = renderer.getRenderSectionManager();
        assertNotNull(manager.getSectionCache());
        Viewport viewport = Sections.viewport(8, 20, 8);
        SimpleWorldRenderer.CameraState camera = new SimpleWorldRenderer.CameraState(8, 20, 8, 0, 0, 1000f);
        for (int frame = 0; frame < 4; frame++) {
            renderer.setupTerrain(viewport, camera, frame, false, true);
        }
        assertTrue(renderer.isSectionReady(0, 0, 0));
        // The next column over has unloaded neighbours of its own, so it is never handed to the renderer
        assertFalse(renderer.isSectionReady(1, 0, 0));
        assertTrue(renderer.getVisibleChunkCount() > 0);
        assertTrue(renderer.getChunksDebugString().startsWith("C: "));
        // The build results reached the section and its block entities were asked for their bounds
        verify(terrain.chestEntity, atLeastOnce()).getRenderBoundingBox();
        verify(terrain.beaconEntity, atLeastOnce()).getRenderBoundingBox();
        // Only the animated sprite in view is marked active
        verify((SpriteExtension) terrain.animated, atLeastOnce()).impetus$markActive();
        verify((SpriteExtension) terrain.still, never()).impetus$markActive();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            renderer.drawChunkLayer(layer, 8, 20, 8);
        }
        verify(TestGl.gl(), atLeastOnce()).glMultiDrawElementsBaseVertex(anyInt(), anyLong(), anyInt(), anyLong(), anyInt(), anyLong());
        // Each pass binds the atlas; cutout without mipmaps, the rest with them
        verify(terrain.textures, atLeastOnce()).bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        verify(terrain.atlas, atLeastOnce()).setBlurMipmapDirect(false, false);
        verify(terrain.atlas, atLeastOnce()).setBlurMipmapDirect(false, true);

        // Block entities draw per pass, a crash from an invalid one is swallowed and from a live one is not
        when(terrain.chestEntity.shouldRenderInPass(anyInt())).thenReturn(true);
        ImpetusWorldRenderer.TileEntityRenderContext tileContext = new ImpetusWorldRenderer.TileEntityRenderContext(new HashMap<>(), 0.5F);
        assertEquals(2, renderer.renderBlockEntities(tileContext));
        verify(terrain.blockEntities).render(terrain.chestEntity, 0.5F, -1);
        verify(terrain.blockEntities, never()).render(eq(terrain.beaconEntity), anyFloat(), anyInt());
        verify(terrain.blockEntities).preDrawBatch();
        verify(terrain.blockEntities).drawBatch(anyInt());
        doThrow(new IllegalStateException("tile")).when(terrain.blockEntities).render(eq(terrain.chestEntity), anyFloat(), anyInt());
        when(terrain.chestEntity.isInvalid()).thenReturn(true);
        assertEquals(2, renderer.renderBlockEntities(tileContext));
        when(terrain.chestEntity.isInvalid()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> renderer.renderBlockEntities(tileContext));

        // Entity culling asks the graph unless it is off, the entity outlines or names itself, or the shadow pass draws
        Entity near = mock(Entity.class);
        when(near.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(2, 1, 2, 3, 2, 3));
        Entity far = mock(Entity.class);
        when(far.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(200, 1, 200, 201, 2, 201));
        assertTrue(renderer.isEntityVisible(near));
        assertFalse(renderer.isEntityVisible(far));
        when(far.isGlowing()).thenReturn(true);
        assertTrue(renderer.isEntityVisible(far));
        when(far.isGlowing()).thenReturn(false);
        when(far.getAlwaysRenderNameTagForRender()).thenReturn(true);
        assertTrue(renderer.isEntityVisible(far));
        when(far.getAlwaysRenderNameTagForRender()).thenReturn(false);
        options.performance.useEntityCulling = false;
        assertTrue(renderer.isEntityVisible(far));
        options.performance.useEntityCulling = true;

        // The shadow pass only culls, draws from the sun's matrices and sees every entity
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", true);
        assertTrue(manager.isInShadowPass());
        renderer.setupTerrain(viewport, camera, 4, false, false);
        renderer.drawChunkLayer(BlockRenderLayer.SOLID, 8, 20, 8);
        assertTrue(renderer.isEntityVisible(far));
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);

        // A spectator inside the floor turns occlusion culling off, and a block change rebuilds its section
        Viewport inside = Sections.viewport(8, 0.5, 8);
        renderer.setupTerrain(inside, new SimpleWorldRenderer.CameraState(8, 0.5, 8, 0, 0, 1000f), 5, true, false);
        terrain.set(new BlockPos(10, 1, 10), Blocks.STONE.getDefaultState());
        renderer.scheduleRebuildForChunk(0, 0, 0, true);
        renderer.setupTerrain(viewport, camera, 6, false, true);
        renderer.setupTerrain(viewport, camera, 7, false, true);
        assertTrue(renderer.isSectionReady(0, 0, 0));

        renderer.setWorld(null);
        assertNull(renderer.getRenderSectionManager());
    }

    @Test
    void aVisuallyEmptySectionIsNeverBuilt() {
        Terrain terrain = new Terrain().build();
        CommandList commands = device.createCommandList();
        VintageRenderSectionManager manager = VintageRenderSectionManager.create(ChunkMeshFormats.COMPACT, terrain.world, 2, commands);
        assertEquals(AsyncOcclusionMode.NONE, Mixins.<AsyncOcclusionMode>call(manager, "getAsyncOcclusionMode"));
        assertTrue(Mixins.<Boolean>call(manager, "shouldRespectUpdateTaskQueueSizeLimit"));
        assertTrue(Mixins.<Boolean>call(manager, "useFogOcclusion"));
        assertTrue(Mixins.<Boolean>call(manager, "allowImportantRebuilds"));
        assertFalse(Mixins.<Boolean>call(manager, "isSectionVisuallyEmpty", 0, 0, 0));
        assertTrue(Mixins.<Boolean>call(manager, "isSectionVisuallyEmpty", 0, 1, 0));
        assertTrue(Mixins.<Boolean>call(manager, "isSectionVisuallyEmpty", 0, -1, 0));
        assertTrue(Mixins.<Boolean>call(manager, "isSectionVisuallyEmpty", 0, 16, 0));
        when(terrain.chunks.get(ChunkPos.asLong(0, 0)).isEmpty()).thenReturn(true);
        assertTrue(Mixins.<Boolean>call(manager, "isSectionVisuallyEmpty", 0, 0, 0));
        // An absent section has nothing to prepare
        assertNull(WorldSlice.prepare(terrain.world, new SectionPos(0, 3, 0), manager.getSectionCache()));
        assertNull(Mixins.call(manager, "createRebuildTask", new RenderSection(null, 0, 3, 0), 1));
        manager.destroy();
        commands.close();
    }

    @Test
    void theSliceAnswersFromItsCopy() {
        Terrain terrain = new Terrain().build();
        terrain.storage(9, 1, 1).setSkyLight(9, 1, 1, 0);
        terrain.storage(9, 2, 1).setSkyLight(9, 2, 1, 15);
        ClonedChunkSectionCache cache = new ClonedChunkSectionCache(terrain.world);
        ChunkRenderContext snapshot = terrain.snapshot(cache);
        assertEquals(new SectionPos(0, 0, 0), snapshot.getOrigin());
        assertEquals(-2, snapshot.getVolume().minX);
        assertEquals(64, snapshot.getSections().length);
        WorldSlice slice = new WorldSlice(terrain.world);
        assertNull(slice.getChunk(0, 0));
        slice.copyData(snapshot);
        assertSame(snapshot.getOrigin(), slice.getOrigin());
        assertSame(terrain.world, slice.getWorld());
        assertSame(terrain.chunks.get(ChunkPos.asLong(0, 0)), slice.getChunk(0, 0));
        assertNull(slice.getChunk(5, 0));
        assertNull(slice.getChunk(0, -5));
        assertSame(WorldType.DEFAULT, slice.getWorldType());

        // Blocks, relative reads and air
        assertSame(Blocks.GLASS.getDefaultState(), slice.getBlockState(GLASS));
        assertSame(Blocks.GLASS.getDefaultState(), slice.getBlockStateRelative(GLASS.getX() + 16, GLASS.getY() + 16, GLASS.getZ() + 16));
        assertFalse(slice.isAirBlock(GLASS));
        assertTrue(slice.isAirBlock(GLASS.up()));
        assertTrue(slice.isSideSolid(new BlockPos(0, 0, 0), EnumFacing.UP, false));
        assertEquals(0, slice.getStrongPower(GLASS, EnumFacing.UP));

        // Tile entities come from the copy; a block missing its own gets a stand-in once, a block that cannot make one gets none
        assertSame(terrain.chestEntity, slice.getTileEntity(CHEST));
        assertNull(slice.getTileEntity(GLASS));
        assertNull(slice.getBlockEntity(100, 1, 1));
        TileEntity standIn = slice.getTileEntity(LONE_CHEST);
        assertInstanceOf(TileEntityChest.class, standIn);
        assertEquals(LONE_CHEST, standIn.getPos());
        assertSame(standIn, slice.getTileEntity(LONE_CHEST));
        terrain.set(GRASS.up(), BROKEN_CONTAINER.getDefaultState());
        WorldSlice broken = new WorldSlice(terrain.world);
        broken.copyData(terrain.snapshot(new ClonedChunkSectionCache(terrain.world)));
        assertNull(broken.getTileEntity(GRASS.up()));
        assertNull(broken.getTileEntity(GRASS.up()));
        assertEquals(2, terrain.scheduled.size());

        // The queued task creates the real entity and re-renders only if the world still wants one there
        Chunk origin = terrain.chunks.get(ChunkPos.asLong(0, 0));
        Runnable create = terrain.scheduled.get(0);
        when(terrain.world.getTileEntity(LONE_CHEST)).thenReturn(new TileEntityChest());
        create.run();
        verify(terrain.world).markBlockRangeForRenderUpdate(LONE_CHEST, LONE_CHEST);
        when(origin.getTileEntity(LONE_CHEST, Chunk.EnumCreateEntityType.CHECK)).thenReturn(new TileEntityChest());
        create.run();
        when(terrain.world.isBlockLoaded(LONE_CHEST)).thenReturn(false);
        create.run();
        Mixins.set(terrain.client, "world", null);
        create.run();
        Mixins.set(terrain.client, "world", terrain.world);
        when(terrain.world.isBlockLoaded(LONE_CHEST)).thenReturn(true);
        when(origin.getTileEntity(LONE_CHEST, Chunk.EnumCreateEntityType.CHECK)).thenReturn(null);
        terrain.set(LONE_CHEST, Blocks.STONE.getDefaultState());
        create.run();
        verify(terrain.world).markBlockRangeForRenderUpdate(LONE_CHEST, LONE_CHEST);
        when(terrain.world.getTileEntity(LONE_CHEST)).thenReturn(null);
        terrain.set(LONE_CHEST, Blocks.CHEST.getDefaultState());
        create.run();
        verify(terrain.world).markBlockRangeForRenderUpdate(LONE_CHEST, LONE_CHEST);

        // Light: open air reads the sky, outside the copy is the default, a slab takes its neighbours' brightest
        assertEquals(15 << 20, slice.getCombinedLight(GLASS.up(), 0));
        assertEquals(15 << 20 | 13 << 4, slice.getCombinedLight(WATER, 0));
        assertEquals(15 << 20 | 7 << 4, slice.getCombinedLight(GLASS.up(), 7));
        assertEquals(15 << 20 | 4 << 4, slice.getCombinedLight(new BlockPos(100, 1, 1), 4));
        assertEquals(15 << 20, slice.getCombinedLight(SLAB, 0));
        // With Fulgor's render fix a bottom slab is lit through its top face only
        FulgorConfig fixed = Mc.uninitialized(FulgorConfig.class);
        fixed.enabled = true;
        fixed.fixRenderLighting = true;
        Mixins.set(FulgorConfig.class, "instance", fixed);
        WorldSlice faceLit = new WorldSlice(terrain.world);
        faceLit.copyData(snapshot);
        assertEquals(15 << 20, faceLit.getCombinedLight(SLAB, 0));
        assertEquals(0, faceLit.getCombinedLight(new BlockPos(0, 0, 0), 0));
        assertEquals(15 << 20, faceLit.getCombinedLight(GLASS.up(), 0));
        // Without a sky the sky level is always zero and unshaded faces dim
        when(terrain.dimension.hasSkyLight()).thenReturn(false);
        WorldSlice nether = new WorldSlice(terrain.world);
        nether.copyData(snapshot);
        assertEquals(0, nether.getCombinedLight(GLASS.up(), 0));
        assertEquals(0.9F, nether.getBrightness(EnumFacing.UP, false));
        assertEquals(1.0F, slice.getBrightness(EnumFacing.UP, false));
        assertEquals(0.5F, slice.getBrightness(EnumFacing.DOWN, true), 1e-6F);

        // Biomes and tints come from the copy, with plains for anything it lacks
        assertSame(Biomes.FOREST, slice.getBiome(GLASS));
        assertSame(Biomes.PLAINS, slice.getBiome(new BlockPos(40, 1, 1)));
        assertSame(Biomes.FOREST, slice.getBiome(1, 1, 1));
        assertSame(Biomes.PLAINS, slice.getBiome(1, 200, 1));
        assertEquals(0x336699, slice.getBlockTint(GRASS, terrain.tint));
        assertEquals(0x112233, slice.getBlockTint(new BlockPos(100, 1, 1), terrain.tint));

        slice.reset();
        assertNotSame(standIn, slice.getTileEntity(LONE_CHEST));
    }

    @Test
    void readsPastTheSliceGoThroughTheSectionCache() {
        Terrain terrain = new Terrain().build();
        terrain.set(new BlockPos(20, 40, 20), Blocks.GOLD_BLOCK.getDefaultState());
        WorldSlice slice = new WorldSlice(terrain.world);
        slice.copyData(terrain.snapshot(new ClonedChunkSectionCache(terrain.world)));
        BlockPos outside = new BlockPos(20, 40, 20);
        // On the main thread the live world answers
        when(terrain.client.isCallingFromMinecraftThread()).thenReturn(true);
        assertSame(Blocks.GOLD_BLOCK.getDefaultState(), slice.getBlockState(outside));
        when(terrain.client.isCallingFromMinecraftThread()).thenReturn(false);
        // Off it, with no renderer or no section manager yet, it reads as air
        RenderGlobalMixin global = Mixins.instance(RenderGlobalMixin.class);
        Mixins.set(terrain.client, "renderGlobal", global);
        assertSame(Blocks.AIR.getDefaultState(), slice.getBlockState(outside));
        ImpetusWorldRenderer renderer = new ImpetusWorldRenderer();
        Mixins.set(global, "renderer", renderer);
        assertSame(Blocks.AIR.getDefaultState(), slice.getBlockState(outside));
        // With a manager the section is cloned through its cache on the render thread, once
        renderer.setWorld(terrain.world);
        assertSame(Blocks.GOLD_BLOCK.getDefaultState(), slice.getBlockState(outside));
        // The copy is kept, so even a column whose storage was swapped out answers from it
        terrain.chunks.get(ChunkPos.asLong(1, 1)).getBlockStorageArray()[2] = new ExtendedBlockStorage(32, true);
        terrain.set(outside, Blocks.IRON_BLOCK.getDefaultState());
        assertSame(Blocks.GOLD_BLOCK.getDefaultState(), slice.getBlockState(outside));
        // A player who left the world gets air rather than a wait, and an unloaded column is an error
        Mixins.set(terrain.client, "world", null);
        assertSame(Blocks.AIR.getDefaultState(), slice.getBlockState(new BlockPos(20, 60, 20)));
        Mixins.set(terrain.client, "world", terrain.world);
        RuntimeException missing = assertThrows(RuntimeException.class, () -> slice.getBlockState(new BlockPos(500, 1, 500)));
        assertEquals("Failed to fetch fallback section", missing.getMessage());
        renderer.setWorld(null);
    }

    @Test
    void clonedSectionsAreCachedAndSwept() {
        Terrain terrain = new Terrain().build();
        ClonedChunkSectionCache cache = new ClonedChunkSectionCache(terrain.world);
        ClonedChunkSection section = cache.acquire(0, 0, 0);
        assertSame(section, cache.acquire(0, 0, 0));
        assertEquals(new SectionPos(0, 0, 0), section.getPosition());
        assertSame(terrain.chunks.get(ChunkPos.asLong(0, 0)), section.getChunk());
        assertSame(Blocks.GLASS.getDefaultState(), section.getBlockState(1, 1, 1));
        assertSame(Biomes.FOREST, section.getBiomeForNoiseGen(3, 4));
        assertEquals(256, section.getBiomeData().length);
        assertSame(terrain.chestEntity, section.getBlockEntity(4, 1, 1));
        assertNull(section.getBlockEntity(4, 8, 4));
        assertEquals(13, section.getLightLevel(3, 1, 1, EnumSkyBlock.BLOCK));
        assertEquals(15, section.getLightLevel(3, 1, 1, EnumSkyBlock.SKY));
        assertNull(section.getFluidData());
        // A section with no storage, or past the column, is empty air in the default light
        ClonedChunkSection empty = cache.acquire(0, 20, 0);
        assertSame(Blocks.AIR.getDefaultState(), empty.getBlockState(0, 0, 0));
        assertEquals(EnumSkyBlock.SKY.defaultLightValue, empty.getLightLevel(0, 0, 0, EnumSkyBlock.SKY));
        // Invalidation re-clones, and cleanup drops what has not been touched for a while
        cache.invalidate(0, 0, 0);
        assertNotSame(section, cache.acquire(0, 0, 0));
        ClonedChunkSection stale = cache.acquire(1, 0, 0);
        stale.setLastUsedTimestamp(Long.MIN_VALUE / 2);
        assertEquals(Long.MIN_VALUE / 2, stale.getLastUsedTimestamp());
        cache.cleanup();
        assertNotSame(stale, cache.acquire(1, 0, 0));
        assertThrows(RuntimeException.class, () -> cache.acquire(40, 0, 40));
    }

    @Test
    void thePassConfigurationSplitsVanillaLayers() {
        new VintageRenderPassConfigurationBuilder();
        Terrain terrain = new Terrain();
        RenderPassConfiguration<BlockRenderLayer> consolidated = passes();
        // Cutout-mipped rides along with solid, cutout keeps its own unmipmapped pass
        assertEquals(2, consolidated.vanillaRenderStages().get(BlockRenderLayer.SOLID).size());
        assertNull(consolidated.vanillaRenderStages().get(BlockRenderLayer.CUTOUT_MIPPED));
        options.performance.useRenderPassConsolidation = false;
        options.quality.chunkFadeInDuration = 250;
        RenderPassConfiguration<BlockRenderLayer> separate = VintageRenderPassConfigurationBuilder.build(ChunkMeshFormats.VANILLA_LIKE);
        assertEquals(1, separate.vanillaRenderStages().get(BlockRenderLayer.CUTOUT_MIPPED).size());

        TerrainRenderPass cutout = separate.vanillaRenderStages().get(BlockRenderLayer.CUTOUT).iterator().next();
        TerrainRenderPass solid = separate.vanillaRenderStages().get(BlockRenderLayer.SOLID).iterator().next();
        cutout.startDrawing();
        verify(terrain.atlas).setBlurMipmapDirect(false, false);
        cutout.endDrawing();
        verify(terrain.atlas).setBlurMipmapDirect(false, true);
        solid.startDrawing();
        verify(terrain.atlas, Mockito.times(2)).setBlurMipmapDirect(false, true);
        solid.endDrawing();
        verify(terrain.atlas, Mockito.times(2)).setBlurMipmapDirect(false, true);
        // With mipmaps off even the mipmapped passes bind unmipped, and a texture that is not an atlas is left alone
        terrain.settings.mipmapLevels = 0;
        solid.startDrawing();
        verify(terrain.atlas, Mockito.times(2)).setBlurMipmapDirect(false, false);
        when(terrain.textures.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)).thenReturn(null);
        solid.startDrawing();
        verify(terrain.textures, Mockito.times(5)).bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    @Test
    void vanillaShadingFollowsThePack() {
        VintageDiffuseProvider diffuse = VintageDiffuseProvider.INSTANCE;
        assertEquals(ModelQuadFacing.UNASSIGNED, VintageDiffuseProvider.fromEnumFacingOrUnassigned(null));
        assertEquals(ModelQuadFacing.POS_X, VintageDiffuseProvider.fromEnumFacingOrUnassigned(EnumFacing.EAST));
        assertEquals(0.5F, diffuse.getDiffuse(ModelQuadFacing.NEG_Y, true), 1e-6F);
        assertEquals(1.0F, diffuse.getDiffuse(ModelQuadFacing.UNASSIGNED, true), 1e-6F);
        assertEquals(1.0F, diffuse.getDiffuse(ModelQuadFacing.NEG_Y, false));
        assertEquals(0.6F, diffuse.getDiffuse(1, 0, 0, true), 1e-6F);
        assertEquals(1.0F, diffuse.getDiffuse(1, 0, 0, false));
        // oldLighting=false makes the pack light by normal itself, so vanilla's shading is not baked in
        WorldRenderingSettings.setOldLighting(false);
        try {
            assertEquals(1.0F, diffuse.getDiffuse(ModelQuadFacing.NEG_Y, true));
            assertEquals(1.0F, diffuse.getDiffuse(1, 0, 0, true));
        } finally {
            WorldRenderingSettings.setOldLighting(true);
        }
        assertThrows(IllegalArgumentException.class, () -> VintageDiffuseProvider.toEnumFacing(ModelQuadFacing.UNASSIGNED));
    }

    @Test
    void fogComesFromGlStateManager() {
        Minecraft client = Mc.client();
        EntityRenderer entityRenderer = Mc.uninitialized(EntityRenderer.class);
        Mixins.set(entityRenderer, "fogColorRed", 0.25F);
        Mixins.set(entityRenderer, "fogColorGreen", 0.5F);
        Mixins.set(entityRenderer, "fogColorBlue", 0.75F);
        client.entityRenderer = entityRenderer;
        GLStateManagerFogService fog = new GLStateManagerFogService();
        GlStateManager.FogState state = GlStateManager.fogState;
        boolean enabled = state.fog.currentState;
        int mode = state.mode;
        float start = state.start;
        float end = state.end;
        float density = state.density;
        try {
            state.start = 10F;
            state.end = 90F;
            state.density = 0.25F;
            state.mode = 9729;
            state.fog.currentState = true;
            assertEquals(10F, fog.getFogStart());
            assertEquals(90F, fog.getFogEnd());
            assertEquals(0.25F, fog.getFogDensity());
            assertEquals(90F, fog.getFogCutoff());
            assertEquals(ChunkFogMode.fromGLMode(9729), fog.getFogMode());
            assertArrayEquals(new float[] {0.25F, 0.5F, 0.75F, 1.0F}, fog.getFogColor());
            assertEquals(Extras.options().render.fogShape.shaderIndex(), fog.getFogShapeIndex());
            // Exponential fog never fully hides a chunk, and no fog at all has no cutoff or mode
            state.mode = 2048;
            assertEquals(Float.MAX_VALUE, fog.getFogCutoff());
            state.fog.currentState = false;
            assertEquals(Float.MAX_VALUE, fog.getFogCutoff());
            assertEquals(ChunkFogMode.NONE, fog.getFogMode());
        } finally {
            state.fog.currentState = enabled;
            state.mode = mode;
            state.start = start;
            state.end = end;
            state.density = density;
        }
    }

    @Test
    void smallHelpers() {
        Mc.client();
        new CameraHelper();
        new SpriteUtil();
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        modelView.clear();
        modelView.put(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 2, 3, 4, 1}).flip();
        // The eye sits at the inverse of the view's translation
        assertEquals(new Vector3f(-2, -3, -4), CameraHelper.getThirdPersonOffset());
        TextureAtlasSprite sprite = Mc.mock(TextureAtlasSprite.class, SpriteExtension.class);
        SpriteUtil.markSpriteActive(sprite);
        verify((SpriteExtension) sprite).impetus$markActive();

        Map<Integer, net.minecraft.client.renderer.DestroyBlockProgress> damage = new HashMap<>();
        ImpetusWorldRenderer.TileEntityRenderContext context = new ImpetusWorldRenderer.TileEntityRenderContext(damage, 0.25F);
        assertSame(damage, context.damagedBlocks());
        assertEquals(0.25F, context.partialTicks());
        assertEquals(new ImpetusWorldRenderer.TileEntityRenderContext(damage, 0.25F), context);
        assertEquals(new ImpetusWorldRenderer.TileEntityRenderContext(damage, 0.25F).hashCode(), context.hashCode());
        assertTrue(context.toString().contains("0.25"));
    }
}
