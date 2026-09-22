package com.bdmajora.impetus.umbra.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.lwjgl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.util.Locale;
import java.util.Optional;

// The alphaTest.<program> directive; Iris compiles it to a discard, but on 1.12.2 the alpha test is real GL state vanilla sets per render type, so it is overridden while bound and restored after (Photon sets off everywhere)
public final class ProgramAlphaTest {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_ALPHA_TEST_FUNC = 0x0BC1;
    private static final int GL_ALPHA_TEST_REF = 0x0BC2;

    // The alpha state live when apply() ran, captured because vanilla uses different references per phase (0.1 most, 0.5 cutout) so no constant would be right; OptiFine lets vanilla re-set it, this port restores explicitly
    private boolean savedEnabled;
    private int savedFunction;
    private float savedReference;
    private boolean saved;

    private static final ProgramAlphaTest EMPTY = new ProgramAlphaTest(false, false, 0, 0.0f);

    private final boolean specified;
    // Set by `alphaTest.<program> = off`, which disables the test outright; distinct from ALWAYS, a function that passes everything
    private final boolean disabled;
    private final int function;
    private final float reference;

    private ProgramAlphaTest(boolean specified, boolean disabled, int function, float reference) {
        this.specified = specified;
        this.disabled = disabled;
        this.function = function;
        this.reference = reference;
    }

    // No directive; vanilla's alpha test stays
    public static ProgramAlphaTest empty() {
        return EMPTY;
    }

    // Reads alphaTest.<program>
    public static ProgramAlphaTest from(ShaderProperties properties, String programName) {
        Optional<String> value = properties.getAlphaTestOverride(programName);
        if (!value.isPresent()) {
            return EMPTY;
        }

        String raw = value.get().trim();
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("false")) {
            return new ProgramAlphaTest(true, true, 0, 0.0f);
        }

        String[] parts = raw.split("\\s+");
        Integer function = parseFunction(parts[0]);
        if (function == null) {
            LOGGER.warn("[Umbra] Unknown alpha test function '{}' in alphaTest.{}, ignoring it", parts[0], programName);
            return EMPTY;
        }
        // GL_ALWAYS/GL_NEVER take no reference value; everything else needs one.
        float reference = 0.0f;
        if (parts.length > 1) {
            try {
                reference = Float.parseFloat(parts[1]);
            } catch (NumberFormatException e) {
                LOGGER.warn("[Umbra] Malformed alpha test reference '{}' in alphaTest.{}, ignoring it",
                        parts[1], programName);
                return EMPTY;
            }
        } else if (function != GL11.GL_ALWAYS && function != GL11.GL_NEVER) {
            LOGGER.warn("[Umbra] alphaTest.{} declares '{}' with no reference value, ignoring it", programName, raw);
            return EMPTY;
        }

        return new ProgramAlphaTest(true, function == GL11.GL_ALWAYS, function, reference);
    }

    // GREATER, GEQUAL and the rest to GL constants; null when unknown
    private static Integer parseFunction(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "NEVER" -> GL11.GL_NEVER;
            case "LESS" -> GL11.GL_LESS;
            case "EQUAL" -> GL11.GL_EQUAL;
            case "LEQUAL" -> GL11.GL_LEQUAL;
            case "GREATER" -> GL11.GL_GREATER;
            case "NOTEQUAL" -> GL11.GL_NOTEQUAL;
            case "GEQUAL" -> GL11.GL_GEQUAL;
            case "ALWAYS" -> GL11.GL_ALWAYS;
            default -> null;
        };
    }

    // Whether anything needs applying
    public boolean hasDirectives() {
        return this.specified;
    }

    // This alpha test as a GLSL discard, or empty when it passes everything; mirrors Iris's NEGATED `if (!(a > ref)) discard;` form, which unlike the direct comparison discards NaN alpha, and NEVER discards unconditionally
    public String toGlslDiscard(String alphaAccessor, String indent) {
        if (!this.specified || this.disabled || this.function == GL11.GL_ALWAYS) {
            return "";
        }
        if (this.function == GL11.GL_NEVER) {
            return indent + "discard;\n";
        }
        String op = glslOperatorFor(this.function);
        if (op == null) {
            LOGGER.warn("[Umbra] Unsupported alphaTest function 0x{}; treating as always-pass",
                    Integer.toHexString(this.function));
            return "";
        }
        return glslDiscard(alphaAccessor, op, Float.toString(this.reference), indent);
    }

    // The same negated-comparison discard, for callers that supply their own threshold rather than the pack's
    public static String glslDiscard(String alphaAccessor, String operator, String threshold, String indent) {
        return indent + "if (!(" + alphaAccessor + " " + operator + " " + threshold + ")) {\n"
                + indent + "    discard;\n"
                + indent + "}\n";
    }

    // GL comparison enum to GLSL operator matching Iris's AlphaTestFunction; null for ALWAYS and NEVER, which compare nothing
    private static String glslOperatorFor(int function) {
        return switch (function) {
            case GL11.GL_LESS -> "<";
            case GL11.GL_EQUAL -> "==";
            case GL11.GL_LEQUAL -> "<=";
            case GL11.GL_GREATER -> ">";
            case GL11.GL_NOTEQUAL -> "!=";
            case GL11.GL_GEQUAL -> ">=";
            default -> null;
        };
    }

    // The pack-declared reference value, uploaded as the alphaTestRef uniform for packs that do their own discard
    public float getReference() {
        return this.disabled ? 0.0f : this.reference;
    }

    // Applies the override after capturing the previous state for restore(); goes through GlStateManager so vanilla's cache stays coherent
    public void apply() {
        if (!this.specified) {
            return;
        }
        // Read the live state once so restore() is exact; only runs for programs declaring an override, so the query cost is bounded
        this.savedEnabled = LWJGL.glGetBoolean(GL_ALPHA_TEST);
        this.savedFunction = LWJGL.glGetInteger(GL_ALPHA_TEST_FUNC);
        this.savedReference = LWJGL.glGetFloat(GL_ALPHA_TEST_REF);
        this.saved = true;
        if (this.disabled) {
            GlStateManager.disableAlpha();
            return;
        }
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(this.function, this.reference);
    }

    // Puts back whatever alpha state was live before apply(); must run before the composite chain or the GUI pass, or the pack's threshold leaks into unrelated geometry
    public void restore() {
        if (!this.specified || !this.saved) {
            return;
        }
        this.saved = false;
        if (this.savedEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        GlStateManager.alphaFunc(this.savedFunction, this.savedReference);
    }
}
