package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.shaderpack.preprocessor.PropertiesPreprocessor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesExpressionEdgeTest {
    @Test
    void hostileNestingIsRefusedNotOverflowed() {
        Map<String, String> defines = new HashMap<>();
        assertEquals(Optional.empty(), PropertiesPreprocessor.evaluateBooleanExpression("!".repeat(20000) + "1", defines));
        assertEquals(Optional.empty(), PropertiesPreprocessor.tryEvaluateBooleanExpression("(".repeat(20000) + "1" + ")".repeat(20000), defines));
        assertEquals(Optional.of(true), PropertiesPreprocessor.evaluateBooleanExpression("!!1", defines));
    }

    @Test
    void divisionByZeroAndOverflowStayDefined() {
        Map<String, String> defines = new HashMap<>();
        assertEquals(Optional.of(false), PropertiesPreprocessor.evaluateBooleanExpression("1 / 0", defines));
        assertEquals(Optional.of(false), PropertiesPreprocessor.evaluateBooleanExpression("5 % 0", defines));
        assertTrue(PropertiesPreprocessor.evaluateBooleanExpression("9223372036854775807 + 1 < 0", defines).isPresent());
        assertEquals(Optional.empty(), PropertiesPreprocessor.tryEvaluateBooleanExpression("", defines));
        assertEquals(Optional.empty(), PropertiesPreprocessor.tryEvaluateBooleanExpression("1 +", defines));
    }
}
