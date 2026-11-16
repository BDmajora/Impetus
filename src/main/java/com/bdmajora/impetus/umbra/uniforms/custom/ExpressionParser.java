package com.bdmajora.impetus.umbra.uniforms.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

// Recursive descent parser of custom uniform expressions into evaluation trees.
public final class ExpressionParser {
    // Failure of a malformed expression.
    public static final class ParseException extends RuntimeException {
        // Creates a failure with a reason.
        public ParseException(String message) {
            super(message);
        }
    }

    // Expression text.
    private final String source;
    // Position of the next character.
    private int pos;
    // Number of smooth calls so far.
    private int smoothCallCount;
    // Current nesting depth.
    private int depth;
    // Deepest nesting of an expression.
    private static final int MAX_DEPTH = 128;
    // Value of true.
    private static final CustomUniformValue TRUE = CustomUniformValue.bool(true);
    // Value of false.
    private static final CustomUniformValue FALSE = CustomUniformValue.bool(false);
    // Value of pi.
    private static final CustomUniformValue PI = CustomUniformValue.scalar((float) Math.PI);
    // Value of an unknown name.
    private static final CustomUniformValue ZERO = CustomUniformValue.scalar(0.0f);

    // Creates a parser of a text.
    private ExpressionParser(String source) {
        this.source = source;
    }

    // Parsed expression with its smooth call count.
    public static final class Result {
        // Evaluation tree.
        public final CompiledExpression expression;
        // Number of smooth calls in the tree.
        public final int smoothCallCount;

        // Creates a result.
        Result(CompiledExpression expression, int smoothCallCount) {
            this.expression = expression;
            this.smoothCallCount = smoothCallCount;
        }
    }

