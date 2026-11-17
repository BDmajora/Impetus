package com.bdmajora.impetus.engine.impl.render.chunk.terrain.material;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters.MaterialParameters;

import java.util.Objects;

// Full render configuration of a geometry element encoded alongside its vertex data.
public final class Material {
    public final TerrainRenderPass pass;
    public final int packed;

    public final AlphaCutoffParameter alphaCutoff;
    public final boolean mipped;

    // Creates a material from its pass and alpha cutoff and mip switch.
    public Material(TerrainRenderPass pass, AlphaCutoffParameter alphaCutoff, boolean mipped) {
        if (alphaCutoff != AlphaCutoffParameter.ZERO && !pass.supportsFragmentDiscard()) {
            throw new IllegalArgumentException("Pass does not support fragment discard");
        }

        this.pass = pass;
        this.packed = MaterialParameters.pack(alphaCutoff, mipped);

        this.alphaCutoff = alphaCutoff;
        this.mipped = mipped;
    }

    // Returns the packed bits encoded in vertex data.
    public int bits() {
        return this.packed;
    }

    // By pass and parameters
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Material material = (Material) o;
        return packed == material.packed && pass.equals(material.pass);
    }

    // By pass and parameters
    @Override
    public int hashCode() {
        return Objects.hash(pass, packed);
    }

    // For debugging
    @Override
    public String toString() {
        return "Material{" +
                "pass=" + pass +
                ", alphaCutoff=" + alphaCutoff +
                ", mipped=" + mipped +
                '}';
    }
}
