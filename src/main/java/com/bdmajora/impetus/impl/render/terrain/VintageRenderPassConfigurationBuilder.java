package com.bdmajora.impetus.impl.render.terrain;

import com.google.common.collect.ImmutableListMultimap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexType;
import com.bdmajora.impetus.ImpetusVintage;

import java.util.HashMap;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import java.util.Locale;
import java.util.Map;

// Builder of the terrain render pass configuration.
public class VintageRenderPassConfigurationBuilder {

    // Pipeline state that turns atlas mipmaps on or off.
    private static final class AtlasMipmapState implements TerrainRenderPass.PipelineState {
        // Flag set when the pass uses mipmaps.
        private final boolean allowMipmaps;

        // Creates an atlas mipmap state from its allow mipmaps.
        AtlasMipmapState(boolean allowMipmaps) {
            this.allowMipmaps = allowMipmaps;
        }

        // Returns whether mipmaps are on.
        private static boolean mipmapsEnabled() {
            return Minecraft.getMinecraft().gameSettings.mipmapLevels > 0;
        }

        // Sets the atlas filtering for a mipped or unmipped pass.
        private static void apply(boolean mipped) {
            var textureManager = Minecraft.getMinecraft().getTextureManager();
            textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            if (textureManager.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE) instanceof AbstractTexture atlas) {
                atlas.setBlurMipmapDirect(false, mipped);
            }
        }

        // Sets up.
        @Override
        public void setup() {
            apply(this.allowMipmaps && mipmapsEnabled());
        }

