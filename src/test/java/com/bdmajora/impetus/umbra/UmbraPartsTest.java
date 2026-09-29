package com.bdmajora.impetus.umbra;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.umbra.gl.blending.BlendOverrideGuard;
import com.bdmajora.impetus.umbra.gl.blending.ProgramBlendState;
import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.material.BlockMaterialMapping;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.mixin.compat.GlStateManagerBlendGuardMixin;
import com.bdmajora.impetus.umbra.mixin.compat.ProgramBlendStateBlendGuardMixin;
import com.bdmajora.impetus.umbra.mixin.compat.UmbraRenderingPipelineBlendGuardMixin;
import com.bdmajora.impetus.umbra.pbr.PBRAtlasManager;
import com.bdmajora.impetus.umbra.pbr.TextureFormatLoader;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.IdMap;
import com.bdmajora.impetus.umbra.shaderpack.materialmap.NamespacedId;
import com.bdmajora.impetus.umbra.targets.BufferFlipper;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.vertices.ExtendedDataHelper;
import com.bdmajora.impetus.umbra.vertices.NormalHelper;
import com.bdmajora.impetus.umbra.vertices.UmbraChunkVertexType;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraPartsTest {
    private Minecraft client;
    private MockedStatic<GL11> gl11;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void mockGl() {
        client = Mc.client();
        Mixins.set(client, "gameSettings", Mc.uninitialized(net.minecraft.client.settings.GameSettings.class));
        gl11 = Mockito.mockStatic(GL11.class);
    }

    @AfterEach
    void restore() {
        gl11.close();
        PBRAtlasManager.destroy();
        BlendOverrideGuard.release();
        WorldRenderingSettings.setOldLighting(true);
        WorldRenderingSettings.setAmbientOcclusionLevel(1.0F);
        WorldRenderingSettings.setItemIds(null);
        WorldRenderingSettings.setBlockRenderLayers(null);
        WorldRenderingSettings.setVoxelizeLightBlocks(false);
        WorldRenderingSettings.setBlockStateIds(null);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static TextureAtlasSprite sprite(String name, int width, int height) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        Mixins.set(sprite, "width", width);
        Mixins.set(sprite, "height", height);
        return sprite;
    }

    private static IResource resource(byte[] bytes) {
        IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return resource;
    }

    @Test
    void theTerrainVertexCarriesTheOptiFineExtras() {
        assertSame(UmbraChunkVertexType.VERTEX_FORMAT, UmbraChunkVertexType.INSTANCE.getVertexFormat());
        ChunkVertexEncoder.Vertex vertex = new ChunkVertexEncoder.Vertex();
        vertex.blockId = 100_000;
        vertex.blockRenderType = -100_000;
        vertex.blockData = 3;
        vertex.midBlockX = 4.0F;
        vertex.midBlockY = -4.0F;
        vertex.midBlockZ = 0.25F;
        vertex.blockEmission = 300;
        Material material = mock(Material.class);
        java.nio.ByteBuffer memory = java.nio.ByteBuffer.allocateDirect(UmbraChunkVertexType.STRIDE * 2);
        long base = LWJGL.memAddress(memory);
        long next = UmbraChunkVertexType.INSTANCE.createEncoder().write(base, material, vertex, 2);
        assertEquals(base + UmbraChunkVertexType.STRIDE, next);
        // Ids clamp to the short range and the mid-block offset and emission to their bytes
        assertEquals(Short.MAX_VALUE, LWJGL.memGetShort(base + 44));
        assertEquals(Short.MIN_VALUE, LWJGL.memGetShort(base + 46));
        assertEquals(0x7F | (0x80 << 8) | (16 << 16) | (0xFF << 24), LWJGL.memGetInt(base + 52));
        vertex.midBlockX = 0.0F;
        vertex.blockEmission = -5;
        UmbraChunkVertexType.INSTANCE.createEncoder().write(next, material, vertex, 0);
        assertEquals((0x80 << 8) | (16 << 16), LWJGL.memGetInt(next + 52));

        // A flat quad's normal points out of it, and the tangent follows the U direction
        Vector3f normal = new Vector3f();
        NormalHelper.computeFaceNormal(normal, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0, 0);
        assertEquals(1.0F, Math.abs(normal.y), 1e-6F);
        NormalHelper.computeFaceNormal(normal, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(new Vector3f(), normal);
        int tangent = NormalHelper.computeTangent(0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 1);
        int flipped = NormalHelper.computeTangent(0, -1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 1);
        assertNotEquals(tangent, flipped);
        NormalHelper.computeTangent(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertEquals(Block.getIdFromBlock(Blocks.WOOL), ExtendedDataHelper.getBlockId(Blocks.WOOL.getStateFromMeta(5)));
        assertEquals(5, ExtendedDataHelper.getBlockData(Blocks.WOOL.getStateFromMeta(5)));
    }

    @Test
    void pbrAtlasesMirrorTheBlockAtlasLayout(@org.junit.jupiter.api.io.TempDir java.nio.file.Path fml) throws IOException {
        // Texture allocation synchronises on SplashProgress, whose initialiser locates the FML jar
        com.bdmajora.testing.Statics.set(net.minecraftforge.fml.common.asm.FMLSanityChecker.class, "fmlLocation", fml.toFile());
        IResourceManager resources = client.getResourceManager();
        when(resources.getResource(any())).thenThrow(new FileNotFoundException());
        // Nothing but the neutral fill without companions, and the caller's fallback is bound
        Map<String, TextureAtlasSprite> sprites = new LinkedHashMap<>();
        sprites.put("stone", sprite("minecraft:blocks/stone", 16, 16));
        sprites.put("empty", sprite("minecraft:blocks/empty", 0, 16));
        PBRAtlasManager.rebuild(sprites, 64, 64, 0);
        assertEquals(7, PBRAtlasManager.getNormalsAtlas(7));
        assertEquals(7, PBRAtlasManager.getSpecularAtlas(7));

        // A normal map is scaled to the sprite and mipmapped with the atlas; a short animation strip clamps to its last row
        IResource normals = resource(png(32, 32));
        IResource speculars = resource(png(2, 1));
        Mockito.reset(resources);
        when(resources.getResource(any())).thenAnswer(invocation -> {
            ResourceLocation location = invocation.getArgument(0);
            if (location.getPath().equals("textures/blocks/stone_n.png")) {
                return normals;
            }
            if (location.getPath().equals("textures/blocks/stone_s.png")) {
                return speculars;
            }
            throw new FileNotFoundException(location.toString());
        });
        PBRAtlasManager.rebuild(sprites, 64, 64, 2);
        assertNotEquals(7, PBRAtlasManager.getNormalsAtlas(7));
        assertNotEquals(7, PBRAtlasManager.getSpecularAtlas(7));
        // An unreadable companion counts as absent
        Mockito.reset(resources);
        IResource garbage = resource(new byte[] {1, 2, 3});
        when(resources.getResource(any())).thenReturn(garbage);
        PBRAtlasManager.rebuild(sprites, 64, 64, 0);
        assertEquals(7, PBRAtlasManager.getNormalsAtlas(7));
        // A failure part way never breaks the reload
        PBRAtlasManager.rebuild(null, 64, 64, 0);
        assertEquals(7, PBRAtlasManager.getNormalsAtlas(7));
        assertNotNull(Mixins.construct(PBRAtlasManager.class));
    }

    @Test
    void theTextureFormatDeclarationBecomesMacros() throws IOException {
        IResourceManager resources = client.getResourceManager();
        IResource declaration = resource("format=lab-pbr-1.3".getBytes(StandardCharsets.UTF_8));
        when(resources.getResource(new ResourceLocation("minecraft", "optifine/texture.properties"))).thenReturn(declaration);
        Map<String, String> macros = new HashMap<>();
        TextureFormatLoader.addFormatMacros(macros);
        assertEquals(Map.of("MC_TEXTURE_FORMAT_LAB_PBR", "", "MC_TEXTURE_FORMAT_LAB_PBR_1_3", ""), macros);
    }

    @Test
    void worldSettingsPublishThePacksMaterialChoices() {
        assertNull(WorldRenderingSettings.getBlockStateIds());
        int[] table = new int[4];
        WorldRenderingSettings.setBlockStateIds(table);
        assertSame(table, WorldRenderingSettings.getBlockStateIds());
        WorldRenderingSettings.setItemIds(Map.of(new NamespacedId("minecraft:diamond"), 9));
        assertEquals(9, WorldRenderingSettings.getItemId(new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND)));
        assertNull(WorldRenderingSettings.getForcedRenderLayer(Blocks.GLASS));
        WorldRenderingSettings.setBlockRenderLayers(Map.of(Blocks.GLASS, BlockRenderLayer.TRANSLUCENT));
        assertEquals(BlockRenderLayer.TRANSLUCENT, WorldRenderingSettings.getForcedRenderLayer(Blocks.GLASS));
        WorldRenderingSettings.setVoxelizeLightBlocks(true);
        assertTrue(WorldRenderingSettings.isVoxelizeLightBlocks());
        WorldRenderingSettings.setOldLighting(false);
        assertFalse(WorldRenderingSettings.isOldLighting());
        assertTrue(WorldRenderingSettings.shouldDisableDirectionalShading());
        // Vanilla's AO passes through at full level and is flattened towards unoccluded below it
        assertEquals(0.4F, WorldRenderingSettings.applyAmbientOcclusionLevel(0.4F));
        WorldRenderingSettings.setAmbientOcclusionLevel(0.5F);
        assertEquals(0.7F, WorldRenderingSettings.applyAmbientOcclusionLevel(0.4F), 1e-6F);

        // block.properties predicates match on the named property, and a property the block lacks is ignored
        Map<AbsolutePackPath, String> sources = Map.of(AbsolutePackPath.fromAbsolutePath("/block.properties"),
                "block.5 = minecraft:wheat:age=7 minecraft:stone:nonexistent=1");
        int[] ids = BlockMaterialMapping.createBlockStateIdTable(new IdMap(sources, Map.of()));
        assertEquals(5, ids[Block.getStateId(Blocks.WHEAT.getDefaultState().withProperty(BlockCrops.AGE, 7)) & 0xFFFF]);
        assertEquals(-1, ids[Block.getStateId(Blocks.WHEAT.getDefaultState()) & 0xFFFF]);
        assertEquals(5, ids[Block.getStateId(Blocks.STONE.getDefaultState()) & 0xFFFF]);
    }

    @Test
    void targetsReportTheirSizeAndTheFlipperResets() {
        UmbraRenderTarget target = new UmbraRenderTarget(InternalTextureFormat.RGBA, 320, 240);
        assertEquals(320, target.getWidth());
        assertEquals(240, target.getHeight());
        BufferFlipper flipper = new BufferFlipper();
        flipper.flip(3);
        assertTrue(flipper.isFlipped(3));
        flipper.reset();
        assertFalse(flipper.isFlipped(3));
    }

    @Test
    void theBlendGuardHoldsAPacksBlendOffUntilTheNextPhase() {
        ProgramBlendStateBlendGuardMixin program = Mixins.instance(ProgramBlendStateBlendGuardMixin.class);
        CallbackInfo enable = Mixins.ci();
        // A program with blend directives that turned blending off swallows vanilla's enableBlend
        Mockito.doReturn(true).when(program).hasDirectives();
        when(TestGl.gl().glGetInteger(GL11.GL_BLEND)).thenReturn(0);
        Mixins.call(program, "impetus$beforeBlendApply", new int[] {0}, Mixins.ci());
        Mixins.call(program, "impetus$afterBlendApply", new int[] {0}, Mixins.ci());
        Mixins.call(GlStateManagerBlendGuardMixin.class, "impetus$deferEnableBlend", enable);
        assertTrue(enable.isCancelled());
        // Every pipeline stage boundary releases it
        UmbraRenderingPipelineBlendGuardMixin pipeline = Mixins.instance(UmbraRenderingPipelineBlendGuardMixin.class);
        Mixins.call(pipeline, "impetus$releaseAtWorldStart", 0.5F, Mixins.ci());
        CallbackInfo released = Mixins.ci();
        Mixins.call(GlStateManagerBlendGuardMixin.class, "impetus$deferEnableBlend", released);
        assertFalse(released.isCancelled());
        Mixins.call(pipeline, "impetus$releaseAtPhaseChange", null, Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtTerrainDraw", new int[0], null, Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtTerrainDrawFull", new int[0], null, null, true, Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtTranslucents", Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtHandDepthCopy", Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtHandRendering", Mixins.cir());
        Mixins.call(pipeline, "impetus$releaseAtHandTranslucentRendering", Mixins.cir());
        Mixins.call(pipeline, "impetus$releaseAtHandEnd", Mixins.ci());
        Mixins.call(pipeline, "impetus$releaseAtWorldFinish", Mixins.ci());
        assertNotNull(Mixins.instance(GlStateManagerBlendGuardMixin.class));
    }
}
