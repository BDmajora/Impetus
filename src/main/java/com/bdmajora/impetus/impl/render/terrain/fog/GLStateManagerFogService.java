package com.bdmajora.impetus.impl.render.terrain.fog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkFogMode;
import com.bdmajora.impetus.lwjgl.GL20;

public class GLStateManagerFogService implements FogService {
    // From GlStateManager's cached fog state
    @Override
    public float getFogEnd() {
        return GlStateManager.fogState.end;
    }

    // From GlStateManager's cached fog state
    @Override
    public float getFogStart() {
        return GlStateManager.fogState.start;
    }

    // From GlStateManager's cached fog state
    @Override
    public float getFogDensity() {
        return GlStateManager.fogState.density;
    }

    // The terrain shader's u_FogShape from the Extras fog-shape option; only terrain uses that shader, so any shape but VANILLA makes terrain and fixed-function-fogged entities disagree, which is inherent to the option
    @Override
    public int getFogShapeIndex() {
        return com.bdmajora.extras.Extras.options().render.fogShape.shaderIndex();
    }

    // Distance past which fog is fully opaque so chunks behind it can be skipped, NOT raw GL_FOG_END: getEffectiveRenderDistance clamps the occlusion BFS to this, and 1.12 has no fog alpha to guard with, so GL_EXP/EXP2 modes and disabled fog (where end is stale) must answer Float.MAX_VALUE, which the caller's min() lets the render distance win
    @Override
    public float getFogCutoff() {
        if (!GlStateManager.fogState.fog.currentState || GlStateManager.fogState.mode != GL20.GL_LINEAR) {
            return Float.MAX_VALUE;
        }
        return GlStateManager.fogState.end;
    }

    // Alpha is genuinely 1.0 on 1.12 (setupFog always uses an opaque colour), so Sodium's opacity guard never fires and the whole culling decision rests on getFogCutoff() being meaningful
    @Override
    public float[] getFogColor() {
        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        float[] color = this.fogColor;
        color[0] = entityRenderer.fogColorRed;
        color[1] = entityRenderer.fogColorGreen;
        color[2] = entityRenderer.fogColorBlue;
        return color;
    }

    // Asked once per terrain pass, so the array is reused; the uniform copies it out
    private final float[] fogColor = { 0.0F, 0.0F, 0.0F, 1.0F };

    // Maps vanilla's fog mode constants to the chunk shader's
    @Override
    public ChunkFogMode getFogMode() {
        if (!GlStateManager.fogState.fog.currentState) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GlStateManager.fogState.mode);
    }
}
