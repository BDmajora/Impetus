package com.bdmajora.impetus.umbra.terrain;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gl.shader.GlProgram;
import com.bdmajora.impetus.engine.impl.gl.shader.GlShader;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderParser;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderType;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderInterface;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderTextureSlot;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshDrawPhase;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;
import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.shaderpack.preprocessor.GlslPreprocessor;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator;
import com.bdmajora.impetus.umbra.terrain.mesh.MeshShaderTranslator.UnsupportedProgramException;
import com.bdmajora.impetus.umbra.util.SafeInt;
import com.bdmajora.impetus.umbra.vertices.UmbraMeshVertex;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Mesh shader terrain shading built from the Nvidium port of a shader pack.
public final class UmbraMeshShading implements MeshTerrainShading {
    // Logger of build failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/UmbraMesh");

    // GL enum of the active uniform block count.
    static final int ACTIVE_UNIFORM_BLOCKS = 0x8A36;
    // Uniform blocks the engine gives a ported program.
    static final int ENGINE_UNIFORM_BLOCKS = 3;

    // Pattern of the thread count define of a ported mesh shader.
    private static final Pattern THREADS = Pattern.compile("#define\\s+UMBRA_MESH_THREADS\\s+(\\d+)");
    // Pattern of the port format marker.
    private static final Pattern PORT_FORMAT = Pattern.compile("port format (\\d+)");

    // Pack whose programs failed to build or null.
    private static @Nullable ShaderPack failedPack;
    // Reason of the last build failure.
    private static String failureReason = "";

    // Pack the programs come from.
    private final ShaderPack pack;
    // Mesh terrain settings.
    private final MeshTerrainConfig config;

    // Build attempt state of the programs.
    private boolean built;
    // Failed state of the build.
    private boolean failed;
    // Opaque terrain program or null.
    private @Nullable PackProgram opaque;
    // Translucent terrain program or null.
    private @Nullable PackProgram translucent;
    // Opaque shadow program or null.
    private @Nullable PackProgram shadowOpaque;
    // Translucent shadow program or null.
    private @Nullable PackProgram shadowTranslucent;

    // Program whose state is set up or null.
    private @Nullable PackProgram bound;

    // Creates shading for a pack whose programs build on first use.
    public UmbraMeshShading(ShaderPack pack, MeshTerrainConfig config) {
        this.pack = pack;
        this.config = config;
    }

    // Returns whether a pack has a ported terrain program and did not fail before.
    public static boolean canRun(@Nullable ShaderPack pack) {
        return pack != null && pack != failedPack
                && pack.getProgramSet().get(ProgramId.TerrainCutoutMip).map(ProgramSource::hasMeshStages).orElse(false);
    }

    // Returns the reason of the last build failure.
    public static String getFailureReason() {
        return failureReason;
    }

    // Returns why a pack cannot run on mesh shaders or an empty string.
    public static String whyNot(@Nullable ShaderPack pack) {
        if (pack != null && pack == failedPack) {
            return failureReason;
        }
        if (canRun(pack)) {
            return "";
        }
        String note = Umbra.getNvidiumPortNote();
        return note.isEmpty() ? "the pack's terrain program has no Nvidium port" : note;
    }

    // Returns whether the build failed.
    public boolean hasFailed() {
        return this.failed;
    }

    // Returns the bytes of one vertex.
    @Override
    public int vertexStride() {
        return UmbraMeshVertex.STRIDE;
    }

