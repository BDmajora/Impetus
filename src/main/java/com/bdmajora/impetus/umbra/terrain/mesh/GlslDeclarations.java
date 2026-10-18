package com.bdmajora.impetus.umbra.terrain.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

// Finds the global-scope statements of a GLSL source and lets a caller rewrite the declarations among them, leaving comments, directives, function bodies and parameters alone; statements stay inside whatever #if branch they sat in, so the driver still picks the same one
public final class GlslDeclarations {
    // Storage qualifiers a rewrite can be keyed on
    static final Set<String> STORAGE = Set.of("in", "out", "varying", "attribute", "uniform", "buffer", "shared", "const");
    // Qualifiers that may sit around the storage qualifier
    static final Set<String> QUALIFIERS = Set.of("flat", "smooth", "noperspective", "centroid", "sample", "patch", "invariant",
            "precise", "highp", "mediump", "lowp", "coherent", "volatile", "restrict", "readonly", "writeonly");

    private GlslDeclarations() {
    }

    // One parsed global declaration; layout is the raw layout(...) text or empty, declarators keep any array suffix and initializer verbatim
    public record Declaration(String layout, List<String> qualifiers, String storage, String type, List<Declarator> declarators,
                              String blockBody, String text) {
        // Whether this declares an interface block rather than variables
        public boolean isBlock() {
            return this.blockBody != null;
        }
    }

    // A name with its array suffix ("[4]" or "") and initializer ("= ..." or "")
    public record Declarator(String name, String arraySuffix, String initializer) {
    }

    // Rewrites every global statement the function answers non-null for; a statement a directive interrupts is passed through untouched, and the rewrite keeps the original's line count so driver errors still point at the right lines
    public static String rewrite(String source, Function<Declaration, String> rewriter) {
        StringBuilder out = new StringBuilder(source.length() + 256);
        int length = source.length();
        int copied = 0;
        int depth = 0;
        int parens = 0;
        int start = -1;
        boolean interrupted = false;
        boolean functionBody = false;
        boolean lineStart = true;

        for (int i = 0; i < length; i++) {
            char c = source.charAt(i);

            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                i = endOfLine(source, i) - 1;
                continue;
            }
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                int close = source.indexOf("*/", i + 2);
                i = close < 0 ? length - 1 : close + 1;
                continue;
            }
            if (c == '\n') {
                lineStart = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '#' && lineStart) {
                i = endOfDirective(source, i) - 1;
                if (start >= 0) {
                    interrupted = true;
                }
                continue;
            }
            lineStart = false;

            if (depth == 0 && start < 0) {
                start = i;
                interrupted = false;
                functionBody = false;
            }

