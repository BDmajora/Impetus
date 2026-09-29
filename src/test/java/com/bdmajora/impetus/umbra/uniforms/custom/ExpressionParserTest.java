package com.bdmajora.impetus.umbra.uniforms.custom;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionParserTest {
    // A context over a plain name table, with one smoothing slot per call site and a fixed frame time
    private static final class Context implements CustomUniformContext {
        final Map<String, CustomUniformValue> values = new HashMap<>();
        final SmoothState[] smooth = {new SmoothState(), new SmoothState(), new SmoothState()};
        float frameTime = 0.05F;

        @Override
        public CustomUniformValue resolve(String name) {
            return values.get(name);
        }

        @Override
        public SmoothState smoothState(int index) {
            return smooth[index];
        }

        @Override
        public float frameTime() {
            return frameTime;
        }
    }

    private static CustomUniformValue eval(String expression, Context context) {
        return ExpressionParser.parse(expression).expression.evaluate(context);
    }

    private static float x(String expression) {
        return eval(expression, new Context()).x();
    }

    private static String vec(String expression, Context context) {
        return eval(expression, context).toString();
    }

    @Test
    void operatorsBindWithGlslPrecedence() {
        assertEquals(2.0F, x("1 ? 2 : 3"));
        assertEquals(3.0F, x("0 ? 2 : 3"));
        assertEquals(1.0F, x("0 || 2") * x("1 && 1"));
        assertEquals(0.0F, x("0 || 0") + x("1 && 0"));
        assertEquals(1.0F, x("2 == 2") * x("2 != 3") * x("1 <= 1") * x("2 >= 1") * x("1 < 2") * x("2 > 1"));
        assertEquals(0.0F, x("2 == 3") + x("2 != 2") + x("2 <= 1") + x("1 >= 2") + x("2 < 1") + x("1 > 2"));
        assertEquals(7.0F, x("1 + 2 * 3"));
        assertEquals(-1.0F, x("1 - 2"));
        assertEquals(2.5F, x("5 / 2"));
        assertEquals(1.0F, x("7 % 3"));
        assertEquals(9.0F, x("(1 + 2) * 3"));
        assertEquals(-4.0F, x("--(-4)"));
        assertEquals(1.0F, x("!0"));
        assertEquals(0.0F, x("!!0"));
        assertEquals(150.0F, x("1.5e+2"));
        assertEquals(0.2F, x("2E-1"));
        assertEquals(0.5F, x(".5"));
        assertEquals(1.0F, x("true") + x("false"));
        assertEquals((float) Math.PI, x("pi"));
        assertEquals((float) Math.PI, x("PI"));
    }

    @Test
    void namedConstantsAndLookupsResolve() {
        assertEquals(0.0F, x("PPT_NONE"));
        assertEquals(1.0F, x("PPT_RAIN"));
        assertEquals(2.0F, x("PPT_SNOW"));
        assertEquals(8.0F, x("BIOME_NETHER_WASTES"));
        for (String missing : new String[] {"BIOME_CRIMSON_FOREST", "BIOME_WARPED_FOREST", "BIOME_BASALT_DELTAS",
                "BIOME_SOUL_SAND_VALLEY", "BIOME_PALE_GARDEN"}) {
            assertEquals(-1.0F, x(missing), missing);
        }
        Context context = new Context();
        context.values.put("eyeBrightness", CustomUniformValue.of(10, 240));
        context.values.put("color", CustomUniformValue.of(1, 2, 3, 4));
        context.values.put("whole.x", CustomUniformValue.scalar(42));
        context.values.put("m.0.1", CustomUniformValue.scalar(7));
        context.values.put("_under", CustomUniformValue.scalar(5));
        assertEquals(240.0F, eval("eyeBrightness.y", context).x());
        assertEquals("vec2(1.0, 3.0)", vec("color.xz", context));
        assertEquals("vec4(4.0, 3.0, 2.0, 1.0)", vec("color.abgr", context));
        assertEquals("vec4(1.0, 2.0, 3.0, 4.0)", vec("color.stpq", context));
        // A component past the value's width reads zero, as does a swizzle of nothing
        assertEquals("vec2(240.0, 0.0)", vec("eyeBrightness.yz", context));
        assertEquals(0.0F, eval("nothing.x", context).x());
        // A name the table has whole wins over decomposing it
        assertEquals(42.0F, eval("whole.x", context).x());
        // Matrix cells and anything else dotted are plain lookups
        assertEquals(7.0F, eval("m.0.1", context).x());
        assertEquals(0.0F, eval("color.xyzwx", context).x());
        assertEquals(0.0F, eval("color.k", context).x());
        assertEquals(0.0F, eval("color.", context).x());
        assertEquals(5.0F, eval("_under", context).x());
        assertEquals(0.0F, eval("unknown", context).x());
    }

    @Test
    void builtinsCoverGlslAndOptiFine() {
        Context context = new Context();
        assertEquals("vec2(1.0, 1.0)", vec("vec2(1)", context));
        assertEquals("vec3(1.0, 2.0, 3.0)", vec("vec3(1, 2, 3)", context));
        assertEquals("vec4(1.0, 2.0, 3.0, 4.0)", vec("vec4(vec2(1, 2), 3, 4)", context));
        assertEquals("vec2(1.0, 2.0)", vec("vec2(1, 2, 3)", context));
        assertEquals("vec3(1.0, 2.0, 0.0)", vec("vec3(vec2(1, 2))", context));
        assertEquals(2.0F, x("if(1, 2, 3)"));
        assertEquals(3.0F, x("if(0, 1, 1, 3, 4)"));
        assertEquals(4.0F, x("if(0, 1, 0, 3, 4)"));
        assertEquals(1.0F, x("min(1, 2)"));
        assertEquals(2.0F, x("max(1, 2)"));
        assertEquals(1.0F, x("mod(7, 3)") * x("fmod(7, 3)"));
        assertEquals(8.0F, x("pow(2, 3)"));
        assertEquals((float) Math.atan2(1, 2), x("atan2(1, 2)"));
        assertEquals(1.0F, x("clamp(5, 0, 1)"));
        assertEquals(0.0F, x("clamp(-5, 0, 1)"));
        assertEquals(2.5F, x("mix(2, 3, 0.5)"));
        assertEquals("vec2(0.5, 1.0)", vec("mix(vec2(0, 0), vec2(1, 2), 0.5)", context));
        assertEquals(1.0F, x("in(2, 1, 2, 3)"));
        assertEquals(0.0F, x("in(5, 1, 2)"));
        assertEquals(1.0F, x("equals(1, 1.000001)"));
        assertEquals(0.0F, x("equals(1, 1.1)"));
        assertEquals(1.0F, x("equals(1, 1.1, -0.5)"));
        assertEquals(1.0F, x("equals(vec2(1, 2), vec2(1, 2))"));
        assertEquals(0.0F, x("equals(vec3(1, 2, 3), 1)"));
        String[][] unary = {{"sin", "0"}, {"cos", "1"}, {"tan", "0"}, {"asin", "0"}, {"acos", "0"}, {"atan", "0"},
                {"exp", "1"}, {"exp2", "8"}, {"log", "0"}, {"log2", "3"}, {"sqrt", "3"}, {"inversesqrt", "4"},
                {"abs", "3"}, {"sign", "-1"}, {"signum", "-1"}, {"floor", "1"}, {"ceil", "2"}, {"round", "2"},
                {"trunc", "1"}, {"frac", "0.5"}, {"fract", "0.5"}, {"radians", "0"}, {"degrees", "0"}};
        String[] inputs = {"0", "0", "0", "0", "1", "0", "0", "3", "1", "8", "9", "0.0625", "-3", "-2", "-2", "1.5",
                "1.5", "1.5", "1.9", "1.5", "1.5", "0", "0"};
        for (int i = 0; i < unary.length; i++) {
            assertEquals(Float.parseFloat(unary[i][1]), x(unary[i][0] + "(" + inputs[i] + ")"), 1.0e-5F, unary[i][0]);
        }
    }

    @Test
    void smoothingFollowsTheTargetAtItsHalfLife() {
        Context context = new Context();
        context.values.put("target", CustomUniformValue.scalar(0));
        CompiledExpression optifine = ExpressionParser.parse("smooth(1, target, 0.5, 1.0)").expression;
        // The first frame jumps straight to the target
        assertEquals(0.0F, optifine.evaluate(context).x());
        context.values.put("target", CustomUniformValue.scalar(1));
        float rising = optifine.evaluate(context).x();
        assertTrue(rising > 0 && rising < 1, String.valueOf(rising));
        context.values.put("target", CustomUniformValue.scalar(0));
        float falling = optifine.evaluate(context).x();
        assertTrue(falling < rising, String.valueOf(falling));

        // A zero half-life snaps, and the short forms default their fades
        ExpressionParser.Result three = ExpressionParser.parse("smooth(target, 0) + smooth(target, 0, 0) + smooth(target)");
        assertEquals(3, three.smoothCallCount);
        Context fresh = new Context();
        fresh.values.put("target", CustomUniformValue.scalar(2));
        assertEquals(6.0F, three.expression.evaluate(fresh).x());
        fresh.values.put("target", CustomUniformValue.scalar(4));
        float mixed = three.expression.evaluate(fresh).x();
        assertTrue(mixed > 10 && mixed < 12, String.valueOf(mixed));
    }

    @Test
    void malformedExpressionsAreRejected() {
        for (String bad : new String[] {"1 2", "@", "(1", "1 ? 2", "vec2(1,", "if(1, 2)", "if(1, 2, 3, 4)", "min(1)",
                "sin(1, 2)", "nope(1)", "in(1)", "equals(1)", "equals(1, 2, 3, 4)", "smooth()", "smooth(1, 2, 3, 4, 5)", ""}) {
            assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse(bad), bad);
        }
        assertEquals(0, ExpressionParser.parse("1").smoothCallCount);
    }

    @Test
    void valuesBroadcastScalarsAcrossVectors() {
        assertThrows(IllegalArgumentException.class, () -> CustomUniformValue.of());
        assertThrows(IllegalArgumentException.class, () -> CustomUniformValue.of(1, 2, 3, 4, 5));
        CustomUniformValue sum = CustomUniformValue.combine(CustomUniformValue.of(1, 2), CustomUniformValue.scalar(1), Double::sum);
        assertEquals("vec2(2.0, 3.0)", sum.toString());
        assertEquals("vec2(-2.0, -3.0)", sum.map(v -> -v).toString());
        assertTrue(CustomUniformValue.bool(true).asBoolean());
        assertFalse(CustomUniformValue.bool(false).asBoolean());
    }
}
