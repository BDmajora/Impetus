package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.OptionIdentifier;

// Untyped ids of the built in pages and groups and options
public final class StandardOptions {
    private static final String MINECRAFT_MOD_ID = "minecraft";
    private static final String IMPETUS_MOD_ID = "impetus";

    // Prevents instantiation
    private StandardOptions() {
    }

    // Returns the interned id for the mod id and path cast to an untyped id
    private static OptionIdentifier<Void> id(String modId, String path) {
        return OptionIdentifier.intern(modId, path).cast();
    }

    // Group ids
    // FLUIDLOGGING shares its id with the page of the same name
    public static final class Group {
        public static final OptionIdentifier<Void> RENDERING = id(MINECRAFT_MOD_ID, "rendering");
        public static final OptionIdentifier<Void> WINDOW = id(MINECRAFT_MOD_ID, "window");
        public static final OptionIdentifier<Void> INDICATORS = id(MINECRAFT_MOD_ID, "indicators");
        public static final OptionIdentifier<Void> GRAPHICS = id(MINECRAFT_MOD_ID, "graphics");
        public static final OptionIdentifier<Void> MIPMAPS = id(MINECRAFT_MOD_ID, "mipmaps");
        public static final OptionIdentifier<Void> DETAILS = id(MINECRAFT_MOD_ID, "details");
        public static final OptionIdentifier<Void> CHUNK_UPDATES = id(IMPETUS_MOD_ID, "chunk_updates");
        public static final OptionIdentifier<Void> RENDERING_CULLING = id(IMPETUS_MOD_ID, "rendering_culling");
        public static final OptionIdentifier<Void> CPU_SAVING = id(IMPETUS_MOD_ID, "cpu_saving");
        public static final OptionIdentifier<Void> SORTING = id(IMPETUS_MOD_ID, "sorting");
        public static final OptionIdentifier<Void> LIGHTING = id(IMPETUS_MOD_ID, "lighting");
        public static final OptionIdentifier<Void> FILTERING = id(IMPETUS_MOD_ID, "filtering");
        public static final OptionIdentifier<Void> FLUIDS = id(IMPETUS_MOD_ID, "fluids");
        public static final OptionIdentifier<Void> MESH_TERRAIN = id(IMPETUS_MOD_ID, "mesh_terrain");
        public static final OptionIdentifier<Void> FLUIDLOGGING = id(IMPETUS_MOD_ID, "fluidlogging");

        // Prevents instantiation
        private Group() {
        }
    }

    // Page ids
    public static final class Pages {
        public static final OptionIdentifier<Void> GENERAL = id(IMPETUS_MOD_ID, "general");
        public static final OptionIdentifier<Void> QUALITY = id(IMPETUS_MOD_ID, "quality");
        public static final OptionIdentifier<Void> PERFORMANCE = id(IMPETUS_MOD_ID, "performance");
        public static final OptionIdentifier<Void> NVIDIUM = id(IMPETUS_MOD_ID, "nvidium");
        public static final OptionIdentifier<Void> FLUIDLOGGING = id(IMPETUS_MOD_ID, "fluidlogging");
        public static final OptionIdentifier<Void> ADVANCED = id(IMPETUS_MOD_ID, "advanced");
        public static final OptionIdentifier<Void> SHADERS = id(IMPETUS_MOD_ID, "shaders");

        // Prevents instantiation
        private Pages() {
        }
    }

