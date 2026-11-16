package com.bdmajora.impetus.umbra.uniforms.custom;

import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
import com.bdmajora.impetus.umbra.uniforms.MatrixUniforms;
import com.bdmajora.impetus.umbra.uniforms.SystemTimeUniforms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Custom uniforms and variables of a shader pack evaluated once per frame in definition order.
public final class CustomUniforms {
    // Logger of evaluation and parse failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Built in values the expressions read.
    private final CustomUniformInputs inputs;
    // Variables in definition order.
    private final List<Variable> variables;
    // Variables by name.
    private final Map<String, Variable> byName;
    // Context reused for every evaluation.
    private final VariableContext context = new VariableContext();

    // Creates the uniforms from parsed variables.
    private CustomUniforms(CustomUniformInputs inputs, List<Variable> variables) {
        this.inputs = inputs;
        this.variables = variables;
        this.byName = new HashMap<>();
        for (Variable variable : variables) {
            this.byName.put(variable.name, variable);
        }
    }

    // Evaluates every variable and keeps the last value of one that fails and warns once.
    public void update() {
        VariableContext context = this.context;
        context.frameTime = Math.max(SystemTimeUniforms.COUNTER.getLastFrameTime(), 1.0e-4f);

        for (Variable variable : this.variables) {
            context.variable = variable;
            try {
                variable.current = coerce(variable.expression.evaluate(context), variable.type);
            } catch (Exception e) {
                if (!variable.warned) {
                    variable.warned = true;
                    LOGGER.warn("[Umbra] Custom uniform '{}' failed to evaluate: {}", variable.name, e.toString());
                }
            }
        }
    }

    // Registers every uniform variable with a collector.
    public void assignTo(UniformCollector collector) {
        for (Variable variable : this.variables) {
            if (!variable.isUniform) {
                continue;
            }

            Variable v = variable;
            switch (variable.type) {
                case BOOL:
                case INT:
                    collector.uniform1i(UniformUpdateFrequency.PER_FRAME, v.name, () -> (int) v.current.x());
                    break;
                case FLOAT:
                    collector.uniform1f(UniformUpdateFrequency.PER_FRAME, v.name, () -> v.current.x());
                    break;
                case VEC2:
                    collector.uniform2f(UniformUpdateFrequency.PER_FRAME, v.name,
                            () -> new Vector2f(component(v, 0), component(v, 1)));
                    break;
                case VEC3:
                    collector.uniform3f(UniformUpdateFrequency.PER_FRAME, v.name,
                            () -> new Vector3f(component(v, 0), component(v, 1), component(v, 2)));
                    break;
                case VEC4:
                    collector.uniform4f(UniformUpdateFrequency.PER_FRAME, v.name,
                            () -> new Vector4f(component(v, 0), component(v, 1), component(v, 2), component(v, 3)));
                    break;
            }
        }
    }

    // Returns whether no variable was defined.
    public boolean isEmpty() {
        return this.variables.isEmpty();
    }

    // Returns the number of variables.
    public int size() {
        return this.variables.size();
    }

