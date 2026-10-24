package com.bdmajora.impetus.engine.impl.asm.elsewhere;

// Top level with only public types around it, so a copy in another loader links without nest or inner class checks
public class Plain implements Elsewhere.Visible {
    @Override
    public int visible() {
        return 8;
    }
}
