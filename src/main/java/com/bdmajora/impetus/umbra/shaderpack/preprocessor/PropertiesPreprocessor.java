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

// C style preprocessor of shader pack properties files with numeric macro expansion.
public final class PropertiesPreprocessor {
    // Logger of unbalanced directives.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Prevents instantiation
    private PropertiesPreprocessor() {
    }

    // Resolves the directives of a source.
    public static String preprocess(String source, Map<String, String> defines) {
        return preprocess(source, defines, false);
    }

    // Resolves the directives of a properties file and expands numeric macros in its values.
    public static String preprocessProperties(String source, Map<String, String> defines) {
        return preprocess(source, defines, true);
    }

    // Resolves conditionals and defines line by line and keeps the active lines.
    private static String preprocess(String source, Map<String, String> defines, boolean expandValues) {
        List<String> logicalLines = joinContinuations(source);
        StringBuilder out = new StringBuilder(source.length());
        defines = new HashMap<>(defines);

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

    // Pattern of an identifier that is not part of a dotted name.
    private static final Pattern PROPERTY_IDENTIFIER =
            Pattern.compile("(?<![A-Za-z0-9_.])([A-Za-z_][A-Za-z0-9_]*)(?![A-Za-z0-9_])");

    // Longest chain of defines that is followed.
    private static final int MAX_DEFINE_HOPS = 8;

    // Returns whether a key describes the option menu layout.
    private static boolean isMenuLayoutKey(String key) {
        String trimmed = key.trim();
        return trimmed.equals("sliders") || trimmed.equals("screen")
                || trimmed.startsWith("screen.") || trimmed.startsWith("profile.");
    }

    // Replaces identifiers in a value with the numbers their defines resolve to.
    private static String expandNumericMacrosInValue(String line, Map<String, String> defines) {
        int separator = line.indexOf('=');
        if (separator < 0 || defines.isEmpty() || isMenuLayoutKey(line.substring(0, separator))) {
            return line;
        }
        String value = line.substring(separator + 1);
        Matcher identifier = PROPERTY_IDENTIFIER.matcher(value);
        StringBuilder expanded = new StringBuilder(value.length());
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

    // Returns the number a define resolves to through a short chain or null.
    private static String resolveNumericDefine(String name, Map<String, String> defines) {
        String current = name;
        for (int hop = 0; hop < MAX_DEFINE_HOPS; hop++) {
            String value = defines.get(current);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (isNumeric(trimmed)) {
                return trimmed;
            }
            current = trimmed;
        }
        return null;
    }

    // Returns whether a text is a number literal.
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

    // Removes the comments of a directive.
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

    // Stores the name and value of a define directive.
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

    // Returns the keyword of a directive.
    static String directiveKeyword(String directive) {
        int end = 0;
        while (end < directive.length() && Character.isLetter(directive.charAt(end))) {
            end++;
        }
        return directive.substring(0, end);
    }

    // Returns whether the innermost branch is active.
    private static boolean currentActive(Deque<boolean[]> stack) {
        return parentActive(stack) && (stack.isEmpty() || stack.peek()[0]);
    }

    // Returns whether the branch around the innermost one is active.
    private static boolean parentActive(Deque<boolean[]> stack) {
        for (boolean[] frame : stack) {
            if (!frame[0]) {
                return false;
            }
        }
        return true;
    }

    // Returns the logical lines with backslash continuations joined.
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

    // Returns whether a line ends with an odd number of backslashes.
    private static boolean endsWithOddBackslashes(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return (count & 1) == 1;
    }

    // Evaluates a condition and reads a malformed one as false.
    private static boolean evaluate(String expression, Map<String, String> defines) {
        return evaluateBooleanExpression(expression, defines).orElse(false);
    }

    // Evaluates a condition.
    public static Optional<Boolean> evaluateBooleanExpression(String expression, Map<String, String> defines) {
        try {
            return Optional.of(new ExpressionParser(expression, defines).parse() != 0);
        } catch (RuntimeException e) {
            LOGGER.warn("[Umbra] Failed to evaluate properties conditional \"#if{}\": {}", expression, e.getMessage());
            return Optional.empty();
        }
    }

    // Evaluates a condition or returns empty for a malformed one.
    public static Optional<Boolean> tryEvaluateBooleanExpression(String expression, Map<String, String> defines) {
        try {
            return Optional.of(new ExpressionParser(expression, defines).parse() != 0);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // Recursive descent evaluator of preprocessor conditions.
    private static final class ExpressionParser {
        // Condition text.
        private final String text;
        // Defines in effect.
        private final Map<String, String> defines;
        // Depth of nested define expansion.
        private final int defineDepth;
        // Position of the next character.
        private int pos;
        // Current parenthesis nesting.
        private int nesting;
        // Deepest parenthesis nesting.
        private static final int MAX_NESTING = 200;

        // Creates an evaluator of a condition.
        ExpressionParser(String text, Map<String, String> defines) {
            this(text, defines, 0);
        }

        // Creates an evaluator at a define expansion depth.
        private ExpressionParser(String text, Map<String, String> defines, int defineDepth) {
            this.text = text;
            this.defines = defines;
            this.defineDepth = defineDepth;
        }

        // Evaluates the whole condition and rejects trailing input.
        long parse() {
            long value = parseOr();
            skipWhitespace();
            if (this.pos < this.text.length()) {
                throw new IllegalArgumentException("Trailing input at position " + this.pos);
            }
            return value;
        }

        // Evaluates a logical or chain.
        private long parseOr() {
            long left = parseAnd();
            while (eat("||")) {
                long right = parseAnd();
                left = (left != 0 || right != 0) ? 1 : 0;
            }
            return left;
        }

        // Evaluates a logical and chain.
        private long parseAnd() {
            long left = parseEquality();
            while (eat("&&")) {
                long right = parseEquality();
                left = (left != 0 && right != 0) ? 1 : 0;
            }
            return left;
        }

        // Evaluates an equality chain.
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

        // Evaluates an ordering chain.
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

        // Evaluates a sum or difference chain.
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

        // Evaluates a product or quotient or remainder chain.
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

        // Evaluates a negation or logical not.
        private long parseUnary() {
            if (++this.nesting > MAX_NESTING) {
                throw new IllegalArgumentException("Expression nests too deeply");
            }
            try {
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
            } finally {
                this.nesting--;
            }
        }

        // Evaluates a number or defined test or define or parenthesized condition.
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

        // Reads an identifier.
        private String scanIdentifier() {
            int start = this.pos;
            while (this.pos < this.text.length()
                    && (Character.isLetterOrDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '_')) {
                this.pos++;
            }
            return this.text.substring(start, this.pos);
        }

        // Reads a decimal or hexadecimal number.
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

        // Evaluates the value of a define within the expansion depth.
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

        // Consumes a text after whitespace and returns whether it matched.
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

        // Consumes a minus that is not part of a decrement.
        private boolean eatMinus() {
            skipWhitespace();
            if (this.pos < this.text.length() && this.text.charAt(this.pos) == '-') {
                this.pos++;
                return true;
            }
            return false;
        }

        // Consumes a character after whitespace and returns whether it matched.
        private boolean eatChar(char c) {
            skipWhitespace();
            if (this.pos < this.text.length() && this.text.charAt(this.pos) == c) {
                this.pos++;
                return true;
            }
            return false;
        }

        // Moves past whitespace.
        private void skipWhitespace() {
            while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
                this.pos++;
            }
        }
    }
}
