package com.bdmajora.impetus.umbra.gl.blending;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL14;

import com.github.bsideup.jabel.Desugar;

import java.util.Locale;

// The blend factors a pack declares for one program via `blend.<program> = SRC DST` or the four-factor form; four because packs blend RGB normally while writing alpha ONE/ZERO so it carries data
@Desugar
public record BlendMode(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    // Accepts OptiFine's two- and four-factor forms; throws on anything else so the caller logs and drops just this directive
    public static BlendMode parse(String value) {
        String[] parts = value.trim().split("\\s+");
        // Two factors mean the same pair applies to both colour and alpha, which is GL's own glBlendFunc default
        if (parts.length == 2) {
            int src = function(parts[0]);
            int dst = function(parts[1]);
            return new BlendMode(src, dst, src, dst);
        }
        if (parts.length == 4) {
            return new BlendMode(
                    function(parts[0]), function(parts[1]),
                    function(parts[2]), function(parts[3]));
        }
        throw new IllegalArgumentException("expected two or four blend factors, got " + parts.length);
    }

    // Factor name to GL enum, upper-cased through ROOT so a Turkish locale cannot break SRC_ALPHA matching
    private static int function(String name) {
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "ZERO" -> GL11.GL_ZERO;
            case "ONE" -> GL11.GL_ONE;
            case "SRC_COLOR" -> GL11.GL_SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR" -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case "DST_COLOR" -> GL11.GL_DST_COLOR;
            case "ONE_MINUS_DST_COLOR" -> GL11.GL_ONE_MINUS_DST_COLOR;
            case "SRC_ALPHA" -> GL11.GL_SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "DST_ALPHA" -> GL11.GL_DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA" -> GL11.GL_ONE_MINUS_DST_ALPHA;
            case "SRC_ALPHA_SATURATE" -> GL11.GL_SRC_ALPHA_SATURATE;
            case "CONSTANT_COLOR" -> GL14.GL_CONSTANT_COLOR;
            case "ONE_MINUS_CONSTANT_COLOR" -> GL14.GL_ONE_MINUS_CONSTANT_COLOR;
            case "CONSTANT_ALPHA" -> GL14.GL_CONSTANT_ALPHA;
            case "ONE_MINUS_CONSTANT_ALPHA" -> GL14.GL_ONE_MINUS_CONSTANT_ALPHA;
            default -> throw new IllegalArgumentException("unknown blend factor " + name);
        };
    }
}
