package com.bdmajora.impetus.api.options.structure;

// Actions Apply performs after a changed option is written
public enum OptionFlag {
    // Rebuilds the world renderer and switches the shader pack to the version matching the Graphics API and includes a renderer update
    REQUIRES_RENDERER_RELOAD,
    // Schedules a terrain update that searches the section graph again without rebuilding any mesh
    REQUIRES_RENDERER_UPDATE,
    // Applies the mipmap level and reloads every resource
    REQUIRES_ASSET_RELOAD,
    // Shows a warning that the game must be restarted
    REQUIRES_GAME_RESTART,
    // Parses the selected shader pack again and rebuilds the world renderer and includes a renderer reload
    REQUIRES_SHADER_PIPELINE_RELOAD
}