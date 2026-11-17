package com.bdmajora.impetus.umbra.shaderpack.loading;

// Numbered program chains in the order they run.
public enum ProgramArrayId {
    // Programs that run once after the pack loads.
    Setup("setup", 100),
    // Programs that run at the start of a frame.
    Begin("begin", 100),
    // Programs that run after the shadow pass.
    ShadowComposite("shadowcomp", 100),
    // Programs run after the shadow composites and before the gbuffers.
    Prepare("prepare", 100),
    // Programs that run after the opaque pass.
    Deferred("deferred", 100),
    // Programs that run after the translucent pass.
    Composite("composite", 100);

    // Base file name.
    private final String baseName;
    // Number of numbered programs.
    private final int numPrograms;

    // Creates a chain from its file name stem and the most programs it can hold.
    ProgramArrayId(String baseName, int numPrograms) {
        this.baseName = baseName;
        this.numPrograms = numPrograms;
    }

    // Returns the file name stem of the chain's first program.
    public String getBaseName() {
        return this.baseName;
    }

    // Returns the most programs the chain can hold.
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