    // Parses a whole expression and rejects null or malformed or trailing input.
    public static Result parse(String source) {
        if (source == null) {
            throw new ParseException("Expression is null");
        }
        ExpressionParser parser = new ExpressionParser(source);
        CompiledExpression expr = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.pos < parser.source.length()) {
            throw new ParseException("Unexpected trailing input at " + parser.pos + " in: " + source);
        }
        return new Result(expr, parser.smoothCallCount);
    }

    // Parses a nested expression within the depth limit.
    private CompiledExpression parseExpression() {
        enter();
        try {
            return parseTernary();
        } finally {
            this.depth--;
        }
    }

    // Enters a nesting level and rejects one past the limit.
    private void enter() {
        if (++this.depth > MAX_DEPTH) {
            throw new ParseException("Expression nests too deeply in: " + this.source);
        }
    }

    // Parses a conditional expression.
    private CompiledExpression parseTernary() {
        CompiledExpression condition = parseOr();
        skipWhitespace();
        if (consume('?')) {
            CompiledExpression whenTrue = parseExpression();
            skipWhitespace();
            expect(':');
            CompiledExpression whenFalse = parseExpression();
            return ctx -> condition.evaluate(ctx).asBoolean() ? whenTrue.evaluate(ctx) : whenFalse.evaluate(ctx);
        }
        return condition;
    }

    // Parses a logical or chain.
    private CompiledExpression parseOr() {
        CompiledExpression left = parseAnd();
        while (true) {
            skipWhitespace();
            if (consumeSequence("||")) {
                CompiledExpression right = parseAnd();
                CompiledExpression l = left;
                left = ctx -> CustomUniformValue.bool(l.evaluate(ctx).asBoolean() || right.evaluate(ctx).asBoolean());
            } else {
                return left;
            }
        }
    }

    // Parses a logical and chain.
    private CompiledExpression parseAnd() {
        CompiledExpression left = parseEquality();
        while (true) {
            skipWhitespace();
            if (consumeSequence("&&")) {
                CompiledExpression right = parseEquality();
                CompiledExpression l = left;
                left = ctx -> CustomUniformValue.bool(l.evaluate(ctx).asBoolean() && right.evaluate(ctx).asBoolean());
            } else {
                return left;
            }
        }
    }

    // Parses an equality comparison chain.
    private CompiledExpression parseEquality() {
        CompiledExpression left = parseComparison();
        while (true) {
            skipWhitespace();
            if (consumeSequence("==")) {
                left = binary(left, parseComparison(), (a, b) -> a == b ? 1.0 : 0.0);
            } else if (consumeSequence("!=")) {
                left = binary(left, parseComparison(), (a, b) -> a != b ? 1.0 : 0.0);
            } else {
                return left;
            }
        }
    }

    // Parses an ordering comparison chain.
    private CompiledExpression parseComparison() {
        CompiledExpression left = parseAdditive();
        while (true) {
            skipWhitespace();
            if (consumeSequence("<=")) {
                left = binary(left, parseAdditive(), (a, b) -> a <= b ? 1.0 : 0.0);
            } else if (consumeSequence(">=")) {
                left = binary(left, parseAdditive(), (a, b) -> a >= b ? 1.0 : 0.0);
            } else if (peek() == '<') {
                pos++;
                left = binary(left, parseAdditive(), (a, b) -> a < b ? 1.0 : 0.0);
            } else if (peek() == '>') {
                pos++;
                left = binary(left, parseAdditive(), (a, b) -> a > b ? 1.0 : 0.0);
            } else {
                return left;
            }
        }
    }

    // Parses a sum or difference chain.
    private CompiledExpression parseAdditive() {
        CompiledExpression left = parseTerm();
        while (true) {
            skipWhitespace();
            char c = peek();
            if (c == '+') {
                pos++;
                left = binary(left, parseTerm(), Double::sum);
            } else if (c == '-') {
                pos++;
                left = binary(left, parseTerm(), (a, b) -> a - b);
            } else {
                return left;
            }
        }
    }

    // Parses a product or quotient or remainder chain.
    private CompiledExpression parseTerm() {
        CompiledExpression left = parseUnary();
        while (true) {
            skipWhitespace();
            char c = peek();
            if (c == '*') {
                pos++;
                left = binary(left, parseUnary(), (a, b) -> a * b);
            } else if (c == '/') {
                pos++;
                left = binary(left, parseUnary(), (a, b) -> a / b);
            } else if (c == '%') {
                pos++;
                left = binary(left, parseUnary(), (a, b) -> a % b);
            } else {
                return left;
            }
        }
    }

    // Parses a negation or logical not.
    private CompiledExpression parseUnary() {
        skipWhitespace();
        char c = peek();
        if (c == '-' || c == '!') {
            pos++;
            enter();
            CompiledExpression operand;
            try {
                operand = parseUnary();
            } finally {
                this.depth--;
            }
            if (c == '-') {
                return ctx -> operand.evaluate(ctx).map(v -> -v);
            }
            return ctx -> CustomUniformValue.bool(!operand.evaluate(ctx).asBoolean());
        }
        return parsePrimary();
    }

    // Parses a number or name or call or parenthesized expression.
    private CompiledExpression parsePrimary() {
        skipWhitespace();
        char c = peek();

        if (c == '(') {
            pos++;
            CompiledExpression inner = parseExpression();
            skipWhitespace();
            expect(')');
            return inner;
        }

        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        if (Character.isLetter(c) || c == '_') {
            String ident = parseIdentifier();
            skipWhitespace();
            if (peek() == '(') {
                return parseCall(ident);
            }
            switch (ident) {
                case "true":
                    return ctx -> TRUE;
                case "false":
                    return ctx -> FALSE;
                case "pi":
                case "PI":
                    return ctx -> PI;
                default:
                    Float constant = namedConstant(ident);
                    if (constant != null) {
                        CustomUniformValue named = CustomUniformValue.scalar(constant);
                        return ctx -> named;
                    }
                    int dot = ident.indexOf('.');
                    if (dot > 0) {
                        String base = ident.substring(0, dot);
                        int[] indices = swizzleIndices(ident.substring(dot + 1));
                        if (indices != null) {
                            return ctx -> {
                                CustomUniformValue whole = ctx.resolve(ident);
                                if (whole != null) {
                                    return whole;
                                }
                                CustomUniformValue value = ctx.resolve(base);
                                if (value == null) {
                                    return ZERO;
                                }
                                float[] out = new float[indices.length];
                                for (int i = 0; i < indices.length; i++) {
                                    out[i] = indices[i] < value.width ? value.components[indices[i]] : 0.0f;
                                }
                                return CustomUniformValue.of(out);
                            };
                        }
                    }
                    return ctx -> {
                        CustomUniformValue value = ctx.resolve(ident);
                        return value != null ? value : ZERO;
                    };
            }
        }

        throw new ParseException("Unexpected character '" + c + "' at " + pos + " in: " + source);
    }

    // Parses a number literal into a constant and rejects a malformed one.
    private CompiledExpression parseNumber() {
        int start = pos;
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.'
                || source.charAt(pos) == 'e' || source.charAt(pos) == 'E'
                || ((source.charAt(pos) == '+' || source.charAt(pos) == '-') && pos > start
                && (source.charAt(pos - 1) == 'e' || source.charAt(pos - 1) == 'E')))) {
            pos++;
        }
        float value;
        try {
            value = Float.parseFloat(source.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid number '" + source.substring(start, pos) + "' at " + start + " in: " + source);
        }
        // Reuses one immutable value for every evaluation
        CustomUniformValue constant = CustomUniformValue.scalar(value);
        return ctx -> constant;
    }

    // Parses the arguments of a call.
    private CompiledExpression parseCall(String name) {
        expect('(');
        List<CompiledExpression> args = new ArrayList<>();
        skipWhitespace();
        if (peek() != ')') {
            args.add(parseExpression());
            skipWhitespace();
            while (consume(',')) {
                args.add(parseExpression());
                skipWhitespace();
            }
        }
        expect(')');
        return buildFunction(name, args);
    }

    // Returns the tree of a named function and rejects an unknown one.
    private CompiledExpression buildFunction(String name, List<CompiledExpression> args) {
        switch (name) {
            case "vec2":
                return vector(args, 2);
            case "vec3":
                return vector(args, 3);
            case "vec4":
                return vector(args, 4);
            case "if":
                return buildIf(args);
            case "min":
                requireArity(name, args, 2);
                return ctx -> CustomUniformValue.combine(args.get(0).evaluate(ctx), args.get(1).evaluate(ctx), Math::min);
            case "max":
                requireArity(name, args, 2);
                return ctx -> CustomUniformValue.combine(args.get(0).evaluate(ctx), args.get(1).evaluate(ctx), Math::max);
            case "mod":
            case "fmod":
                requireArity(name, args, 2);
                return ctx -> CustomUniformValue.combine(args.get(0).evaluate(ctx), args.get(1).evaluate(ctx), (a, b) -> a % b);
            case "pow":
                requireArity(name, args, 2);
                return ctx -> CustomUniformValue.combine(args.get(0).evaluate(ctx), args.get(1).evaluate(ctx), Math::pow);
            case "atan2":
                requireArity(name, args, 2);
                return ctx -> CustomUniformValue.combine(args.get(0).evaluate(ctx), args.get(1).evaluate(ctx), Math::atan2);
            case "clamp":
                requireArity(name, args, 3);
                return ctx -> {
                    CustomUniformValue v = args.get(0).evaluate(ctx);
                    CustomUniformValue lo = args.get(1).evaluate(ctx);
                    CustomUniformValue hi = args.get(2).evaluate(ctx);
                    return CustomUniformValue.combine(CustomUniformValue.combine(v, lo, Math::max), hi, Math::min);
                };
            case "mix":
                requireArity(name, args, 3);
                return ctx -> {
                    CustomUniformValue a = args.get(0).evaluate(ctx);
                    CustomUniformValue b = args.get(1).evaluate(ctx);
                    CustomUniformValue t = args.get(2).evaluate(ctx);
                    return CustomUniformValue.combine(a, CustomUniformValue.combine(CustomUniformValue.combine(b, a, (bb, aa) -> bb - aa), t, (d, tt) -> d * tt), Double::sum);
                };
            case "in":
                return buildIn(args);
            case "equals":
                return buildEquals(args);
            case "smooth":
                return buildSmooth(args);
            default:
                return buildUnaryFunction(name, args);
        }
    }

    // Returns the tree of an if call with pairs of conditions and values.
    private CompiledExpression buildIf(List<CompiledExpression> args) {
        if (args.size() < 3 || (args.size() & 1) == 0) {
            throw new ParseException("if() expects condition/value pairs plus a fallback, got " + args.size());
        }
        return ctx -> {
            for (int i = 0; i < args.size() - 1; i += 2) {
                if (args.get(i).evaluate(ctx).asBoolean()) {
                    return args.get(i + 1).evaluate(ctx);
                }
            }
            return args.get(args.size() - 1).evaluate(ctx);
        };
    }

    // Returns the expression of a one argument function.
    private CompiledExpression buildUnaryFunction(String name, List<CompiledExpression> args) {
        requireArity(name, args, 1);
        CompiledExpression arg = args.get(0);
        java.util.function.DoubleUnaryOperator op;
        switch (name) {
            case "sin": op = Math::sin; break;
            case "cos": op = Math::cos; break;
            case "tan": op = Math::tan; break;
            case "asin": op = Math::asin; break;
            case "acos": op = Math::acos; break;
            case "atan": op = Math::atan; break;
            case "exp": op = Math::exp; break;
            case "exp2": op = v -> Math.pow(2.0, v); break;
            case "log": op = Math::log; break;
            case "log2": op = v -> Math.log(v) / Math.log(2.0); break;
            case "sqrt": op = Math::sqrt; break;
            case "inversesqrt": op = v -> 1.0 / Math.sqrt(v); break;
            case "abs": op = Math::abs; break;
            case "sign": case "signum": op = Math::signum; break;
            case "floor": op = Math::floor; break;
            case "ceil": op = Math::ceil; break;
            case "round": op = v -> (double) Math.round(v); break;
            case "trunc": op = v -> (double) (long) v; break;
            case "frac": case "fract": op = v -> v - Math.floor(v); break;
            case "radians": op = Math::toRadians; break;
            case "degrees": op = Math::toDegrees; break;
            default:
                throw new ParseException("Unknown function '" + name + "' in: " + source);
        }
        return ctx -> arg.evaluate(ctx).map(op);
    }

    // Returns the tree of an in call that matches any of its values.
    private CompiledExpression buildIn(List<CompiledExpression> args) {
        if (args.size() < 2) {
            throw new ParseException("in() expects at least 2 arguments, got " + args.size());
        }
        CompiledExpression valueExpr = args.get(0);
        List<CompiledExpression> candidates = args.subList(1, args.size());
        return ctx -> {
            float value = valueExpr.evaluate(ctx).x();
            for (CompiledExpression candidate : candidates) {
                if (Math.abs(value - candidate.evaluate(ctx).x()) < 1.0e-5f) {
                    return CustomUniformValue.bool(true);
                }
            }
            return CustomUniformValue.bool(false);
        };
    }

    // Returns the tree of an equals call with a tolerance.
    private CompiledExpression buildEquals(List<CompiledExpression> args) {
        if (args.size() != 2 && args.size() != 3) {
            throw new ParseException("equals() expects 2 or 3 arguments, got " + args.size());
        }
        return ctx -> {
            CustomUniformValue left = args.get(0).evaluate(ctx);
            CustomUniformValue right = args.get(1).evaluate(ctx);
            float epsilon = args.size() == 3 ? Math.abs(args.get(2).evaluate(ctx).x()) : 1.0e-5f;
            int width = Math.max(left.width, right.width);
            for (int i = 0; i < width; i++) {
                if (Math.abs(component(left, i) - component(right, i)) > epsilon) {
                    return CustomUniformValue.bool(false);
                }
            }
            return CustomUniformValue.bool(true);
        };
    }

    // Returns the tree of a smooth call with its own state slot.
    private CompiledExpression buildSmooth(List<CompiledExpression> args) {
        if (args.isEmpty() || args.size() > 4) {
            throw new ParseException("smooth() takes 1-4 arguments in: " + source);
        }
        int slot = smoothCallCount++;
        int valueIndex = args.size() == 4 ? 1 : 0;
        CompiledExpression valueExpr = args.get(valueIndex);
        CompiledExpression upExpr = args.size() >= valueIndex + 2 ? args.get(valueIndex + 1) : null;
        CompiledExpression downExpr = args.size() >= valueIndex + 3 ? args.get(valueIndex + 2) : upExpr;

        return ctx -> {
            float target = valueExpr.evaluate(ctx).x();
            CustomUniformContext.SmoothState state = ctx.smoothState(slot);
            if (!state.initialized) {
                state.initialized = true;
                state.value = target;
                return CustomUniformValue.scalar(target);
            }
            float up = upExpr != null ? upExpr.evaluate(ctx).x() : 1.0f;
            float down = downExpr != null ? downExpr.evaluate(ctx).x() : up;
            float halfLife = target > state.value ? up : down;
            float scaledHalfLife = halfLife * 0.1f;
            float rate = scaledHalfLife <= 0.0f ? 1.0f
                    : (float) (1.0 - Math.pow(0.5, ctx.frameTime() / scaledHalfLife));
            state.value += (target - state.value) * rate;
            return CustomUniformValue.scalar(state.value);
        };
    }

    // Returns the tree of a vector constructor of a width.
    private CompiledExpression vector(List<CompiledExpression> args, int width) {
        return ctx -> {
            float[] out = new float[width];
            int i = 0;
            for (CompiledExpression arg : args) {
                CustomUniformValue v = arg.evaluate(ctx);
                for (int k = 0; k < v.width && i < width; k++) {
                    out[i++] = v.components[k];
                }
            }
            if (args.size() == 1 && i == 1) {
                for (; i < width; i++) {
                    out[i] = out[0];
                }
            }
            return CustomUniformValue.of(out);
        };
    }

    // Rejects a call with the wrong number of arguments.
    private static void requireArity(String name, List<CompiledExpression> args, int arity) {
        if (args.size() != arity) {
            throw new ParseException(name + "() expects " + arity + " arguments, got " + args.size());
        }
    }

    // Returns a component of a value or its last component.
    private static float component(CustomUniformValue value, int index) {
        return value.width == 1 ? value.components[0] : value.components[Math.min(index, value.width - 1)];
    }

    // Returns the tree of a component wise binary operator.
    private CompiledExpression binary(CompiledExpression left, CompiledExpression right, DoubleBinaryOperator op) {
        return ctx -> CustomUniformValue.combine(left.evaluate(ctx), right.evaluate(ctx), op);
    }

    // Returns the value of a named constant or null.
    private static Float namedConstant(String name) {
        switch (name) {
            case "PPT_NONE":
                return 0.0f;
            case "PPT_RAIN":
                return 1.0f;
            case "PPT_SNOW":
                return 2.0f;
            case "BIOME_NETHER_WASTES":
                return 8.0f;
            case "BIOME_CRIMSON_FOREST":
            case "BIOME_WARPED_FOREST":
            case "BIOME_BASALT_DELTAS":
            case "BIOME_SOUL_SAND_VALLEY":
            case "BIOME_PALE_GARDEN":
                return -1.0f;
            default:
                return null;
        }
    }

    // Returns the component indices of a swizzle or null for a malformed one.
    private static int[] swizzleIndices(String suffix) {
        if (suffix.isEmpty() || suffix.length() > 4 || suffix.indexOf('.') >= 0) {
            return null;
        }
        int[] indices = new int[suffix.length()];
        for (int i = 0; i < suffix.length(); i++) {
            switch (suffix.charAt(i)) {
                case 'x': case 'r': case 's':
                    indices[i] = 0;
                    break;
                case 'y': case 'g': case 't':
                    indices[i] = 1;
                    break;
                case 'z': case 'b': case 'p':
                    indices[i] = 2;
                    break;
                case 'w': case 'a': case 'q':
                    indices[i] = 3;
                    break;
                default:
                    return null;
            }
        }
        return indices;
    }

    // Parses a name with dots.
    private String parseIdentifier() {
        int start = pos;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                pos++;
            } else {
                break;
            }
        }
        return source.substring(start, pos);
    }

    // Moves past whitespace.
    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }

    // Returns the next character after whitespace or zero at the end.
    private char peek() {
        skipWhitespace();
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    // Consumes a character after whitespace and returns whether it matched.
    private boolean consume(char expected) {
        skipWhitespace();
        if (pos < source.length() && source.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    // Consumes a text after whitespace and returns whether it matched.
    private boolean consumeSequence(String expected) {
        skipWhitespace();
        if (source.regionMatches(pos, expected, 0, expected.length())) {
            pos += expected.length();
            return true;
        }
        return false;
    }

    // Consumes a character and rejects a different one.
    private void expect(char expected) {
        if (!consume(expected)) {
            throw new ParseException("Expected '" + expected + "' at " + pos + " in: " + source);
        }
    }
}
