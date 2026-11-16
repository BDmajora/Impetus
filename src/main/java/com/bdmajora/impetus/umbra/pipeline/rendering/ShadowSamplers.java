package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.util.Arrays;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Compare and raw samplers of the shadow maps in every filter flavour.
public final class ShadowSamplers {
    // Number of filter flavours.
    private static final int FLAVOURS = 4;
    // Flavour bit of a mipmapped sampler.
    private static final int MIPMAPPED = 2;
    // Flavour bit of a nearest sampler.
    private static final int NEAREST = 1;

    // Hardware filtering of each shadow map.
    private final boolean[] hardwareFiltering = new boolean[2];
    // Mipmapping of each shadow map.
    private final boolean[] mipmap = new boolean[2];
    // Nearest filtering of each shadow map.
    private final boolean[] nearest = new boolean[2];
    // Compare samplers by flavour.
    private final int[] compare = new int[FLAVOURS];
    // Raw depth samplers by flavour.
    private final int[] raw = new int[FLAVOURS];
    // Separate hardware sampler state of the pack.
    private final boolean separateHardwareSamplers;
    // Deleted state of the samplers.
    private boolean destroyed;

    // Creates every sampler and deletes them all when one fails.
    public ShadowSamplers(boolean separateHardwareSamplers) {
        this.separateHardwareSamplers = separateHardwareSamplers;
        try {
            for (int flavour = 0; flavour < FLAVOURS; flavour++) {
                this.compare[flavour] = create(flavour, true);
                this.raw[flavour] = create(flavour, false);
            }
        } catch (RuntimeException e) {
            destroy();
            throw e;
        }
    }

    // Creates one sampler of a flavour and deletes it when its setup fails.
    private static int create(int flavour, boolean compare) {
        boolean linear = (flavour & NEAREST) == 0;
        boolean mipmapped = (flavour & MIPMAPPED) != 0;
        int sampler = LWJGL.glGenSamplers();
        try {
            LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, mipmapped
                    ? (linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST)
                    : (linear ? GL11.GL_LINEAR : GL11.GL_NEAREST));
            LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            LWJGL.glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_MODE,
                    compare ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE);
        } catch (RuntimeException e) {
            LWJGL.glDeleteSamplers(sampler);
            throw e;
        }
        return sampler;
    }

    // Reads the filter directives of the pack.
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

    // Applies a directive for both maps and then the directive of each map.
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

    // Returns the hardware filtering of each map.
    public boolean[] hardwareFiltering() {
        return this.hardwareFiltering;
    }

    // Returns the mipmapping of each map.
    public boolean[] mipmap() {
        return this.mipmap;
    }

    // Returns the nearest filtering of each map.
    public boolean[] nearest() {
        return this.nearest;
    }

    // Returns whether the pack binds hardware samplers to their own units.
    public boolean separateHardwareSamplers() {
        return this.separateHardwareSamplers;
    }

    // Returns the flavour of a map.
    private int flavour(int index) {
        return (this.mipmap[index] ? MIPMAPPED : 0) | (this.nearest[index] ? NEAREST : 0);
    }

    // Returns the compare sampler of a map.
    public int compareFor(int index) {
        return this.compare[flavour(index)];
    }

    // Returns the raw sampler of a map.
    public int rawFor(int index) {
        return this.raw[flavour(index)];
    }

    // Binds the sampler kinds of both maps to their units.
    public void apply(ShadowSamplerKinds kinds, boolean gbufferStage) {
        if (this.destroyed || this.separateHardwareSamplers || kinds == null) {
            return;
        }
        LWJGL.glBindSampler(gbufferStage ? SamplerLayout.GBUFFER_SHADOW_TEX_0_UNIT : SamplerLayout.SHADOW_TEX_0_UNIT,
                kinds.isRaw(0) ? rawFor(0) : compareFor(0));
        LWJGL.glBindSampler(gbufferStage ? SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT : SamplerLayout.SHADOW_TEX_1_UNIT,
                kinds.isRaw(1) ? rawFor(1) : compareFor(1));
    }

    // Deletes every created sampler once and continues after a failure.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        RuntimeException failure = null;
        for (int flavour = 0; flavour < FLAVOURS; flavour++) {
            failure = delete(this.compare, flavour, failure);
            failure = delete(this.raw, flavour, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    // Deletes one sampler that was created and returns the first failure.
    private static RuntimeException delete(int[] samplers, int index, RuntimeException failure) {
        int sampler = samplers[index];
        samplers[index] = 0;
        if (sampler == 0) {
            return failure;
        }
        try {
            LWJGL.glDeleteSamplers(sampler);
        } catch (RuntimeException e) {
            if (failure == null) {
                return e;
            }
            if (e != failure) {
                failure.addSuppressed(e);
            }
        }
        return failure;
    }
}
