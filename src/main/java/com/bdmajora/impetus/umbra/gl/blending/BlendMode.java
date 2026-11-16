package com.bdmajora.impetus.umbra.gl.blending;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.Locale;

// Data of the blend mode.
public record BlendMode(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
    // Parses a blend mode from pack text or returns null.
    public static BlendMode parse(String value) {
        String[] parts = value.trim().split("\\s+");
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

    // Returns the GL enum of a blend factor name.
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
