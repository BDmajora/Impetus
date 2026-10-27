package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.pipeline.GbufferPrograms;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Covers the entity and block entity shadow program pair
class ShadowCasterProgramsTest {
    // Source of the plain shadow program
    private final ProgramSource shadow = source("shadow");
    // Source of the shadow_entities program
    private final ProgramSource entities = source("shadow_entities");
    // Source of the shadow_block program
    private final ProgramSource blocks = source("shadow_block");
    // Sources passed to the compiler in order
    private final List<ProgramSource> compiled = new ArrayList<>();
    // Entry the compiler returns for each source with a missing key meaning a failed compile
    private final Map<ProgramSource, GbufferPrograms.Entry> entries = new HashMap<>();
    // Events of binds and restores and uniform updates in order
    private final List<String> events = new ArrayList<>();
    // Restorer that records each run
    private final Runnable restorer = () -> this.events.add("restore");

    // Returns a source with only a name
    private static ProgramSource source(String name) {
        return new ProgramSource(name, "void main() {}", null, null, null, "void main() {}", null);
    }

    // Returns an entry whose bind and uniform update are recorded under a name
    private GbufferPrograms.Entry entry(String name) {
        GbufferPrograms.Entry entry = mock(GbufferPrograms.Entry.class);
        UmbraProgram program = mock(UmbraProgram.class);
        GlProgram glProgram = mock(GlProgram.class);
        ProgramUniforms uniforms = mock(ProgramUniforms.class);
        when(entry.getProgram()).thenReturn(program);
        when(program.getProgram()).thenReturn(glProgram);
        when(entry.getUniforms()).thenReturn(uniforms);
        Mockito.doAnswer(inv -> this.events.add("bind " + name)).when(glProgram).bind();
        Mockito.doAnswer(inv -> this.events.add("update " + name)).when(uniforms).update();
        Mockito.doAnswer(inv -> this.events.add("destroy " + name)).when(program).destroy();
        return entry;
    }

    // Returns a compiler that records each source and answers from the entry map
    private Function<ProgramSource, GbufferPrograms.Entry> compiler() {
        return source -> {
            this.compiled.add(source);
            return this.entries.get(source);
        };
    }

