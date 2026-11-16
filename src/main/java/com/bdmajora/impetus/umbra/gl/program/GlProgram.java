package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.umbra.gl.GlResource;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Linked GL program of a shader pack.
public class GlProgram extends GlResource {
    // Name of the program.
    private final String name;
    // Tessellation state of the program.
    private final boolean tessellated;

    // Wraps a linked program.
    GlProgram(int handle, String name, boolean tessellated) {
        this.name = name;
        this.tessellated = tessellated;
        setHandle(handle);
    }

    // Uses the program and records its tessellation state.
    public void bind() {
        LWJGL.glUseProgram(getGlId());
        ImmediateTessellation.programBound(getGlId(), this.tessellated);
    }

    // Uses no program.
    public void unbind() {
        LWJGL.glUseProgram(0);
        ImmediateTessellation.programBound(0, false);
    }

    // Returns the location of a uniform or minus one.
    public int getUniformLocation(CharSequence name) {
        return LWJGL.glGetUniformLocation(getGlId(), name);
    }

    // Returns the location of an attribute or minus one.
    public int getAttributeLocation(CharSequence name) {
        return LWJGL.glGetAttribLocation(getGlId(), name);
    }

    // Returns the name of the program.
    public String getName() {
        return this.name;
    }

    // Deletes the program.
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteProgram(getGlId());
    }
}
