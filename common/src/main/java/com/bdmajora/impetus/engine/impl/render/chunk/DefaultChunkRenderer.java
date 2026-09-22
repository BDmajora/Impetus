package com.bdmajora.impetus.engine.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexAttributeBinding;
import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.tessellation.*;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataStorage;
import com.bdmajora.impetus.engine.impl.render.chunk.data.SectionRenderDataUnsafe;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderListIterable;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.DirectMultiDrawEmitter;
import com.bdmajora.impetus.engine.impl.render.chunk.multidraw.MultiDrawEmitter;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.util.BitwiseMath;
import java.util.Iterator;

public abstract class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final MultiDrawEmitter emitter;

    private final Reference2ReferenceMap<ChunkPrimitiveType, SharedQuadIndexBuffer> sharedIndexBuffers;

    private TerrainRenderPass currentRenderPass;
    private GlVertexFormat currentVertexFormat;

    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        this(device, renderPassConfiguration, new DirectMultiDrawEmitter());
    }

    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, MultiDrawEmitter emitter) {
        super(device, renderPassConfiguration);

        this.emitter = emitter;
        this.sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    }

    // Skip faces pointing away from the camera
    protected boolean useBlockFaceCulling() {
        return true;
    }

    // The shared index buffer for a primitive type, created lazily
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

            ChunkShaderInterface shader = this.activeProgram.getInterface();
            shader.setProjectionMatrix(matrices.projection());
            shader.setModelViewMatrix(matrices.modelView());

            var primitiveType = shader.getPrimitiveType();

            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

            this.currentRenderPass = renderPass;
            this.currentVertexFormat = this.currentRenderPass.vertexType().getVertexFormat();

            this.configureShaderInterface(shader);

            long timestamp = System.nanoTime();

            while (iterator.hasNext()) {
                ChunkRenderList renderList = iterator.next();

                var region = renderList.getRegion();
                var storage = region.getStorage(renderPass);

                if (storage == null) {
                    continue;
                }

                fillCommandBuffer(this.emitter, region, storage, renderList, occlusionCamera, renderPass, useBlockFaceCulling && !renderPass.isSorted());

                if (this.emitter.isEmpty()) {
                    continue;
                }

                if (!renderPass.isSorted()) {
                   getSharedIndexBuffer(renderPass.primitiveType(), commandList).ensureCapacity(commandList, this.emitter.getIndexBufferSize());
                }

                var tessellation = this.prepareTessellation(commandList, region);

                setModelMatrixUniforms(shader, region, camera);
                shader.setSectionAges(timestamp, region.getSectionLoadTimes());
                this.emitter.executeBatch(commandList, tessellation, primitiveType);
            }

            this.currentVertexFormat = null;
            this.currentRenderPass = null;
        }

        this.end(renderPass);
    }

    private static void fillCommandBuffer(MultiDrawEmitter emitter,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling) {
        emitter.clear();

        var iterator = renderList.sectionsWithGeometryIterator(pass.isReverseOrder());

        if (iterator == null) {
            return;
        }

        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        int indexPointerMask = pass.isSorted() ? 0xFFFFFFFF : 0;

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            if (slices != 0) {
                emitter.addDrawCommands(pMeshData, slices, indexPointerMask);
            }
        }
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X      = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y      = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z      = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X      = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y      = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z      = ModelQuadFacing.NEG_Z.ordinal();

    // Inverts the block-face culling test so only the faces that WOULD be culled are drawn; compile-time constant so the branch folds away
    private static final boolean DEBUG_BLOCK_FACE_CULLING = false;

    // Which facings can face the camera, from the section's position relative to it
    private static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        // Branch-less on purpose: HotSpot never emits SETcc/CMOV for (a > b) ? 1 : 0, so use (b - a) >> 31 sign-bit extension and masking instead (compare LLVM's output: https://godbolt.org/z/GaaEx39T9)

        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        if (DEBUG_BLOCK_FACE_CULLING) {
            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_POS_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_POS_Z;

            planes |=    BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_NEG_X;
            planes |=    BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_NEG_Y;
            planes |=    BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_NEG_Z;
        } else {
            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

            planes |=    BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
            planes |=    BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
            planes |=    BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;
        }

        return planes;
    }

    // Region offset relative to the split camera position
    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    // One axis of the camera-relative offset
    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    // The region's tessellation, built on first use
    private GlTessellation prepareTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources(this.currentVertexFormat);
        var tessellation = this.currentRenderPass.isSorted() ? resources.getIndexedTessellation() : resources.getTessellation();

        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources);
            if (this.currentRenderPass.isSorted()) {
                resources.updateIndexedTessellation(commandList, tessellation);
            } else {
                resources.updateTessellation(commandList, tessellation);
            }
        }

        return tessellation;
    }

    // Attribute bindings from the vertex format
    private GlVertexAttributeBinding[] generateVertexAttributeBindings() {
        var attributes = this.currentVertexFormat.getAttributes();
        var bindings = new GlVertexAttributeBinding[attributes.size()];
        int i = 0;
        for (var attr : attributes) {
            bindings[i] = new GlVertexAttributeBinding(i, attr);
            i++;
        }
        return bindings;
    }

    // Vertex and index buffer bindings for a region
    protected TessellationBinding[] makeTessellationBindingArray(CommandList commandList, RenderRegion.DeviceResources resources) {
        return new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getVertexBuffer(), this.generateVertexAttributeBindings()),
                TessellationBinding.forElementBuffer(this.currentRenderPass.isSorted() ? resources.getIndexBuffer() : this.getSharedIndexBuffer(this.currentRenderPass.primitiveType(), commandList).getBufferObject())
        };
    }

    // A VAO over the region's buffers
    protected GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        var bindings = makeTessellationBindingArray(commandList, resources);
        GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), bindings);
        tessellation.init(commandList);

        return tessellation;
    }

    // Frees shared buffers and programs
    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffers.values().forEach(buffer -> buffer.delete(commandList));
        this.emitter.delete();
    }
}