    // Returns the current value of every variable as text.
    public Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Variable variable : this.variables) {
            CustomUniformValue value = variable.current;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < value.width; i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(value.components[i]);
            }
            out.put((variable.isUniform ? "uniform." : "variable.") + variable.name, text.toString());
        }
        return out;
    }

    // Returns a component of the current value or zero past its width.
    private static float component(Variable variable, int index) {
        CustomUniformValue value = variable.current;
        return index < value.width ? value.components[index] : 0.0f;
    }

    // Returns a value widened or narrowed to the width of a type and repeats a scalar.
    private static CustomUniformValue coerce(CustomUniformValue value, Type type) {
        if (value.width == type.width) {
            return value;
        }
        float[] out = new float[type.width];
        for (int i = 0; i < type.width; i++) {
            out[i] = i < value.width ? value.components[i] : (value.width == 1 ? value.components[0] : 0.0f);
        }
        return CustomUniformValue.of(out);
    }

    // Context that resolves names for the variable being evaluated.
    private final class VariableContext implements CustomUniformContext {
        // Variable being evaluated.
        private Variable variable;
        // Length of the last frame in seconds.
        private float frameTime;

        // Returns another variable or a built in value by name.
        @Override
        public CustomUniformValue resolve(String name) {
            Variable other = byName.get(name);
            if (other != null && other != this.variable) {
                return other.current;
            }
            return inputs.resolve(name);
        }

        // Returns a smoothing state of the variable.
        @Override
        public SmoothState smoothState(int index) {
            return this.variable.smoothStates[index];
        }

        // Returns the length of the last frame in seconds.
        @Override
        public float frameTime() {
            return this.frameTime;
        }
    }

    // Types of custom uniforms.
    public enum Type {
        // Scalar and vector types with their component counts.
        BOOL(1), INT(1), FLOAT(1), VEC2(2), VEC3(3), VEC4(4);

        // Number of components.
        final int width;

        // Creates a type with a component count.
        Type(int width) {
            this.width = width;
        }

        // Returns the type of a token or null.
        static Type parse(String token) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "bool": return BOOL;
                case "int": return INT;
                case "float": return FLOAT;
                case "vec2": case "ivec2": return VEC2;
                case "vec3": case "ivec3": return VEC3;
                case "vec4": case "ivec4": return VEC4;
                default: return null;
            }
        }
    }

    // One custom uniform or variable.
    private static final class Variable {
        // Name of the variable.
        final String name;
        // Type of the variable.
        final Type type;
        // Uniform state that exposes the variable to programs.
        final boolean isUniform;
        // Compiled expression.
        final CompiledExpression expression;
        // States of the smooth calls in the expression.
        final CustomUniformContext.SmoothState[] smoothStates;
        // Value of the last evaluation.
        CustomUniformValue current;
        // Warned state of a failed evaluation.
        boolean warned;

        // Creates a variable with a zero value.
        Variable(String name, Type type, boolean isUniform, CompiledExpression expression, int smoothCount) {
            this.name = name;
            this.type = type;
            this.isUniform = isUniform;
            this.expression = expression;
            this.smoothStates = new CustomUniformContext.SmoothState[smoothCount];
            for (int i = 0; i < smoothCount; i++) {
                this.smoothStates[i] = new CustomUniformContext.SmoothState();
            }
            this.current = CustomUniformValue.of(new float[type.width]);
        }
    }

    // Collector of variable definitions.
    public static final class Builder {
        // Definitions by name in definition order.
        private final Map<String, PendingVariable> pending = new LinkedHashMap<>();

        // Adds a definition and ignores an unknown type or a repeated name.
        public void addVariable(String typeToken, String name, String expression, boolean isUniform) {
            Type type = Type.parse(typeToken);
            if (type == null) {
                LOGGER.warn("[Umbra] Unknown custom uniform type '{}' for '{}', ignoring", typeToken, name);
                return;
            }
            if (this.pending.containsKey(name)) {
                LOGGER.warn("[Umbra] Duplicate custom uniform or variable '{}', keeping the first definition", name);
                return;
            }
            this.pending.put(name, new PendingVariable(name, type, expression, isUniform));
        }

        // Returns whether no definition was added.
        public boolean isEmpty() {
            return this.pending.isEmpty();
        }

        // Parses every definition and skips those that fail.
        public CustomUniforms build() {
            CustomUniformInputs inputs = new CustomUniformInputs();
            CommonUniforms.addCommonUniforms(inputs);
            MatrixUniforms.addMatrixUniforms(inputs);

            List<Variable> variables = new ArrayList<>();
            for (PendingVariable p : this.pending.values()) {
                try {
                    ExpressionParser.Result result = ExpressionParser.parse(p.expression);
                    variables.add(new Variable(p.name, p.type, p.isUniform, result.expression, result.smoothCallCount));
                } catch (ExpressionParser.ParseException e) {
                    LOGGER.warn("[Umbra] Failed to parse custom {} '{}' = '{}': {}",
                            p.isUniform ? "uniform" : "variable", p.name, p.expression, e.getMessage());
                }
            }

            return new CustomUniforms(inputs, variables);
        }

        // Definition that has not been parsed.
        private record PendingVariable(String name, Type type, String expression, boolean isUniform) {
        }
    }
}
