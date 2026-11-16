package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.api.util.ColorMixer;

// Ways ambient occlusion is folded into the packed color of a chunk vertex.
public enum ChunkColorWriter {
    // Mode that stores occlusion in alpha and keeps the tint in the color channels.
    SEPARATE_AO {
        // Returns the color with occlusion applied.
        @Override
        public int writeColor(int colorWithAlpha, float aoValue) {
            return ColorABGR.withAlpha(colorWithAlpha, aoValue);
        }
    },
    // Mode that multiplies occlusion into the color channels and keeps alpha.
    IMPETUS {
        // Returns the color with occlusion applied.
        @Override
        public int writeColor(int colorWithAlpha, float aoValue) {
            return ColorMixer.mulSingleWithoutAlpha(colorWithAlpha, (int)(aoValue * 255));
        }
    };

    // Returns a packed ABGR color with an occlusion value between 0 and 1 applied.
    public abstract int writeColor(int colorWithAlpha, float aoValue);

    // Returns the writer of the current occlusion mode.
    public static ChunkColorWriter active() {
        return SeparateAoState.enabled ? SEPARATE_AO : IMPETUS;
    }

    // Flag that keeps ambient occlusion out of the vertex color.
    public static final class SeparateAoState {
        // Separate occlusion state shared across threads.
        private static volatile boolean enabled;

        // Prevents instantiation
        private SeparateAoState() {
        }

        // Stores the separate occlusion state.
        public static void set(boolean value) {
            enabled = value;
        }

        // Returns the separate occlusion state.
        public static boolean isEnabled() {
            return enabled;
        }
    }
}
