package com.bdmajora.impetus.umbra.gl.sampler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Choice of comparison or raw sampling for each shadow sampler.
public final class ShadowSamplerKinds {
    // GL enum of the active uniform count.
    private static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    // Gl sampler 2 d.
    private static final int GL_SAMPLER_2D = 0x8B5E;
    // Gl sampler 2 d shadow.
    private static final int GL_SAMPLER_2D_SHADOW = 0x8B62;

    // Kinds with every sampler compared.
    public static final ShadowSamplerKinds ALL_COMPARE = new ShadowSamplerKinds(false, false);

    // Flags of the samplers read raw.
    private final boolean[] raw;

    // Creates a shadow sampler kinds from its raw shadowtex0 and raw shadowtex1.
    private ShadowSamplerKinds(boolean rawShadowtex0, boolean rawShadowtex1) {
        this.raw = new boolean[]{rawShadowtex0, rawShadowtex1};
    }

    // Returns whether a shadow sampler is read raw.
    public boolean isRaw(int index) {
        return this.raw[index];
    }

    // Returns the sampler kinds of a linked program.
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

    // Returns whether any alias of a sampler is declared plain.
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
