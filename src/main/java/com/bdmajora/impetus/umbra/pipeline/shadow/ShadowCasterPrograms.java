package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.Function;

// Fixed function shadow programs that entities and block entities draw with
public final class ShadowCasterPrograms {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Program for entities or null when it failed to compile
    private final GbufferPrograms.Entry entity;
    // Program for block entities which is the entity program when both share one source
    private final GbufferPrograms.Entry blockEntity;
    // Flag set when both fields hold the same program
    private final boolean shared;
    // Action that rebinds the pack textures and images to their units
    private final Runnable resourceRestorer;
    // Flag set once destroy has run
    private boolean destroyed;

    // Compiles the entity and block entity programs and falls back to the shadow source for either one that is null
    public ShadowCasterPrograms(ProgramSource shadowSource, ProgramSource entitiesSource, ProgramSource blockSource,
                                Function<ProgramSource, GbufferPrograms.Entry> compiler, Runnable resourceRestorer) {
        Objects.requireNonNull(shadowSource, "shadowSource");
        Objects.requireNonNull(compiler, "compiler");
        this.resourceRestorer = Objects.requireNonNull(resourceRestorer, "resourceRestorer");
        ProgramSource entities = entitiesSource != null ? entitiesSource : shadowSource;
        ProgramSource blocks = blockSource != null ? blockSource : shadowSource;
        this.entity = compiler.apply(entities);
        if (this.entity == null) {
            LOGGER.warn("[Umbra] Fixed-function shadow program failed to compile; entity shadows disabled");
        }
        this.shared = blocks == entities;
        this.blockEntity = this.shared ? this.entity : compiler.apply(blocks);
    }

    // Returns whether entities have a program to draw with
    public boolean hasEntityProgram() {
        return this.entity != null;
    }

    // Returns whether block entities have their own program or the entity program to fall back on
    public boolean hasBlockEntityProgram() {
        return this.blockEntity != null || this.entity != null;
    }

    // Binds the entity program and refreshes its uniforms
    public void bindEntity() {
        bind(this.entity);
    }

    // Binds the block entity program or the entity program when it is missing and refreshes its uniforms
    public void bindBlockEntity() {
        bind(this.blockEntity != null ? this.blockEntity : this.entity);
    }

    // Binds one program between two resource restores and refreshes its uniforms
    private void bind(GbufferPrograms.Entry program) {
        if (program == null) {
            return;
        }
        this.resourceRestorer.run();
        program.getProgram().getProgram().bind();
        this.resourceRestorer.run();
        program.getUniforms().update();
    }

    // Frees each distinct program once
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        if (!this.shared && this.blockEntity != null) {
            this.blockEntity.getProgram().destroy();
        }
        if (this.entity != null) {
            this.entity.getProgram().destroy();
        }
    }
}
