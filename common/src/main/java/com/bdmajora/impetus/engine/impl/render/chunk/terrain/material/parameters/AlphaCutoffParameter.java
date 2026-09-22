package com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true)
@Getter
public enum AlphaCutoffParameter {
    ZERO(0.0f),
    ONE_TENTH(0.1f),
    HALF(0.5f),
    ONE(1.0f);

    private static final AlphaCutoffParameter[] VALUES = values();

    private final float cutoff;

    // Nearest supported cutoff to a float threshold
    public static AlphaCutoffParameter valueOf(float val) {
        for (var param : VALUES) {
            if (Math.abs(param.cutoff() - val) < 0.001f) {
                return param;
            }
        }
        throw new IllegalArgumentException("No alpha cutoff option found for value: " + val);
    }
}
