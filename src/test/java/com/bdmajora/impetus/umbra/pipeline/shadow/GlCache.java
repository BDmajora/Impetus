package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

// Reads and seeds the GlStateManager caches that the shadow stages change
public final class GlCache {
    // Prevents instantiation
    private GlCache() {
    }

    // Returns one cached state object of GlStateManager
    private static Object state(String name) {
        return Statics.get(GlStateManager.class, name);
    }

    // Returns the current value of a cached switch
    private static boolean flag(Object booleanState) {
        return Mixins.<Boolean>get(booleanState, "currentState");
    }

    // Returns whether face culling is cached as enabled
    public static boolean cull() {
        return flag(Mixins.get(state("cullState"), "cullFace"));
    }

    // Returns whether blending is cached as enabled
    public static boolean blend() {
        return flag(Mixins.get(state("blendState"), "blend"));
    }

    // Returns whether the depth test is cached as enabled
    public static boolean depthTest() {
        return flag(Mixins.get(state("depthState"), "depthTest"));
    }

    // Returns whether depth writes are cached as enabled
    public static boolean depthMask() {
        return Mixins.<Boolean>get(state("depthState"), "maskEnabled");
    }

    // Returns the cached depth function
    public static int depthFunc() {
        return Mixins.<Integer>get(state("depthState"), "depthFunc");
    }

    // Returns the cached clear depth
    public static double clearDepth() {
        return Mixins.<Double>get(state("clearState"), "depth");
    }

    // Returns the cached clear colour as four components
    public static float[] clearColor() {
        Object color = Mixins.get(state("clearState"), "color");
        return new float[]{Mixins.<Float>get(color, "red"), Mixins.<Float>get(color, "green"),
                Mixins.<Float>get(color, "blue"), Mixins.<Float>get(color, "alpha")};
    }

    // Writes every cached switch and value that the shadow stages touch
    public static void seed(boolean cull, boolean blend, boolean depthTest, boolean depthMask, int depthFunc,
                            double clearDepth, float red, float green, float blue, float alpha) {
        Mixins.set(Mixins.get(state("cullState"), "cullFace"), "currentState", cull);
        Mixins.set(Mixins.get(state("blendState"), "blend"), "currentState", blend);
        Object depth = state("depthState");
        Mixins.set(Mixins.get(depth, "depthTest"), "currentState", depthTest);
        Mixins.set(depth, "maskEnabled", depthMask);
        Mixins.set(depth, "depthFunc", depthFunc);
        Object clear = state("clearState");
        Mixins.set(clear, "depth", clearDepth);
        Object color = Mixins.get(clear, "color");
        Mixins.set(color, "red", red);
        Mixins.set(color, "green", green);
        Mixins.set(color, "blue", blue);
        Mixins.set(color, "alpha", alpha);
    }

    // Puts every touched cache back to the vanilla start values
    public static void reset() {
        seed(false, false, false, true, GL11.GL_LESS, 1.0, 0.0f, 0.0f, 0.0f, 0.0f);
    }
}