    // Checks that missing sources fall back to the shadow source which compiles once and is shared
    @Test
    void missingSourcesFallBackToTheShadowProgramAndShareIt() {
        this.entries.put(this.shadow, entry("shadow"));
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, null, null, compiler(), this.restorer);
        assertEquals(List.of(this.shadow), this.compiled);
        assertTrue(programs.hasEntityProgram());
        assertTrue(programs.hasBlockEntityProgram());
        programs.bindEntity();
        programs.bindBlockEntity();
        assertEquals(List.of("restore", "bind shadow", "restore", "update shadow",
                "restore", "bind shadow", "restore", "update shadow"), this.events);
        this.events.clear();
        programs.destroy();
        assertEquals(List.of("destroy shadow"), this.events);
    }

    // Checks that the same source object for both compiles once and is shared
    @Test
    void oneSourceForBothCompilesOnce() {
        this.entries.put(this.entities, entry("entities"));
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, this.entities, this.entities, compiler(), this.restorer);
        assertEquals(List.of(this.entities), this.compiled);
        programs.destroy();
        assertEquals(List.of("destroy entities"), this.events);
    }

    // Checks that distinct sources compile separately and bind and destroy separately
    @Test
    void distinctSourcesCompileBindAndDestroySeparately() {
        this.entries.put(this.entities, entry("entities"));
        this.entries.put(this.blocks, entry("blocks"));
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, this.entities, this.blocks, compiler(), this.restorer);
        assertEquals(List.of(this.entities, this.blocks), this.compiled);
        programs.bindBlockEntity();
        assertEquals(List.of("restore", "bind blocks", "restore", "update blocks"), this.events);
        this.events.clear();
        programs.destroy();
        assertEquals(List.of("destroy blocks", "destroy entities"), this.events);
        this.events.clear();
        programs.destroy();
        assertTrue(this.events.isEmpty());
    }

    // Checks that a missing block source falls back to the shadow source which compiles separately from the entity source
    @Test
    void aMissingBlockSourceCompilesTheShadowSource() {
        this.entries.put(this.entities, entry("entities"));
        this.entries.put(this.shadow, entry("shadow"));
        new ShadowCasterPrograms(this.shadow, this.entities, null, compiler(), this.restorer);
        assertEquals(List.of(this.entities, this.shadow), this.compiled);
    }

    // Checks that a failed block program falls back to the entity program
    @Test
    void aFailedBlockProgramFallsBackToTheEntityProgram() {
        this.entries.put(this.entities, entry("entities"));
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, this.entities, this.blocks, compiler(), this.restorer);
        assertTrue(programs.hasBlockEntityProgram());
        programs.bindBlockEntity();
        assertEquals(List.of("restore", "bind entities", "restore", "update entities"), this.events);
        this.events.clear();
        programs.destroy();
        assertEquals(List.of("destroy entities"), this.events);
    }

    // Checks that a failed entity program disables entity binds and logs a warning while the block program still works
    @Test
    void aFailedEntityProgramDisablesOnlyEntities() {
        Logger original = Statics.get(ShadowCasterPrograms.class, "LOGGER");
        Logger logger = mock(Logger.class);
        Statics.set(ShadowCasterPrograms.class, "LOGGER", logger);
        try {
            this.entries.put(this.blocks, entry("blocks"));
            ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, this.entities, this.blocks, compiler(), this.restorer);
            verify(logger).warn(anyString());
            assertFalse(programs.hasEntityProgram());
            assertTrue(programs.hasBlockEntityProgram());
            programs.bindEntity();
            assertTrue(this.events.isEmpty());
            programs.bindBlockEntity();
            assertEquals(List.of("restore", "bind blocks", "restore", "update blocks"), this.events);
            this.events.clear();
            programs.destroy();
            assertEquals(List.of("destroy blocks"), this.events);
        } finally {
            Statics.set(ShadowCasterPrograms.class, "LOGGER", original);
        }
    }

    // Checks that with both programs failed nothing binds and destroy does nothing
    @Test
    void bothProgramsFailedBindsNothing() {
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, this.entities, this.blocks, compiler(), this.restorer);
        assertFalse(programs.hasEntityProgram());
        assertFalse(programs.hasBlockEntityProgram());
        programs.bindEntity();
        programs.bindBlockEntity();
        programs.destroy();
        assertTrue(this.events.isEmpty());
    }

    // Checks that the restorer runs before the bind and again before the uniform update
    @Test
    void theRestorerSurroundsTheBind() {
        GbufferPrograms.Entry entry = entry("shadow");
        this.entries.put(this.shadow, entry);
        Runnable restorer = mock(Runnable.class);
        ShadowCasterPrograms programs = new ShadowCasterPrograms(this.shadow, null, null, compiler(), restorer);
        programs.bindEntity();
        InOrder order = Mockito.inOrder(restorer, entry.getProgram().getProgram(), entry.getUniforms());
        order.verify(restorer).run();
        order.verify(entry.getProgram().getProgram()).bind();
        order.verify(restorer).run();
        order.verify(entry.getUniforms()).update();
    }

    // Checks that null arguments are rejected before anything compiles
    @Test
    void nullArgumentsAreRejectedBeforeCompiling() {
        Function<ProgramSource, GbufferPrograms.Entry> compiler = compiler();
        assertThrows(NullPointerException.class, () -> new ShadowCasterPrograms(null, this.entities, this.blocks, compiler, this.restorer));
        assertThrows(NullPointerException.class, () -> new ShadowCasterPrograms(this.shadow, null, null, null, this.restorer));
        assertThrows(NullPointerException.class, () -> new ShadowCasterPrograms(this.shadow, null, null, compiler, null));
        assertTrue(this.compiled.isEmpty());
    }

    // Checks that a throwing compiler propagates its failure
    @Test
    void aThrowingCompilerPropagates() {
        Runnable restorer = mock(Runnable.class);
        assertThrows(IllegalStateException.class, () -> new ShadowCasterPrograms(this.shadow, null, null,
                source -> {
                    throw new IllegalStateException("compiler");
                }, restorer));
        verifyNoInteractions(restorer);
        verify(restorer, never()).run();
    }
}