        // Restores the atlas filtering.
        @Override
        public void clear() {
            if (!this.allowMipmaps) {
                apply(mipmapsEnabled());
            }
        }
    }

    // Translucent pass state that writes depth like modern Sodium, so out of order faces hide what is behind them.
    static final class TranslucentDepthState implements TerrainRenderPass.PipelineState {
        // GL query of the depth write mask.
        private static final int GL_DEPTH_WRITEMASK = 0x0B72;
        // Mipmap state of the pass.
        private final TerrainRenderPass.PipelineState atlas;
        // Set while depth writes were turned on here and must be turned off again.
        private boolean writing;

        TranslucentDepthState(TerrainRenderPass.PipelineState atlas) {
            this.atlas = atlas;
        }

        // Turns depth writes on unless a shader pack drives the water state itself.
        // Raw GL on both ends keeps GlStateManager's cached mask matching GL afterwards.
        @Override
        public void setup() {
            this.atlas.setup();
            if (!com.bdmajora.impetus.umbra.Umbra.isShaderPackInUse() && !LWJGL.glGetBoolean(GL_DEPTH_WRITEMASK)) {
                this.writing = true;
                LWJGL.glDepthMask(true);
            }
        }

        // Turns depth writes back off for the pass one entities vanilla draws next.
        @Override
        public void clear() {
            try {
                if (this.writing) {
                    this.writing = false;
                    LWJGL.glDepthMask(false);
                }
            } finally {
                this.atlas.clear();
            }
        }
    }

    // State of a mipped pass.
    private static final TerrainRenderPass.PipelineState MIPMAPPED_STATE = new AtlasMipmapState(true);
    // State of an unmipped pass.
    private static final TerrainRenderPass.PipelineState UNMIPMAPPED_STATE = new AtlasMipmapState(false);

    // Returns the passes of a block layer.
    private static TerrainRenderPass.TerrainRenderPassBuilder builderForRenderType(BlockRenderLayer chunkRenderType, ChunkVertexType vertexType) {
        var extraDefines = new HashMap<String, String>();

        if (ImpetusVintage.options().quality.chunkFadeInDuration > 0) {
            extraDefines.put("CHUNK_FADE_IN_DURATION_MS", String.valueOf(ImpetusVintage.options().quality.chunkFadeInDuration));
        }

        var pipelineState = chunkRenderType == BlockRenderLayer.CUTOUT ? UNMIPMAPPED_STATE : MIPMAPPED_STATE;

        return TerrainRenderPass.builder().extraDefines(extraDefines).pipelineState(pipelineState).vertexType(vertexType).primitiveType(QuadPrimitiveType.TRIANGULATED);
    }

    // Returns the passes of every block layer.
    public static RenderPassConfiguration<BlockRenderLayer> build(ChunkVertexType vertexType) {
        return build(vertexType, ImpetusVintage.options().performance.useTranslucentFaceSorting);
    }

    static RenderPassConfiguration<BlockRenderLayer> build(ChunkVertexType vertexType, boolean sortTranslucentFaces) {
        TerrainRenderPass solidPass, cutoutMippedPass, translucentPass;

        solidPass = builderForRenderType(BlockRenderLayer.SOLID, vertexType)
                .name("solid")
                .fragmentDiscard(false)
                .useReverseOrder(false)
                .build();
        cutoutMippedPass = builderForRenderType(BlockRenderLayer.CUTOUT_MIPPED, vertexType)
                .name("cutout_mipped")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();
        // Discards what vanilla's alpha test drops so invisible texels write no depth
        translucentPass = builderForRenderType(BlockRenderLayer.TRANSLUCENT, vertexType)
                .pipelineState(new TranslucentDepthState(MIPMAPPED_STATE))
                .name("translucent")
                .fragmentDiscard(true)
                .useReverseOrder(true)
                .useTranslucencySorting(sortTranslucentFaces)
                .build();

        ImmutableListMultimap.Builder<BlockRenderLayer, TerrainRenderPass> vanillaRenderStages = ImmutableListMultimap.builder();

        Material solidMaterial, cutoutMaterial, cutoutMippedMaterial, translucentMaterial;
        solidMaterial = new Material(solidPass, AlphaCutoffParameter.ZERO, true);
        translucentMaterial = new Material(translucentPass, AlphaCutoffParameter.ONE_TENTH, true);
        cutoutMippedMaterial = new Material(cutoutMippedPass, AlphaCutoffParameter.ONE_TENTH, true);

        vanillaRenderStages.put(BlockRenderLayer.SOLID, solidPass);
        vanillaRenderStages.put(BlockRenderLayer.TRANSLUCENT, translucentPass);

        TerrainRenderPass cutoutPass = builderForRenderType(BlockRenderLayer.CUTOUT, vertexType)
                .name("cutout")
                .fragmentDiscard(true)
                .useReverseOrder(false)
                .build();

        cutoutMaterial = new Material(cutoutPass, AlphaCutoffParameter.ONE_TENTH, false);
        vanillaRenderStages.put(BlockRenderLayer.CUTOUT, cutoutPass);

        if (ImpetusVintage.options().performance.useRenderPassConsolidation) {
            vanillaRenderStages.put(BlockRenderLayer.SOLID, cutoutMippedPass);
        } else {
            vanillaRenderStages.put(BlockRenderLayer.CUTOUT_MIPPED, cutoutMippedPass);
        }

        Map<BlockRenderLayer, Material> renderTypeToMaterialMap = new Reference2ReferenceOpenHashMap<>(4,
                Reference2ReferenceOpenHashMap.VERY_FAST_LOAD_FACTOR);

        renderTypeToMaterialMap.put(BlockRenderLayer.SOLID, solidMaterial);
        renderTypeToMaterialMap.put(BlockRenderLayer.CUTOUT, cutoutMaterial);
        renderTypeToMaterialMap.put(BlockRenderLayer.CUTOUT_MIPPED, cutoutMippedMaterial);
        renderTypeToMaterialMap.put(BlockRenderLayer.TRANSLUCENT, translucentMaterial);

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (!renderTypeToMaterialMap.containsKey(layer)) {
                ImpetusVintage.logger().warn("Falling back to cutout-like behavior for custom block render layer '{}'", layer);
                TerrainRenderPass pass = builderForRenderType(layer, vertexType).name(layer.name().toLowerCase(Locale.ROOT)).fragmentDiscard(true).useReverseOrder(false).build();
                Material material = new Material(pass, AlphaCutoffParameter.ONE_TENTH, true);
                vanillaRenderStages.put(layer, pass);
                renderTypeToMaterialMap.put(layer, material);
            }
        }

        var vanillaRenderStageMap = vanillaRenderStages.build();

        return new RenderPassConfiguration<>(renderTypeToMaterialMap,
                vanillaRenderStageMap.asMap(),
                solidMaterial,
                cutoutMippedMaterial,
                translucentMaterial);
    }
}
