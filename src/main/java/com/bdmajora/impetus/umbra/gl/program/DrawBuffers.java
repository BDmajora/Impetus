package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.github.bsideup.jabel.Desugar;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parses the directive a fragment shader uses to declare which attachments it writes: OptiFine's DRAWBUFFERS (one digit each, targets 0-9) or Iris's RENDERTARGETS (comma list, the only way to reach 10-15); neither means colortex0 alone
public final class DrawBuffers {
    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS:([0-9]+)\\s*\\*/");
    private static final Pattern RENDERTARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS:\\s*([0-9,\\s]+)\\*/");
    private static final Pattern FRAG_COLOR = Pattern.compile("\\bgl_FragColor\\b");
    private static final Pattern NAMED_FRAGMENT_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*)(?:layout\\s*\\((?s:.*?)\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "out\\s+(?:(?:lowp|mediump|highp)\\s+)?(float|vec2|vec3|vec4)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*(?://.*)?$");
    private static final Pattern DEFINE_DIRECTIVE = Pattern.compile(
            "^(\\s*#\\s*define\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\b.*)$");
    private static final Pattern LAYOUT_LOCATION = Pattern.compile("\\blocation\\s*=\\s*(\\d+)\\b");

    // The default for a shader with no directive. Shared, so every caller that keeps it must clone first
    public static final int[] DEFAULT = new int[]{0};

    private static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    private static int fragmentOutputArraySize = -1;

    private DrawBuffers() {
    }

    // Length for `out vec4 iris_FragData[N]`, queried from the driver: an array output needs N CONTIGUOUS locations capped by GL_MAX_DRAW_BUFFERS (8), and the old hardcoded 16 linked on NVIDIA but failed on Mesa/Intel Arc; clamping loses nothing since directive indices are dense output slots
    public static int fragmentOutputArraySize() {
        if (fragmentOutputArraySize < 0) {
            int reported = LWJGL.glGetInteger(GL_MAX_DRAW_BUFFERS);
            // A failed query reports 0; 8 is the GL 3.3 floor and the universal real-world value.
            fragmentOutputArraySize = Math.min(reported > 0 ? reported : 8, UmbraRenderTargets.MAX_COLOR_BUFFERS);
        }
        return fragmentOutputArraySize;
    }

    // Parses honouring #ifdef gates, so an option can change which targets a pass writes
    public static int[] parseActive(String fragmentSource, Map<String, String> defines) {
        if (fragmentSource == null) {
            return DEFAULT.clone();
        }
        int[] raw = parseLastDirective(fragmentSource);
        try {
            String evaluated = PropertiesPreprocessor.preprocess(fragmentSource, defines == null ? ShaderMacros.standard() : defines);
            List<Directive> activeDirectives = directives(evaluated);
            // The conditional evaluator cannot expand every macro shape; if evaluation removes every target directive, keep the source-order fallback rather than invent a default mask
            return activeDirectives.isEmpty()
                    ? raw
                    : activeDirectives.get(activeDirectives.size() - 1).buffers().clone();
        } catch (RuntimeException e) {
            return raw;
        }
    }

    // Drops out-of-range targets silently
    public static int[] sanitize(int[] drawBuffers, int maxExclusive) {
        return sanitize(drawBuffers, maxExclusive, null);
    }

    // Drops out-of-range and repeated targets, reporting each invalid one; an already-clean array is returned as is, since this runs on every phase switch and the inputs are usually the sanitized arrays the programs were built with. Callers must treat the result as read-only
    public static int[] sanitize(int[] drawBuffers, int maxExclusive, IntConsumer invalidBufferConsumer) {
        if (drawBuffers == null || drawBuffers.length == 0) {
            return DEFAULT.clone();
        }
        // Targets are bounded by MAX_COLOR_BUFFERS, so a bitmask stands in for the seen table
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

    // Render targets are packed into DENSE attachments like Iris: DRAWBUFFERS:03648 means slot 0 writes colortex0, slot 1 colortex3, with no holes; gl_FragData[N] and layout(location = N) already name dense slots, so only gl_FragColor and implicit named outputs are normalised
    public static String rewriteFragmentOutputs(String fragmentSource, int[] drawBuffers) {
        if (fragmentSource == null) {
            return fragmentSource;
        }
        String rewritten = rewriteFragColor(fragmentSource);
        return rewriteNamedFragmentOutputs(rewritten);
    }

    // gl_FragColor onto gl_FragData[0] so both spellings route the same way
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

    // Named fragment outputs stay as real `out` declarations like Iris: an explicit layout(location = N) passes through, one without gets the declaration order; the old `#define <name> gl_FragData[slot]` corrupted shaders reusing the name as a local (Photon's `result`)
    private static String rewriteNamedFragmentOutputs(String source) {
        Matcher matcher = NAMED_FRAGMENT_OUTPUT.matcher(source);
        StringBuffer rewritten = new StringBuffer(source.length());
        int implicitSlot = 0;
        while (matcher.find()) {
            String declaration = matcher.group(0);
            Matcher layout = LAYOUT_LOCATION.matcher(declaration);
            if (layout.find()) {
                implicitSlot = Math.max(implicitSlot, Integer.parseInt(layout.group(1)) + 1);
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

    // Preprocessed source can still hold several target directives under nested option gates (Complementary's water/lava); the LAST is right, since earlier ones are fallback declarations narrower than what the program actually writes
    private static int[] parseLastDirective(String fragmentSource) {
        List<Directive> directives = directives(fragmentSource);
        if (directives.isEmpty()) {
            return DEFAULT.clone();
        }
        return directives.get(directives.size() - 1).buffers().clone();
    }

    // Every DRAWBUFFERS and RENDERTARGETS comment with its enclosing conditional
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

    // Comma-separated form; any unparsable entry voids the whole directive
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

    // A target directive and where it sits in the source, so the last one in file order wins
    @Desugar
    private record Directive(int offset, int[] buffers) {
    }
}
