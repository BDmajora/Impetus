package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;

// File and line of an option declaration.
public class OptionLocation {
    // File path.
    private final AbsolutePackPath filePath;
    // Line index.
    private final int lineIndex;

    // Creates an option location from its file path and line index.
    public OptionLocation(AbsolutePackPath filePath, int lineIndex) {
        this.filePath = filePath;
        this.lineIndex = lineIndex;
    }

    // Returns the file path.
    public AbsolutePackPath getFilePath() {
        return filePath;
    }

    // Returns the line index.
    public int getLineIndex() {
        return lineIndex;
    }
}
