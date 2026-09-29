package com.bdmajora.impetus.engine.impl.model.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelPipelineTest {
    @Test
    void registryPrefersAvailablePipelines() {
        assertEquals(2, ModelPipelineRegistry.getPipelines().size());
        ModelPipelineRegistry.register(VanillaForgeModelPipeline.INSTANCE);
        assertEquals(2, ModelPipelineRegistry.getPipelines().size());
        assertSame(VanillaForgeModelPipeline.INSTANCE, ModelPipelineRegistry.findAvailable(ModelPipelineBackend.VANILLA_FORGE).orElseThrow());
        assertTrue(ModelPipelineRegistry.findAvailable(ModelPipelineBackend.FRAPI_INDIGO).isEmpty());
        assertTrue(VanillaForgeModelPipeline.INSTANCE.canHandle(new Object()));
        assertEquals("", VanillaForgeModelPipeline.INSTANCE.getUnavailableReason());
        assertFalse(FrapiIndigoPipeline.INSTANCE.isAvailable());
        assertFalse(FrapiIndigoPipeline.INSTANCE.canHandle(new Object()));
        assertEquals(ModelPipelineBackend.FRAPI_INDIGO, FrapiIndigoPipeline.INSTANCE.backend());
        assertFalse(FrapiIndigoPipeline.INSTANCE.getUnavailableReason().isEmpty());
        assertEquals("FRAPI/Indigo", ModelPipelineBackend.FRAPI_INDIGO.getDisplayName());
    }
}
