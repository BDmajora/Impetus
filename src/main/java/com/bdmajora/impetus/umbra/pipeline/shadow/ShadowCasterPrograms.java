package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.Function;

// Shadow programs of entities and block entities that share one program when the pack gives one.
public final class ShadowCasterPrograms {
    // Logger of compile failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Program of entities or null.
    private final GbufferPrograms.Entry entity;
    // Program of block entities or null.
    private final GbufferPrograms.Entry blockEntity;
    // Shared state of the two programs.
    private final boolean shared;
    // Action that rebinds the pack resources.
    private final Runnable resourceRestorer;
    // Released state of the programs.
    private boolean destroyed;

    // Compiles both programs and deletes the first when the second fails.
    public ShadowCasterPrograms(ProgramSource shadowSource, ProgramSource entitiesSource, ProgramSource blockSource,
                                Function<ProgramSource, GbufferPrograms.Entry> compiler, Runnable resourceRestorer) {
        Objects.requireNonNull(shadowSource, "shadowSource");
        Objects.requireNonNull(compiler, "compiler");
        this.resourceRestorer = Objects.requireNonNull(resourceRestorer, "resourceRestorer");
        ProgramSource entities = entitiesSource != null ? entitiesSource : shadowSource;
        ProgramSource blocks = blockSource != null ? blockSource : shadowSource;
        this.entity = compiler.apply(entities);
        if (this.entity == null) {
            LOGGER.warn("[Umbra] Fixed function shadow program failed to compile; entity shadows disabled");
        }
        this.shared = blocks == entities;
        // Deletes the entity program when the block program fails
        try {
            this.blockEntity = this.shared ? this.entity : compiler.apply(blocks);
        } catch (RuntimeException | Error e) {
            if (this.entity != null) {
                this.entity.getProgram().destroy();
            }
            throw e;
        }
    }

    // Returns whether the entity program compiled.
    public boolean hasEntityProgram() {
        return this.entity != null;
    }

    // Returns whether a block entity program exists.
    public boolean hasBlockEntityProgram() {
        return this.blockEntity != null || this.entity != null;
    }

    // Binds the entity program.
    public void bindEntity() {
        bind(this.entity);
    }

    // Binds the block entity program or the entity program.
    public void bindBlockEntity() {
        bind(this.blockEntity != null ? this.blockEntity : this.entity);
    }

    // Binds a program between pack resource rebinds and uploads its uniforms.
    private void bind(GbufferPrograms.Entry program) {
        if (program == null) {
            return;
        }
        this.resourceRestorer.run();
        program.getProgram().getProgram().bind();
        this.resourceRestorer.run();
        program.getUniforms().update();
    }

    // Deletes the programs once.
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        try {
            if (!this.shared && this.blockEntity != null) {
                this.blockEntity.getProgram().destroy();
            }
        } finally {
            if (this.entity != null) {
                this.entity.getProgram().destroy();
            }
        }
    }
}
