package com.bdmajora.impetus.umbra.uniforms.custom;

// Contract of the compiled expression.
@FunctionalInterface
public interface CompiledExpression {
    // Returns the value of the expression.
    CustomUniformValue evaluate(CustomUniformContext context);
}
