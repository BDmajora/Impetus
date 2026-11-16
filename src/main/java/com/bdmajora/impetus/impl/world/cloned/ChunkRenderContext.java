package com.bdmajora.impetus.impl.world.cloned;

import net.minecraft.world.gen.structure.StructureBoundingBox;
import com.bdmajora.impetus.engine.impl.util.position.SectionPos;

// Cloned sections and volume of one meshing task.
public class ChunkRenderContext {
    // Position of the center section.
    private final SectionPos sectionCoord;
    // Cloned sections around the section.
    private final ClonedChunkSection[] sections;
    // Volume of blocks to mesh.
    private final StructureBoundingBox volume;

    // Creates a chunk render context from its section coord and sections and volume.
    public ChunkRenderContext(SectionPos sectionCoord, ClonedChunkSection[] sections, StructureBoundingBox volume) {
        this.sectionCoord = sectionCoord;
        this.sections = sections;
        this.volume = volume;
    }

    // Returns the sections.
    public ClonedChunkSection[] getSections() {
        return this.sections;
    }

    // Returns the origin.
    public SectionPos getOrigin() {
        return this.sectionCoord;
    }

    // Returns the volume.
    public StructureBoundingBox getVolume() {
        return this.volume;
    }
}