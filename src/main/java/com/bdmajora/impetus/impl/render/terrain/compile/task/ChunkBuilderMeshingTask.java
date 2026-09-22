package com.bdmajora.impetus.impl.render.terrain.compile.task;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import com.bdmajora.impetus.engine.impl.asm.ProxyClassGenerator;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffers;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.GraphDirection;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.impl.world.WorldSlice;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.impl.world.cloned.ChunkRenderContext;

import java.util.*;

public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    public static boolean USE_NEW_BLOCK_RENDERER = (Boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment") || Boolean.getBoolean("impetus.useVintageFastBlockRenderer");
    private static final ProxyClassGenerator<WorldSlice, ImpetusBlockAccess> WORLD_SLICE_LOCAL_GENERATOR = new ProxyClassGenerator<>(WorldSlice.class, "WorldSliceLocal", ImpetusBlockAccess.class);
    private final RenderSection render;
    private final int buildTime;
    private final Vector3d camera;
    private final ChunkRenderContext renderContext;

    public ChunkBuilderMeshingTask(RenderSection render, ChunkRenderContext context, int time, Vector3d camera) {
        this.render = render;
        this.buildTime = time;
        this.camera = camera;
        this.renderContext = context;
    }

    // Meshes one section: blocks, fluids, tile entities and the visibility graph
    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        VintageChunkBuildContext buildContext = (VintageChunkBuildContext)context;
        MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData = new MinecraftBuiltRenderSectionData<>();
        VisGraph occluder = new VisGraph();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(minX, minY, minZ);

        buildContext.getWorldSlice().copyData(this.renderContext);

        var slice = WORLD_SLICE_LOCAL_GENERATOR.generateWrapper(buildContext.getWorldSlice());

        var dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

        buildContext.setupTranslation(minX, minY, minZ);

        // Pack-wide switches read once per section rather than per block
        boolean voxelizeLightBlocks = com.bdmajora.impetus.umbra.material.WorldRenderingSettings.isVoxelizeLightBlocks();
        boolean useNewBlockRenderer = USE_NEW_BLOCK_RENDERER;
        var blockRenderer = buildContext.getBlockRenderer();

        try {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        blockPos.setPos(x, y, z);

                        IBlockState blockState = slice.getBlockState(blockPos);
                        var block = blockState.getBlock();

                        if (block == Blocks.AIR) {
                            continue;
                        }

                        if (block.hasTileEntity(blockState)) {
                            TileEntity tileEntity = slice.getTileEntity(blockPos);
                            if (tileEntity != null) {
                                TileEntitySpecialRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getRenderer(tileEntity);

                                if (tesr != null) {
                                    (tesr.isGlobalRenderer(tileEntity) ? renderData.globalBlockEntities : renderData.culledBlockEntities).add(tileEntity);
                                }
                            }
                        }

                        blockRenderer.resetSharedState();

                        // block.properties `layer.<rendertype>`: the pack can move a block to a different chunk render layer than it reports (OptiFine's render layer override)
                        BlockRenderLayer forcedLayer =
                                com.bdmajora.impetus.umbra.material.WorldRenderingSettings.getForcedRenderLayer(block);

                        // `voxelizeLightBlocks`: a block emitting light but drawing nothing is invisible to shadow-pass voxelization, so any INVISIBLE block with a light value (the 1.12.2 stand-in for `minecraft:light`) is attributed to the solid layer
                        if (voxelizeLightBlocks
                                && blockState.getRenderType() == EnumBlockRenderType.INVISIBLE
                                && blockState.getLightValue(slice, blockPos) > 0) {
                            buildContext.recordVanillaBlockAttribution(
                                    BlockRenderLayer.SOLID, blockState, blockPos);
                        }

                        // Baked block entity models (chests, signs, beds, shulker boxes) need the world and position behind the state, which getQuads cannot carry
                        boolean bakedEntity = com.bdmajora.extras.client.bakedentities.BakedEntities.isBaked(block);
                        if (bakedEntity) {
                            com.bdmajora.extras.client.bakedentities.BakedEntityContext.set(slice, blockPos);
                        }

                        for (BlockRenderLayer layer : VintageChunkBuildContext.LAYERS) {
                            boolean renderHere = forcedLayer != null
                                    ? layer == forcedLayer
                                    : block.canRenderInLayer(blockState, layer);
                            if (renderHere) {
                                ForgeHooksClient.setRenderLayer(layer);
                                if (useNewBlockRenderer && blockState.getRenderType() == EnumBlockRenderType.MODEL) {
                                    blockRenderer.renderBlock(blockState, blockPos, slice, layer);
                                } else {
                                    var buffer = buildContext.getBufferForLayer(layer);
                                    dispatcher.renderBlock(blockState, blockPos, slice, buffer);
                                    // Attribute the emitted quads to this block for Umbra terrain attributes.
                                    buildContext.recordVanillaBlockAttribution(layer, blockState, blockPos);
                                }
                            }
                        }

                        if (bakedEntity) {
                            com.bdmajora.extras.client.bakedentities.BakedEntityContext.clear();
                        }

                        // The compat hook renders the contained fluid and attributes its quads to the fluid's own state
                        if (FluidloggedCompat.IS_LOADED) {
                            FluidloggedCompat.renderFluidState(slice, blockPos, blockState, buildContext, dispatcher);
                        }

                        if (blockState.isOpaqueCube()) {
                            occluder.setOpaqueCube(blockPos);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getCrashReport(), slice, blockPos);
        } catch (Throwable ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }

        buildContext.convertVanillaDataToImpetusData(buffers);

        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers,(float)camera.x - minX, (float)camera.y - minY, (float)camera.z - minZ);

        if (!meshes.isEmpty()) {
            renderData.hasBlockGeometry = true;
        }

        encodeVisibilityData(occluder, renderData);

        return new ChunkBuildOutput(this.render, renderData, meshes, this.buildTime);
    }

    // Attaches the offending block and position to a rendering crash
    private ReportedException fillCrashInfo(CrashReport report, IBlockAccess slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.makeCategory("Block being rendered");

        IBlockState state = null;
        try {
            state = slice.getBlockState(pos);
        } catch (Exception ignored) {}
        CrashReportCategory.addBlockInfo(crashReportSection, pos, state);

        crashReportSection.addCrashSection("Chunk section", this.render);
        return new ReportedException(report);
    }

    private static final EnumFacing[] FACINGS = new EnumFacing[GraphDirection.COUNT];

    static {
        FACINGS[GraphDirection.UP] = EnumFacing.UP;
        FACINGS[GraphDirection.DOWN] = EnumFacing.DOWN;
        FACINGS[GraphDirection.WEST] = EnumFacing.WEST;
        FACINGS[GraphDirection.EAST] = EnumFacing.EAST;
        FACINGS[GraphDirection.NORTH] = EnumFacing.NORTH;
        FACINGS[GraphDirection.SOUTH] = EnumFacing.SOUTH;
    }

    // Packs vanilla's VisGraph result into the section's occlusion data
    private static void encodeVisibilityData(VisGraph occluder, MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData) {
        var data = occluder.computeVisibility();
        renderData.visibilityData = VisibilityEncoding.encode((from, to) -> data.isVisible(FACINGS[from], FACINGS[to]));
    }

}
