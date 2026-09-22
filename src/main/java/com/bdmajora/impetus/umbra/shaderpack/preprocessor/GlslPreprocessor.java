package com.bdmajora.impetus.umbra.shaderpack.preprocessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Light-touch GLSL transformation after #include flattening and before compilation: find #version, inject #define macros after it, default to #version 120 when missing; legacy built-in substitution is NOT done here
public final class GlslPreprocessor {

    // Trailing \r? is load-bearing: callers split on "\n" and call matches(), and Windows line endings failed every #version match, silently breaking detectVersion, injectDefines and hoistExtensionDirectives
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\s*#version\\s+(\\d+)(?:\\s+(\\w+))?.*\\r?$");

    // Default GLSL version assumed for 1.12.2-era packs that omit a #version directive
    public static final int DEFAULT_VERSION = 120;

    private GlslPreprocessor() {
    }

    // Inserts defines right after #version (prepending a default if none); defines is ordered name -> value, an empty value yielding a bare #define NAME
    public static List<String> injectDefines(List<String> lines, Map<String, String> defines) {
        List<String> out = new ArrayList<>(lines.size() + defines.size() + 1);

        int versionIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (VERSION_PATTERN.matcher(lines.get(i)).matches()) {
                versionIndex = i;
                break;
            }
        }

        if (versionIndex < 0) {
            // No #version present: prepend a default one, then defines, then the original source.
            out.add("#version " + DEFAULT_VERSION);
            out.addAll(toDefineLines(defines));
            out.addAll(lines);
            return out;
        }

