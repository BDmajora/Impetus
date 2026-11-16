package com.bdmajora.impetus.impl.render.terrain.compile.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Renders the quads of baked block models into the chunk mesh buffers.
public class VintageBlockRenderer {
    // Build context of the chunk.
    private final VintageChunkBuildContext context;
    // Consumer of vertex data.
    private final VertexBufferConsumer consumer;
    // Smooth and flat lighters.
    private final LightPipelineProvider lighters;
    // Color providers by block.
    private final Map<IRegistryDelegate<Block>, IBlockColor> blockColors;

    // Light of the current quad.
    private final QuadLightData quadLightData = new QuadLightData();
    // Tint of each vertex of the current quad.
    private final int[] quadColors = new int[4];
    // Vertices of the current quad.
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    // Orientation chosen for each cull face of the current block.
    private final ModelQuadOrientation[] currentOrientations = new ModelQuadOrientation[EnumFacing.VALUES.length];
    // Render pass optimization state of the options.
    private final boolean useRenderPassOptimization;

    // State of the current block.
    private IBlockState currentState;
    // World view of the current block or null.
    private ImpetusBlockAccess currentBlockAccess;

    // Analyzer of the quad groups.
    private final BakedQuadGroupAnalyzer analyzer = new BakedQuadGroupAnalyzer();

    // Rendering flags of the current quad group.
    private int currentQuadRenderingFlags;

    // Shader pack state of the current block.
    private boolean shadersActive;
    // Shader block id of the current block.
    private int currentBlockId;
    // Render type ordinal of the current block.
    private int currentBlockRenderType;
    // Metadata of the current block.
    private int currentBlockData;
    // Emission of the current block between 0 and 255.
    private int currentBlockEmission;
    // Writer that folds occlusion into the vertex color.
    private ChunkColorWriter colorWriter = ChunkColorWriter.IMPETUS;

    // Creates a renderer for a build context and a light cache.
    public VintageBlockRenderer(VintageChunkBuildContext context, LightDataCache cache) {
        this.consumer = new VertexBufferConsumer();
        this.context = context;
        this.lighters = new LightPipelineProvider(cache, VintageDiffuseProvider.INSTANCE, true);
        this.blockColors = ((BlockColorsAccessor)Minecraft.getMinecraft().getBlockColors()).getBlockColorMap();
        this.useRenderPassOptimization = ImpetusVintage.options().performance.useRenderPassOptimization;
    }

    // Forgets the orientations of the last block.
    public void resetSharedState() {
        Arrays.fill(this.currentOrientations, null);
    }

    // Renders every face of a model and returns whether a quad was emitted.
    public boolean renderModel(IBakedModel model, IBlockState state, BlockPos pos, ImpetusBlockAccess blockAccess, BlockRenderLayer layer) {
        int defaultFlags = BakedQuadGroupAnalyzer.USE_ALL_THINGS;
        if (!useRenderPassOptimization) {
            defaultFlags &= ~BakedQuadGroupAnalyzer.USE_RENDER_PASS_OPTIMIZATION;
        }
        this.analyzer.setDefaultRenderingFlags(defaultFlags);

        boolean rendered = false;
        this.currentState = state;
        this.currentBlockAccess = blockAccess;
        this.colorWriter = ChunkColorWriter.active();
        this.shadersActive = UmbraTerrainProgramOverride.areShadersActive();
        boolean aoSetting = Minecraft.isAmbientOcclusionEnabled();
        // Light value of the block read once when the emission or the occlusion check needs it
        int lightValue = this.shadersActive || aoSetting ? state.getLightValue(blockAccess, pos) : 0;
        if (this.shadersActive) {
            this.currentBlockId = WorldRenderingSettings.getBlockStateId(state);
            this.currentBlockRenderType = state.getRenderType().ordinal();
            this.currentBlockData = state.getBlock().getMetaFromState(state);
            this.currentBlockEmission = clampBlockEmission(lightValue);
        }

        var buffers = this.context.buffers;
        var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(layer);
        var buffer = buffers.get(material);

        long rand = MathHelper.getPositionRandom(pos);

        IBlockColor colorProvider = this.blockColors.get(state.getBlock().delegate);

        var offset = this.currentState.getOffset(blockAccess, pos);

        var aoEnabled = aoSetting && lightValue == 0 && model.isAmbientOcclusion(state);

        var lighter = this.lighters.getLighter(aoEnabled ? LightMode.SMOOTH : LightMode.FLAT);

        for (var dir : EnumFacing.VALUES) {
            var quads = model.getQuads(state, dir, rand);

            if (quads.isEmpty() || !state.shouldSideBeRendered(blockAccess, pos, dir)) {
                continue;
            }

            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(VintageDiffuseProvider.fromEnumFacing(dir), BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, dir, lighter, colorProvider, offset, quads);
            rendered = true;
        }

        var quads = model.getQuads(state, null, rand);

        if (!quads.isEmpty()) {
            this.currentQuadRenderingFlags = this.analyzer.getFlagsForRendering(ModelQuadFacing.UNASSIGNED, BakedQuadView.ofList(quads));
            renderQuadList(buffer, buffers, material, pos, null, lighter, colorProvider, offset, quads);
            rendered = true;
        }

        this.currentBlockAccess = null;
        return rendered;
    }