    // Option ids
    public static final class Option {
        public static final OptionIdentifier<Void> RENDER_DISTANCE = id(MINECRAFT_MOD_ID, "render_distance");
        public static final OptionIdentifier<Void> SIMULATION_DISTANCE = id(MINECRAFT_MOD_ID, "simulation_distance");
        public static final OptionIdentifier<Void> BRIGHTNESS = id(MINECRAFT_MOD_ID, "brightness");
        public static final OptionIdentifier<Void> GUI_SCALE = id(MINECRAFT_MOD_ID, "gui_scale");
        public static final OptionIdentifier<Void> FULLSCREEN = id(MINECRAFT_MOD_ID, "fullscreen");
        public static final OptionIdentifier<Void> FULLSCREEN_RESOLUTION = id(MINECRAFT_MOD_ID, "fullscreen_resolution");
        public static final OptionIdentifier<Void> VSYNC = id(MINECRAFT_MOD_ID, "vsync");
        public static final OptionIdentifier<Void> MAX_FRAMERATE = id(MINECRAFT_MOD_ID, "max_frame_rate");
        public static final OptionIdentifier<Void> VIEW_BOBBING = id(MINECRAFT_MOD_ID, "view_bobbing");
        public static final OptionIdentifier<Void> INACTIVITY_FPS_LIMIT = id(MINECRAFT_MOD_ID, "inactivity_fps_limit");
        public static final OptionIdentifier<Void> ATTACK_INDICATOR = id(MINECRAFT_MOD_ID, "attack_indicator");
        public static final OptionIdentifier<Void> AUTOSAVE_INDICATOR = id(MINECRAFT_MOD_ID, "autosave_indicator");
        public static final OptionIdentifier<Void> GRAPHICS_API = id(MINECRAFT_MOD_ID, "graphics_api");
        public static final OptionIdentifier<Void> GRAPHICS_MODE = id(MINECRAFT_MOD_ID, "graphics_mode");
        public static final OptionIdentifier<Void> CLOUDS = id(MINECRAFT_MOD_ID, "clouds");
        public static final OptionIdentifier<Void> CLOUD_HEIGHT = id(MINECRAFT_MOD_ID, "cloud_height");
        public static final OptionIdentifier<Void> CLOUD_DISTANCE = id(MINECRAFT_MOD_ID, "cloud_distance");
        public static final OptionIdentifier<Void> WEATHER = id(MINECRAFT_MOD_ID, "weather");
        public static final OptionIdentifier<Void> WEATHER_EFFECT_RADIUS = id(MINECRAFT_MOD_ID, "weather_effect_radius");
        public static final OptionIdentifier<Void> LEAVES = id(MINECRAFT_MOD_ID, "leaves");
        public static final OptionIdentifier<Void> SEE_THROUGH_LEAVES = id(MINECRAFT_MOD_ID, "see_through_leaves");
        public static final OptionIdentifier<Void> PARTICLES = id(MINECRAFT_MOD_ID, "particles");
        public static final OptionIdentifier<Void> SMOOTH_LIGHT = id(MINECRAFT_MOD_ID, "smooth_lighting");
        public static final OptionIdentifier<Void> BIOME_BLEND = id(MINECRAFT_MOD_ID, "biome_blend");
        public static final OptionIdentifier<Void> ENTITY_DISTANCE = id(MINECRAFT_MOD_ID, "entity_distance");
        public static final OptionIdentifier<Void> ENTITY_SHADOWS = id(MINECRAFT_MOD_ID, "entity_shadows");
        public static final OptionIdentifier<Void> VIGNETTE = id(MINECRAFT_MOD_ID, "vignette");
        public static final OptionIdentifier<Void> MIPMAP_LEVEL = id(MINECRAFT_MOD_ID, "mipmap_levels");
        public static final OptionIdentifier<Void> TEXTURE_FILTERING = id(MINECRAFT_MOD_ID, "texture_filtering");
        public static final OptionIdentifier<Void> ANISOTROPIC_FILTERING = id(MINECRAFT_MOD_ID, "anisotropic_filtering");
        public static final OptionIdentifier<Void> TEXEL_INTERPOLATION = id(MINECRAFT_MOD_ID, "texel_interpolation");
        public static final OptionIdentifier<Void> CHUNK_UPDATE_THREADS = id(IMPETUS_MOD_ID, "chunk_update_threads");
        public static final OptionIdentifier<Void> DEFFER_CHUNK_UPDATES = id(IMPETUS_MOD_ID, "defer_chunk_updates");
        public static final OptionIdentifier<Void> BLOCK_FACE_CULLING = id(IMPETUS_MOD_ID, "block_face_culling");
        public static final OptionIdentifier<Void> COMPACT_VERTEX_FORMAT = id(IMPETUS_MOD_ID, "compact_vertex_format");
        public static final OptionIdentifier<Void> FOG_OCCLUSION = id(IMPETUS_MOD_ID, "fog_occlusion");
        public static final OptionIdentifier<Void> ENTITY_CULLING = id(IMPETUS_MOD_ID, "entity_culling");
        public static final OptionIdentifier<Void> ANIMATE_VISIBLE_TEXTURES = id(IMPETUS_MOD_ID, "animate_only_visible_textures");
        public static final OptionIdentifier<Void> NO_ERROR_CONTEXT = id(IMPETUS_MOD_ID, "no_error_context");
        public static final OptionIdentifier<Void> PERSISTENT_MAPPING = id(IMPETUS_MOD_ID, "persistent_mapping");
        public static final OptionIdentifier<Void> MULTI_DRAW_INDIRECT = id(IMPETUS_MOD_ID, "multi_draw_indirect");
        public static final OptionIdentifier<Void> CPU_FRAMES_AHEAD = id(IMPETUS_MOD_ID, "cpu_render_ahead_limit");
        public static final OptionIdentifier<Void> TRANSLUCENT_FACE_SORTING = id(IMPETUS_MOD_ID, "translucent_face_sorting");
        public static final OptionIdentifier<Void> USE_QUAD_NORMALS_FOR_LIGHTING = id(IMPETUS_MOD_ID, "use_quad_normals_for_lighting");
        public static final OptionIdentifier<Void> RENDER_PASS_OPTIMIZATION = id(IMPETUS_MOD_ID, "render_pass_optimization");
        public static final OptionIdentifier<Void> RENDER_PASS_CONSOLIDATION = id(IMPETUS_MOD_ID, "render_pass_consolidation");
        public static final OptionIdentifier<Void> USE_FASTER_CLOUDS = id(IMPETUS_MOD_ID, "use_faster_clouds");
        public static final OptionIdentifier<Void> ASYNC_GRAPH_SEARCH = id(IMPETUS_MOD_ID, "async_graph_search");
        public static final OptionIdentifier<Void> CHUNK_FADE_IN_DURATION = id(IMPETUS_MOD_ID, "chunk_fade_in_duration");
        public static final OptionIdentifier<Void> RASTER_OCCLUSION_CULLING = id(IMPETUS_MOD_ID, "raster_occlusion_culling");
        public static final OptionIdentifier<Void> IMPROVED_TRANSPARENCY = id(IMPETUS_MOD_ID, "improved_transparency");
        public static final OptionIdentifier<Void> FLUID_CULLING = id(IMPETUS_MOD_ID, "fluid_culling");
        public static final OptionIdentifier<Void> FLUID_SHAPING = id(IMPETUS_MOD_ID, "fluid_shaping");
        public static final OptionIdentifier<Void> INFERRED_FLUIDLOGGING = id(IMPETUS_MOD_ID, "inferred_fluidlogging");
        public static final OptionIdentifier<Void> ENTITY_SORTING = id(IMPETUS_MOD_ID, "entity_sorting");
        public static final OptionIdentifier<Void> FAST_BLOCK_RENDERER = id(IMPETUS_MOD_ID, "fast_block_renderer");
        public static final OptionIdentifier<Void> MEMORY_TRACING = id(IMPETUS_MOD_ID, "memory_tracing");
        public static final OptionIdentifier<Void> SHOW_TOASTS = id(IMPETUS_MOD_ID, "show_toasts");
        public static final OptionIdentifier<Void> INCOMPATIBLE_PACK_WARNINGS = id(IMPETUS_MOD_ID, "incompatible_pack_warnings");
        public static final OptionIdentifier<Void> MESH_TEMPORAL_COHERENCE = id(IMPETUS_MOD_ID, "mesh_temporal_coherence");
        public static final OptionIdentifier<Void> MESH_TRANSLUCENCY_SORTING = id(IMPETUS_MOD_ID, "mesh_translucency_sorting");
        public static final OptionIdentifier<Void> MESH_AUTOMATIC_MEMORY = id(IMPETUS_MOD_ID, "mesh_automatic_memory");
        public static final OptionIdentifier<Void> MESH_MAX_MEMORY = id(IMPETUS_MOD_ID, "mesh_max_geometry_memory");
        public static final OptionIdentifier<Void> MESH_KEEP_DISTANCE = id(IMPETUS_MOD_ID, "mesh_region_keep_distance");
        public static final OptionIdentifier<Void> MESH_STATISTICS = id(IMPETUS_MOD_ID, "mesh_statistics");

        // Prevents instantiation
        private Option() {
        }
    }
}