        out.add(lines.get(versionIndex));
        out.addAll(toDefineLines(defines));
        for (int i = 0; i < lines.size(); i++) {
            if (i == versionIndex || VERSION_PATTERN.matcher(lines.get(i)).matches()) {
                continue;
            }
            out.add(lines.get(i));
        }
        return out;
    }

    // #define lines to inject
    private static List<String> toDefineLines(Map<String, String> defines) {
        List<String> result = new ArrayList<>(defines.size());
        for (Map.Entry<String, String> e : defines.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                result.add("#define " + e.getKey());
            } else {
                result.add("#define " + e.getKey() + " " + e.getValue());
            }
        }
        return result;
    }

    // Drops #if/#ifdef branches the macro set does not take, so a scanner sees what the GPU compiles; for directive extraction only (every # line is consumed), call once per stage, and #define-form directives (SHADOWRES) still need raw source. Returns source unchanged if unevaluable
    public static String resolveConditionals(String source, Map<String, String> defines) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        try {
            String resolved = PropertiesPreprocessor.preprocess(source, defines);
            // An unterminated #if can swallow the rest of the file; keep the raw source rather than scan nothing.
            return resolved.trim().isEmpty() ? source : resolved;
        } catch (RuntimeException e) {
            return source;
        }
    }

    // A GLSL floating-point literal: 1.0, .5, 0., 1e-3
    private static final Pattern FLOAT_LITERAL =
            Pattern.compile("(?<![A-Za-z0-9_.])(?:\\d+\\.\\d*|\\.\\d+|\\d+[eE][-+]?\\d+)");
    // "defined X" / "defined(X)" - the one place an identifier must NOT be macro-expanded
    private static final Pattern DEFINED_OPERATOR =
            Pattern.compile("\\bdefined\\s*(?:\\(\\s*\\w+\\s*\\)|\\s+\\w+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern CONDITIONAL_DIRECTIVE =
            Pattern.compile("(?m)^([\\t ]*#[\\t ]*)(if|elif)\\b([^\\n]*)$");

    // Folds #if/#elif conditionals resolving to a float comparison into a literal 1/0, since the C preprocessor is integer-only and `#if MOTION_BLUR > 0.0` crashes NVIDIA with C0105 (Clarity's composite.csh); Iris's JCPP truncates floats, this is the narrow equivalent, leaving anything unparseable (including backslash-continued lines) as taken and unfolded
    public static String foldFloatConditionals(String source, Map<String, String> defines) {
        if (source == null || source.indexOf('#') < 0) {
            return source;
        }
        Map<String, String> active = new LinkedHashMap<>(defines);
        StringBuilder out = new StringBuilder(source.length());
        // Nesting levels, each [0] = this branch active, [1] = some branch already taken.
        Deque<boolean[]> stack = new ArrayDeque<>();
        String[] lines = source.split("\n", -1);
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // The real preprocessor strips comments before finding directives, so a `#if` inside /* */ is not one; Clarity parks a dead #ifdef/#endif pair in a comment block, and interpreting it would desync the macro bookkeeping
            boolean commented = inBlockComment;
            inBlockComment = advanceBlockComment(line, inBlockComment);
            out.append(commented ? line : foldDirective(line, active, stack));
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    // Whether the line ENDS inside a block comment given whether it started in one, carried across lines so a directive commented out by a multi-line block is not evaluated
    private static boolean advanceBlockComment(String line, boolean inBlockComment) {
        for (int i = 0; i < line.length() - 1; i++) {
            if (inBlockComment) {
                if (line.charAt(i) == '*' && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
            } else if (line.charAt(i) == '/' && line.charAt(i + 1) == '/') {
                return false;
            } else if (line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
            }
        }
        return inBlockComment;
    }

    // Evaluates one #if-family line and updates the active stack
    private static String foldDirective(String line, Map<String, String> defines, Deque<boolean[]> stack) {
        Matcher conditional = CONDITIONAL_DIRECTIVE.matcher(line);
        if (conditional.matches()) {
            String expression = stripComment(conditional.group(3));
            boolean[] frame = "elif".equals(conditional.group(2)) ? stack.poll() : null;
            boolean alreadyTaken = frame != null && frame[1];
            Boolean value = PropertiesPreprocessor.tryEvaluateBooleanExpression(expression, defines).orElse(null);
            boolean malformed = value == null && !isContinued(expression) && isSyntacticallyInvalid(expression);
            boolean taken = !alreadyTaken && allActive(stack) && !malformed && (value == null || value);
            stack.push(new boolean[]{taken, alreadyTaken || taken});
            if (malformed || (value != null && expandsToFloat(expression, defines, 0))) {
                return conditional.group(1) + conditional.group(2) + " " + (!malformed && value ? "1" : "0");
            }
            return line;
        }

        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
            return line;
        }
        // Comments come off first as the real preprocessor does: `#endif // label` and `#else /* label */` are idiomatic, and a trailing label stopping `#else` from being recognized would desync the branch stack
        String directive = stripComment(trimmed.substring(1)).trim();
        if (directive.startsWith("ifdef ") || directive.startsWith("ifndef ")) {
            boolean defined = defines.containsKey(directive.substring(directive.indexOf(' ') + 1).trim());
            boolean taken = allActive(stack) && (directive.startsWith("ifdef ") == defined);
            stack.push(new boolean[]{taken, taken});
        } else if (directive.equals("else")) {
            boolean[] frame = stack.poll();
            boolean alreadyTaken = frame != null && frame[1];
            stack.push(new boolean[]{!alreadyTaken && allActive(stack), true});
        } else if (directive.equals("endif")) {
            stack.poll();
        } else if (directive.startsWith("define ") && allActive(stack)) {
            PropertiesPreprocessor.recordDefine(directive, defines);
        } else if (directive.startsWith("undef ") && allActive(stack)) {
            defines.remove(directive.substring("undef ".length()).trim());
        }
        return line;
    }

    // Whether this directive is only the FIRST line of a backslash-continued one, so its balancing parentheses follow; Photon has three (e.g. a bare `#elif ( \`) that look unbalanced alone, and folding them to 0 would delete live branches from its gbuffer programs
    private static boolean isContinued(String expression) {
        String trimmed = expression.trim();
        return trimmed.endsWith("\\");
    }

    // Whether the expression is something NO preprocessor could accept, so folding to 0 beats forwarding; deliberately narrow (our parser failing is not grounds), with unbalanced parentheses the one sure signal: Pastel v1.200's `#if (in(biome, BIOME_SOUL_SAND_VALLEY)` costs NVIDIA its whole deferred1 pass, and false is what the pack means
    private static boolean isSyntacticallyInvalid(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                return true;
            }
        }
        return depth != 0;
    }

    // Whether every enclosing conditional is currently true
    private static boolean allActive(Deque<boolean[]> stack) {
        for (boolean[] frame : stack) {
            if (!frame[0]) {
                return false;
            }
        }
        return true;
    }

    // Truncates at the first comment opener of either kind — a directive never carries meaning past one
    private static String stripComment(String text) {
        int line = text.indexOf("//");
        int block = text.indexOf("/*");
        int comment = line < 0 ? block : (block < 0 ? line : Math.min(line, block));
        return comment < 0 ? text : text.substring(0, comment);
    }

    // Whether the expression contains a float literal ONCE ITS MACROS ARE SUBSTITUTED, as the driver sees it; a macro expanding to 0.5 makes an integer-looking conditional a float one
    private static boolean expandsToFloat(String expression, Map<String, String> defines, int depth) {
        if (FLOAT_LITERAL.matcher(expression).find()) {
            return true;
        }
        if (depth >= 8) {
            return false;
        }
        Matcher identifiers = IDENTIFIER.matcher(DEFINED_OPERATOR.matcher(expression).replaceAll(""));
        while (identifiers.find()) {
            String value = defines.get(identifiers.group());
            if (value != null && !value.isEmpty() && expandsToFloat(value, defines, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    // #extension GL_FOO : enable in any legal spacing; the trailing \r? is for CRLF pack files, as with VERSION_PATTERN
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^\\s*#\\s*extension\\s+.*\\r?$");

    // Every rewrite that must happen between the transformers and glShaderSource because packs are authored against NVIDIA while this port must satisfy Mesa; all no-ops unless tripped. Call from EVERY path handing pack source to the driver: there are two GlShader classes, and hanging this off the Umbra one missed the engine one the terrain/shadow override uses, so the #extension fix in 0323d2aaa never ran on gbuffers_terrain
    public static String finalizeForDriver(String name, String source) {
        return rewriteIntegerSamplerLookups(name, hoistExtensionDirectives(source));
    }

    // Moves every #extension up to just below #version where GLSL requires it: NVIDIA honours them anywhere but MESA ENFORCES the rule, and include flattening puts Complementary's worldSpaceRef.glsl `#extension` mid-program (Iris does the same in JcppProcessor); only rewrites when needed, de-duplicates, keeps order, leaves a blank line for line numbers, and hoisting out of an #if is safe since `: enable` warns rather than fails
    public static String hoistExtensionDirectives(String source) {
        if (source == null || !source.contains("#extension")) {
            return source;
        }

        String[] lines = source.split("\n", -1);
        int firstRealToken = indexOfFirstNonPreprocessorLine(lines);
        if (firstRealToken < 0) {
            return source;
        }

        boolean misplaced = false;
        for (int i = firstRealToken; i < lines.length; i++) {
            if (EXTENSION_PATTERN.matcher(lines[i]).matches()) {
                misplaced = true;
                break;
            }
        }
        if (!misplaced) {
            return source;
        }

        List<String> directives = new ArrayList<>();
        List<String> kept = new ArrayList<>(lines.length);
        int versionLine = -1;
        for (String line : lines) {
            if (EXTENSION_PATTERN.matcher(line).matches()) {
                String directive = line.trim();
                if (!directives.contains(directive)) {
                    directives.add(directive);
                }
                kept.add("");
                continue;
            }
            if (versionLine < 0 && VERSION_PATTERN.matcher(line).matches()) {
                versionLine = kept.size();
            }
            kept.add(line);
        }

        kept.addAll(versionLine < 0 ? 0 : versionLine + 1, directives);
        return String.join("\n", kept);
    }

    // The compatibility-profile texture lookups and the core built-in replacing each; every entry requires ( immediately after the name, so texture2D never matches inside texture2DLod and iteration order is irrelevant
    private static final Map<String, String> LEGACY_TEXTURE_LOOKUPS;

    static {
        Map<String, String> lookups = new LinkedHashMap<>();
        lookups.put("texture1D", "texture");
        lookups.put("texture2D", "texture");
        lookups.put("texture3D", "texture");
        lookups.put("textureCube", "texture");
        lookups.put("texture1DLod", "textureLod");
        lookups.put("texture2DLod", "textureLod");
        lookups.put("texture3DLod", "textureLod");
        lookups.put("textureCubeLod", "textureLod");
        lookups.put("texture1DProj", "textureProj");
        lookups.put("texture2DProj", "textureProj");
        lookups.put("texture3DProj", "textureProj");
        lookups.put("texture1DProjLod", "textureProjLod");
        lookups.put("texture2DProjLod", "textureProjLod");
        lookups.put("texture3DProjLod", "textureProjLod");
        LEGACY_TEXTURE_LOOKUPS = Collections.unmodifiableMap(lookups);
    }

    // An integer sampler type followed by whatever it declares, up to ; or ); deliberately NOT anchored to `uniform`, since a helper like `uint readVoxel(usampler3D s, vec3 p)` declares the sampler as a PARAMETER and that is the call site the driver rejects
    private static final Pattern INTEGER_SAMPLER_DECLARATION =
            Pattern.compile("\\b[ui]sampler[A-Za-z0-9]*[ \\t]+([^;)\\n]+)");

    // Points legacy texture2D-family lookups at their core equivalents ONLY where this source declares the sampler as usampler*/isampler*: no texture2D(usampler2D, vec2) overload exists, NVIDIA resolves it anyway but Mesa rejects it, so Complementary Unbound's terrain programs failed on Mesa with coloured lighting on and leaves stopped waving; restricted to integer samplers since the legacy names stay live here, keyed off the DECLARATION, and never shifts a line number
    public static String rewriteIntegerSamplerLookups(String name, String source) {
        if (source == null || !source.contains("sampler")) {
            return source;
        }
        Set<String> samplers = integerSamplerNames(source);
        if (samplers.isEmpty()) {
            return source;
        }

        StringBuilder alternation = new StringBuilder();
        for (String sampler : samplers) {
            if (alternation.length() > 0) {
                alternation.append('|');
            }
            alternation.append(Pattern.quote(sampler));
        }

        String result = source;
        for (Map.Entry<String, String> lookup : LEGACY_TEXTURE_LOOKUPS.entrySet()) {
            if (!result.contains(lookup.getKey())) {
                continue;
            }
            // The trailing (?=[,)]) is the correctness guard: the sampler (optionally with one flat subscript) must be the entire first argument, so texture2D(pick(voxel_sampler), uv) is left for the driver
            Matcher call = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(lookup.getKey())
                    + "[ \\t]*\\([ \\t]*(" + alternation + ")(\\[[^\\[\\]]*\\])?[ \\t]*(?=[,)])").matcher(result);
            StringBuffer rewrite = new StringBuffer();
            while (call.find()) {
                String subscript = call.group(2) == null ? "" : call.group(2);
                call.appendReplacement(rewrite,
                        Matcher.quoteReplacement(lookup.getValue() + "(" + call.group(1) + subscript));
            }
            call.appendTail(rewrite);
            result = rewrite.toString();
        }

        return result;
    }

    // Every identifier this source declares as a usampler* or isampler*, whether as a uniform or a function parameter
    private static Set<String> integerSamplerNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher declaration = INTEGER_SAMPLER_DECLARATION.matcher(source);
        while (declaration.find()) {
            String[] declarators = declaration.group(1).split(",");
            for (int i = 0; i < declarators.length; i++) {
                String declarator = declarators[i].trim();
                String identifier = identifierPrefix(declarator);
                if (identifier.isEmpty()) {
                    break;
                }
                // Only the first declarator surely belongs to the sampler type: a comma continues `usampler2D a, b;` but starts a new parameter in `usampler3D s, vec3 p`, so stop at the first non-bare one
                if (i > 0 && !isBareDeclarator(declarator, identifier)) {
                    break;
                }
                names.add(identifier);
            }
        }
        return names;
    }

    // Whether the declarator is just that identifier, optionally with an array subscript; anything more means the match caught something other than a plain declaration
    private static boolean isBareDeclarator(String declarator, String identifier) {
        String remainder = declarator.substring(identifier.length()).trim();
        return remainder.isEmpty() || (remainder.startsWith("[") && remainder.endsWith("]"));
    }

    // Strips an array subscript ("shadowVoxels[2]" gives "shadowVoxels"); anything not starting with an identifier yields "", which the caller treats as no match
    private static String identifierPrefix(String declarator) {
        int end = 0;
        while (end < declarator.length()
                && (Character.isLetterOrDigit(declarator.charAt(end)) || declarator.charAt(end) == '_')) {
            end++;
        }
        return declarator.substring(0, end);
    }

    // The index of the first line carrying a real GLSL token, or -1 for pure directives; where a hoisted #extension must land before, skipping blanks, comments, # directives and block comments
    private static int indexOfFirstNonPreprocessorLine(String[] lines) {
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (inBlockComment) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                line = line.substring(end + 2).trim();
                inBlockComment = false;
            }
            while (line.contains("/*")) {
                int start = line.indexOf("/*");
                int end = line.indexOf("*/", start + 2);
                if (end < 0) {
                    inBlockComment = true;
                    line = line.substring(0, start).trim();
                    break;
                }
                line = (line.substring(0, start) + line.substring(end + 2)).trim();
            }
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
