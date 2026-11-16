package com.bdmajora.impetus.impl.render.terrain.fog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import org.lwjgl.opengl.GL20;

// Fog service that reads the state manager fog.
public class GLStateManagerFogService implements FogService {
    // Shared instance.
    public static final GLStateManagerFogService INSTANCE = new GLStateManagerFogService();

    // Returns the fog end.
    @Override
    public float getFogEnd() {
        return GlStateManager.fogState.end;
    }

    // Returns the fog start.
    @Override
    public float getFogStart() {
        return GlStateManager.fogState.start;
    }

    // Returns the fog density.
    @Override
    public float getFogDensity() {
        return GlStateManager.fogState.density;
    }

    // Returns the fog shape index.
    @Override
    public int getFogShapeIndex() {
        return com.bdmajora.extras.Extras.options().render.fogShape.shaderIndex();
    }

    // Returns the fog cutoff.
    @Override
    public float getFogCutoff() {
        if (!GlStateManager.fogState.fog.currentState || GlStateManager.fogState.mode != GL20.GL_LINEAR) {
            return Float.MAX_VALUE;
        }
        return GlStateManager.fogState.end;
    }

    // Returns the fog color.
    @Override
    public float[] getFogColor() {
        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        float[] color = this.fogColor;
        color[0] = entityRenderer.fogColorRed;
        color[1] = entityRenderer.fogColorGreen;
        color[2] = entityRenderer.fogColorBlue;
        return color;
    }

    // Fog color.
    private final float[] fogColor = { 0.0F, 0.0F, 0.0F, 1.0F };

    // Returns the fog mode.
    @Override
    public ChunkFogMode getFogMode() {
        if (!GlStateManager.fogState.fog.currentState) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GlStateManager.fogState.mode);
    }
}
