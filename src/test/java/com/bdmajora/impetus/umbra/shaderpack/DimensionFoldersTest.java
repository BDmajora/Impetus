package com.bdmajora.impetus.umbra.shaderpack;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramArrayId;
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DimensionFoldersTest {
    private static Map<AbsolutePackPath, String> files(String... pathsAndContents) {
        Map<AbsolutePackPath, String> files = new HashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            files.put(AbsolutePackPath.fromAbsolutePath(pathsAndContents[i]), pathsAndContents[i + 1]);
        }
        return files;
    }

    @Test
    void withoutAPropertiesFileTheWorldFoldersServeTheirDimensions() {
        DimensionFolders folders = DimensionFolders.from(files(
                "/world0/final.fsh", "", "/world-1/final.fsh", "", "/world1/final.fsh", "", "/world7/final.fsh", "",
                "/final.fsh", "", "/lib/x.glsl", ""), Map.of());
        assertEquals("world0", folders.baseFolder());
        assertEquals("world0", folders.folderFor(0, "overworld"));
        assertEquals("world-1", folders.folderFor(-1, "the_nether"));
        assertEquals("world1", folders.folderFor(1, "the_end"));
        // OptiFine's numbered folders reach modded dimensions; one without a folder takes the base set
        assertEquals("world7", folders.folderFor(7, "twilight_forest"));
        assertNull(folders.folderFor(9, "aether"));

        // A pack shipping only the root has no base folder at all
        DimensionFolders rootOnly = DimensionFolders.from(files("/final.fsh", ""), Map.of());
        assertNull(rootOnly.baseFolder());
        assertNull(rootOnly.folderFor(-1, "the_nether"));
    }

    @Test
    void aPropertiesFileReplacesTheDefaultsAndIsPreprocessed() {
        DimensionFolders folders = DimensionFolders.from(files(
                "/dimension.properties", String.join("\n",
                        "dimension.overworld = minecraft:overworld *",
                        "#ifdef NETHER_FOLDER",
                        "dimension.nether = the_nether",
                        "#endif",
                        "dimension.gone = minecraft:the_end",
                        "unrelated = 1"),
                "/overworld/final.fsh", "", "/nether/final.fsh", "", "/world7/final.fsh", ""),
                Map.of("NETHER_FOLDER", ""));
        assertEquals("overworld", folders.baseFolder());
        assertEquals("nether", folders.folderFor(-1, "the_nether"));
        // A mapped folder the pack does not have is ignored, and numbered folders only count without the file
        assertNull(folders.folderFor(1, "the_end"));
        assertNull(folders.folderFor(7, "twilight_forest"));

        // Gated off, the Nether falls to the base set
        assertNull(DimensionFolders.from(files("/dimension.properties", "#ifdef NETHER_FOLDER\ndimension.nether = the_nether\n#endif\ndimension.overworld = *",
                "/nether/final.fsh", "", "/overworld/final.fsh", ""), Map.of()).folderFor(-1, "the_nether"));

        // A file Properties cannot read falls back to the world folders
        DimensionFolders broken = DimensionFolders.from(files("/dimension.properties", "dimension.x = \\uZZZZ", "/world-1/final.fsh", ""), Map.of());
        assertEquals("world-1", broken.folderFor(-1, "the_nether"));
    }

    @Test
    void aDimensionWithItsOwnFolderGetsThatFolderAlone() {
        ShaderPack pack = new ShaderPack(files(
                "/composite.vsh", "// root composite", "/composite.fsh", "void main() {}",
                "/composite1.vsh", "// root only", "/composite1.fsh", "void main() {}",
                "/world0/composite.vsh", "// overworld composite", "/world0/composite.fsh", "void main() {}",
                "/world-1/composite.vsh", "// nether composite", "/world-1/composite.fsh", "void main() {}",
                "/world-1/final.vsh", "void main() {}", "/world-1/final.fsh", "void main() {}"));

        // The base set reads world0 ahead of the root
        ProgramSet base = pack.getProgramSet();
        assertEquals("// overworld composite", base.get(ProgramArrayId.Composite, 0).orElseThrow().getVertexSource().orElseThrow());
        assertTrue(base.get(ProgramArrayId.Composite, 1).isPresent());
        assertFalse(pack.selectDimension(0, "overworld"));

        // The Nether's folder is the whole program set: no root composite1, but its own final
        assertTrue(pack.selectDimension(-1, "the_nether"));
        ProgramSet nether = pack.getProgramSet();
        assertEquals("// nether composite", nether.get(ProgramArrayId.Composite, 0).orElseThrow().getVertexSource().orElseThrow());
        assertTrue(nether.get(ProgramArrayId.Composite, 1).isEmpty());
        assertTrue(nether.get(ProgramId.Final).isPresent());
        assertFalse(pack.selectDimension(-1, "the_nether"));

        // Back home is the base set again, and the Nether's set is kept for the next visit; the End has no folder so it shares the base
        assertTrue(pack.selectDimension(0, "overworld"));
        assertSame(base, pack.getProgramSet());
        assertFalse(pack.selectDimension(1, "the_end"));
        pack.selectDimension(-1, "the_nether");
        assertSame(nether, pack.getProgramSet());
    }

    @Test
    void umbraFollowsTheWorldAndRebuildsWhenTheSetChanges() {
        ShaderPack pack = new ShaderPack(files(
                "/final.vsh", "void main() {}", "/final.fsh", "void main() {}",
                "/world1/final.vsh", "void main() {}", "/world1/final.fsh", "void main() {}"));
        Mixins.set(Umbra.class, "currentPack", pack);
        Mixins.set(Umbra.class, "pipelineNeedsInit", false);
        try {
            // Leaving to the title screen changes nothing
            Umbra.onWorldChanged(0, null);
            assertFalse(Mixins.<Boolean>get(Umbra.class, "pipelineNeedsInit"));
            Umbra.onWorldChanged(0, "overworld");
            assertFalse(Mixins.<Boolean>get(Umbra.class, "pipelineNeedsInit"));
            // Into the End, whose folder is its own program set
            Umbra.onWorldChanged(1, "the_end");
            assertTrue(Mixins.<Boolean>get(Umbra.class, "pipelineNeedsInit"));
            // Without a pack the dimension is still remembered for the next one loaded
            Mixins.set(Umbra.class, "currentPack", null);
            Umbra.onWorldChanged(-1, "the_nether");
            assertEquals("the_nether", Mixins.get(Umbra.class, "dimensionName"));
        } finally {
            Mixins.set(Umbra.class, "currentPack", null);
            Mixins.set(Umbra.class, "pipelineNeedsInit", false);
            Umbra.onWorldChanged(0, null);
        }
    }
}
