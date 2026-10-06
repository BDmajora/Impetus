package com.bdmajora.impetus.umbra.uniforms.custom;

import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.impetus.umbra.uniforms.CommonUniforms;
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

// The pack's uniform.<type>.<name> and variable.<type>.<name> expressions, evaluated once per frame in declaration order so later ones reference earlier results; only uniform. entries are uploaded
public final class CustomUniforms {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final CustomUniformInputs inputs;
    private final List<Variable> variables;
    private final Map<String, Variable> byName;

    private CustomUniforms(CustomUniformInputs inputs, List<Variable> variables) {
        this.inputs = inputs;
        this.variables = variables;
        this.byName = new HashMap<>();
        for (Variable variable : variables) {
            this.byName.put(variable.name, variable);
        }
    }

    // Evaluates every variable for this frame, once and BEFORE any program uniform update or programs upload last frame's values
    public void update() {
        float frameTime = Math.max(SystemTimeUniforms.COUNTER.getLastFrameTime(), 1.0e-4f);

        for (Variable variable : this.variables) {
            CustomUniformContext context = new VariableContext(variable, frameTime);
            try {
                variable.current = coerce(variable.expression.evaluate(context), variable.type);
            } catch (Exception e) {
                // Never let a bad pack expression take down the frame; freeze on the last good value.
                if (!variable.warned) {
                    variable.warned = true;
                    LOGGER.warn("[Umbra] Custom uniform '{}' failed to evaluate: {}", variable.name, e.toString());
                }
            }
        }
    }

    // Registers every `uniform.`-declared entry with a program's builder regardless of use, since the builder drops locations resolving to -1
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

    // Whether the pack declared any
    public boolean isEmpty() {
        return this.variables.isEmpty();
    }

    // Variable count
    public int size() {
        return this.variables.size();
    }

    // Every variable's value as evaluated this frame in declaration order, reporting only; a pack expression sharing a built-in's name WINS at program build, so these are the values reaching the shader and comparing against the built-in shows a divergence
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

    // One component of a variable's current value
    private static float component(Variable variable, int index) {
        CustomUniformValue value = variable.current;
        return index < value.width ? value.components[index] : 0.0f;
    }

    // Widens or narrows to the declared type
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

    private final class VariableContext implements CustomUniformContext {
        private final Variable variable;
        private final float frameTime;

        VariableContext(Variable variable, float frameTime) {
            this.variable = variable;
            this.frameTime = frameTime;
        }

        // A variable's current value, or a builtin's
        @Override
        public CustomUniformValue resolve(String name) {
            Variable other = byName.get(name);
            if (other != null && other != this.variable) {
                return other.current;
            }
            return inputs.resolve(name);
        }

        // Per-smooth() call state
        @Override
        public SmoothState smoothState(int index) {
            return this.variable.smoothStates[index];
        }

        // Delta for the smoothers
        @Override
        public float frameTime() {
            return this.frameTime;
        }
    }

    public enum Type {
        BOOL(1), INT(1), FLOAT(1), VEC2(2), VEC3(3), VEC4(4);

        final int width;

        Type(int width) {
            this.width = width;
        }

        // bool, int, float, vec2..4
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

    private static final class Variable {
        final String name;
        final Type type;
        final boolean isUniform;
        final CompiledExpression expression;
        final CustomUniformContext.SmoothState[] smoothStates;
        CustomUniformValue current;
        boolean warned;

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

    public static final class Builder {
        private final Map<String, PendingVariable> pending = new LinkedHashMap<>();

        // Declares one variable.<type>.<name> or uniform.<type>.<name>
        public void addVariable(String typeToken, String name, String expression, boolean isUniform) {
            Type type = Type.parse(typeToken);
            if (type == null) {
                LOGGER.warn("[Umbra] Unknown custom uniform type '{}' for '{}', ignoring", typeToken, name);
                return;
            }
            if (this.pending.containsKey(name)) {
                LOGGER.warn("[Umbra] Duplicate custom uniform/variable '{}', keeping the first definition", name);
                return;
            }
            this.pending.put(name, new PendingVariable(name, type, expression, isUniform));
        }

        // Whether anything was declared
        public boolean isEmpty() {
            return this.pending.isEmpty();
        }

        // Compiles every expression, in dependency order
        public CustomUniforms build() {
            CustomUniformInputs inputs = new CustomUniformInputs();
            // Capture the full built-in uniform surface (common + celestial + system-time all register through addCommonUniforms; matrices resolve per-cell, e.g. gbufferProjection.1.1)
            CommonUniforms.addCommonUniforms(inputs);
            com.bdmajora.impetus.umbra.uniforms.MatrixUniforms.addMatrixUniforms(inputs);

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

        // A declaration awaiting compilation
        private record PendingVariable(String name, Type type, String expression, boolean isUniform) {
        }
    }
}
