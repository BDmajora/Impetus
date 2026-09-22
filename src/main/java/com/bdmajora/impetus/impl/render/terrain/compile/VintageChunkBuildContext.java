package com.bdmajora.impetus.impl.render.terrain.compile;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.BufferBuilder;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildBuffers;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.util.QuadUtil;
import org.lwjgl.opengl.GL11;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.impl.extensions.TextureMapExtension;
import com.bdmajora.impetus.impl.render.terrain.compile.light.LightDataCache;
import com.bdmajora.impetus.impl.render.terrain.compile.pipeline.VintageBlockRenderer;
import com.bdmajora.impetus.impl.world.WorldSlice;

import java.nio.ByteBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public class VintageChunkBuildContext extends ChunkBuildContext {
    public static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();
    private final TextureMapExtension textureAtlas;
    private final BufferBuilder[] worldRenderers = new BufferBuilder[LAYERS.length];
    private final boolean[] usedWorldRenderers = new boolean[LAYERS.length];
    // Per layer, block attribution of vanilla-sourced quads (fluids and other non-model renders) as runs of (quadEndExclusive, mcEntityId, renderType, metadata, emission, localX/Y/Z), recorded under a shader pack so mc_Entity and at_midBlock survive the BufferBuilder round-trip
    private final IntArrayList[] vanillaBlockRuns =
            new IntArrayList[LAYERS.length];
    private static final int VANILLA_BLOCK_RUN_STRIDE = 8;
    @Getter
    private int offX, offY, offZ;
    @Getter
    private final WorldSlice worldSlice;
    @Getter
    private final VintageBlockRenderer blockRenderer;
    private final RenderPassConfiguration<?> renderPassConfiguration;
    private final LightDataCache lightDataCache;
    private final boolean useRenderPassOptimization;

    public VintageChunkBuildContext(WorldClient world, RenderPassConfiguration renderPassConfiguration) {
        super(renderPassConfiguration);
        this.renderPassConfiguration = renderPassConfiguration;
        this.worldSlice = new WorldSlice(world);
        this.lightDataCache = new LightDataCache(this.worldSlice);
        this.blockRenderer = new VintageBlockRenderer(this, lightDataCache);
        this.textureAtlas = (TextureMapExtension) Minecraft.getMinecraft().getTextureMapBlocks();
        this.useRenderPassOptimization = ImpetusVintage.options().performance.useRenderPassOptimization;
    }

    // Offsets vanilla buffers so tile entity renderers see section-relative coordinates
    public void setupTranslation(int x, int y, int z) {
        this.lightDataCache.reset(x, y, z);

        this.offX = x;
        this.offY = y;
        this.offZ = z;
    }

    // Vanilla buffer for blocks that bypass the Impetus pipeline, one per layer
    public BufferBuilder getBufferForLayer(BlockRenderLayer layer) {
        var builder = this.worldRenderers[layer.ordinal()];
        if (builder == null) {
            builder = new BufferBuilder(131072);
            this.worldRenderers[layer.ordinal()] = builder;
        }
        if (!this.usedWorldRenderers[layer.ordinal()]) {
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            builder.setTranslation(-this.offX, -this.offY, -this.offZ);
            this.usedWorldRenderers[layer.ordinal()] = true;
            if (this.vanillaBlockRuns[layer.ordinal()] != null) {
                this.vanillaBlockRuns[layer.ordinal()].clear();
            }
        }
        return builder;
    }

    // Records which block the vanilla-buffered quads since the last call belong to, so copyBlockData can fill mc_Entity, at_midBlock and emission; call right after every dispatcher.renderBlock, no-op without a shader pack
    public void recordVanillaBlockAttribution(BlockRenderLayer layer, net.minecraft.block.state.IBlockState state, BlockPos pos) {
        if (!com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride.areShadersActive()) {
            return;
        }
        int i = layer.ordinal();
        var builder = this.worldRenderers[i];
        if (builder == null || !this.usedWorldRenderers[i]) {
            return;
        }
        var runs = this.vanillaBlockRuns[i];
        if (runs == null) {
            runs = new IntArrayList();
            this.vanillaBlockRuns[i] = runs;
        }
        int quadCount = builder.getVertexCount() / 4;
        int lastEnd = runs.isEmpty() ? 0 : runs.getInt(runs.size() - VANILLA_BLOCK_RUN_STRIDE);
        if (quadCount <= lastEnd) {
            return; // the block emitted nothing into this layer
        }
        int renderType = state.getRenderType().ordinal();
        int metadata = state.getBlock().getMetaFromState(state);
        int id = com.bdmajora.impetus.umbra.material.WorldRenderingSettings.getBlockStateId(state);
        runs.add(quadCount);
        runs.add(id);
        runs.add(renderType);
        runs.add(metadata);
        runs.add(clampBlockEmission(state.getLightValue(this.worldSlice, pos)));
        runs.add(pos.getX() & 15);
        runs.add(pos.getY() & 15);
        runs.add(pos.getZ() & 15);
    }

    // Copies anything vanilla-rendered into the Impetus mesh buffers
    public void convertVanillaDataToImpetusData(ChunkBuildBuffers buffers) {
        var renderers = this.worldRenderers;
        var used = this.usedWorldRenderers;
        for (int i = 0; i < renderers.length; i++) {
            if(!used[i]) {
                continue;
            }
            var bufferBuilder = Objects.requireNonNull(renderers[i]);
            bufferBuilder.finishDrawing();
            used[i] = false;
            ByteBuffer rawBuffer = bufferBuilder.getByteBuffer();
            var material = buffers.getRenderPassConfiguration().getMaterialForRenderType(LAYERS[i]);
            copyBlockData(rawBuffer, buffers, material, this.vanillaBlockRuns[i]);
        }
    }

    // Resets the vanilla buffers between sections
    @Override
    public void cleanup() {
        super.cleanup();
        this.worldSlice.reset();
        for (int i = 0; i < LAYERS.length; i++) {
            if (this.usedWorldRenderers[i]) {
                this.worldRenderers[i].finishDrawing();
                this.usedWorldRenderers[i] = false;
            }
        }
    }

    // Upgrades the material when the sprite needs alpha or mipmap handling
    private Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata() && this.useRenderPassOptimization) {
            var transparencyLevel = ((SpriteTransparencyLevel.Holder)sprite).impetus$getTransparencyLevel();
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE) {
                // Downgrade to solid
                return this.renderPassConfiguration.defaultSolidMaterial();
            } else if (material == this.renderPassConfiguration.defaultTranslucentMaterial() && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                // Downgrade to cutout
                return this.renderPassConfiguration.defaultCutoutMippedMaterial();
            }
        }
        return material;
    }

    private static final int BLOCK_VERTEX_FORMAT_SIZE;

    static {
        var format = DefaultVertexFormats.BLOCK;
        int size = 0;
        for (int i = 0; i < format.getElementCount(); i++) {
            size += format.getElement(i).getSize();
        }
        BLOCK_VERTEX_FORMAT_SIZE = size;
    }

    private void copyBlockData(ByteBuffer source, ChunkBuildBuffers buffers, Material material,
                               IntArrayList blockRuns) {
        int vsize = BLOCK_VERTEX_FORMAT_SIZE;
        int numQuads = source.limit() / (vsize * 4);
        long ptr = LWJGL.memAddress(source);
        var quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
        var animatedSpritesList = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>)buffers.getSectionContextBundle()).animatedSprites;
        // Walk the per-block attribution runs in lockstep with the quads (see recordVanillaBlockAttribution).
        int runCursor = 0;
        int runId = 0;
        int runRenderType = 0;
        int runMetadata = 0;
        int runEmission = 0;
        int runLocalX = 0;
        int runLocalY = 0;
        int runLocalZ = 0;
        boolean shadersActive = com.bdmajora.impetus.umbra.terrain.UmbraTerrainProgramOverride.areShadersActive();
        for(int q = 0; q < numQuads; q++) {
            boolean hasRun = false;
            if (blockRuns != null) {
                while (runCursor < blockRuns.size() && q >= blockRuns.getInt(runCursor)) {
                    runCursor += VANILLA_BLOCK_RUN_STRIDE;
                }
                if (runCursor < blockRuns.size()) {
                    hasRun = true;
                    runId = blockRuns.getInt(runCursor + 1);
                    runRenderType = blockRuns.getInt(runCursor + 2);
                    runMetadata = blockRuns.getInt(runCursor + 3);
                    runEmission = blockRuns.getInt(runCursor + 4);
                    runLocalX = blockRuns.getInt(runCursor + 5);
                    runLocalY = blockRuns.getInt(runCursor + 6);
                    runLocalZ = blockRuns.getInt(runCursor + 7);
                }
            }
            float uSum = 0, vSum = 0, xSum = 0, ySum = 0, zSum = 0;
            for(int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.x = LWJGL.memGetFloat(ptr);
                vertex.y = LWJGL.memGetFloat(ptr + 4);
                vertex.z = LWJGL.memGetFloat(ptr + 8);
                vertex.color = LWJGL.memGetInt(ptr + 12);
                vertex.u = LWJGL.memGetFloat(ptr + 16);
                vertex.v = LWJGL.memGetFloat(ptr + 20);
                uSum += vertex.u;
                vSum += vertex.v;
                xSum += vertex.x;
                ySum += vertex.y;
                zSum += vertex.z;
                vertex.light = LWJGL.memGetInt(ptr + 24);
                ptr += vsize;
            }
            TextureAtlasSprite sprite = this.textureAtlas.impetus$findFromUV(uSum * 0.25f, vSum * 0.25f);
            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSpritesList.add(sprite);
            }
            int trueNormal = QuadUtil.calculateNormal(quad);
            for (int v = 0; v < 4; v++) {
                var vertex = quad[v];
                vertex.vanillaNormal = trueNormal;
                vertex.trueNormal = trueNormal;
            }
            if (shadersActive) {
                // OptiFine attributes for the vanilla-sourced path: mc_Entity from the attribution runs, mid-tex as the centre of this quad's mapped region (not the sprite centre, see VintageBlockRenderer.populateUmbraVertexData), tangent derived here
                float midU = uSum * 0.25f;
                float midV = vSum * 0.25f;
                int tangent = com.bdmajora.impetus.umbra.vertices.NormalHelper.computeTangent(
                        NormI8.unpackX(trueNormal),
                        NormI8.unpackY(trueNormal),
                        NormI8.unpackZ(trueNormal),
                        quad[0].x, quad[0].y, quad[0].z, quad[0].u, quad[0].v,
                        quad[1].x, quad[1].y, quad[1].z, quad[1].u, quad[1].v,
                        quad[2].x, quad[2].y, quad[2].z, quad[2].u, quad[2].v);
                int localX = hasRun ? runLocalX : clampSectionCoord((int)Math.floor(xSum * 0.25f));
                int localY = hasRun ? runLocalY : clampSectionCoord((int)Math.floor(ySum * 0.25f));
                int localZ = hasRun ? runLocalZ : clampSectionCoord((int)Math.floor(zSum * 0.25f));
                int id = hasRun ? runId : 0;
                int renderType = hasRun ? runRenderType : 0;
                int metadata = hasRun ? runMetadata : 0;
                int emission = hasRun ? runEmission : 0;
                for (int v = 0; v < 4; v++) {
                    var vertex = quad[v];
                    vertex.midTexU = midU;
                    vertex.midTexV = midV;
                    vertex.tangent = tangent;
                    vertex.blockId = id;
                    vertex.blockRenderType = renderType;
                    vertex.blockData = metadata;
                    vertex.blockEmission = emission;
                    vertex.midBlockX = localX + 0.5f - vertex.x;
                    vertex.midBlockY = localY + 0.5f - vertex.y;
                    vertex.midBlockZ = localZ + 0.5f - vertex.z;
                }
            }
            ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            Material correctMaterial = selectMaterial(material, sprite);
            buffers.get(correctMaterial).getVertexBuffer(facing).push(quad, correctMaterial);
        }
    }

    // The emission byte of at_midBlock; vanilla is 0-15 but a mod's getLightValue may answer past that
    private static int clampBlockEmission(int value) {
        return MathUtil.clamp(value, 0, 255);
    }

    // Section-local coordinates are 0-15
    private static int clampSectionCoord(int value) {
        return MathUtil.clamp(value, 0, 15);
    }
}
