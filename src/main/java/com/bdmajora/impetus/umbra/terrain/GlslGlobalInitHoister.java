package com.bdmajora.impetus.umbra.terrain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Moves global initialisers that read non constant values into an assignment block for main.
public final class GlslGlobalInitHoister {
    // Pattern of a global declaration with an initialiser.
    private static final Pattern GLOBAL_INIT_START = Pattern.compile(
            "^(\\s*)(float|int|bool|vec[234]|ivec[234]|mat[234])\\s+(\\w+)\\s*=\\s*(.*)$");
    // Pattern of whitespace and an optional line comment after a semicolon.
    private static final Pattern TRAILING_AFTER_TERMINATOR = Pattern.compile("\\s*(?://.*)?");
    // Pattern of an identifier.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    // Pattern of the identifiers allowed in a constant initialiser.
    private static final Pattern CONSTANT_CALLEES = Pattern.compile(
            "vec[234]|ivec[234]|mat[234]|float|int|bool|true|false");

    // Rewritten source and the assignments to run at the start of main.
    public static final class Result {
        // Source with the initialisers removed.
        public final String body;
        // Assignments wrapped in the global conditional directives or empty.
        public final String hoistedAssignments;

        // Creates a result.
        Result(String body, String hoistedAssignments) {
            this.body = body;
            this.hoistedAssignments = hoistedAssignments;
        }
    }

    // Prevents instantiation
    private GlslGlobalInitHoister() {
    }

    // Splits non constant global initialisers out of a source and keeps its line count.
    public static Result hoist(String source) {
        StringBuilder body = new StringBuilder(source.length());
        StringBuilder hoisted = new StringBuilder();
        int depth = 0;
        boolean hasHoistedAssignments = false;

        String[] lines = source.split("\n", -1);
        for (int idx = 0; idx < lines.length; idx++) {
            String line = lines[idx];
            String trimmed = line.trim();
            if (depth == 0 && isConditionalDirective(trimmed)) {
                hoisted.append(line).append('\n');
            }

            if (depth == 0 && isDeclarationCandidate(trimmed)) {
                Matcher starter = GLOBAL_INIT_START.matcher(line);
                if (starter.matches()) {
                    Initializer init = collectInitializer(lines, idx, starter.group(4));
                    if (init != null && !isConstantExpression(init.expression)) {
                        body.append(starter.group(1)).append(starter.group(2)).append(' ')
                                .append(starter.group(3)).append(";\n");
                        for (int k = idx + 1; k <= init.endLine; k++) {
                            body.append('\n');
                        }
                        hoisted.append("    ").append(starter.group(3)).append(" = ")
                                .append(init.expression).append(";\n");
                        hasHoistedAssignments = true;
                        for (int k = idx; k <= init.endLine; k++) {
                            depth += braceDelta(lines[k]);
                        }
                        idx = init.endLine;
                        continue;
                    }
                }
            }

            body.append(line).append('\n');
            depth += braceDelta(line);
        }
        return new Result(body.toString(), hasHoistedAssignments ? hoisted.toString() : "");
    }

    // Initialiser text and the line that ends it.
    private static final class Initializer {
        // Initialiser text without the semicolon.
        final String expression;
        // Index of the line with the semicolon.
        final int endLine;

        // Creates an initialiser.
        Initializer(String expression, int endLine) {
            this.expression = expression;
            this.endLine = endLine;
        }
    }

    // Reads an initialiser up to its semicolon or returns null when code follows it or it never ends.
    private static Initializer collectInitializer(String[] lines, int startLine, String firstRemainder) {
        StringBuilder expression = new StringBuilder();
        int nesting = 0;
        String remainder = firstRemainder;
        for (int lineIndex = startLine; lineIndex < lines.length; lineIndex++) {
            for (int i = 0; i < remainder.length(); i++) {
                char c = remainder.charAt(i);
                if (c == '(' || c == '[' || c == '{') {
                    nesting++;
                } else if (c == ')' || c == ']' || c == '}') {
                    nesting--;
                } else if (c == ';' && nesting == 0) {
                    if (!TRAILING_AFTER_TERMINATOR.matcher(remainder.substring(i + 1)).matches()) {
                        return null;
                    }
                    return new Initializer(expression.toString().trim(), lineIndex);
                }
                expression.append(c);
            }
            expression.append('\n');
            if (lineIndex + 1 < lines.length) {
                remainder = lines[lineIndex + 1];
            }
        }
        return null;
    }

    // Returns whether a line can start a global variable declaration.
    private static boolean isDeclarationCandidate(String trimmed) {
        return !trimmed.isEmpty()
                && !trimmed.startsWith("const")
                && !trimmed.startsWith("#")
                && !trimmed.startsWith("//")
                && !trimmed.startsWith("uniform")
                && !trimmed.startsWith("varying")
                && !trimmed.startsWith("attribute")
                && !trimmed.startsWith("in ")
                && !trimmed.startsWith("out ");
    }

    // Returns the opened braces minus the closed braces of a line.
    private static int braceDelta(String line) {
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                delta++;
            } else if (c == '}') {
                delta--;
            }
        }
        return delta;
    }

    // Returns whether a line is a conditional preprocessor directive.
    private static boolean isConditionalDirective(String trimmed) {
        if (!trimmed.startsWith("#")) {
            return false;
        }
        String directive = trimmed.substring(1).trim();
        return startsDirective(directive, "if")
                || startsDirective(directive, "ifdef")
                || startsDirective(directive, "ifndef")
                || startsDirective(directive, "elif")
                || startsDirective(directive, "else")
                || startsDirective(directive, "endif");
    }

    // Returns whether a directive is a keyword followed by nothing or a space or a bracket or a comment.
    private static boolean startsDirective(String directive, String keyword) {
        if (!directive.startsWith(keyword)) {
            return false;
        }
        if (directive.length() == keyword.length()) {
            return true;
        }
        char next = directive.charAt(keyword.length());
        return next == '(' || next == '/' || Character.isWhitespace(next);
    }

    // Returns whether an expression uses only literals and constructors.
    private static boolean isConstantExpression(String expression) {
        Matcher identifiers = IDENTIFIER.matcher(expression);
        while (identifiers.find()) {
            int start = identifiers.start();
            // Suffix or exponent of a number literal
            if (start > 0 && isNumberChar(expression.charAt(start - 1))) {
                continue;
            }
            if (!CONSTANT_CALLEES.matcher(identifiers.group()).matches()) {
                return false;
            }
        }
        return true;
    }

    // Returns whether a character belongs to a number literal.
    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }
}
