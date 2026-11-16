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

// Pipeline of a loaded shader pack.
public class UmbraPipeline {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    private final Map<String, UmbraProgram> programs = new LinkedHashMap<>();

    // Creates an umbra pipeline from its pack.
    public UmbraPipeline(ShaderPack pack) {
        compilePrograms(pack);
    }

    // Compiles the programs of a pack.
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

    // Returns the defines of a pack.
    private static Map<String, String> buildDefines(ShaderPack pack) {
        Map<String, String> defines = pack.getEnvironmentDefines();
        try {
            int major = LWJGL.glGetInteger(GL30.GL_MAJOR_VERSION);
            int minor = LWJGL.glGetInteger(GL30.GL_MINOR_VERSION);
            int glVersion = major * 100 + minor * 10;
            ShaderMacros.withGlInfo(defines, 120, glVersion,
                    LWJGL.glGetString(GL11.GL_VENDOR), LWJGL.glGetString(GL11.GL_RENDERER));
        } catch (Exception e) {
            LOGGER.warn("Umbra: could not query GL info for shader macros: {}", e.getMessage());
        }
        return defines;
    }

    // Returns a program by name or null.
    public UmbraProgram getProgram(String name) {
        return this.programs.get(name);
    }

    // Returns the compiled program count.
    public int getCompiledProgramCount() {
        return this.programs.size();
    }

    // Deletes the pipeline resources.
    public void destroy() {
        for (UmbraProgram program : this.programs.values()) {
            program.destroy();
        }
        this.programs.clear();
    }
}