    // Binds the opaque program of a phase and returns whether one exists.
    @Override
    public boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        return this.bind(phase == MeshDrawPhase.SHADOW ? this.shadowOpaqueProgram() : this.opaqueProgram(), phase, pass, matrices);
    }

    // Binds the translucent program of a phase and returns whether one exists.
    @Override
    public boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.ensureBuilt();
        return this.bind(phase == MeshDrawPhase.SHADOW ? this.shadowTranslucent : this.translucent, phase, pass, matrices);
    }

    // Restores the state of the bound program.
    @Override
    public void finish() {
        if (this.bound != null) {
            this.bound.program.getInterface().restoreState();
            this.bound = null;
        }
    }

    // Returns whether the pack has shadow programs.
    @Override
    public boolean hasShadowPrograms() {
        return this.shadowOpaqueProgram() != null;
    }

    // Deletes every program and forgets the bound one.
    @Override
    public void delete() {
        this.bound = null;
        for (PackProgram program : new PackProgram[]{this.opaque, this.translucent, this.shadowOpaque, this.shadowTranslucent}) {
            if (program != null) {
                program.program.delete();
            }
        }
        this.opaque = this.translucent = this.shadowOpaque = this.shadowTranslucent = null;
    }

    // Returns the opaque program after building.
    private @Nullable PackProgram opaqueProgram() {
        this.ensureBuilt();
        return this.opaque;
    }

    // Returns the opaque shadow program after building.
    private @Nullable PackProgram shadowOpaqueProgram() {
        this.ensureBuilt();
        return this.shadowOpaque;
    }

    // Binds a program and sets up its state once and uploads the phase uniforms.
    private boolean bind(@Nullable PackProgram program, MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        if (program == null || pass == null) {
            return false;
        }

        program.program.bind();

        if (this.bound != program) {
            this.finish();
            ChunkShaderInterface shader = program.program.getInterface();
            shader.setupState(pass);
            shader.setProjectionMatrix(matrices.projection());
            shader.setModelViewMatrix(matrices.modelView());
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
            this.bound = program;
        }

        if (program.taskMode >= 0) {
            LWJGL.glUniform1i(program.taskMode, switch (phase) {
                case MAIN -> 0;
                case TEMPORAL -> 1;
                case SHADOW -> 2;
            });
        }
        if (program.cullFacing >= 0) {
            LWJGL.glUniform1i(program.cullFacing, phase != MeshDrawPhase.SHADOW && cullsFacing() ? 1 : 0);
        }
        return true;
    }

    // Returns whether back facing quads are culled.
    private static boolean cullsFacing() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        return ImpetusVintage.options().performance.useBlockFaceCulling
                && (pipeline == null || pipeline.shouldCullBackFaces(BlockRenderLayer.SOLID.ordinal()));
    }

    // Builds every program once and deletes them all and marks the pack failed when one fails.
    private void ensureBuilt() {
        if (this.built) {
            return;
        }
        this.built = true;

        List<PackProgram> programs = new ArrayList<>();
        Map<String, GlShader> tasks = new HashMap<>();
        try {
            this.opaque = require(build(ProgramId.TerrainCutoutMip, false, tasks), "gbuffers_terrain");
            programs.add(this.opaque);
            this.translucent = require(build(ProgramId.Water, true, tasks), "gbuffers_water");
            programs.add(this.translucent);
            this.shadowOpaque = build(ProgramId.ShadowCutout, false, tasks);
            if (this.shadowOpaque != null) {
                programs.add(this.shadowOpaque);
                this.shadowTranslucent = build(ProgramId.ShadowWater, true, tasks);
            }
            LOGGER.info("[Umbra] Nvidium-ported terrain programs built{}", this.shadowOpaque != null ? ", shadow pass included" : "");
        } catch (RuntimeException e) {
            programs.forEach(program -> program.program.delete());
            this.opaque = this.translucent = this.shadowOpaque = this.shadowTranslucent = null;
            this.failed = true;
            failedPack = this.pack;
            failureReason = String.valueOf(e.getMessage());
            LOGGER.warn("[Umbra] The Nvidium port's terrain cannot run ({}); rebuilding on the OpenGL path", failureReason);
        } finally {
            tasks.values().forEach(GlShader::delete);
        }
    }

    // Returns a program and rejects a missing one.
    private static PackProgram require(@Nullable PackProgram program, String name) {
        if (program == null) {
            throw new UnsupportedProgramException("no " + name + " program");
        }
        return program;
    }

    // Builds the program of a pack program and returns null when the pack has none.
    private @Nullable PackProgram build(ProgramId id, boolean translucent, Map<String, GlShader> tasks) {
        UmbraTerrainProgramOverride.PreparedProgram prepared = UmbraTerrainProgramOverride.prepareMesh(this.pack, id);
        if (prepared == null) {
            return null;
        }

        String name = prepared.shaderName();
        int threads = meshThreads(name, prepared.vertex());
        ShaderConstants.Builder constants = this.config.shaderConstants().add("UMBRA_MESH_QUADS", String.valueOf(threads / 4));
        if (translucent) {
            constants.add("UMBRA_TRANSLUCENT", "1");
        }
        ShaderConstants stageConstants = constants.build();

        String mesh = GlslPreprocessor.finalizeForDriver(name + ".mesh",
                ShaderParser.parseShader(prepared.vertex(), ShaderLoader::getShaderSource, stageConstants));
        String fragment = GlslPreprocessor.finalizeForDriver(name + ".fsh",
                prepared.fragment(translucent ? prepared.alphaTestSnippet() : opaqueAlphaTest(prepared)));

        String taskPath = translucent ? "impetus:mesh/pack/translucent.task" : "impetus:mesh/pack/terrain.task";
        GlShader task = tasks.get(taskPath + threads);
        if (task == null) {
            task = ShaderLoader.loadShader(ShaderType.TASK, taskPath, stageConstants);
            tasks.put(taskPath + threads, task);
        }

        List<GlShader> shaders = new ArrayList<>();
        GlProgram<ChunkShaderInterface> program;
        try {
            shaders.add(new GlShader(ShaderType.MESH, name + ".mesh", mesh));
            shaders.add(new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragment));

            var builder = GlProgram.builder("impetus:umbra_mesh_" + id.getSourceName()).attachShader(task);
            shaders.forEach(builder::attachShader);
            program = builder.link(context -> new UmbraTerrainShaderInterface(context, prepared.drawBuffers(), prepared.blendState(),
                    prepared.alphaTest(), false));
        } finally {
            shaders.forEach(GlShader::delete);
        }

        // Deletes the linked program when its checks or uniform setup fail
        try {
            if (LWJGL.glGetProgrami(program.handle(), ACTIVE_UNIFORM_BLOCKS) > ENGINE_UNIFORM_BLOCKS) {
                throw new UnsupportedProgramException(id.getSourceName() + " declares its own uniform blocks");
            }

            UmbraTerrainProgramOverride.configureUniforms(program, id.getSourceName());
            return new PackProgram(program, LWJGL.glGetUniformLocation(program.handle(), "umbra_TaskMode"),
                    LWJGL.glGetUniformLocation(program.handle(), "umbra_CullFacing"));
        } catch (RuntimeException | Error e) {
            program.delete();
            throw e;
        }
    }

    // Returns the thread count of a ported mesh shader and rejects another port format.
    static int meshThreads(String name, String mesh) {
        Matcher format = PORT_FORMAT.matcher(mesh);
        if (!format.find() || SafeInt.parse(format.group(1)) != MeshShaderTranslator.PORT_FORMAT) {
            throw new UnsupportedProgramException(name + " was ported by another version of Impetus; port the pack again");
        }
        Matcher threads = THREADS.matcher(mesh);
        if (!threads.find()) {
            throw new UnsupportedProgramException(name + ".mesh has no UMBRA_MESH_THREADS");
        }
        return SafeInt.parse(threads.group(1));
    }

    // Returns the alpha test of the opaque program.
    static String opaqueAlphaTest(UmbraTerrainProgramOverride.PreparedProgram prepared) {
        String cutout = prepared.alphaTestSnippet();
        String solid = prepared.alphaTest().hasDirectives() ? cutout : "";
        if (cutout.equals(solid)) {
            return cutout;
        }
        return "    if (iris_AlphaCutoff > 0.0) {\n" + cutout + "    } else {\n" + solid + "    }\n";
    }

    // Linked program with its phase uniform locations.
    private record PackProgram(GlProgram<ChunkShaderInterface> program, int taskMode, int cullFacing) {
    }
}
