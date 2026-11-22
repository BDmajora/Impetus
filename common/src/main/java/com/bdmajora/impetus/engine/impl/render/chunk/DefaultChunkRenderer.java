package com.bdmajora.impetus.engine.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.debug.GLDebug;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.tessellation.*;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderListIterable;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.BatchAssembler;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import java.util.Iterator;

// Chunk renderer that draws each region with a cached multi draw batch.
public abstract class DefaultChunkRenderer extends ShaderChunkRenderer {
    // Shared index buffers by primitive type.
    private final Reference2ReferenceMap<ChunkPrimitiveType, SharedQuadIndexBuffer> sharedIndexBuffers;

    // Provider that creates region tessellations for the batch assembler.
    private final BatchAssembler.TessellationProvider tessellationProvider = this::prepareTessellation;

    // Pass drawn by the current render call or null.
    private TerrainRenderPass currentRenderPass;
    // Vertex format of the current pass or null.
    private GlVertexFormat currentVertexFormat;

    // Creates the renderer and rejects null arguments.
    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        super(device, renderPassConfiguration, fogService);

        this.sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    }

    // Returns true when hidden block faces are culled.
    protected boolean useBlockFaceCulling() {
        return true;
    }

    // Returns the shared index buffer of a primitive type and creates it when missing.
    protected final SharedQuadIndexBuffer getSharedIndexBuffer(ChunkPrimitiveType type, CommandList commandList) {
        var buffer = this.sharedIndexBuffers.get(type);
        if (buffer == null) {
            buffer = new SharedQuadIndexBuffer(commandList, type);
            this.sharedIndexBuffers.put(type, buffer);
        }
        return buffer;
    }

    // Sets the uniforms of a subclass on the shader.
    protected abstract void configureShaderInterface(ChunkShaderInterface shader);

    // Draws every region of a pass and restores the pass state on every path.
    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform occlusionCamera,
                       CameraTransform camera) {
        if (!renderLists.hasPass(renderPass)) {
            return;
        }

        this.begin(renderPass);

        try {
            // Skips drawing when the program failed to compile
            if (this.activeProgram != null) {
                this.drawRegions(matrices, commandList, renderLists, renderPass, occlusionCamera, camera);
            }
        } finally {
            this.end(renderPass);
        }
    }

    // Draws the regions of a pass inside a debug group.
    private void drawRegions(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists,
                             TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera) {
        GLDebug.pushGroup(770, renderPass.name() + " terrain pass");

        try {
            ChunkShaderInterface shader = this.activeProgram.getInterface();
            shader.setProjectionMatrix(matrices.projection());
            shader.setModelViewMatrix(matrices.modelView());

            var primitiveType = shader.getPrimitiveType();

            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

            this.currentRenderPass = renderPass;
            this.currentVertexFormat = renderPass.vertexType().getVertexFormat();

            this.configureShaderInterface(shader);

            long timestamp = System.nanoTime();

            boolean useBlockFaceCulling = this.useBlockFaceCulling() && !renderPass.isSorted() && !renderPass.isReverseOrder();
            var cacheParams = new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling);

            while (iterator.hasNext()) {
                ChunkRenderList renderList = iterator.next();

                var region = renderList.getRegion();
                var storage = region.getStorage(renderPass);

                if (storage == null) {
                    continue;
                }

                var cached = storage.getCachedMultiDrawBatch(cacheParams);

                if (cached == null || !cached.isValidFor(renderList.getSectionsWithGeometry(), renderList.getSectionsWithGeometryCount(),
                        occlusionCamera.intX, occlusionCamera.intY, occlusionCamera.intZ)) {
                    cached = BatchAssembler.createCachedBatch(region, storage, renderList, occlusionCamera, renderPass,
                            useBlockFaceCulling, commandList, this.tessellationProvider);

                    storage.storeCachedMultiDrawBatch(cacheParams, cached);
                }

                var batch = cached.getBatch();

                if (batch != null && !batch.isEmpty()) {
                    if (!renderPass.isSorted()) {
                        getSharedIndexBuffer(renderPass.primitiveType(), commandList)
                                .ensureCapacity(commandList, batch.getIndexBufferSize());
                    }

                    setModelMatrixUniforms(shader, region, camera);
                    shader.setSectionAges(timestamp, region.getSectionLoadTimes());
                    batch.execute(commandList, cached.getTessellation(), primitiveType);
                }
            }
        } finally {
            this.currentVertexFormat = null;
            this.currentRenderPass = null;

            GLDebug.popGroup();
        }
    }

    // Stores the offset of a region from the camera on the shader.
    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    // Returns the offset of a block coordinate from the camera.
    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    // Returns the tessellation of a region for a pass and creates it when missing.
    private GlTessellation prepareTessellation(CommandList commandList, RenderRegion region, TerrainRenderPass pass) {
        var resources = region.getResources();
        var key = pass.tessellationKey();

        var tessellation = resources.getTessellation(key);

        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources);
            resources.updateTessellation(commandList, key, tessellation);
        }

        return tessellation;
    }

    // Creates the vertex and element bindings of a region for the current pass.
    protected TessellationBinding[] makeTessellationBindingArray(CommandList commandList, RenderRegion.DeviceResources resources) {
        return new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getVertexBuffer(), this.currentVertexFormat),
                TessellationBinding.forElementBuffer(this.currentRenderPass.isSorted() ? resources.getIndexBuffer() : this.getSharedIndexBuffer(this.currentRenderPass.primitiveType(), commandList).getBufferObject())
        };
    }

    // Creates a vertex array tessellation for a region and deletes it when setup fails.
    protected GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        var bindings = makeTessellationBindingArray(commandList, resources);
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), bindings);
        try {
            tessellation.init(commandList);
        } catch (RuntimeException | Error failure) {
            try {
                tessellation.delete(commandList);
            } catch (RuntimeException suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }

        return tessellation;
    }

    // Deletes the programs and every shared index buffer once.
    @Override
    public void delete(CommandList commandList) {
        try {
            super.delete(commandList);
        } finally {
            var buffers = this.sharedIndexBuffers.values().toArray(new SharedQuadIndexBuffer[0]);
            this.sharedIndexBuffers.clear();
            for (SharedQuadIndexBuffer buffer : buffers) {
                buffer.delete(commandList);
            }
        }
    }
}
