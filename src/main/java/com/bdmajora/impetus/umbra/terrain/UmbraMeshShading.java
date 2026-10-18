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

// An Nvidium-ported shader pack's terrain programs as the mesh backend draws them: the cutout terrain program shades every opaque quad (per-quad alpha test from the material), gbuffers_water the translucent ones, and the shadow pair the shadow map; the port already holds the mesh stages, so building is only the per-load passes and the compiles, done on first use like the raster overrides so the frame pipeline and its custom uniforms already exist
public final class UmbraMeshShading implements MeshTerrainShading {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/UmbraMesh");

    static final int ACTIVE_UNIFORM_BLOCKS = 0x8A36;

    private static final Pattern THREADS = Pattern.compile("#define\\s+UMBRA_MESH_THREADS\\s+(\\d+)");
    private static final Pattern PORT_FORMAT = Pattern.compile("port format (\\d+)");

    // The ported pack whose programs failed to build and why; that pack instance goes to the raster path, a reload with new options tries again
    private static @Nullable ShaderPack failedPack;
    private static String failureReason = "";

    private final ShaderPack pack;
    private final MeshTerrainConfig config;

    private boolean built;
    private boolean failed;
    private @Nullable PackProgram opaque;
    private @Nullable PackProgram translucent;
    private @Nullable PackProgram shadowOpaque;
    private @Nullable PackProgram shadowTranslucent;

    // The program whose pass state is applied, undone by finish()
    private @Nullable PackProgram bound;

    // The mesh pipeline's specialisation defines (sorting, statistics) carry into the pack programs' task and mesh stages
    public UmbraMeshShading(ShaderPack pack, MeshTerrainConfig config) {
        this.pack = pack;
        this.config = config;
    }

    // Whether the mesh backend can draw this pack: it is an Nvidium port, its opaque terrain program has its ported stages, and it has not already failed to build
    public static boolean canRun(@Nullable ShaderPack pack) {
        return pack != null && pack != failedPack
                && pack.getProgramSet().get(ProgramId.TerrainCutoutMip).map(ProgramSource::hasMeshStages).orElse(false);
    }

    // Why the last ported pack fell back to the raster path, for the log and the debug screen
    public static String getFailureReason() {
        return failureReason;
    }

    // Why the mesh backend cannot draw this pack, for the debug screen: its programs failed to build, or porting left its terrain on OpenGL; empty when it can
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

    // Whether this pack's ported programs could not be built, so the renderer has to rebuild on the raster path
    public boolean hasFailed() {
        return this.failed;
    }

    // The two-uvec4 shader pack vertex
    @Override
    public int vertexStride() {
        return UmbraMeshVertex.STRIDE;
    }

    // gbuffers_terrain, or the shadow program, for every opaque quad
    @Override
    public boolean bindOpaque(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        return this.bind(phase == MeshDrawPhase.SHADOW ? this.shadowOpaqueProgram() : this.opaqueProgram(), phase, pass, matrices);
    }

    // gbuffers_water, or its shadow counterpart
    @Override
    public boolean bindTranslucent(MeshDrawPhase phase, @Nullable TerrainRenderPass pass, ChunkRenderMatrices matrices) {
        this.ensureBuilt();
        return this.bind(phase == MeshDrawPhase.SHADOW ? this.shadowTranslucent : this.translucent, phase, pass, matrices);
    }

    // Hands the pass state back to the pipeline's defaults
    @Override
    public void finish() {
        if (this.bound != null) {
            this.bound.program.getInterface().restoreState();
            this.bound = null;
        }
    }

    // Whether the port carries shadow programs
    @Override
    public boolean hasShadowPrograms() {
        return this.shadowOpaqueProgram() != null;
    }

    // Frees every program
    @Override
    public void delete() {
        for (PackProgram program : new PackProgram[]{this.opaque, this.translucent, this.shadowOpaque, this.shadowTranslucent}) {
            if (program != null) {
                program.program.delete();
            }
        }
        this.opaque = this.translucent = this.shadowOpaque = this.shadowTranslucent = null;
    }

    private @Nullable PackProgram opaqueProgram() {
        this.ensureBuilt();
        return this.opaque;
    }

    private @Nullable PackProgram shadowOpaqueProgram() {
        this.ensureBuilt();
        return this.shadowOpaque;
    }

    // Binds a program for a phase; the first bind since finish() applies the pass's state (gbuffer, blend, pack samplers, uniforms), which then survives the occlusion programs the pipeline binds in between
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

    // Facing groups the eye cannot see may be skipped unless the option is off or the pack draws the solid layer's back faces
    private static boolean cullsFacing() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        return ImpetusVintage.options().performance.useBlockFaceCulling
                && (pipeline == null || pipeline.shouldCullBackFaces(BlockRenderLayer.SOLID.ordinal()));
    }

    // Builds every program once; a failure frees what was built, remembers the pack, and leaves the renderer to rebuild on the raster path
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

    // A program the backend cannot do without
    private static PackProgram require(@Nullable PackProgram program, String name) {
        if (program == null) {
            throw new UnsupportedProgramException("no " + name + " program");
        }
        return program;
    }

    // One ported program linked from its task, mesh and fragment stages, or null when the pack has none; a task stage is compiled once per variant and shared
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

        // Unified uniform memory reads every bound block by address while the mesh draws run, so beyond the two scene blocks (the task stage's and the mesh stage's prefixed copy) a pack block of its own would read the scene or nothing
        if (LWJGL.glGetProgrami(program.handle(), ACTIVE_UNIFORM_BLOCKS) > 2) {
            program.delete();
            throw new UnsupportedProgramException(id.getSourceName() + " declares its own uniform blocks");
        }

        UmbraTerrainProgramOverride.configureUniforms(program, id.getSourceName());
        return new PackProgram(program, LWJGL.glGetUniformLocation(program.handle(), "umbra_TaskMode"),
                LWJGL.glGetUniformLocation(program.handle(), "umbra_CullFacing"));
    }

    // The lanes per meshlet the port chose, after checking it was made for this loader
    static int meshThreads(String name, String mesh) {
        Matcher format = PORT_FORMAT.matcher(mesh);
        if (!format.find() || Integer.parseInt(format.group(1)) != MeshShaderTranslator.PORT_FORMAT) {
            throw new UnsupportedProgramException(name + " was ported by another version of Impetus; port the pack again");
        }
        Matcher threads = THREADS.matcher(mesh);
        if (!threads.find()) {
            throw new UnsupportedProgramException(name + ".mesh has no UMBRA_MESH_THREADS");
        }
        return Integer.parseInt(threads.group(1));
    }

    // Opaque quads of every pass share one program, so the pass's alpha test becomes a per-quad choice: cutout materials get the cutout program's test, solid ones none unless the pack wrote alphaTest directives
    static String opaqueAlphaTest(UmbraTerrainProgramOverride.PreparedProgram prepared) {
        String cutout = prepared.alphaTestSnippet();
        String solid = prepared.alphaTest().hasDirectives() ? cutout : "";
        if (cutout.equals(solid)) {
            return cutout;
        }
        return "    if (iris_AlphaCutoff > 0.0) {\n" + cutout + "    } else {\n" + solid + "    }\n";
    }

    // A linked program with its task-mode and facing-cull uniforms, -1 where the stage compiled them away
    private record PackProgram(GlProgram<ChunkShaderInterface> program, int taskMode, int cullFacing) {
    }
}
