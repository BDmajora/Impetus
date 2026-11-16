package com.bdmajora.impetus.umbra.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import java.util.Locale;
import java.util.Optional;

// Alpha test override of a program from the pack properties.
public final class ProgramAlphaTest {
    // Logger of malformed directives.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // GL enum of the alpha test capability.
    private static final int GL_ALPHA_TEST = 0x0BC0;
    // GL enum of the alpha test function.
    private static final int GL_ALPHA_TEST_FUNC = 0x0BC1;
    // GL enum of the alpha test reference.
    private static final int GL_ALPHA_TEST_REF = 0x0BC2;

    // Alpha test state saved by apply.
    private boolean savedEnabled;
    // Alpha function saved by apply.
    private int savedFunction;
    // Alpha reference saved by apply.
    private float savedReference;
    // Saved state of the GL alpha test.
    private boolean saved;

    // Override that changes nothing.
    private static final ProgramAlphaTest EMPTY = new ProgramAlphaTest(false, false, 0, 0.0f);

    // Directive state of the program.
    private final boolean specified;
    // Disabled state of the alpha test.
    private final boolean disabled;
    // GL alpha function.
    private final int function;
    // Alpha reference value.
    private final float reference;

    // Creates an override.
    private ProgramAlphaTest(boolean specified, boolean disabled, int function, float reference) {
        this.specified = specified;
        this.disabled = disabled;
        this.function = function;
        this.reference = reference;
    }

    // Returns the override that changes nothing.
    public static ProgramAlphaTest empty() {
        return EMPTY;
    }

    // Parses the override of a program and ignores a malformed one.
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

    // Returns the GL function of a name or null.
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

    // Returns whether the pack set an override.
    public boolean hasDirectives() {
        return this.specified;
    }

    // Returns a GLSL discard that matches the override or an empty string.
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

    // Returns a GLSL discard of fragments that fail a comparison.
    public static String glslDiscard(String alphaAccessor, String operator, String threshold, String indent) {
        return indent + "if (!(" + alphaAccessor + " " + operator + " " + threshold + ")) {\n"
                + indent + "    discard;\n"
                + indent + "}\n";
    }

    // Returns the GLSL operator of a GL function or null.
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

    // Returns the reference or zero when the test is disabled.
    public float getReference() {
        return this.disabled ? 0.0f : this.reference;
    }

    // Saves the GL alpha test and applies the override.
    public void apply() {
        if (!this.specified) {
            return;
        }
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

    // Restores the saved GL alpha test once.
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
