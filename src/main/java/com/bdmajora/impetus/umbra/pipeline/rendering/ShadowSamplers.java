package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.util.Arrays;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The sampler objects on the two shadow depth units: four filtering flavours, each with depth comparison (sampler2DShadow) and without (a plain sampler2D reading raw depth); a bound sampler object replaces the texture's own parameters wholesale
public final class ShadowSamplers {
    private static final int FLAVOURS = 4;
    private static final int MIPMAPPED = 2;
    private static final int NEAREST = 1;

    // Per shadow texture (0 = shadowtex0, 1 = shadowtex1), from shadowHardwareFiltering and the shadowtex Mipmap/Nearest directives
    private final boolean[] hardwareFiltering = new boolean[2];
    private final boolean[] mipmap = new boolean[2];
    private final boolean[] nearest = new boolean[2];
    // Indexed by flavour: mipmapped * 2 + nearest
    private final int[] compare = new int[FLAVOURS];
    private final int[] raw = new int[FLAVOURS];
    // Whether the pack DECLARED SEPARATE_HARDWARE_SAMPLERS (Umbra reads hasFeature), not whether the port could provide it; keying off isUsable() suppressed GL_TEXTURE_COMPARE_MODE and left sampler2DShadow reading "fully lit" on NVIDIA
    private final boolean separateHardwareSamplers;
    private boolean destroyed;

    public ShadowSamplers(boolean separateHardwareSamplers) {
        this.separateHardwareSamplers = separateHardwareSamplers;
        try {
            for (int flavour = 0; flavour < FLAVOURS; flavour++) {
                this.compare[flavour] = create(flavour, true);
                this.raw[flavour] = create(flavour, false);
            }
        } catch (RuntimeException e) {
            // The caller never gets this object to destroy, so free what was made before failing
            destroy();
            throw e;
        }
    }

    private static int create(int flavour, boolean compare) {
        boolean linear = (flavour & NEAREST) == 0;
        boolean mipmapped = (flavour & MIPMAPPED) != 0;
        int sampler = LWJGL.glGenSamplers();
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, mipmapped
                ? (linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST)
                : (linear ? GL11.GL_LINEAR : GL11.GL_NEAREST));
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        LWJGL.glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_MODE,
                compare ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE);
        return sampler;
    }

    // Reads shadowHardwareFiltering and the shadowtex filtering constants: a shared constant sets both, a per-index one (<prefix><i><suffix>) overrides
    public void parse(ConstDirectives consts) {
        Arrays.fill(this.hardwareFiltering, false);
        Arrays.fill(this.mipmap, false);
        Arrays.fill(this.nearest, false);
        applyBothThenIndexed(consts, "shadowHardwareFiltering", "shadowHardwareFiltering", "", this.hardwareFiltering);
        applyBothThenIndexed(consts, "generateShadowMipmap", "shadowtex", "Mipmap", this.mipmap);
        consts.getOptionalBool("shadowtexMipmap").ifPresent(value -> this.mipmap[0] = value);
        applyBothThenIndexed(consts, null, "shadowtex", "Nearest", this.nearest);
        consts.getOptionalBool("shadowtexNearest").ifPresent(value -> this.nearest[0] = value);
        for (int i = 0; i < this.nearest.length; i++) {
            final int index = i;
            consts.getOptionalBool("shadow" + i + "MinMagNearest").ifPresent(value -> this.nearest[index] = value);
        }
    }

    private static void applyBothThenIndexed(ConstDirectives consts, String bothName, String prefix, String suffix,
                                             boolean[] values) {
        if (bothName != null) {
            consts.getOptionalBool(bothName).ifPresent(value -> Arrays.fill(values, value));
        }
        for (int i = 0; i < values.length; i++) {
            final int index = i;
            consts.getOptionalBool(prefix + i + suffix).ifPresent(value -> values[index] = value);
        }
    }

    // The live per-texture flags; UmbraShadowRenderer copies them at construction
    public boolean[] hardwareFiltering() {
        return this.hardwareFiltering;
    }

    // Per texture, whether the map is sampled mipmapped
    public boolean[] mipmap() {
        return this.mipmap;
    }

    // Per texture, whether the map is sampled nearest
    public boolean[] nearest() {
        return this.nearest;
    }

    // Whether the pack moved comparison onto the *HW aliases
    public boolean separateHardwareSamplers() {
        return this.separateHardwareSamplers;
    }

    private int flavour(int index) {
        return (this.mipmap[index] ? MIPMAPPED : 0) | (this.nearest[index] ? NEAREST : 0);
    }

    // The comparison sampler for one shadow texture; shadowHardwareFiltering only picks the filtering, comparison is unconditional
    public int compareFor(int index) {
        return this.compare[flavour(index)];
    }

    // The raw-depth sampler with the same filtering, for a unit read through a plain sampler2D
    public int rawFor(int index) {
        return this.raw[flavour(index)];
    }

    // Re-points both shadow depth units at the flavour matching how the program about to draw DECLARED each of them; called after every program bind, since the frame's binds restore the comparison default in between. Gbuffer-stage programs read units 4/5, everything else 19/20, and under SEPARATE_HARDWARE_SAMPLERS the plain units already carry no sampler (the pack asked for raw depth there)
    public void apply(ShadowSamplerKinds kinds, boolean gbufferStage) {
        if (this.destroyed || this.separateHardwareSamplers || kinds == null) {
            return;
        }
        LWJGL.glBindSampler(gbufferStage ? SamplerLayout.GBUFFER_SHADOW_TEX_0_UNIT : SamplerLayout.SHADOW_TEX_0_UNIT,
                kinds.isRaw(0) ? rawFor(0) : compareFor(0));
        LWJGL.glBindSampler(gbufferStage ? SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT : SamplerLayout.SHADOW_TEX_1_UNIT,
                kinds.isRaw(1) ? rawFor(1) : compareFor(1));
    }

    // Deletes all eight; safe twice, and a sampler that was never made is 0, which GL ignores
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        for (int flavour = 0; flavour < FLAVOURS; flavour++) {
            LWJGL.glDeleteSamplers(this.compare[flavour]);
            LWJGL.glDeleteSamplers(this.raw[flavour]);
        }
    }
}
