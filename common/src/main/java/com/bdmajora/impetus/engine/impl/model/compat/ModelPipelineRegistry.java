package com.bdmajora.impetus.engine.impl.model.compat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

// Registry of model pipelines in priority order.
public final class ModelPipelineRegistry {
    // Registered pipelines.
    private static final CopyOnWriteArrayList<ModdedModelPipeline> PIPELINES = new CopyOnWriteArrayList<>();

    static {
        register(VanillaForgeModelPipeline.INSTANCE);
        register(FrapiIndigoPipeline.INSTANCE);
    }

    // Prevents instantiation
    private ModelPipelineRegistry() {
    }

    // Adds a pipeline unless it is registered already and rejects null.
    public static void register(ModdedModelPipeline pipeline) {
        PIPELINES.addIfAbsent(Objects.requireNonNull(pipeline, "pipeline"));
    }

    // Returns a copy of the registered pipelines.
    public static List<ModdedModelPipeline> getPipelines() {
        return List.copyOf(PIPELINES);
    }

    // Returns the first available pipeline of a backend.
    public static Optional<ModdedModelPipeline> findAvailable(ModelPipelineBackend backend) {
        for (ModdedModelPipeline pipeline : PIPELINES) {
            if (pipeline.backend() == backend && pipeline.isAvailable()) {
                return Optional.of(pipeline);
            }
        }
        return Optional.empty();
    }
}
