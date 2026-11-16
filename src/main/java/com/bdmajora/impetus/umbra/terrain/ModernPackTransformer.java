package com.bdmajora.impetus.umbra.terrain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rewriter of modern GLSL for the legacy pipeline.
public final class ModernPackTransformer {
    // Pattern of the version line.
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)(?:\\s+\\w+)?\\s*$");
    // Uint declaration.
    private static final Pattern UINT_DECLARATION = Pattern.compile(
            "(?m)^(\\s*(?:const\\s+)?uint\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)([^;]+)(;.*)$");
    // Uvec constructor.
    private static final Pattern UVEC_CONSTRUCTOR = Pattern.compile("\\buvec([234])\\s*\\(([^()]*)\\)");
    // Vec2 declaration from fixed function vec4.
    private static final Pattern VEC2_DECLARATION_FROM_FIXED_FUNCTION_VEC4 = Pattern.compile(
            "(?m)^(\\s*(?:const\\s+)?vec2\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*)([^;\\n]+)(;.*)$");
    // Fixed function vec4 term.
    private static final Pattern FIXED_FUNCTION_VEC4_TERM = Pattern.compile(
            "\\b(?:gl_MultiTexCoord[0-7]|gl_Color|gl_Vertex|mc_midTexCoord|iris_MultiTexCoord[0-7]"
                    + "|iris_Color|iris_Vertex|iris_MidTexFull)\\b");
    // Trailing swizzle.
    private static final Pattern TRAILING_SWIZZLE = Pattern.compile("\\.\\s*[xyzwrgastpq]{1,4}\\s*$");

    // Prevents instantiation
    private ModernPackTransformer() {
    }

    // Returns whether a source uses modern GLSL.
    public static boolean isModernSource(String source) {
        if (source == null) {
            return false;
        }
        Matcher matcher = VERSION.matcher(source);
        return matcher.find() && com.bdmajora.impetus.umbra.util.SafeInt.parse(matcher.group(1)) >= 130;
    }

    // Returns the rewritten source.
    public static String transform(String source) {
        String body = VERSION.matcher(source).replaceAll("");
        body = rewriteFogParameters(body);
        body = rewriteUnsignedStrictness(body);
        return "#version " + targetVersion(source) + " compatibility\n" + stripLeadingBlankLines(body);
    }

    // Returns the version to compile a source at.
    private static int targetVersion(String source) {
        int version = 330;
        java.util.regex.Matcher declared = VERSION.matcher(source);
        if (declared.find()) {
            version = Math.max(version, com.bdmajora.impetus.umbra.util.SafeInt.parse(declared.group(1)));
        }
        if (source.contains("imageStore") || source.contains("imageLoad") || source.contains("imageAtomic")) {
            version = Math.max(version, 430);
        }
        return version;
    }

    // Removes blank lines before the first line.
    private static String stripLeadingBlankLines(String source) {
        int start = 0;
        while (start < source.length()) {
            char c = source.charAt(start);
            if (c == '\n' || c == '\r') {
                start++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                start++;
                continue;
            }
            break;
        }
        return source.substring(start);
    }

    // Replaces fog struct reads with the fog uniforms.
    private static String rewriteFogParameters(String source) {
        if (!source.contains("gl_Fog")) {
            return source;
        }
        boolean[] alreadyDeclared = new boolean[FogParameters.DECLARATIONS.length];
        for (int i = 0; i < FogParameters.DECLARATIONS.length; i++) {
            alreadyDeclared[i] = source.contains(identifierOf(FogParameters.DECLARATIONS[i]));
        }

        source = FogParameters.rewrite(source);

        StringBuilder declarations = new StringBuilder();
        for (int i = 0; i < FogParameters.DECLARATIONS.length; i++) {
            String identifier = identifierOf(FogParameters.DECLARATIONS[i]);
            if (!alreadyDeclared[i] && source.contains(identifier)) {
                declarations.append(FogParameters.DECLARATIONS[i]).append('\n');
            }
        }
        return declarations.length() == 0 ? source : injectAfterPreamble(source, declarations.toString());
    }

    // Identifiers the of.
    private static String identifierOf(String declaration) {
        String trimmed = declaration.trim();
        int end = trimmed.lastIndexOf(';');
        int start = trimmed.lastIndexOf(' ', end);
        return trimmed.substring(start + 1, end);
    }

    // Name of the fullscreen texture coordinate attribute.
    public static final String FULLSCREEN_TEXCOORD_ATTRIBUTE = "iris_QuadTexCoord";

    // Pattern of the first texture coordinate read.
    private static final Pattern MULTI_TEX_COORD_0 =
            Pattern.compile("(?<![A-Za-z0-9_])gl_MultiTexCoord0(?![A-Za-z0-9_])");

    // Feeds the fullscreen texture coordinate to a vertex stage.
    public static String bindFullscreenTexCoord(String vertexSource) {
        if (vertexSource == null || !vertexSource.contains("gl_MultiTexCoord0")) {
            return vertexSource;
        }
        String rewritten = MULTI_TEX_COORD_0.matcher(vertexSource).replaceAll(
                Matcher.quoteReplacement("vec4(" + FULLSCREEN_TEXCOORD_ATTRIBUTE + ", 0.0, 1.0)"));
        return injectAfterPreamble(rewritten, "in vec2 " + FULLSCREEN_TEXCOORD_ATTRIBUTE + ";");
    }

    // Inserts text after the version and extension lines.
    private static String injectAfterPreamble(String source, String declarations) {
        String[] lines = source.split("\n", -1);
        boolean inBlockComment = false;
        int insertAt = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (inBlockComment) {
                int close = trimmed.indexOf("*/");
                if (close < 0) {
                    insertAt = i + 1;
                    continue;
                }
                inBlockComment = false;
                trimmed = trimmed.substring(close + 2).trim();
            }
            while (trimmed.startsWith("/*")) {
                int close = trimmed.indexOf("*/", 2);
                if (close < 0) {
                    inBlockComment = true;
                    trimmed = "";
                    break;
                }
                trimmed = trimmed.substring(close + 2).trim();
            }
            if (inBlockComment || trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                insertAt = i + 1;
                continue;
            }
            insertAt = i;
            break;
        }
        StringBuilder out = new StringBuilder(source.length() + declarations.length());
        for (int i = 0; i < lines.length; i++) {
            if (i == insertAt) {
                out.append(declarations);
            }
            out.append(lines[i]);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        if (insertAt >= lines.length) {
            out.append(declarations);
        }
        return out.toString();
    }

    // Adds unsigned suffixes where strict drivers need them.
    static String rewriteUnsignedStrictness(String source) {
        Matcher constUint = UINT_DECLARATION.matcher(source);
        StringBuffer rewritten = new StringBuffer(source.length());
        while (constUint.find()) {
            String expression = constUint.group(2).trim();
            constUint.appendReplacement(rewritten, Matcher.quoteReplacement(
                    constUint.group(1) + "uint(" + expression + ")" + constUint.group(3)));
        }
        constUint.appendTail(rewritten);
        return rewriteFixedFunctionVec2Narrowing(rewriteUnsignedVectorConstructors(rewritten.toString()));
    }

    // Wraps int arguments of unsigned vector constructors.
    private static String rewriteUnsignedVectorConstructors(String source) {
        Matcher constructor = UVEC_CONSTRUCTOR.matcher(source);
        StringBuffer rewritten = new StringBuffer(source.length());
        while (constructor.find()) {
            String[] args = constructor.group(2).split(",", -1);
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                String trimmed = args[i].trim();
                if (trimmed.matches("\\d+")) {
                    args[i] = args[i].replaceFirst("\\d+", trimmed + "u");
                    changed = true;
                }
            }
            if (changed) {
                constructor.appendReplacement(rewritten, Matcher.quoteReplacement(
                        "uvec" + constructor.group(1) + "(" + String.join(",", args) + ")"));
            } else {
                constructor.appendReplacement(rewritten, Matcher.quoteReplacement(constructor.group(0)));
            }
        }
        constructor.appendTail(rewritten);
        return rewritten.toString();
    }

    // Narrows fixed function vec4 reads assigned to vec2.
    private static String rewriteFixedFunctionVec2Narrowing(String source) {
        Matcher declaration = VEC2_DECLARATION_FROM_FIXED_FUNCTION_VEC4.matcher(source);
        StringBuffer rewritten = new StringBuffer(source.length());
        while (declaration.find()) {
            String expression = declaration.group(2).trim();
            if (FIXED_FUNCTION_VEC4_TERM.matcher(expression).find()
                    && !TRAILING_SWIZZLE.matcher(expression).find()
                    && !expression.startsWith("vec2(")) {
                declaration.appendReplacement(rewritten, Matcher.quoteReplacement(
                        declaration.group(1) + "(" + expression + ").xy" + declaration.group(3)));
            } else {
                declaration.appendReplacement(rewritten, Matcher.quoteReplacement(declaration.group(0)));
            }
        }
        declaration.appendTail(rewritten);
        return rewritten.toString();
    }
}
