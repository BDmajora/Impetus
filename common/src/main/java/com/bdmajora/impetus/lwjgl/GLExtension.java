package com.bdmajora.impetus.lwjgl;

// Extensions of OpenGL that the engine asks about.
public enum GLExtension {
    // Immutable buffer storage.
    ARB_buffer_storage,
    // Clearing buffers without a staging array.
    ARB_clear_buffer_object,
    // Multi draw calls from a command buffer.
    ARB_multi_draw_indirect,
    // Draw calls with a base vertex.
    ARB_draw_elements_base_vertex,
    // Access to objects without binding.
    ARB_direct_state_access,
    // Shader storage buffers.
    ARB_shader_storage_buffer_object,
    // Extension of sync fences.
    ARB_sync,
    // Timer queries.
    ARB_timer_query,
    // Uniform buffers.
    ARB_uniform_buffer_object,
    // Vertex arrays.
    ARB_vertex_array_object,
    // Mapping of buffer ranges.
    ARB_map_buffer_range,
    // Pixel buffers.
    ARB_pixel_buffer_object,
    // Copies between buffers.
    ARB_copy_buffer,
    // Immutable texture storage.
    ARB_texture_storage,
    // Draw calls with a base instance.
    ARB_base_instance,
    // Compatibility profile.
    ARB_compatibility,
    // Debug labels and groups.
    KHR_debug,
    // Free video memory query.
    NVX_gpu_memory_info,

    // Mesh and task shaders.
    NV_mesh_shader,
    // Buffer addresses and residency.
    NV_shader_buffer_load,
    // Buffer binding by address for vertices.
    NV_vertex_buffer_unified_memory,
    // Buffer binding by address for uniforms.
    NV_uniform_buffer_unified_memory,
    // One fragment per primitive.
    NV_representative_fragment_test,
    // Indirect draws from a GPU written buffer.
    NV_bindless_multi_draw_indirect,
    // Extra scalar types in shaders.
    NV_gpu_shader5,
    // Barycentric coordinates in fragment shaders.
    NV_fragment_shader_barycentric,
    // Sparse buffers with committed pages.
    ARB_sparse_buffer
}
