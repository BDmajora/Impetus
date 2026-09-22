package com.bdmajora.impetus.impl.render.terrain.compile.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.registries.IRegistryDelegate;
import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.model.light.LightMode;
import com.bdmajora.impetus.engine.impl.model.light.LightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.LightPipelineProvider;
import com.bdmajora.impetus.engine.impl.model.light.data.QuadLightData;
import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadOrientation;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkColorWriter;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffers;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.pipeline.BakedQuadGroupAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.util.ModelQuadUtil;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.render.terrain.compile.VintageChunkBuildContext;
import com.bdmajora.impetus.impl.render.terrain.compile.light.LightDataCache;
import com.bdmajora.impetus.impl.render.terrain.compile.light.VintageDiffuseProvider;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride;
import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.umbra.vertices.NormalHelper;
import com.bdmajora.impetus.mixin.core.terrain.BlockColorsAccessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class VintageBlockRenderer {
    private final BlockModelShapes shapes;
    private final VintageChunkBuildContext context;
    private final VertexBufferConsumer consumer;
    private final LightPipelineProvider lighters;
    private final Map<IRegistryDelegate<Block>, IBlockColor> blockColors;

    private final QuadLightData quadLightData = new QuadLightData();
    private final int[] quadColors = new int[4];
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    private final ModelQuadOrientation[] currentOrientations = new ModelQuadOrientation[EnumFacing.VALUES.length];
    private final boolean useRenderPassOptimization;

    private IBlockState currentState;
    private ImpetusBlockAccess currentBlockAccess;

    private final BakedQuadGroupAnalyzer analyzer = new BakedQuadGroupAnalyzer();

    private int currentQuadRenderingFlags;

    // The OptiFine per-block attributes (mc_Entity, emission) and the AO writer, resolved once per block rather than per quad or per vertex
    private boolean shadersActive;
    private int currentBlockId;
    private int currentBlockRenderType;
    private int currentBlockData;
    private int currentBlockEmission;
    private ChunkColorWriter colorWriter = ChunkColorWriter.IMPETUS;

    public VintageBlockRenderer(VintageChunkBuildContext context, LightDataCache cache) {
        this.shapes = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
        this.consumer = new VertexBufferConsumer();
        this.context = context;
        this.lighters = new LightPipelineProvider(cache, VintageDiffuseProvider.INSTANCE, true);
        this.blockColors = ((BlockColorsAccessor)Minecraft.getMinecraft().getBlockColors()).getBlockColorMap();
        this.useRenderPassOptimization = ImpetusVintage.options().performance.useRenderPassOptimization;
    }

    // Clears per-section caches before the next section
    public void resetSharedState() {
        Arrays.fill(this.currentOrientations, null);
    }

    // Emits every quad of a block model into the mesh, culling faces against neighbours
    public void renderBlock(IBlockState state, BlockPos pos, ImpetusBlockAccess blockAccess, BlockRenderLayer layer) {
        int defaultFlags = BakedQuadGroupAnalyzer.USE_ALL_THINGS;
        if (!useRenderPassOptimization) {
            defaultFlags &= ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        }
        this.analyzer.setDefaultRenderingFlags(defaultFlags);

        if (blockAccess.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
            state = state.getActualState(blockAccess, pos);
        }
        var model = this.shapes.getModelForState(state);
        state = state.getBlock().getExtendedState(state, blockAccess, pos);
        this.currentState = state;
        this.currentBlockAccess = blockAccess;
        this.colorWriter = ChunkColorWriter.active();
        this.shadersActive = UmbraTerrainProgramOverride.areShadersActive();
        if (this.shadersActive) {
            this.currentBlockId = com.bdmajora.impetus.umbra.material.WorldRenderingSettings.getBlockStateId(state);
            this.currentBlockRenderType = state.getRenderType().ordinal();
            this.currentBlockData = state.getBlock().getMetaFromState(state);
            this.currentBlockEmission = clampBlockEmission(state.getLightValue(blockAccess, pos));
        }

        var buffers = this.context.buffers;
        var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(layer);
        var buffer = buffers.get(material);

        long rand = MathHelper.getPositionRandom(pos);

        IBlockColor colorProvider = this.blockColors.get(state.getBlock().delegate);

        var offset = this.currentState.getOffset(blockAccess, pos);

        var aoEnabled = Minecraft.isAmbientOcclusionEnabled() && state.getLightValue(blockAccess, pos) == 0 && model.isAmbientOcclusion(state);

        var lighter = this.lighters.getLighter(aoEnabled ? LightMode.SMOOTH : LightMode.FLAT);

        for (var dir : EnumFacing.VALUES) {
            var quads = model.getQuads(state, dir, rand);

            if (quads.isEmpty() || !state.shouldSideBeRendered(blockAccess, pos, dir)) {
                continue;
            }

            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(VintageDiffuseProvider.fromEnumFacing(dir), BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, dir, lighter, colorProvider, offset, quads);
        }

        var quads = model.getQuads(state, null, rand);

        if (!quads.isEmpty()) {
            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(ModelQuadFacing.UNASSIGNED, BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, null, lighter, colorProvider, offset, quads);
        }

        this.currentBlockAccess = null;
    }

    // Runs the chosen light pipeline for one quad
    private QuadLightData getVertexLight(LightPipeline lighter, BlockPos pos, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, pos.getX(), pos.getY(), pos.getZ(), light, VintageDiffuseProvider.fromEnumFacingOrUnassigned(cullFace), quad.getLightFace(), quad.hasShade(), false);

        return light;
    }

    // Tint colours per vertex, through the biome blender when the quad is tinted
    private int[] getVertexColors(BlockPos pos, IBlockColor colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;

        if (colorProvider != null && quad.hasColor()) {
            // Force full alpha on all colors
            Arrays.fill(vertexColors, 0xFF000000 | ColorARGB.toABGR(colorProvider.colorMultiplier(this.currentState, this.currentBlockAccess, pos, quad.getColorIndex())));
        } else {
            Arrays.fill(vertexColors, 0xFFFFFFFF);
        }

        return vertexColors;
    }

    private void renderQuadList(ChunkModelBuilder defaultBuffer, ChunkBuildBuffers buffers,
                                Material material, BlockPos pos, EnumFacing cullFace,
                                LightPipeline lighter, IBlockColor colorProvider, Vec3d offset,
                                List<BakedQuad> quads) {
        int localX = pos.getX() & 15, localY = pos.getY() & 15, localZ = pos.getZ() & 15;
        var config = buffers.getRenderPassConfiguration();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            var quad = quads.get(i);
            var format = quad.getFormat();
            if (format != DefaultVertexFormats.ITEM && format != DefaultVertexFormats.BLOCK && format.getElement(0).getUsage() != VertexFormatElement.EnumUsage.POSITION) {
                throw new IllegalStateException("Vertex format does not have POSITION as first element: " + format);
            }

            BakedQuadView quadView = (BakedQuadView)quad;

            // Run through our light pipeline
            var light = this.getVertexLight(lighter, pos, cullFace, quadView);
            var colors = this.getVertexColors(pos, colorProvider, quadView);

            ModelQuadOrientation orientation = cullFace != null ? this.currentOrientations[cullFace.ordinal()] : ModelQuadOrientation.NORMAL;
            if (orientation == null) {
                this.currentOrientations[cullFace.ordinal()] = orientation = ModelQuadOrientation.orientByBrightness(light.br, light.lm);
            }

            var quadMaterial = BakedQuadGroupAnalyzer.chooseOptimalMaterial(this.currentQuadRenderingFlags, material, config, BakedQuadView.of(quad));
            ChunkModelBuilder buffer = (quadMaterial == material) ? defaultBuffer : buffers.get(quadMaterial);

            this.writeGeometry(localX, localY, localZ, buffer, offset, quadMaterial,
                    quadView, colors, light, orientation);

            TextureAtlasSprite sprite = (TextureAtlasSprite)quadView.impetus$getSprite();

            if (sprite.hasAnimationMetadata() && buffer.getSectionContextBundle() instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
                //noinspection unchecked
                ((Collection<TextureAtlasSprite>)mcData.animatedSprites).add(sprite);
            }
        }
    }

    private void writeGeometry(int localX, int localY, int localZ, ChunkModelBuilder builder,
                               Vec3d offset,
                               Material material,
                               BakedQuadView quad,
                               int[] colors,
                               QuadLightData light,
                               ModelQuadOrientation orientation)
    {
        var vertices = this.vertices;

        ModelQuadFacing normalFace = quad.getNormalFace();

        int vanillaNormal = normalFace.getPackedNormal();
        int trueNormal = quad.getComputedFaceNormal();
        float originX = localX + (float) offset.x;
        float originY = localY + (float) offset.y;
        float originZ = localZ + (float) offset.z;
        ChunkColorWriter colorWriter = this.colorWriter;

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex];
            out.x = originX + quad.getX(srcIndex);
            out.y = originY + quad.getY(srcIndex);
            out.z = originZ + quad.getZ(srcIndex);

            out.color = colorWriter.writeColor(ModelQuadUtil.mixARGBColors(colors[srcIndex], quad.getColor(srcIndex)), light.br[srcIndex]);

            out.u = quad.getTexU(srcIndex);
            out.v = quad.getTexV(srcIndex);

            out.light = ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), quad.getVanillaLightEmission(), light.lm[srcIndex]);

            out.vanillaNormal = vanillaNormal;
            out.trueNormal = trueNormal;

            // at_midBlock: (0.5 - model-local vertex position) points at the block centre regardless of chunk/region offset; colored-lighting voxelization samples there to stay off voxel-cell boundaries
            out.midBlockX = 0.5f - quad.getX(srcIndex);
            out.midBlockY = 0.5f - quad.getY(srcIndex);
            out.midBlockZ = 0.5f - quad.getZ(srcIndex);
        }

        if (this.shadersActive) {
            populateUmbraVertexData(vertices, quad, trueNormal);
        }

        var vertexBuffer = builder.getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }

    // Fills the OptiFine per-vertex attributes (mc_midTexCoord, at_tangent, mc_Entity) UmbraChunkVertexType encodes under a shader pack; all four vertices share the values
    private void populateUmbraVertexData(ChunkVertexEncoder.Vertex[] vertices, BakedQuadView quad, int trueNormal) {
        // mc_midTexCoord must be the centre of THIS QUAD's mapped region (sum of UVs * 0.25, as Umbra does), not the sprite centre; Chocapic-derived packs rebuild their sprite basis from it, and the sprite centre smeared torch caps into a 1px band
        float midU = 0.0f, midV = 0.0f;
        for (int i = 0; i < 4; i++) {
            midU += quad.getTexU(i);
            midV += quad.getTexV(i);
        }
        midU *= 0.25f;
        midV *= 0.25f;

        // Read positions/UVs off the source quad, never `vertices`, which are already rotated into ModelQuadOrientation order; a rotated triple yields a tangent turned 90° and made Sildur's water wave normals checkerboard under grazing fresnel
        int tangent = NormalHelper.computeTangent(
                NormI8.unpackX(trueNormal), NormI8.unpackY(trueNormal), NormI8.unpackZ(trueNormal),
                quad.getX(0), quad.getY(0), quad.getZ(0), quad.getTexU(0), quad.getTexV(0),
                quad.getX(1), quad.getY(1), quad.getZ(1), quad.getTexU(1), quad.getTexV(1),
                quad.getX(2), quad.getY(2), quad.getZ(2), quad.getTexU(2), quad.getTexV(2));

        int blockId = this.currentBlockId;
        int blockRenderType = this.currentBlockRenderType;
        int blockData = this.currentBlockData;
        int blockEmission = this.currentBlockEmission;

        for (int i = 0; i < 4; i++) {
            var out = vertices[i];
            out.midTexU = midU;
            out.midTexV = midV;
            out.tangent = tangent;
            out.blockId = blockId;
            out.blockRenderType = blockRenderType;
            out.blockData = blockData;
            out.blockEmission = blockEmission;
        }
    }

    // The emission byte of at_midBlock; vanilla is 0-15 but a mod's getLightValue may answer past that
    private static int clampBlockEmission(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}
