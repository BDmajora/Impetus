package com.bdmajora.impetus.umbra.uniforms.custom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests the value type and the constant folding of custom uniform expressions.
class CustomUniformValueTest {
    // Spreads a scalar over a vector and combines vectors of one width.
    @Test
    void combineSpreadsScalarsAndPairsComponents() {
        CustomUniformValue scalar = CustomUniformValue.scalar(2);
        CustomUniformValue pair = CustomUniformValue.of(1, 3);
        assertArrayEquals(new float[]{3, 5}, CustomUniformValue.combine(scalar, pair, Double::sum).components);
        assertArrayEquals(new float[]{3, 5}, CustomUniformValue.combine(pair, scalar, Double::sum).components);
        assertArrayEquals(new float[]{2, 6}, CustomUniformValue.combine(pair, pair, Double::sum).components);
        assertArrayEquals(new float[]{4}, CustomUniformValue.combine(scalar, scalar, (a, b) -> a * b).components);
    }

    // Rejects vectors of different widths with the widths in the message.
    @Test
    void combineRejectsMismatchedWidths() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CustomUniformValue.combine(
                CustomUniformValue.of(1, 2), CustomUniformValue.of(1, 2, 3), Double::sum));
        assertEquals("Mismatched vector widths 2 and 3", error.getMessage());
    }

    // Rejects widths outside one to four.
    @Test
    void widthLimits() {
        assertThrows(IllegalArgumentException.class, CustomUniformValue::of);
        assertThrows(IllegalArgumentException.class, () -> CustomUniformValue.of(1, 2, 3, 4, 5));
        assertEquals(4, CustomUniformValue.of(1, 2, 3, 4).width);
        assertEquals("vec2(1.0, 2.0)", CustomUniformValue.of(1, 2).toString());
        assertTrue(CustomUniformValue.bool(true).asBoolean());
        assertFalse(CustomUniformValue.bool(false).asBoolean());
    }

    // Returns one shared value for every evaluation of a constant.
    @Test
    void constantsEvaluateToOneInstance() {
        for (String source : new String[]{"1.5", "true", "false", "pi", "PPT_RAIN"}) {
            CompiledExpression expression = ExpressionParser.parse(source).expression;
            assertSame(expression.evaluate(null), expression.evaluate(null), source);
        }
        assertEquals(1.5f, ExpressionParser.parse("1.5").expression.evaluate(null).x());
    }
}
