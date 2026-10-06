package com.bdmajora.impetus.umbra.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.program.UmbraProgram;
import com.bdmajora.impetus.umbra.gl.program.ShaderProgramCompiler;
import com.bdmajora.impetus.umbra.gl.shader.ShaderMacros;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The per-pack compile step building every program a parsed ShaderPack declares, up front so a pack that will not build says so at load rather than mid-play; render thread only, owns the programs and teardown while UmbraRenderingPipeline owns targets and passes
public class UmbraPipeline {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<String, UmbraProgram> programs = new LinkedHashMap<>();

    public UmbraPipeline(ShaderPack pack) {
        compilePrograms(pack);
    }

    // Compiles every immediate-mode gbuffer program the pack ships
    private void compilePrograms(ShaderPack pack) {
        Map<String, String> defines = buildDefines(pack);
        Map<String, ProgramSource> declared = pack.getProgramSet().collectDeclaredPrograms();

        int compiled = 0;
        int failed = 0;

        for (Map.Entry<String, ProgramSource> entry : declared.entrySet()) {
            String name = entry.getKey();
            try {
                UmbraProgram program = ShaderProgramCompiler.compile(name, entry.getValue(), defines);
                this.programs.put(name, program);
                compiled++;
            } catch (Exception e) {
                failed++;
                LOGGER.error("Umbra: failed to compile program '{}': {}", name, e.getMessage());
            }
        }

    }

    // The shared #define set: MC_* macros plus the pack's option values
    private static Map<String, String> buildDefines(ShaderPack pack) {
        Map<String, String> defines = pack.getEnvironmentDefines();
        try {
            int major = LWJGL.glGetInteger(GL30.GL_MAJOR_VERSION);
            int minor = LWJGL.glGetInteger(GL30.GL_MINOR_VERSION);
            int glVersion = major * 100 + minor * 10;
            ShaderMacros.withGlInfo(defines, 120, glVersion,
                    LWJGL.glGetString(GL11.GL_VENDOR), LWJGL.glGetString(GL11.GL_RENDERER));
        } catch (Exception e) {
            // GL info is best-effort; a pack still compiles without the vendor/renderer macros.
            LOGGER.warn("Umbra: could not query GL info for shader macros: {}", e.getMessage());
        }
        return defines;
    }

    // Compiled program by name, or null
    public UmbraProgram getProgram(String name) {
        return this.programs.get(name);
    }

    // For the startup log
    public int getCompiledProgramCount() {
        return this.programs.size();
    }

    // Frees every GL resource this owns; render thread only, since a GL delete from another thread has no context and silently leaks
    public void destroy() {
        for (UmbraProgram program : this.programs.values()) {
            program.destroy();
        }
        this.programs.clear();
    }
}
