package com.bdmajora.impetus.engine.impl.render.chunk.shader;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderBindingContext;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformFloat4v;
import com.bdmajora.impetus.engine.impl.gl.shader.uniform.GlUniformInt;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;

// Fog uniforms of the chunk shader that follow the fixed function fog.
public abstract class ChunkShaderFogComponent implements ChunkShaderComponent {
    // Source of the fog settings.
    protected final FogService fogService;

    // Creates a component.
    protected ChunkShaderFogComponent(FogService fogService) {
        this.fogService = fogService;
    }

    // Component of the mode without fog.
    public static class None extends ChunkShaderFogComponent {
        // Creates the component.
        public None(ShaderBindingContext context, ChunkShaderEnvironment environment) {
            super(environment.fogService());
        }

        // Uploads the fog uniforms.
        @Override
        public void setup() {

        }
    }

    // Component of the exponential mode.
    public static class Exp extends ChunkShaderFogComponent {
        // Uniform of the fog color.
        private final GlUniformFloat4v uFogColor;
        // Uniform of the fog density.
        private final GlUniformFloat uFogDensity;

        // Creates the component.
        public Exp(ShaderBindingContext context, ChunkShaderEnvironment environment) {
            super(environment.fogService());

            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogDensity = context.bindUniform("u_FogDensity", GlUniformFloat::new);
        }

        @Override
        public void setup() {
            this.uFogColor.set(this.fogService.getFogColor());
            this.uFogDensity.set(this.fogService.getFogDensity());
        }
    }

    // Component of the squared exponential mode.
    public static class Exp2 extends ChunkShaderFogComponent {
        private final GlUniformFloat4v uFogColor;
        private final GlUniformFloat uFogDensity;

        // Creates the component.
        public Exp2(ShaderBindingContext context, ChunkShaderEnvironment environment) {
            super(environment.fogService());

            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogDensity = context.bindUniform("u_FogDensity", GlUniformFloat::new);
        }

        @Override
        public void setup() {
            this.uFogColor.set(this.fogService.getFogColor());
            this.uFogDensity.set(this.fogService.getFogDensity());
        }
    }

    // Component of the linear mode.
    public static class Smooth extends ChunkShaderFogComponent {
        private final GlUniformFloat4v uFogColor;

        // Uniform of the fog shape.
        private final GlUniformInt uFogShape;
        // Uniform of the fog start.
        private final GlUniformFloat uFogStart;
        // Uniform of the fog end.
        private final GlUniformFloat uFogEnd;

        // Creates the component.
        public Smooth(ShaderBindingContext context, ChunkShaderEnvironment environment) {
            super(environment.fogService());

            this.uFogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
            this.uFogShape = context.bindUniform("u_FogShape", GlUniformInt::new);
            this.uFogStart = context.bindUniform("u_FogStart", GlUniformFloat::new);
            this.uFogEnd = context.bindUniform("u_FogEnd", GlUniformFloat::new);
        }

        @Override
        public void setup() {
            this.uFogColor.set(this.fogService.getFogColor());
            this.uFogShape.set(this.fogService.getFogShapeIndex());

            this.uFogStart.set(this.fogService.getFogStart());
            this.uFogEnd.set(this.fogService.getFogEnd());
        }
    }

}
