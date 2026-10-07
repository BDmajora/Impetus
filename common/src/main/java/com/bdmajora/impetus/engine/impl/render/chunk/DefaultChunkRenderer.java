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

public abstract class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final Reference2ReferenceMap<ChunkPrimitiveType, SharedQuadIndexBuffer> sharedIndexBuffers;

    private final BatchAssembler.TessellationProvider tessellationProvider = this::prepareTessellation;

    private TerrainRenderPass currentRenderPass;
    private GlVertexFormat currentVertexFormat;

    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        super(device, renderPassConfiguration, fogService);

        this.sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    }

    protected boolean useBlockFaceCulling() {
        return true;
    }

    protected final SharedQuadIndexBuffer getSharedIndexBuffer(ChunkPrimitiveType type, CommandList commandList) {
        var buffer = this.sharedIndexBuffers.get(type);
        if (buffer == null) {
            buffer = new SharedQuadIndexBuffer(commandList, type);
            this.sharedIndexBuffers.put(type, buffer);
        }
        return buffer;
    }

    protected abstract void configureShaderInterface(ChunkShaderInterface shader);

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

        // If there is no active program, shader compilation probably failed, and we can't render anything.
        if (this.activeProgram != null) {
            boolean useBlockFaceCulling = this.useBlockFaceCulling();

            GLDebug.pushGroup(770, renderPass.name() + " terrain pass");

            ChunkShaderInterface shader = this.activeProgram.getInterface();
            shader.setProjectionMatrix(matrices.projection());
            shader.setModelViewMatrix(matrices.modelView());

            var primitiveType = shader.getPrimitiveType();

            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

            this.currentRenderPass = renderPass;
            this.currentVertexFormat = this.currentRenderPass.vertexType().getVertexFormat();

            this.configureShaderInterface(shader);

            long timestamp = System.nanoTime();

            useBlockFaceCulling = useBlockFaceCulling && !renderPass.isSorted();
            var cacheParams = new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling);

            int numRebuilds = 0;

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
                    numRebuilds++;
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

            this.currentVertexFormat = null;
            this.currentRenderPass = null;

            GLDebug.popGroup();
        }

        this.end(renderPass);
    }

    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

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

    protected TessellationBinding[] makeTessellationBindingArray(CommandList commandList, RenderRegion.DeviceResources resources) {
        return new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getVertexBuffer(), this.currentVertexFormat),
                TessellationBinding.forElementBuffer(this.currentRenderPass.isSorted() ? resources.getIndexBuffer() : this.getSharedIndexBuffer(this.currentRenderPass.primitiveType(), commandList).getBufferObject())
        };
    }

    protected GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        var bindings = makeTessellationBindingArray(commandList, resources);
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), bindings);
        tessellation.init(commandList);

        return tessellation;
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffers.values().forEach(buffer -> buffer.delete(commandList));
    }
}
