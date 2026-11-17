package com.bdmajora.impetus.umbra.shaderpack.preprocessor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Preprocessor of pack sources.
public final class GlslPreprocessor {

    // Pattern of the version line.
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\s*#version\\s+(\\d+)(?:\\s+(\\w+))?.*\\r?$");

    // Version used without a version line.
    public static final int DEFAULT_VERSION = 120;

    // Prevents instantiation
    private GlslPreprocessor() {
    }

    // Adds define lines after the version line.
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

    // Returns the define lines of a map.
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

    // Removes inactive preprocessor branches.
    public static String resolveConditionals(String source, Map<String, String> defines) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        try {
            String resolved = PropertiesPreprocessor.preprocess(source, defines);
            return resolved.trim().isEmpty() ? source : resolved;
        } catch (RuntimeException e) {
            return source;
        }
    }

    // Pattern of a float literal.
    private static final Pattern FLOAT_LITERAL =
            Pattern.compile("(?<![A-Za-z0-9_.])(?:\\d+\\.\\d*|\\.\\d+|\\d+[eE][-+]?\\d+)");
    // Pattern of a defined operator.
    private static final Pattern DEFINED_OPERATOR =
            Pattern.compile("\\bdefined\\s*(?:\\(\\s*\\w+\\s*\\)|\\s+\\w+)");
    // Pattern of an identifier.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
    // Pattern of a conditional directive.
    private static final Pattern CONDITIONAL_DIRECTIVE =
            Pattern.compile("(?m)^([\\t ]*#[\\t ]*)(if|elif)\\b([^\\n]*)$");

    // Evaluates conditionals that compare float macros.
    public static String foldFloatConditionals(String source, Map<String, String> defines) {
        if (source == null || source.indexOf('#') < 0) {
            return source;
        }
        Map<String, String> active = new LinkedHashMap<>(defines);
        StringBuilder out = new StringBuilder(source.length());
        Deque<boolean[]> stack = new ArrayDeque<>();
        String[] lines = source.split("\n", -1);
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean commented = inBlockComment;
            inBlockComment = advanceBlockComment(line, inBlockComment);
            out.append(commented ? line : foldDirective(line, active, stack));
            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    // Tracks block comments on a line and returns whether one is open.
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

    // Returns a directive with float comparisons folded.
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
        String directive = stripComment(trimmed.substring(1)).trim();
        // Reads the directive keyword.
        String keyword = PropertiesPreprocessor.directiveKeyword(directive);
        String argument = directive.substring(keyword.length()).trim();
        switch (keyword) {
            case "ifdef", "ifndef" -> {
                boolean taken = allActive(stack) && (keyword.equals("ifdef") == defines.containsKey(argument));
                stack.push(new boolean[]{taken, taken});
            }
            case "else" -> {
                boolean[] frame = stack.poll();
                boolean alreadyTaken = frame != null && frame[1];
                stack.push(new boolean[]{!alreadyTaken && allActive(stack), true});
            }
            case "endif" -> stack.poll();
            case "define" -> {
                if (allActive(stack) && !argument.isEmpty()) {
                    PropertiesPreprocessor.recordDefine("define " + argument, defines);
                }
            }
            case "undef" -> {
                if (allActive(stack)) {
                    defines.remove(argument);
                }
            }
            default -> {
            }
        }
        return line;
    }

    // Returns whether an expression continues on the next line.
    private static boolean isContinued(String expression) {
        String trimmed = expression.trim();
        return trimmed.endsWith("\\");
    }

    // Returns whether an expression cannot be parsed.
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

    // Returns whether every branch on the stack is active.
    private static boolean allActive(Deque<boolean[]> stack) {
        for (boolean[] frame : stack) {
            if (!frame[0]) {
                return false;
            }
        }
        return true;
    }

    // Returns text without its line comment.
    private static String stripComment(String text) {
        int line = text.indexOf("//");
        int block = text.indexOf("/*");
        int comment = line < 0 ? block : (block < 0 ? line : Math.min(line, block));
        return comment < 0 ? text : text.substring(0, comment);
    }

    // Returns whether an expression uses a float macro.
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

    // Extension pattern.
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^\\s*#\\s*extension\\s+.*\\r?$");

    // Applies the last fixes before a driver compile.
    public static String finalizeForDriver(String name, String source) {
        return rewriteIntegerSamplerLookups(name, hoistExtensionDirectives(source));
    }

    // Moves extension lines that follow code up to just after the version line.
    public static String hoistExtensionDirectives(String source) {
        if (source == null || !source.contains("#extension")) {
            return source;
        }

        String[] lines = source.split("\n", -1);
        int firstRealToken = indexOfFirstNonPreprocessorLine(lines);
        if (firstRealToken < 0) {
            return source;
        }

        boolean[] skipped = skippedLines(lines);
        boolean misplaced = false;
        for (int i = firstRealToken; i < lines.length; i++) {
            if (!skipped[i] && EXTENSION_PATTERN.matcher(lines[i]).matches()) {
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
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Moves an extension line that follows code and sits in a live branch.
            if (i >= firstRealToken && !skipped[i] && EXTENSION_PATTERN.matcher(line).matches()) {
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

    // Returns for each line whether it sits in a branch the driver certainly skips.
    static boolean[] skippedLines(String[] lines) {
        boolean[] skipped = new boolean[lines.length];
        Map<String, String> defines = new HashMap<>();
        // Macros defined or undefined in a branch that may or may not be taken
        Set<String> uncertain = new HashSet<>();
        // Stack of branch states and whether an earlier branch of the chain was taken.
        Deque<int[]> stack = new ArrayDeque<>();
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            boolean commented = inBlockComment;
            inBlockComment = advanceBlockComment(lines[i], inBlockComment);
            int state = branchState(stack);
            skipped[i] = state == 0;
            String trimmed = lines[i].trim();
            if (commented || !trimmed.startsWith("#")) {
                continue;
            }
            String directive = stripComment(trimmed.substring(1)).trim();
            String keyword = PropertiesPreprocessor.directiveKeyword(directive);
            String argument = directive.substring(keyword.length()).trim();
            switch (keyword) {
                case "if" -> pushBranch(stack, 0, evaluate(argument, defines, uncertain));
                case "ifdef", "ifndef" -> {
                    int defined = isDefined(argument, defines, uncertain);
                    pushBranch(stack, 0, keyword.equals("ifdef") || defined < 0 ? defined : 1 - defined);
                }
                case "elif" -> pushBranch(stack, earlierTaken(stack.poll()), evaluate(argument, defines, uncertain));
                case "else" -> pushBranch(stack, earlierTaken(stack.poll()), 1);
                case "endif" -> stack.poll();
                case "define", "undef" -> {
                    String name = macroName(argument);
                    if (state == 0 || name.isEmpty()) {
                        break;
                    }
                    if (state < 0) {
                        uncertain.add(name);
                    } else {
                        uncertain.remove(name);
                        if (keyword.equals("define")) {
                            PropertiesPreprocessor.recordDefine("define " + argument, defines);
                        } else {
                            defines.remove(name);
                        }
                    }
                }
                default -> {
                }
            }
        }
        return skipped;
    }

    // Returns the macro name at the start of a define or undef argument
    private static String macroName(String argument) {
        Matcher name = IDENTIFIER.matcher(argument);
        return name.lookingAt() ? name.group() : "";
    }

    // Returns 1 or 0 or -1 for a macro that is defined or absent or unknown.
    private static int isDefined(String name, Map<String, String> defines, Set<String> uncertain) {
        if (uncertain.contains(name) || (!defines.containsKey(name) && isDriverMacro(name))) {
            return -1;
        }
        return defines.containsKey(name) ? 1 : 0;
    }

    // Returns whether the driver may define the macro itself.
    private static boolean isDriverMacro(String name) {
        return name.startsWith("GL_") || name.startsWith("__");
    }

    // Returns 1 or 0 or -1 for a condition that is true or false or unknown.
    private static int evaluate(String expression, Map<String, String> defines, Set<String> uncertain) {
        Matcher identifiers = IDENTIFIER.matcher(expression);
        while (identifiers.find()) {
            String name = identifiers.group();
            if (uncertain.contains(name) || (!defines.containsKey(name) && isDriverMacro(name))) {
                return -1;
            }
        }
        Boolean value = PropertiesPreprocessor.tryEvaluateBooleanExpression(expression, defines).orElse(null);
        return value == null ? -1 : (value ? 1 : 0);
    }

    // Pushes the next branch of a chain.
    private static void pushBranch(Deque<int[]> stack, int earlier, int value) {
        int state = earlier == 1 ? 0 : (earlier == 0 ? value : (value == 0 ? 0 : -1));
        stack.push(new int[]{state, either(earlier, state)});
    }

    // Returns whether any branch before the next one of a chain was taken
    private static int earlierTaken(int[] frame) {
        return frame == null ? 0 : either(frame[0], frame[1]);
    }

    // Returns the tri-state or of two branch states
    private static int either(int a, int b) {
        if (a == 1 || b == 1) {
            return 1;
        }
        return a == -1 || b == -1 ? -1 : 0;
    }

    // Returns 0 when an enclosing branch is skipped or -1 when one is unknown and 1 otherwise.
    private static int branchState(Deque<int[]> stack) {
        int state = 1;
        for (int[] frame : stack) {
            if (frame[0] == 0) {
                return 0;
            }
            if (frame[0] == -1) {
                state = -1;
            }
        }
        return state;
    }

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

    // Pattern of an integer sampler declaration.
    private static final Pattern INTEGER_SAMPLER_DECLARATION =
            Pattern.compile("\\b[ui]sampler[A-Za-z0-9]*[ \\t]+([^;)\\n]+)");

    // Rewrites lookups on integer samplers to return floats.
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

    // Returns the names of integer samplers.
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
                if (i > 0 && !isBareDeclarator(declarator, identifier)) {
                    break;
                }
                names.add(identifier);
            }
        }
        return names;
    }

    // Returns whether a declarator is a plain name.
    private static boolean isBareDeclarator(String declarator, String identifier) {
        String remainder = declarator.substring(identifier.length()).trim();
        return remainder.isEmpty() || (remainder.startsWith("[") && remainder.endsWith("]"));
    }

    // Returns the leading identifier of a declarator.
    private static String identifierPrefix(String declarator) {
        int end = 0;
        while (end < declarator.length()
                && (Character.isLetterOrDigit(declarator.charAt(end)) || declarator.charAt(end) == '_')) {
            end++;
        }
        return declarator.substring(0, end);
    }

    // Returns the index of the first line that is not a directive.
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
