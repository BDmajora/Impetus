package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramBuilder;
import com.bdmajora.impetus.umbra.gl.shader.GlShader;
import com.bdmajora.impetus.umbra.gl.shader.ShaderType;
import com.bdmajora.impetus.lwjgl.GL11;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

import com.bdmajora.impetus.umbra.gl.texture.TextureParameters;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Final-presentation conversion from sRGB to a wide-gamut target (P3, Rec.2020, Adobe RGB) like Iris: scratch copy, then a fullscreen quad through decode, 3x3 primaries transform, encode; skipped for sRGB
public final class ColorSpaceConverter {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    public enum ColorSpace {
        SRGB, DCI_P3, DISPLAY_P3, REC2020, ADOBE_RGB;

        // Pack or option name to a colour space; SRGB when unknown
        public static ColorSpace byName(String name) {
            if (name == null) {
                return SRGB;
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SRGB;
            }
        }
    }

    // The user's selected output colourspace, read by the pipeline every frame so a change takes effect at once; static since the options screen sets it and the pipeline reads it
    private static ColorSpace current = ColorSpace.SRGB;

    private GlProgram program;
    private ColorSpace programSpace;
    private int scratchTexture = -1;
    private int scratchWidth = -1, scratchHeight = -1;
    private boolean broken;

    // Selects the output transform
    public static void setColorSpace(ColorSpace space) {
        current = space != null ? space : ColorSpace.SRGB;
    }

    // Current output transform
    public static ColorSpace getColorSpace() {
        return current;
    }

    // Whether a non-sRGB transform is selected
    public boolean isActive() {
        return current != ColorSpace.SRGB && !this.broken;
    }

    // Converts the bound draw framebuffer's colour in place; requires the presentation framebuffer bound for BOTH read and draw with blending and depth off, exactly the state the composite chain ends in
    public void run(int width, int height, FullscreenQuadRenderer quad) {
        if (!isActive() || width <= 0 || height <= 0) {
            return;
        }

        try {
            ensureProgram();
            ensureScratch(width, height);

            GlTextureUnits.resetToUnit0();
            int previous = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.scratchTexture);
            LWJGL.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

            this.program.bind();
            quad.draw();
            this.program.unbind();

            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        } catch (Exception e) {
            // A conversion failure must never take out presentation; disable and keep rendering sRGB.
            this.broken = true;
            LOGGER.error("[Umbra] Colorspace conversion disabled after error", e);
        }
    }

    // Frees the scratch texture and program
    public void destroy() {
        if (this.program != null) {
            this.program.destroy();
            this.program = null;
            this.programSpace = null;
        }
        if (this.scratchTexture != -1) {
            LWJGL.glDeleteTextures(this.scratchTexture);
            this.scratchTexture = -1;
        }
        this.scratchWidth = -1;
        this.scratchHeight = -1;
        this.broken = false;
    }

    // Lazily sizes the intermediate texture
    private void ensureScratch(int width, int height) {
        if (this.scratchTexture != -1 && this.scratchWidth == width && this.scratchHeight == height) {
            return;
        }
        if (this.scratchTexture == -1) {
            this.scratchTexture = LWJGL.glGenTextures();
        }
        GlTextureUnits.resetToUnit0();
        int previous = LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.scratchTexture);
        LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
        TextureParameters.setFilter2D(GL11.GL_NEAREST);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        this.scratchWidth = width;
        this.scratchHeight = height;
    }

    // Lazily compiles the conversion shader
    private void ensureProgram() {
        if (this.program != null && this.programSpace == current) {
            return;
        }
        if (this.program != null) {
            this.program.destroy();
            this.program = null;
        }

        String name = "impetus_colorspace_" + current.name().toLowerCase(Locale.ROOT);
        GlShader vertex = new GlShader(ShaderType.VERTEX, name + ".vsh", VERTEX_SOURCE);
        GlShader fragment = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource(current));
        try {
            this.program = ProgramBuilder.begin(name)
                    .attach(vertex)
                    .attach(fragment)
                    .bindAttributeLocation(FullscreenQuadRenderer.POSITION_SLOT, "a_Position")
                    .bindAttributeLocation(FullscreenQuadRenderer.TEXCOORD_SLOT, "a_TexCoord")
                    .link();
        } finally {
            vertex.destroy();
            fragment.destroy();
        }
        this.programSpace = current;

        // The scratch copy always sits on unit 0.
        this.program.bind();
        int location = this.program.getUniformLocation("u_Source");
        if (location != -1) {
            LWJGL.glUniform1i(location, 0);
        }
        this.program.unbind();
    }

    private static final String VERTEX_SOURCE =
            "#version 120\n" +
            "attribute vec2 a_Position;\n" +
            "attribute vec2 a_TexCoord;\n" +
            "varying vec2 v_Tex;\n" +
            "void main() {\n" +
            "    v_Tex = a_TexCoord;\n" +
            // The shared fullscreen quad supplies a_Position in [0,1]; map to NDC [-1,1] here.
            "    gl_Position = vec4(a_Position * 2.0 - 1.0, 0.0, 1.0);\n" +
            "}\n";

    // Builds the conversion shader for one target: linear Rec.709/sRGB into XYZ at D65, out to the target's primaries, then its transfer function; every matrix is published CIE data, not a fit
    private static String fragmentSource(ColorSpace space) {
        String matrix;
        String encode;
        switch (space) {
            case DCI_P3:
                matrix = "mat3(0.8224621, 0.0331941, 0.0170827, 0.1775380, 0.9668058, 0.0723974, -0.0000001, 0.0000001, 0.9105199)";
                encode = "pow(c, vec3(1.0 / 2.6))";
                break;
            case DISPLAY_P3:
                matrix = "mat3(0.8224621, 0.0331941, 0.0170827, 0.1775380, 0.9668058, 0.0723974, -0.0000001, 0.0000001, 0.9105199)";
                encode = "linearToSrgb(c)";
                break;
            case REC2020:
                matrix = "mat3(0.6274040, 0.0690970, 0.0163916, 0.3292820, 0.9195400, 0.0880132, 0.0433136, 0.0113612, 0.8955950)";
                encode = "pow(c, vec3(1.0 / 2.4))";
                break;
            case ADOBE_RGB:
                matrix = "mat3(0.7152249, 0.0000000, 0.0000000, 0.2848247, 1.0000000, 0.0411507, 0.0000000, 0.0000000, 0.9587653)";
                encode = "pow(c, vec3(1.0 / 2.19921875))";
                break;
            default:
                matrix = "mat3(1.0)";
                encode = "linearToSrgb(c)";
                break;
        }

        return "#version 120\n" +
                "uniform sampler2D u_Source;\n" +
                "varying vec2 v_Tex;\n" +
                "vec3 srgbToLinear(vec3 c) {\n" +
                "    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));\n" +
                "}\n" +
                "vec3 linearToSrgb(vec3 c) {\n" +
                "    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));\n" +
                "}\n" +
                "void main() {\n" +
                "    vec3 srgb = texture2D(u_Source, v_Tex).rgb;\n" +
                "    vec3 c = " + matrix + " * srgbToLinear(srgb);\n" +
                "    c = clamp(c, 0.0, 1.0);\n" +
                "    gl_FragColor = vec4(" + encode + ", 1.0);\n" +
                "}\n";
    }
}
