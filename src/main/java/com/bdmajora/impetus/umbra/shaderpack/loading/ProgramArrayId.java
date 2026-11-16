package com.bdmajora.impetus.umbra.shaderpack.loading;

// Choices of the program array id setting.
public enum ProgramArrayId {
    // Sets up.
    Setup("setup", 100),
    // Programs that run at the start of a frame.
    Begin("begin", 100),
    // Programs that run after the shadow pass.
    ShadowComposite("shadowcomp", 100),
    // Programs that run before the translucent pass.
    Prepare("prepare", 100),
    // Programs that run after the opaque pass.
    Deferred("deferred", 100),
    // Programs that run after the translucent pass.
    Composite("composite", 100);

    // Base file name.
    private final String baseName;
    // Number of numbered programs.
    private final int numPrograms;

    // Creates a program array id from its base name and num programs.
    ProgramArrayId(String baseName, int numPrograms) {
        this.baseName = baseName;
        this.numPrograms = numPrograms;
    }

    // Returns the base name.
    public String getBaseName() {
        return this.baseName;
    }

    // Returns the num programs.
    public int getNumPrograms() {
        return this.numPrograms;
    }

    // Returns the file name of a numbered program.
    public String getSourceName(int index) {
        if (index < 0 || index >= this.numPrograms) {
            throw new IndexOutOfBoundsException("Program index " + index + " out of range for " + this.baseName);
        }
        return index == 0 ? this.baseName : this.baseName + index;
    }
}
