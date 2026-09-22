package com.bdmajora.impetus.umbra.shaderpack.preprocessor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Evaluates the C-preprocessor conditionals OptiFine allows in *.properties files (#if/#ifdef/#ifndef/#elif/#else/#endif and defined()), written from scratch since they only use integer comparisons; other # lines drop as comments, backslash continuations are joined first
public final class PropertiesPreprocessor {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private PropertiesPreprocessor() {
    }

    // Evaluates #if gates in a properties file against the option defines
    public static String preprocess(String source, Map<String, String> defines) {
        return preprocess(source, defines, false);
    }

    // preprocess() plus NUMERIC macro substitution into directive VALUES, since Iris's JCPP expands macros in passed-through text and Complementary sizes `image.wsr_img` by COLORED_LIGHTING (verbatim, parse threw and wsr_sampler stayed unbound all session); numeric-only keeps blend.* factors like SRC_ALPHA safe, and the menu directives read from the ORIGINAL anyway
    public static String preprocessProperties(String source, Map<String, String> defines) {
        return preprocess(source, defines, true);
    }

    // The pass itself; expandValues also substitutes defines into values
    private static String preprocess(String source, Map<String, String> defines, boolean expandValues) {
        List<String> logicalLines = joinContinuations(source);
        StringBuilder out = new StringBuilder(source.length());
        // Track #define/#undef in ACTIVE regions so cascading defines feed later #if evaluation, required when this runs over shader sources to pick the ACTIVE DRAWBUFFERS/RENDERTARGETS variant like Umbra's preprocessed-source extraction
        defines = new HashMap<>(defines);

        // Each conditional nesting level: [0] = this branch active, [1] = any branch so far taken.
        Deque<boolean[]> stack = new ArrayDeque<>();

        for (String line : logicalLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String directive = stripDirectiveComments(trimmed.substring(1)).trim();
                String keyword = directiveKeyword(directive);
                String argument = directive.substring(keyword.length()).trim();

                switch (keyword) {
                    case "if" -> {
                        boolean value = parentActive(stack) && evaluate(argument, defines);
                        stack.push(new boolean[]{value, value});
                    }
                    case "ifdef" -> {
                        boolean value = parentActive(stack) && defines.containsKey(argument);
                        stack.push(new boolean[]{value, value});
                    }
                    case "ifndef" -> {
                        boolean value = parentActive(stack) && !defines.containsKey(argument);
                        stack.push(new boolean[]{value, value});
                    }
                    case "elif" -> {
                        boolean[] frame = stack.poll();
                        if (frame == null) {
                            LOGGER.warn("[Umbra] #elif without #if in properties file; ignoring");
                            continue;
                        }
                        boolean value = !frame[1] && parentActive(stack) && evaluate(argument, defines);
                        stack.push(new boolean[]{value, frame[1] || value});
                    }
                    case "else" -> {
                        boolean[] frame = stack.poll();
                        if (frame == null) {
                            LOGGER.warn("[Umbra] #else without #if in properties file; ignoring");
                            continue;
                        }
                        boolean value = !frame[1] && parentActive(stack);
                        stack.push(new boolean[]{value, true});
                    }
                    case "define" -> {
                        if (currentActive(stack)) {
                            recordDefine(directive, defines);
                        }
                    }
                    case "undef" -> {
                        if (currentActive(stack)) {
                            defines.remove(argument);
                        }
                    }
                    case "endif" -> {
                        if (stack.isEmpty()) {
                            LOGGER.warn("[Umbra] #endif without #if in properties file; ignoring");
                        } else {
                            stack.pop();
                        }
                    }
                    // Any other #-line is a properties comment; dropped either way
                    default -> { }
                }
                continue;
            }
            if (currentActive(stack)) {
                out.append(expandValues ? expandNumericMacrosInValue(line, defines) : line).append('\n');
            }
        }
        if (!stack.isEmpty()) {
            LOGGER.warn("[Umbra] Unterminated #if in properties file ({} level(s) open at EOF)", stack.size());
        }
        return out.toString();
    }

    // An identifier NOT glued to a preceding word character or dot, so a dotted key like image.wsr_img is never matched segment by segment
    private static final Pattern PROPERTY_IDENTIFIER =
            Pattern.compile("(?<![A-Za-z0-9_.])([A-Za-z_][A-Za-z0-9_]*)(?![A-Za-z0-9_])");

    // How far a `#define A B` then `#define B 256` chain is followed; a bound rather than cycle detection, since a self-referential define would loop forever
    private static final int MAX_DEFINE_HOPS = 8;

    // The option-menu layout directives, whose values are option NAMES; ShaderProperties reads them from the original anyway, so skipping only keeps a misleading `sliders = 256 ...` out of the preprocessed text
    private static boolean isMenuLayoutKey(String key) {
        String trimmed = key.trim();
        return trimmed.equals("sliders") || trimmed.equals("screen")
                || trimmed.startsWith("screen.") || trimmed.startsWith("profile.");
    }

    // Substitutes numerically-valued macros into the part AFTER the first =; keys are dotted paths (image.wsr_img, program.composite1.enabled) whose segments would otherwise be candidates
    private static String expandNumericMacrosInValue(String line, Map<String, String> defines) {
        int separator = line.indexOf('=');
        if (separator < 0 || defines.isEmpty() || isMenuLayoutKey(line.substring(0, separator))) {
            return line;
        }
        String value = line.substring(separator + 1);
        Matcher identifier = PROPERTY_IDENTIFIER.matcher(value);
        StringBuffer expanded = new StringBuffer(value.length());
        boolean substituted = false;
        while (identifier.find()) {
            String resolved = resolveNumericDefine(identifier.group(1), defines);
            if (resolved == null) {
                identifier.appendReplacement(expanded, Matcher.quoteReplacement(identifier.group()));
                continue;
            }
            identifier.appendReplacement(expanded, Matcher.quoteReplacement(resolved));
            substituted = true;
        }
        identifier.appendTail(expanded);
        return substituted ? line.substring(0, separator + 1) + expanded : line;
    }

    // The numeric text this name ultimately expands to following a define chain, or null when undefined, empty or non-numeric, which is what keeps identifier-valued macros safe
    private static String resolveNumericDefine(String name, Map<String, String> defines) {
        String current = name;
        for (int hop = 0; hop < MAX_DEFINE_HOPS; hop++) {
            String value = defines.get(current);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                // A bare `#define FOO` is a flag, not a value; JCPP expands it to nothing, which would corrupt a positional directive more quietly than leaving the name
                return null;
            }
            if (isNumeric(trimmed)) {
                return trimmed;
            }
            current = trimmed;
        }
        return null;
    }

    // Integer or float literal
    private static boolean isNumeric(String text) {
        try {
            Long.decode(text);
            return true;
        } catch (NumberFormatException notAnInteger) {
            try {
                Double.parseDouble(text);
                return true;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }
    }

    // JCPP strips comments before evaluating directives, and this must match: packs write `#if OPTION // label`, and feeding the comment into the parser makes a valid pack look unterminated
    private static String stripDirectiveComments(String directive) {
        StringBuilder out = new StringBuilder(directive.length());
        for (int i = 0; i < directive.length(); i++) {
            char c = directive.charAt(i);
            if (c == '/' && i + 1 < directive.length()) {
                char next = directive.charAt(i + 1);
                if (next == '/') {
                    break;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < directive.length()
                            && !(directive.charAt(i) == '*' && directive.charAt(i + 1) == '/')) {
                        i++;
                    }
                    if (i + 1 < directive.length()) {
                        i++;
                    }
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    // Records a "#define NAME [value]" directive's body; a function-like macro is tracked for presence only (defined() checks), its value being unusable in #if
    static void recordDefine(String directive, Map<String, String> defines) {
        String body = directive.substring("define ".length()).trim();
        int space = body.indexOf(' ');
        int paren = body.indexOf('(');
        if (paren >= 0 && (space < 0 || paren < space)) {
            defines.put(body.substring(0, paren), "");
        } else if (space < 0) {
            defines.put(body, "");
        } else {
            defines.put(body.substring(0, space), body.substring(space + 1).trim());
        }
    }

    // The directive's leading keyword (`if`, `ifdef`, `endif`, ...), so `#if(` and `#if ` dispatch alike
    private static String directiveKeyword(String directive) {
        int end = 0;
        while (end < directive.length() && Character.isLetter(directive.charAt(end))) {
            end++;
        }
        return directive.substring(0, end);
    }

    // Whether the innermost branch AND every enclosing one is active, i.e. whether this line is emitted
    private static boolean currentActive(Deque<boolean[]> stack) {
        return parentActive(stack) && (stack.isEmpty() || stack.peek()[0]);
    }

    // Whether every enclosing conditional level is active; a nested #if inside a false branch stays false regardless of its own condition
    private static boolean parentActive(Deque<boolean[]> stack) {
        for (boolean[] frame : stack) {
            if (!frame[0]) {
                return false;
            }
        }
        return true;
    }

    // Splits into lines, joining backslash-continued ones like java.util.Properties, so a directive or value split across lines is seen as one
    private static List<String> joinContinuations(String source) {
        String[] rawLines = source.split("\r\n|\r|\n", -1);
        List<String> lines = new ArrayList<>(rawLines.length);
        int i = 0;
        while (i < rawLines.length) {
            String line = rawLines[i++];
            while (endsWithOddBackslashes(line) && i < rawLines.length) {
                line = line.substring(0, line.length() - 1) + " " + rawLines[i++].trim();
            }
            if (endsWithOddBackslashes(line)) {
                line = line.substring(0, line.length() - 1);
            }
            lines.add(line);
        }
        return lines;
    }

    // A continuation line; an even count is an escaped backslash
    private static boolean endsWithOddBackslashes(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return (count & 1) == 1;
    }

    // ------------------------------------------------------------------ expression evaluation

    private static boolean evaluate(String expression, Map<String, String> defines) {
        return evaluateBooleanExpression(expression, defines).orElse(false);
    }

    // Parses and evaluates a preprocessor condition; empty on a syntax error
    public static Optional<Boolean> evaluateBooleanExpression(String expression, Map<String, String> defines) {
        try {
            return Optional.of(new ExpressionParser(expression, defines).parse() != 0);
        } catch (RuntimeException e) {
            LOGGER.warn("[Umbra] Failed to evaluate properties conditional \"#if{}\": {}", expression, e.getMessage());
            return Optional.empty();
        }
    }

    // The SILENT sibling of evaluateBooleanExpression for callers where "cannot evaluate" is normal, chiefly foldFloatConditionals; Photon alone has 20 backslash-continued conditionals, and warning on each compile would drown the log
    public static Optional<Boolean> tryEvaluateBooleanExpression(String expression, Map<String, String> defines) {
        try {
            return Optional.of(new ExpressionParser(expression, defines).parse() != 0);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // A minimal recursive-descent parser over the C-preprocessor integer expression grammar, enough for properties-file conditionals and nothing more
    private static final class ExpressionParser {
        private final String text;
        private final Map<String, String> defines;
        private final int defineDepth;
        private int pos;

        ExpressionParser(String text, Map<String, String> defines) {
            this(text, defines, 0);
        }

        private ExpressionParser(String text, Map<String, String> defines, int defineDepth) {
            this.text = text;
            this.defines = defines;
            this.defineDepth = defineDepth;
        }

        long parse() {
            long value = parseOr();
            skipWhitespace();
            if (this.pos < this.text.length()) {
                throw new IllegalArgumentException("Trailing input at position " + this.pos);
            }
            return value;
        }

        // ||
        private long parseOr() {
            long left = parseAnd();
            while (eat("||")) {
                long right = parseAnd();
                left = (left != 0 || right != 0) ? 1 : 0;
            }
            return left;
        }

        // &&
        private long parseAnd() {
            long left = parseEquality();
            while (eat("&&")) {
                long right = parseEquality();
                left = (left != 0 && right != 0) ? 1 : 0;
            }
            return left;
        }

        // == and !=
        private long parseEquality() {
            long left = parseComparison();
            while (true) {
                if (eat("==")) {
                    left = left == parseComparison() ? 1 : 0;
                } else if (eat("!=")) {
                    left = left != parseComparison() ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        // <, <=, >, >=
        private long parseComparison() {
            long left = parseAdditive();
            while (true) {
                if (eat("<=")) {
                    left = left <= parseAdditive() ? 1 : 0;
                } else if (eat(">=")) {
                    left = left >= parseAdditive() ? 1 : 0;
                } else if (eat("<")) {
                    left = left < parseAdditive() ? 1 : 0;
                } else if (eat(">")) {
                    left = left > parseAdditive() ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        // + and -
        private long parseAdditive() {
            long left = parseMultiplicative();
            while (true) {
                if (eat("+")) {
                    left += parseMultiplicative();
                } else if (eatMinus()) {
                    left -= parseMultiplicative();
                } else {
                    return left;
                }
            }
        }

        // *, / and %
        private long parseMultiplicative() {
            long left = parseUnary();
            while (true) {
                if (eat("*")) {
                    left *= parseUnary();
                } else if (eat("/")) {
                    long right = parseUnary();
                    left = right == 0 ? 0 : left / right;
                } else if (eat("%")) {
                    long right = parseUnary();
                    left = right == 0 ? 0 : left % right;
                } else {
                    return left;
                }
            }
        }

        // ! and unary -
        private long parseUnary() {
            skipWhitespace();
            if (eatChar('!')) {
                return parseUnary() == 0 ? 1 : 0;
            }
            if (eatChar('-')) {
                return -parseUnary();
            }
            if (eatChar('+')) {
                return parseUnary();
            }
            return parsePrimary();
        }

        // Literals, defined(), parenthesised expressions and identifiers
        private long parsePrimary() {
            skipWhitespace();
            if (eatChar('(')) {
                long value = parseOr();
                skipWhitespace();
                if (!eatChar(')')) {
                    throw new IllegalArgumentException("Missing ')'");
                }
                return value;
            }
            if (this.pos >= this.text.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            char c = this.text.charAt(this.pos);
            if (Character.isDigit(c) || (c == '.' && this.pos + 1 < this.text.length()
                    && Character.isDigit(this.text.charAt(this.pos + 1)))) {
                return parseNumber();
            }
            if (Character.isLetter(c) || c == '_') {
                String name = scanIdentifier();
                if (name.equals("defined")) {
                    skipWhitespace();
                    boolean parens = eatChar('(');
                    skipWhitespace();
                    String ident = scanIdentifier();
                    if (parens) {
                        skipWhitespace();
                        if (!eatChar(')')) {
                            throw new IllegalArgumentException("Missing ')' after defined(" + ident);
                        }
                    }
                    return this.defines.containsKey(ident) ? 1 : 0;
                }
                return resolveDefine(name);
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "'");
        }

        // Consumes the identifier characters at the cursor and returns them, possibly empty
        private String scanIdentifier() {
            int start = this.pos;
            while (this.pos < this.text.length()
                    && (Character.isLetterOrDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '_')) {
                this.pos++;
            }
            return this.text.substring(start, this.pos);
        }

        // Scans one numeric literal; JCPP accepts a float literal and truncates toward zero, and packs rely on it (Clarity's `#if MOTION_BLUR > 0.0` only activates at exactly 1.00 under Iris), so rejecting floats failed the conditional and fell back to raw source. Integer suffixes (u, l) are consumed and ignored like JCPP
        private long parseNumber() {
            int start = this.pos;
            boolean hex = this.text.startsWith("0x", this.pos) || this.text.startsWith("0X", this.pos);
            if (hex) {
                this.pos += 2;
            }
            boolean floating = false;
            while (this.pos < this.text.length()) {
                char c = this.text.charAt(this.pos);
                if (Character.isLetterOrDigit(c)) {
                    // A decimal exponent may carry a sign; in hex, 'e' is just a digit.
                    if (!hex && (c == 'e' || c == 'E') && this.pos + 1 < this.text.length()
                            && (this.text.charAt(this.pos + 1) == '+' || this.text.charAt(this.pos + 1) == '-')) {
                        floating = true;
                        this.pos += 2;
                        continue;
                    }
                    this.pos++;
                } else if (c == '.' && !hex) {
                    floating = true;
                    this.pos++;
                } else {
                    break;
                }
            }
            String number = this.text.substring(start, this.pos);
            if (!hex && (floating || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)) {
                try {
                    return (long) Double.parseDouble(number.replaceAll("[fF]$", ""));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad number: " + number);
                }
            }
            try {
                return Long.decode(number.replaceAll("[uUlL]+$", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad number: " + number);
            }
        }

        // An identifier's value; undefined identifiers evaluate to zero as in C
        private long resolveDefine(String name) {
            String value = this.defines.get(name);
            if (value == null || value.isEmpty() || this.defineDepth >= 8) {
                return 0;
            }
            String trimmed = value.trim();
            try {
                return Long.decode(trimmed);
            } catch (NumberFormatException ignored) {
                try {
                    return new ExpressionParser(trimmed, this.defines, this.defineDepth + 1).parse();
                } catch (IllegalArgumentException e) {
                    return 0;
                }
            }
        }

        // Consumes the operator if present, guarding < and > against matching the first character of <= and >=
        private boolean eat(String op) {
            skipWhitespace();
            if (!this.text.startsWith(op, this.pos)) {
                return false;
            }
            if ((op.equals("<") || op.equals(">"))
                    && this.pos + 1 < this.text.length() && this.text.charAt(this.pos + 1) == '=') {
                return false;
            }
            this.pos += op.length();
            return true;
        }

        // Binary minus needs separating from a unary minus after another operator; at the one call site the parser is past an operand, so it never is
        private boolean eatMinus() {
            skipWhitespace();
            if (this.pos < this.text.length() && this.text.charAt(this.pos) == '-') {
                this.pos++;
                return true;
            }
            return false;
        }

        // Consumes the character if it is next
        private boolean eatChar(char c) {
            skipWhitespace();
            if (this.pos < this.text.length() && this.text.charAt(this.pos) == c) {
                this.pos++;
                return true;
            }
            return false;
        }

        // Advances past spaces and tabs
        private void skipWhitespace() {
            while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
                this.pos++;
            }
        }
    }
}
