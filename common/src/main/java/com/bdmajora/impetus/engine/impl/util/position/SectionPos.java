package com.bdmajora.impetus.engine.impl.util.position;

import org.joml.Vector3i;

public final class SectionPos extends Vector3i {
    public SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    // First block x in the section
    public int minX() {
        return x() * 16;
    }

    // First block y
    public int minY() {
        return y() * 16;
    }

    // First block z
    public int minZ() {
        return z() * 16;
    }

    // Returns the last block x.
    public int maxX() {
        return minX() + 15;
    }

    // Returns the last block y.
    public int maxY() {
        return minY() + 15;
    }

    // Returns the last block z.
    public int maxZ() {
        return minZ() + 15;
    }
}
