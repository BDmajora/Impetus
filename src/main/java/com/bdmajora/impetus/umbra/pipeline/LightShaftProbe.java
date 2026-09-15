package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL30;
import com.bdmajora.impetus.umbra.gl.framebuffer.UmbraFramebuffer;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import com.bdmajora.impetus.umbra.uniforms.custom.ActiveCustomUniforms;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// F3 diagnostic for Complementary's scene-aware light shafts, shown while the loaded pack has LIGHTSHAFT_BEHAVIOUR set to scene-aware or with -Dimpetus.umbra.lightShaftProbe=true (volumetricLight.glsl step 4): replays the pack's SALS probe on the CPU from the same textures it samples (25 shadowtex0/shadowcolor1 texels around the player, 5 depthtex0 sky taps across the top of the screen) and reads back the factor texel the pack keeps in colortex5's top-right alpha, so a build shows whether the factor is stuck because the feedback texel never persists or because the sampled cover sits below the pack's 6-block threshold; costs a few one-texel readbacks every few frames and only while F3 is open
public final class LightShaftProbe {
    public static final boolean ENABLED = Boolean.getBoolean("impetus.umbra.lightShaftProbe");
    public static final LightShaftProbe INSTANCE = new LightShaftProbe();
    // Set by the pipeline from the pack's option values, so the lines appear for the one pack this diagnoses without a JVM flag
    private static volatile boolean packGate;

    // The pack's probe constants: a 5x5 grid over distorted shadow-map coords 0.3..0.7, casters count when their depth is under 0.55, shadowcolor1.a encodes 0.25 + height * 0.05, the mean height must exceed 6 blocks, and four of five sky taps at 90% screen height veto it
    private static final int GRID = 5;
    private static final float DEPTH_LIMIT = 0.55f;
    private static final float HEIGHT_BIAS = 0.25f;
    private static final float HEIGHT_SCALE = 0.05f;
    private static final float HEIGHT_THRESHOLD = 6.0f;
    private static final int SKY_TAPS = 5;
    private static final int SKY_VETO = 4;
    private static final int FRAME_INTERVAL = 6;

    private final ByteBuffer pixel = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    private UmbraFramebuffer shadowReadFramebuffer;
    private UmbraFramebuffer screenReadFramebuffer;
    private UmbraFramebuffer colorReadFramebuffer;
    private int attachedShadowDepth = -1;
    private int attachedShadowColor = -1;
    private int attachedScreenDepth = -1;
    private int attachedColorTexture = -1;

    // Last replayed probe: mean caster height, how many of the 25 taps counted, their extremes, how many taps were casters at all, sky taps, and the factor texel
    private volatile float meanHeight = Float.NaN;
    private volatile int counted;
    private volatile int casters;
    private volatile float minHeight = Float.NaN;
    private volatile float maxHeight = Float.NaN;
    private volatile int skyTaps;
    private volatile float factor = Float.NaN;
    private volatile float[] factorTexel = new float[4];
    private volatile int sampledFrame = -1;

    private LightShaftProbe() {
    }

    // Whether the diagnostic can show at all: the property, or a pack running the scene-aware mode this replays
    public static boolean available() {
        return ENABLED || packGate;
    }

    // Only while the overlay that shows the numbers is open, so it costs nothing in play
    public static boolean active() {
        return available() && Minecraft.getMinecraft().gameSettings.showDebugInfo;
    }

    // The pipeline reports whether the loaded pack's LIGHTSHAFT_BEHAVIOUR option is 1 (scene-aware)
    public static void setPackGate(boolean sceneAwarePack) {
        packGate = sceneAwarePack;
    }

