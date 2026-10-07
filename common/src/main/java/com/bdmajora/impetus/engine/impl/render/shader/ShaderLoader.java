package com.bdmajora.impetus.engine.impl.render.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderParser;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;

import java.util.regex.Pattern;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public class ShaderLoader {
    // Compiles one of the engine's GLSL shaders from /assets/{namespace}/shaders/{path}, injecting the specialisation defines right after #version so one source yields several variants
    public static GlShader loadShader(ShaderType type, String name, ShaderConstants constants) {
        return new GlShader(type, name, downgradeIfNeeded(type, ShaderParser.parseShader(getShaderSource(name), ShaderLoader::getShaderSource, constants)));
    }

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^#version.*$", Pattern.MULTILINE);
    private static final Pattern IN_PARAM = Pattern.compile("^in ", Pattern.MULTILINE);
    private static final Pattern OUT_PARAM = Pattern.compile("^out ", Pattern.MULTILINE);
    private static final String LEGACY_PREAMBLE = String.join("\n",
            "#version 120",
            "#define LEGACY",
            "#define uint unsigned int",
            "#define texture texture2D"
    ) + "\n";

    public static boolean useLegacyGlsl() {
        return !LWJGL.isOpenGLVersionSupported(3, 2);
    }

    public static String downgradeIfNeeded(ShaderType type, String shaderSource) {
        if (useLegacyGlsl()) {
            if (type != ShaderType.VERTEX && type != ShaderType.FRAGMENT) {
                throw new IllegalStateException("Cannot load non-vertex/fragment shader on old GL");
            }
            shaderSource = VERSION_DIRECTIVE.matcher(shaderSource).replaceFirst(LEGACY_PREAMBLE);
            if (type == ShaderType.VERTEX) {
                shaderSource = IN_PARAM.matcher(shaderSource).replaceAll("attribute ");
            } else {
                shaderSource = IN_PARAM.matcher(shaderSource).replaceAll("varying ");
            }
            shaderSource = OUT_PARAM.matcher(shaderSource).replaceAll("varying ");
        }
        return shaderSource;
    }

    // Reads a bundled shader from the jar, throwing with the name if missing
    public static String getShaderSource(String name) {
        String[] splitStr;
        if(name.contains(":")) {
            splitStr = name.split(":", 2);
        } else {
            splitStr = new String[] { "minecraft", name };
        }
        String path = String.format("/assets/%s/shaders/%s", splitStr[0], splitStr[1]);

        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source for " + path, e);
        }
    }
}
