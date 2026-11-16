package com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.parameters;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

// Alpha cutoffs that a material can use.
@RequiredArgsConstructor
@Accessors(fluent = true)
@Getter
public enum AlphaCutoffParameter {
    // No cutoff.
    ZERO(0.0f),
    // Cutoff at one tenth.
    ONE_TENTH(0.1f),
    // Cutoff at one half.
    HALF(0.5f),
    // Cutoff at one.
    ONE(1.0f);

    // Every cutoff.
    private static final AlphaCutoffParameter[] VALUES = values();

    // Cutoff value.
    private final float cutoff;

    // Returns the cutoff that matches a value and rejects a value without a cutoff.
    public static AlphaCutoffParameter valueOf(float val) {
        for (var param : VALUES) {
            if (Math.abs(param.cutoff() - val) < 0.001f) {
                return param;
            }
        }
        throw new IllegalArgumentException("No alpha cutoff option found for value: " + val);
    }
}
