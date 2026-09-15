package com.bdmajora.impetus.umbra.gl.sampler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Which of a program's two shadow depth maps it declared as a plain sampler2D and so must read RAW depth: with a depth-comparison sampler on that unit this GL context returned a comparison result ("lit") for texelFetch, which is exactly what Complementary's composite1 does through `texelFetch(shadowtex0, ...)` for both its light-shaft march and its scene-aware probe, so the shafts had no shadow structure and the scene-aware mode never engaged (Iris binds the comparison sampler regardless and gets away with it on its core-profile context; measured here, this context does not). Read from the linked program's active uniform types, the only record of what the pack actually declared
public final class ShadowSamplerKinds {
    private static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    private static final int GL_SAMPLER_2D = 0x8B5E;
    private static final int GL_SAMPLER_2D_SHADOW = 0x8B62;

    // Both units keep the comparison sampler: the OptiFine convention under shadowHardwareFiltering, and what a program that never samples the shadow map gets
    public static final ShadowSamplerKinds ALL_COMPARE = new ShadowSamplerKinds(false, false);

    // Per shadow depth index (0 = shadowtex0, 1 = shadowtex1), true when the program wants the stored depth rather than a comparison
    private final boolean[] raw;

    private ShadowSamplerKinds(boolean rawShadowtex0, boolean rawShadowtex1) {
        this.raw = new boolean[]{rawShadowtex0, rawShadowtex1};
    }

    // Whether shadow depth index 0/1 must be sampled without depth comparison
    public boolean isRaw(int index) {
        return this.raw[index];
    }

    // Walks the program's active uniforms and classifies every shadow depth alias by its declared sampler type; a unit declared both ways (invalid GLSL, one program cannot bind two sampler types to one unit) keeps the comparison sampler, and `shadow` follows the same aliasing rule as sampler-unit assignment: it means shadowtex1 only when `watershadow` is also declared
    public static ShadowSamplerKinds detect(int programId) {
        int activeUniforms = LWJGL.glGetProgrami(programId, GL_ACTIVE_UNIFORMS);
        if (activeUniforms <= 0) {
            return ALL_COMPARE;
        }
        IntBuffer sizeType = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asIntBuffer();
        Set<String> plain = new HashSet<>();
        Set<String> shadow = new HashSet<>();
        for (int index = 0; index < activeUniforms; index++) {
            sizeType.clear();
            String name = LWJGL.glGetActiveUniform(programId, index, 256, sizeType);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (name.endsWith("[0]")) {
                name = name.substring(0, name.length() - 3);
            }
            int type = sizeType.get(1);
            if (type == GL_SAMPLER_2D) {
                plain.add(name);
            } else if (type == GL_SAMPLER_2D_SHADOW) {
                shadow.add(name);
            }
        }
        boolean waterShadow = plain.contains("watershadow") || shadow.contains("watershadow");
        String[] aliases0 = waterShadow
                ? new String[]{"shadowtex0", "shadowtex0DH", "watershadow"}
                : new String[]{"shadowtex0", "shadowtex0DH", "shadow"};
        String[] aliases1 = waterShadow
                ? new String[]{"shadowtex1", "shadowtex1DH", "shadow"}
                : new String[]{"shadowtex1", "shadowtex1DH"};
        boolean raw0 = wantsRaw(aliases0, plain, shadow);
        boolean raw1 = wantsRaw(aliases1, plain, shadow);
        if (!raw0 && !raw1) {
            return ALL_COMPARE;
        }
        return new ShadowSamplerKinds(raw0, raw1);
    }

    // Raw only when at least one alias of the unit is a plain sampler2D and none is a sampler2DShadow
    private static boolean wantsRaw(String[] aliases, Set<String> plain, Set<String> shadow) {
        boolean anyPlain = false;
        for (String alias : aliases) {
            if (shadow.contains(alias)) {
                return false;
            }
            anyPlain |= plain.contains(alias);
        }
        return anyPlain;
    }
}
