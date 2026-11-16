package com.bdmajora.impetus.umbra.uniforms.custom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionParserEdgeTest {
    @Test
    void hostileNestingFailsAsAParseErrorNotAStackOverflow() {
        String deep = "(".repeat(5000) + "1" + ")".repeat(5000);
        assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse(deep));
        assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse("-".repeat(5000) + "1"));
        assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse("!".repeat(5000) + "1"));
        assertNotNull(ExpressionParser.parse("(".repeat(50) + "1" + ")".repeat(50)));
    }

    @Test
    void malformedNumbersAndInputAreParseErrors() {
        for (String bad : new String[] {"1.2.3", ".", "1e", "1e+", "..5", "", "   ", "1 +", "(1", "1)", "a ? 1", "min(1)", "max(1,2,3)", "unknownfn(1)", "if(1,2)", "$", "1 2"}) {
            assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse(bad), bad);
        }
        assertThrows(ExpressionParser.ParseException.class, () -> ExpressionParser.parse(null));
    }

    @Test
    void validNumbersAndSmoothCountsParse() {
        assertEquals(0, ExpressionParser.parse("1e3 + .5 - 2.").smoothCallCount);
        assertEquals(2, ExpressionParser.parse("smooth(1) + smooth(2, 3)").smoothCallCount);
    }
}