    // Replays the shadow-map half of the probe right after the shadow pass, from shadowtex0 (depth) and shadowcolor1 (height in alpha); coordinates are the pack's: coord = 0.3 + 0.4 * (i, h) / 5 for i in 0.25.., h in 0.45.., texel = int(coord * resolution)
    public void sampleShadow(int shadowDepthTexture, int shadowColor1Texture, int resolution) {
        if (!due() || resolution <= 0) {
            return;
        }
        if (this.shadowReadFramebuffer == null || this.attachedShadowDepth != shadowDepthTexture
                || this.attachedShadowColor != shadowColor1Texture) {
            if (this.shadowReadFramebuffer != null) {
                this.shadowReadFramebuffer.destroy();
            }
            this.shadowReadFramebuffer = new UmbraFramebuffer();
            this.shadowReadFramebuffer.bind();
            this.shadowReadFramebuffer.addColorAttachment(0, shadowColor1Texture);
            this.shadowReadFramebuffer.addDepthAttachment(shadowDepthTexture);
            this.shadowReadFramebuffer.noDrawBuffers();
            this.attachedShadowDepth = shadowDepthTexture;
            this.attachedShadowColor = shadowColor1Texture;
        }
        this.shadowReadFramebuffer.readBuffer(0);
        this.shadowReadFramebuffer.bindAsReadBuffer();

        float sum = 0.0f;
        int count = 0;
        int casterCount = 0;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float i = 0.25f; i < GRID; i++) {
            for (float h = 0.45f; h < GRID; h++) {
                float coordX = 0.3f + 0.4f * (i / GRID);
                float coordY = 0.3f + 0.4f * (h / GRID);
                int x = Math.min(resolution - 1, (int) (coordX * resolution));
                int y = Math.min(resolution - 1, (int) (coordY * resolution));
                float depth = readDepth(x, y);
                if (depth >= DEPTH_LIMIT) {
                    continue;
                }
                casterCount++;
                float alpha = readRgba(x, y)[3];
                if (alpha <= 0.0f) {
                    continue;
                }
                float height = Math.max(alpha - HEIGHT_BIAS, 0.0f) / HEIGHT_SCALE;
                sum += height;
                count++;
                min = Math.min(min, height);
                max = Math.max(max, height);
            }
        }
        this.meanHeight = count == 0 ? Float.NaN : sum / count;
        this.counted = count;
        this.casters = casterCount;
        this.minHeight = count == 0 ? Float.NaN : min;
        this.maxHeight = count == 0 ? Float.NaN : max;
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // Replays the sky veto from depthtex0 and reads the factor texel from whichever colortex5 texture the chain leaves as current, after the composite chain has run
    public void sampleScreen(int depthTexture, int colorTexture, int width, int height) {
        if (!due() || width <= 0 || height <= 0) {
            return;
        }
        if (this.screenReadFramebuffer == null || this.attachedScreenDepth != depthTexture) {
            if (this.screenReadFramebuffer != null) {
                this.screenReadFramebuffer.destroy();
            }
            this.screenReadFramebuffer = new UmbraFramebuffer();
            this.screenReadFramebuffer.bind();
            this.screenReadFramebuffer.addDepthAttachment(depthTexture);
            this.screenReadFramebuffer.noDrawBuffers();
            this.attachedScreenDepth = depthTexture;
        }
        this.screenReadFramebuffer.bindAsReadBuffer();
        int sky = 0;
        for (float i = 0.1f; i < 1.0f; i += 0.2f) {
            int x = Math.min(width - 1, (int) (width * i));
            int y = Math.min(height - 1, (int) (height * 0.9f));
            if (readDepth(x, y) == 1.0f) {
                sky++;
            }
        }
        this.skyTaps = sky;

        if (this.colorReadFramebuffer == null || this.attachedColorTexture != colorTexture) {
            if (this.colorReadFramebuffer != null) {
                this.colorReadFramebuffer.destroy();
            }
            this.colorReadFramebuffer = new UmbraFramebuffer();
            this.colorReadFramebuffer.bind();
            this.colorReadFramebuffer.addColorAttachment(0, colorTexture);
            this.colorReadFramebuffer.noDrawBuffers();
            this.attachedColorTexture = colorTexture;
        }
        this.colorReadFramebuffer.readBuffer(0);
        this.colorReadFramebuffer.bindAsReadBuffer();
        float[] texel = readRgba(width - 1, height - 1);
        this.factorTexel = texel;
        this.factor = texel[3];
        this.sampledFrame = SystemTimeUniforms.COUNTER.getFrameCounter();
        LWJGL.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    // The overlay lines, empty until the first sample
    public List<String> lines() {
        if (this.sampledFrame < 0) {
            return Collections.singletonList("LS probe: waiting for a frame");
        }
        List<String> out = new ArrayList<>(3);
        float[] t = this.factorTexel;
        out.add(String.format(Locale.ROOT, "LS probe: vlFactor %.3f (colortex5 top-right rgba %.2f %.2f %.2f %.3f) @frame %d",
                this.factor, t[0], t[1], t[2], t[3], this.sampledFrame));
        String mean = Float.isNaN(this.meanHeight) ? "n/a" : String.format(Locale.ROOT, "%.2f", this.meanHeight);
        String range = Float.isNaN(this.minHeight) ? "" : String.format(Locale.ROOT, " (min %.1f max %.1f)", this.minHeight, this.maxHeight);
        boolean vetoed = this.skyTaps >= SKY_VETO;
        boolean passes = !vetoed && !Float.isNaN(this.meanHeight) && this.meanHeight > HEIGHT_THRESHOLD;
        out.add(String.format(Locale.ROOT, "  SALS mean height %s / need > %.0f%s, %d/%d taps counted, %d casters; sky %d/%d%s -> %s",
                mean, HEIGHT_THRESHOLD, range, this.counted, GRID * GRID, this.casters, this.skyTaps, SKY_TAPS,
                vetoed ? " (veto)" : "", passes ? "RISING" : "falling"));
        out.add("  tick every " + tickDivisor() + " frames (frameTimeSmooth " + frameTimeSmooth() + ")");
        return out;
    }

    // The pack's `frameCounter % int(0.06666 / frameTimeSmooth + 0.5)`, from the value the custom uniform actually uploads
    private static String tickDivisor() {
        String smooth = frameTimeSmooth();
        try {
            float value = Float.parseFloat(smooth);
            return Integer.toString((int) (0.06666f / value + 0.5f));
        } catch (NumberFormatException e) {
            return "?";
        }
    }

    // The custom-uniform value, which replaces the built-in of the same name at program build
    private static String frameTimeSmooth() {
        Map<String, String> snapshot = ActiveCustomUniforms.snapshot();
        String value = snapshot.get("uniform.frameTimeSmooth");
        return value == null ? "n/a" : value;
    }

    // Sample every FRAME_INTERVAL frames, both halves on the same frame
    private boolean due() {
        return active() && SystemTimeUniforms.COUNTER.getFrameCounter() % FRAME_INTERVAL == 0;
    }

    // One depth texel from the bound read framebuffer
    private float readDepth(int x, int y) {
        this.pixel.clear();
        LWJGL.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, this.pixel);
        return this.pixel.asFloatBuffer().get(0);
    }

    // One colour texel from the bound read framebuffer's read buffer
    private float[] readRgba(int x, int y) {
        this.pixel.clear();
        LWJGL.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, this.pixel);
        float[] rgba = new float[4];
        this.pixel.asFloatBuffer().get(rgba);
        return rgba;
    }

    // Frees the readback framebuffers; textures belong to their owners
    public void destroy() {
        if (this.shadowReadFramebuffer != null) {
            this.shadowReadFramebuffer.destroy();
            this.shadowReadFramebuffer = null;
        }
        if (this.screenReadFramebuffer != null) {
            this.screenReadFramebuffer.destroy();
            this.screenReadFramebuffer = null;
        }
        if (this.colorReadFramebuffer != null) {
            this.colorReadFramebuffer.destroy();
            this.colorReadFramebuffer = null;
        }
        this.attachedShadowDepth = -1;
        this.attachedShadowColor = -1;
        this.attachedScreenDepth = -1;
        this.attachedColorTexture = -1;
        this.sampledFrame = -1;
        packGate = false;
    }
}