            switch (c) {
                case '(' -> parens++;
                case ')' -> parens--;
                case '{' -> {
                    if (depth == 0 && parens == 0 && endsWithParenthesis(source, start, i)) {
                        functionBody = true;
                    }
                    depth++;
                }
                case '}' -> {
                    depth--;
                    if (depth == 0 && functionBody) {
                        start = -1;
                    }
                }
                case ';' -> {
                    if (depth == 0 && parens == 0 && start >= 0) {
                        if (!interrupted) {
                            String statement = source.substring(start, i + 1);
                            Declaration declaration = parse(statement);
                            String replacement = declaration == null ? null : rewriter.apply(declaration);
                            if (replacement != null) {
                                out.append(source, copied, start).append(replacement).append(newlinesOf(statement, replacement));
                                copied = i + 1;
                            }
                        }
                        start = -1;
                    }
                }
                default -> {
                }
            }
        }

        return out.append(source, copied, length).toString();
    }

    // Every declaration the source holds at global scope, in order
    public static List<Declaration> collect(String source) {
        List<Declaration> found = new ArrayList<>();
        rewrite(source, declaration -> {
            found.add(declaration);
            return null;
        });
        return found;
    }

    // Parses a statement that declares variables or an interface block under a storage qualifier; null for anything else (functions, structs, plain globals, invariant redeclarations)
    static Declaration parse(String statement) {
        List<String> tokens = tokenize(statement.substring(0, statement.length() - 1));
        int index = 0;
        StringBuilder layout = new StringBuilder();
        List<String> qualifiers = new ArrayList<>();
        String storage = null;

        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.equals("layout") && index + 1 < tokens.size() && tokens.get(index + 1).startsWith("(")) {
                layout.append(layout.isEmpty() ? "" : " ").append("layout").append(tokens.get(index + 1));
                index += 2;
            } else if (QUALIFIERS.contains(token)) {
                qualifiers.add(token);
                index++;
            } else if (storage == null && STORAGE.contains(token)) {
                storage = token;
                index++;
            } else {
                break;
            }
        }

        if (storage == null || index >= tokens.size()) {
            return null;
        }

        String type = tokens.get(index++);

        // An interface block: storage Name { members } instance[array];
        if (index < tokens.size() && tokens.get(index).startsWith("{")) {
            String body = tokens.get(index++);
            String instance = index < tokens.size() && isIdentifier(tokens.get(index)) ? tokens.get(index++) : "";
            String array = index < tokens.size() && tokens.get(index).startsWith("[") ? tokens.get(index) : "";
            return new Declaration(layout.toString(), qualifiers, storage, type, List.of(new Declarator(instance, array, "")),
                    body.substring(1, body.length() - 1), statement);
        }

        // A type array suffix (vec4[4] name) folds onto every declarator
        String typeArray = "";
        if (index < tokens.size() && tokens.get(index).startsWith("[")) {
            typeArray = tokens.get(index++);
        }

        if (!isIdentifier(type)) {
            return null;
        }

        List<Declarator> declarators = new ArrayList<>();
        while (index < tokens.size()) {
            String name = tokens.get(index++);
            if (!isIdentifier(name)) {
                return null;
            }
            StringBuilder array = new StringBuilder(typeArray);
            while (index < tokens.size() && tokens.get(index).startsWith("[")) {
                array.append(tokens.get(index++));
            }
            StringBuilder initializer = new StringBuilder();
            if (index < tokens.size() && tokens.get(index).equals("=")) {
                index++;
                while (index < tokens.size() && !tokens.get(index).equals(",")) {
                    initializer.append(initializer.isEmpty() ? "" : " ").append(tokens.get(index++));
                }
            }
            declarators.add(new Declarator(name, array.toString(), initializer.isEmpty() ? "" : "= " + initializer));
            if (index < tokens.size() && tokens.get(index).equals(",")) {
                index++;
            }
        }

        if (declarators.isEmpty()) {
            return null;
        }
        return new Declaration(layout.toString(), qualifiers, storage, type, declarators, null, statement);
    }

    // Splits a statement into identifiers, numbers, single punctuation and whole bracketed groups ((...), [...], {...}), comments dropped
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int length = text.length();

        for (int i = 0; i < length; ) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
                i = endOfLine(text, i);
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? length : close + 2;
            } else if (c == '(' || c == '[' || c == '{') {
                int end = matching(text, i);
                tokens.add(stripComments(text.substring(i, end)));
                i = end;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                int end = i;
                while (end < length && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_' || text.charAt(end) == '.')) {
                    end++;
                }
                tokens.add(text.substring(i, end));
                i = end;
            } else {
                tokens.add(String.valueOf(c));
                i++;
            }
        }

        return tokens;
    }

    // Whether a token is a plain GLSL identifier
    static boolean isIdentifier(String token) {
        if (token.isEmpty() || !(Character.isLetter(token.charAt(0)) || token.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    // One past the bracket that closes the one at index, skipping comments; the end of the text when it never closes
    private static int matching(String text, int index) {
        int depth = 0;
        for (int i = index; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                i = endOfLine(text, i) - 1;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? text.length() - 1 : close + 1;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (--depth == 0) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    // The text with its comments blanked out, for bracketed groups copied into generated code
    static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", "");
    }

    // Whether the last real character before the brace is a closing parenthesis, which marks a function body
    private static boolean endsWithParenthesis(String source, int start, int brace) {
        for (int i = brace - 1; i >= start; i--) {
            char c = source.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == ')';
            }
        }
        return false;
    }

    // Index of the newline ending this line, or the end of the text
    private static int endOfLine(String text, int from) {
        int newline = text.indexOf('\n', from);
        return newline < 0 ? text.length() : newline;
    }

    // Index of the newline ending a directive, following backslash continuations
    private static int endOfDirective(String text, int from) {
        int end = endOfLine(text, from);
        while (end < text.length() && end > 0 && (text.charAt(end - 1) == '\\' || (text.charAt(end - 1) == '\r' && end > 1 && text.charAt(end - 2) == '\\'))) {
            end = endOfLine(text, end + 1);
        }
        return end;
    }

    // The newlines a rewrite dropped, so the lines after it keep their numbers
    private static String newlinesOf(String original, String replacement) {
        int missing = count(original) - count(replacement);
        return missing > 0 ? "\n".repeat(missing) : "";
    }

    // Newlines in a string
    private static int count(String text) {
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
