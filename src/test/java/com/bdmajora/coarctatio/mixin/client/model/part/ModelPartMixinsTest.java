package com.bdmajora.coarctatio.mixin.client.model.part;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.client.model.CanonicalStringMap;
import com.bdmajora.coarctatio.dedup.ModelCaches;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ModelPartMixinsTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshPools() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
        ModelCaches.open();
    }

    @Test
    void aFacesUvArrayComesFromThePool() {
        BlockFaceUVMixin face = Mixins.instance(BlockFaceUVMixin.class);
        float[] pooled = ModelCaches.FACE_UVS.deduplicate(new float[] {0f, 0f, 16f, 16f});

        Mixins.set(face, "uvs", new float[] {0f, 0f, 16f, 16f});
        Mixins.call(face, "coarctatio$poolUvs", new float[] {0f, 0f, 16f, 16f}, 0, Mixins.ci());
        assertSame(pooled, Mixins.get(face, "uvs"));

        // The setter only fills in a missing array, so the pooled instance goes in its place
        Mixins.set(face, "uvs", new float[] {0f, 0f, 16f, 16f});
        Mixins.call(face, "coarctatio$poolSetUvs", new float[] {0f, 0f, 16f, 16f}, Mixins.ci());
        assertSame(pooled, Mixins.get(face, "uvs"));
    }

    @Test
    void anElementsFacesAreMovedIntoAnEnumMap() {
        BlockPartMixin part = Mixins.instance(BlockPartMixin.class);
        Map<EnumFacing, BlockPartFace> faces = new HashMap<>();
        faces.put(EnumFacing.UP, mock(BlockPartFace.class));
        Mixins.set(part, "mapFaces", faces);
        Mixins.call(part, "coarctatio$compactFaces", null, null, faces, null, true, Mixins.ci());

        Map<EnumFacing, BlockPartFace> compacted = Mixins.get(part, "mapFaces");
        assertInstanceOf(EnumMap.class, compacted);
        assertEquals(faces.get(EnumFacing.UP), compacted.get(EnumFacing.UP));

        // An element with no faces, or one already compacted, is left as it is
        Mixins.call(part, "coarctatio$compactFaces", null, null, faces, null, true, Mixins.ci());
        assertSame(compacted, Mixins.get(part, "mapFaces"));
        Map<EnumFacing, BlockPartFace> empty = new HashMap<>();
        Mixins.set(part, "mapFaces", empty);
        Mixins.call(part, "coarctatio$compactFaces", null, null, empty, null, true, Mixins.ci());
        assertSame(empty, Mixins.get(part, "mapFaces"));
        Mixins.set(part, "mapFaces", null);
        Mixins.call(part, "coarctatio$compactFaces", null, null, null, null, true, Mixins.ci());
        assertNull(Mixins.get(part, "mapFaces"));
    }

    @Test
    void anUnbakedModelsTextureNamesAreInterned() {
        ModelBlockMixin model = Mixins.instance(ModelBlockMixin.class);
        String pooled = ModelCaches.MODEL_TEXTURES.deduplicate("blocks/stone");
        Map<String, String> textures = new LinkedHashMap<>();
        textures.put(new String("all"), new String("blocks/stone"));
        Mixins.set(model, "textures", textures);

        Mixins.call(model, "coarctatio$internTextures", null, null, textures, true, true, null, null, Mixins.ci());
        Map<String, String> interned = Mixins.get(model, "textures");
        assertInstanceOf(CanonicalStringMap.class, interned);
        assertSame(pooled, interned.get("all"));

        // Already interned, or nothing to intern
        Mixins.call(model, "coarctatio$internTextures", null, null, textures, true, true, null, null, Mixins.ci());
        assertSame(interned, Mixins.get(model, "textures"));
        Mixins.set(model, "textures", null);
        Mixins.call(model, "coarctatio$internTextures", null, null, null, true, true, null, null, Mixins.ci());
        assertNull(Mixins.get(model, "textures"));
    }
}