    // Calculates the light of a quad.
    private QuadLightData getVertexLight(LightPipeline lighter, BlockPos pos, EnumFacing cullFace, BakedQuadView quad) {
        QuadLightData light = this.quadLightData;
        lighter.calculate(quad, pos.getX(), pos.getY(), pos.getZ(), light, VintageDiffuseProvider.fromEnumFacingOrUnassigned(cullFace), quad.getLightFace(), quad.hasShade(), false);

        return light;
    }

    // Returns the tint of each vertex of a quad.
    private int[] getVertexColors(BlockPos pos, IBlockColor colorProvider, BakedQuadView quad) {
        final int[] vertexColors = this.quadColors;

        if (colorProvider != null && quad.hasColor()) {
            Arrays.fill(vertexColors, 0xFF000000 | ColorARGB.toABGR(colorProvider.colorMultiplier(this.currentState, this.currentBlockAccess, pos, quad.getColorIndex())));
        } else {
            Arrays.fill(vertexColors, 0xFFFFFFFF);
        }

        return vertexColors;
    }

    // Renders a list of quads and records their animated sprites.
    private void renderQuadList(ChunkModelBuilder defaultBuffer, ChunkBuildBuffers buffers,
                                Material material, BlockPos pos, EnumFacing cullFace,
                                LightPipeline lighter, IBlockColor colorProvider, Vec3d offset,
                                List<BakedQuad> quads) {
        int localX = pos.getX() & 15, localY = pos.getY() & 15, localZ = pos.getZ() & 15;
        var config = buffers.getRenderPassConfiguration();
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            var quad = quads.get(i);
            var format = quad.getFormat();
            if (format != DefaultVertexFormats.ITEM && format != DefaultVertexFormats.BLOCK && format.getElement(0).getUsage() != VertexFormatElement.EnumUsage.POSITION) {
                throw new IllegalStateException("Vertex format does not have POSITION as first element: " + format);
            }

            BakedQuadView quadView = (BakedQuadView)quad;

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

            if (sprite != null && sprite.hasAnimationMetadata() && buffer.getSectionContextBundle() instanceof MinecraftBuiltRenderSectionData<?,?> mcData) {
                @SuppressWarnings("unchecked")
                Collection<TextureAtlasSprite> animated = (Collection<TextureAtlasSprite>) mcData.animatedSprites;
                animated.add(sprite);
            }
        }
    }

    // Writes the four vertices of a quad into a mesh buffer.
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

    // Writes the shader pack attributes of the four vertices.
    private void populateUmbraVertexData(ChunkVertexEncoder.Vertex[] vertices, BakedQuadView quad, int trueNormal) {
        float midU = 0.0f, midV = 0.0f;
        for (int i = 0; i < 4; i++) {
            midU += quad.getTexU(i);
            midV += quad.getTexV(i);
        }
        midU *= 0.25f;
        midV *= 0.25f;

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

    // Returns the emission limited to between 0 and 255.
    private static int clampBlockEmission(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}
