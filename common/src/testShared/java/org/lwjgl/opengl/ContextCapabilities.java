package org.lwjgl.opengl;

// Test stand-in for LWJGL2's capabilities object: the same public flag names, but plain fields a test can set
public final class ContextCapabilities {
    public boolean OpenGL11 = true, OpenGL12 = true, OpenGL13 = true, OpenGL14 = true, OpenGL15 = true;
    public boolean OpenGL20 = true, OpenGL21 = true, OpenGL30 = true, OpenGL31 = true, OpenGL32 = true, OpenGL33 = true;
    public boolean OpenGL40 = true, OpenGL41 = true, OpenGL42 = true, OpenGL43 = true, OpenGL44 = true, OpenGL45 = true;
    public boolean GL_ARB_vertex_array_object = true, GL_APPLE_vertex_array_object, GL_ARB_timer_query = true, GL_ARB_sync = true;
    public boolean GL_ARB_map_buffer_range = true, GL_ARB_buffer_storage = true, GL_ARB_uniform_buffer_object = true;
    public boolean GL_ARB_texture_storage = true, GL_ARB_shader_storage_buffer_object = true, GL_ARB_pixel_buffer_object = true;
    public boolean GL_ARB_multi_draw_indirect = true, GL_ARB_draw_elements_base_vertex = true, GL_ARB_draw_buffers_blend = true;
    public boolean GL_ARB_direct_state_access = true, GL_ARB_copy_buffer = true, GL_ARB_compatibility = true;
    public boolean GL_ARB_clear_buffer_object = true, GL_ARB_base_instance = true, GL_NVX_gpu_memory_info, GL_EXT_gpu_shader4 = true;
    public boolean GL_EXT_texture_filter_anisotropic = true, GL_ARB_clear_texture = true;

    // Everything off, for the fallback paths
    public ContextCapabilities none() {
        for (java.lang.reflect.Field field : getClass().getFields()) {
            try {
                field.setBoolean(this, false);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return this;
    }
}
