package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import java.util.List;

// Hook that ticks the visible sections.
public interface SectionTicker {
    // Ticks the visible sections.
    void tickVisibleRenders();
    // Receives the new render lists.
    void onRenderListUpdated(List<ChunkRenderList> renderLists);

    // Returns the text shown on the debug screen.
    default String getDebugString() {
        return "";
    }
}
