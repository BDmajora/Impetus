package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parser of the draw buffer directives of a fragment shader.
public final class DrawBuffers {
    // Pattern of a DRAWBUFFERS directive.
    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS:([0-9]+)\\s*\\*/");
    // Pattern of a RENDERTARGETS directive.
    private static final Pattern RENDERTARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS:\\s*([0-9,\\s]+)\\*/");
    // Frag color.
    private static final Pattern FRAG_COLOR = Pattern.compile("\\bgl_FragColor\\b");
    // Named fragment output.
    private static final Pattern NAMED_FRAGMENT_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*)(?:layout\\s*\\((?s:.*?)\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "out\\s+(?:(?:lowp|mediump|highp)\\s+)?(float|vec2|vec3|vec4)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*(?://.*)?$");
    // Define directive.
    private static final Pattern DEFINE_DIRECTIVE = Pattern.compile(
            "^(\\s*#\\s*define\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\b.*)$");
    // Layout location.
    private static final Pattern LAYOUT_LOCATION = Pattern.compile("\\blocation\\s*=\\s*(\\d+)\\b");

    // Draw buffers when a shader names none.
    public static final int[] DEFAULT = new int[]{0};

    // GL enum of the draw buffer limit.
    private static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    // Size of the fragment output array or minus one.
    private static int fragmentOutputArraySize = -1;

    // Prevents instantiation
    private DrawBuffers() {
    }

    // Returns the size of the fragment output array.
    public static int fragmentOutputArraySize() {
        if (fragmentOutputArraySize < 0) {
            int reported = LWJGL.glGetInteger(GL_MAX_DRAW_BUFFERS);
            fragmentOutputArraySize = Math.min(reported > 0 ? reported : 8, UmbraRenderTargets.MAX_COLOR_BUFFERS);
        }
        return fragmentOutputArraySize;
    }

    // Returns the draw buffers of the active directive.
    public static int[] parseActive(String fragmentSource, Map<String, String> defines) {
        if (fragmentSource == null) {
            return DEFAULT.clone();
        }
        int[] raw = parseLastDirective(fragmentSource);
        try {
            String evaluated = PropertiesPreprocessor.preprocess(fragmentSource, defines == null ? ShaderMacros.standard() : defines);
            List<Directive> activeDirectives = directives(evaluated);
            return activeDirectives.isEmpty()
                    ? raw
                    : activeDirectives.get(activeDirectives.size() - 1).buffers().clone();
        } catch (RuntimeException e) {
            return raw;
        }
    }

    // Returns the draw buffers below a limit.
    public static int[] sanitize(int[] drawBuffers, int maxExclusive) {
        return sanitize(drawBuffers, maxExclusive, null);
    }

    // Returns the draw buffers below a limit and reports the dropped ones.
    public static int[] sanitize(int[] drawBuffers, int maxExclusive, IntConsumer invalidBufferConsumer) {
        if (drawBuffers == null || drawBuffers.length == 0) {
            return DEFAULT.clone();
        }
        long seen = 0L;
        int valid = 0;
        for (int buffer : drawBuffers) {
            if (buffer >= 0 && buffer < maxExclusive) {
                if ((seen & (1L << buffer)) == 0) {
                    seen |= 1L << buffer;
                    valid++;
                }
            } else if (invalidBufferConsumer != null) {
                invalidBufferConsumer.accept(buffer);
            }
        }
        if (valid == drawBuffers.length) {
            return drawBuffers;
        }
        if (valid == 0) {
            return DEFAULT.clone();
        }
        int[] result = new int[valid];
        seen = 0L;
        int written = 0;
        for (int buffer : drawBuffers) {
            if (buffer >= 0 && buffer < maxExclusive && (seen & (1L << buffer)) == 0) {
                seen |= 1L << buffer;
                result[written++] = buffer;
            }
        }
        return result;
    }

    // Rewrites fragment outputs to match the draw buffers.
    public static String rewriteFragmentOutputs(String fragmentSource, int[] drawBuffers) {
        if (fragmentSource == null) {
            return fragmentSource;
        }
        String rewritten = rewriteFragColor(fragmentSource);
        return rewriteNamedFragmentOutputs(rewritten);
    }

    // Rewrites gl_FragColor writes to gl_FragData.
    private static String rewriteFragColor(String source) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher define = DEFINE_DIRECTIVE.matcher(lines[i]);
            if (define.matches()) {
                if (!"gl_FragColor".equals(define.group(2))) {
                    lines[i] = define.group(1) + define.group(2)
                            + FRAG_COLOR.matcher(define.group(3))
                            .replaceAll(Matcher.quoteReplacement("gl_FragData[0]"));
                }
                continue;
            }
            if (lines[i].trim().startsWith("#")) {
                continue;
            }
            lines[i] = FRAG_COLOR.matcher(lines[i]).replaceAll(Matcher.quoteReplacement("gl_FragData[0]"));
        }
        return String.join("\n", lines);
    }

    // Assigns locations to named fragment outputs.
    private static String rewriteNamedFragmentOutputs(String source) {
        Matcher matcher = NAMED_FRAGMENT_OUTPUT.matcher(source);
        StringBuffer rewritten = new StringBuffer(source.length());
        int implicitSlot = 0;
        while (matcher.find()) {
            String declaration = matcher.group(0);
            Matcher layout = LAYOUT_LOCATION.matcher(declaration);
            if (layout.find()) {
                implicitSlot = Math.max(implicitSlot, com.bdmajora.impetus.umbra.util.SafeInt.parse(layout.group(1)) + 1);
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(declaration));
                continue;
            }
            int outputSlot = implicitSlot++;
            String outputName = matcher.group(3);
            if (outputName.startsWith("iris_") || outputSlot >= UmbraRenderTargets.MAX_COLOR_BUFFERS) {
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(declaration));
                continue;
            }
            String indent = matcher.group(1);
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(
                    indent + "layout(location = " + outputSlot + ") " + declaration.substring(indent.length())));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    // Returns the draw buffers of the last directive.
    private static int[] parseLastDirective(String fragmentSource) {
        List<Directive> directives = directives(fragmentSource);
        if (directives.isEmpty()) {
            return DEFAULT.clone();
        }
        return directives.get(directives.size() - 1).buffers().clone();
    }

    // Returns every directive of a source in order.
    private static List<Directive> directives(String fragmentSource) {
        List<Directive> directives = new ArrayList<>();

        Matcher rt = RENDERTARGETS.matcher(fragmentSource);
        while (rt.find()) {
            int[] buffers = parseRenderTargets(rt.group(1));
            if (buffers.length == 0) {
                continue;
            }
            directives.add(new Directive(rt.start(), buffers));
        }

        Matcher db = DRAWBUFFERS.matcher(fragmentSource);
        while (db.find()) {
            String digits = db.group(1);
            int[] buffers = new int[digits.length()];
            for (int i = 0; i < digits.length(); i++) {
                buffers[i] = Character.digit(digits.charAt(i), 10);
            }
            directives.add(new Directive(db.start(), buffers));
        }

        directives.sort(Comparator.comparingInt(Directive::offset));
        return directives;
    }

    // Parses a RENDERTARGETS list.
    private static int[] parseRenderTargets(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        String[] parts = trimmed.split("\\s*,\\s*");
        int[] buffers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                buffers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                return new int[0];
            }
        }
        return buffers;
    }

    // Data of the directive.
    private record Directive(int offset, int[] buffers) {
    }
}
