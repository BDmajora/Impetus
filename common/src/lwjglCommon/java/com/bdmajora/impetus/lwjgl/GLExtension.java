package com.bdmajora.impetus.lwjgl;

// Every GL extension the engine asks about; names match the GL_ string minus the prefix so the service resolves them by name
public enum GLExtension {
    // Immutable buffer allocation; the backing store for persistently mapped streaming buffers
    ARB_buffer_storage,
    // Clearing a buffer object without a CPU-side staging array
    ARB_clear_buffer_object,
    // One draw call for many chunk sections, the basis of the indirect multidraw path
    ARB_multi_draw_indirect,
    // Base-vertex offsets so sections can share one index buffer instead of one each
    ARB_draw_elements_base_vertex,
    // Bind-free object access; lets state changes skip the bind/modify/unbind dance
    ARB_direct_state_access,
    // SSBOs, needed for shader packs that declare bufferObject.<n>
    ARB_shader_storage_buffer_object,
    // Fences, used to know when the GPU is done with a region before the CPU rewrites it
    ARB_sync,
    // GPU-side timing for the frame profiler
    ARB_timer_query,
    // UBOs for the shared per-frame uniform block
    ARB_uniform_buffer_object,
    // VAOs, so vertex attribute layout is set once per format rather than per draw
    ARB_vertex_array_object,
    // Mapping a sub-range of a buffer, with explicit flush, for streaming uploads
    ARB_map_buffer_range,
    // Pixel-pack buffers, so a readback lands in a buffer the CPU maps a frame later instead of stalling on glReadPixels
    ARB_pixel_buffer_object,
    // GPU-to-GPU buffer copies, no CPU round trip
    ARB_copy_buffer,
    // Immutable texture storage for render targets
    ARB_texture_storage,
    // Instanced draws starting at a non-zero instance
    ARB_base_instance,
    // Presence of the compatibility profile, which decides whether fixed-function state is still usable
    ARB_compatibility,
    // NVIDIA-only free-VRAM query; Umbra refuses oversized SSBO allocations when present and hopes for the best when not
    NVX_gpu_memory_info,

    // Mesh-shader terrain backend extensions, queried only by MeshShaderSupport; none exist in LWJGL 2 so that backend answers false
    NV_mesh_shader,
    // Buffer GPU addresses and residency, so shaders reach buffers through raw 64-bit pointers instead of bindings
    NV_shader_buffer_load,
    // glBufferAddressRangeNV, used to bind the indirect command buffer by address
    NV_vertex_buffer_unified_memory,
    // The same, for the scene uniform block, which survives shader changes because it is bound by address
    NV_uniform_buffer_unified_memory,
    // One representative fragment per primitive is enough for the occlusion rasterizer to record "this box was visible"
    NV_representative_fragment_test,
    // Indirect mesh draws sourced from a GPU-written command buffer, which is what removes the CPU from the loop
    NV_bindless_multi_draw_indirect,
    // 8/16-bit scalar types and 64-bit ints in GLSL; the section and region metadata packing depends on them
    NV_gpu_shader5,
    // gl_BaryCoordNV, so the fragment shader interpolates vertex attributes it fetched itself
    NV_fragment_shader_barycentric,
    // Sparse virtual allocation, so terrain geometry lives in one enormous buffer with pages committed on demand
    ARB_sparse_buffer
}
