package com.bdmajora.extras.client.bakedentities;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.bakedentities.TextureMapRegisteredSpritesAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BakedEntitiesTest {
    private TextureMap blocks;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void atlas() {
        Minecraft client = Minecraft.getMinecraft() == null ? Mc.client() : Minecraft.getMinecraft();
        blocks = Mc.mock(TextureMap.class, TextureMapRegisteredSpritesAccessor.class);
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", "minecraft:entity/chest/normal");
        Mixins.set(sprite, "maxU", 1.0F);
        Mixins.set(sprite, "maxV", 1.0F);
        when(blocks.getAtlasSprite(any())).thenReturn(sprite);
        when(client.getTextureMapBlocks()).thenReturn(blocks);
        everything(true);
    }

    @AfterEach
    void switchOff() {
        everything(false);
        BakedEntityContext.clear();
    }

    private static void everything(boolean on) {
        ExtrasConfig.BakedEntitySettings settings = new ExtrasConfig.BakedEntitySettings();
        settings.enabled = on;
        BakedEntities.apply(settings);
    }

    private static List<BakedQuad> quads(IBlockState state) {
        return BakedEntities.modelFor(state).getQuads(state, null, 0L);
    }

    @Test
    void onlyOurBlockEntitiesAreBakedAndOnlyWhileSwitchedOn() {
        for (net.minecraft.block.Block block : List.of(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.STANDING_SIGN, Blocks.BED, Blocks.PURPLE_SHULKER_BOX)) {
            assertTrue(BakedEntities.isBaked(block));
            assertNotNull(BakedEntities.modelFor(block.getDefaultState()));
        }
        assertFalse(BakedEntities.isBaked(Blocks.STONE));
        assertNull(BakedEntities.modelFor(Blocks.STONE.getDefaultState()));
        everything(false);
        for (net.minecraft.block.Block block : List.of(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.STANDING_SIGN, Blocks.BED, Blocks.PURPLE_SHULKER_BOX)) {
            assertFalse(BakedEntities.isBaked(block));
            assertNull(BakedEntities.modelFor(block.getDefaultState()));
        }

        // The chest sheet follows the type and whether it is half of a pair
        assertEquals(BakedEntities.ChestTexture.NORMAL, BakedEntities.chestTexture(BlockChest.Type.BASIC, false));
        assertEquals(BakedEntities.ChestTexture.NORMAL_DOUBLE, BakedEntities.chestTexture(BlockChest.Type.BASIC, true));
        assertEquals(BakedEntities.ChestTexture.TRAPPED, BakedEntities.chestTexture(BlockChest.Type.TRAP, false));
        assertEquals(BakedEntities.ChestTexture.TRAPPED_DOUBLE, BakedEntities.chestTexture(BlockChest.Type.TRAP, true));
        assertNotNull(Mixins.construct(BakedEntities.class));
    }

    @Test
    void everySheetGoesOnTheBlockAtlasAndARestitchDropsBakedGeometry() {
        Map<String, TextureAtlasSprite> registered = new HashMap<>();
        when(((TextureMapRegisteredSpritesAccessor) blocks).impetus$getRegisteredSprites()).thenReturn(registered);
        // Another mod's atlas is left alone
        BakedEntities.onStitch(new TextureStitchEvent.Pre(Mc.mock(TextureMap.class)));
        assertTrue(registered.isEmpty());
        BakedEntities.onStitch(new TextureStitchEvent.Pre(blocks));
        assertInstanceOf(EntitySheetSprite.class, registered.get("minecraft:entity/sign"));
        assertTrue(registered.containsKey("minecraft:entity/bed/red"));
        assertTrue(registered.containsKey("minecraft:entity/shulker/shulker_purple"));

        IBlockState chest = Blocks.CHEST.getDefaultState();
        List<BakedQuad> first = quads(chest);
        assertSame(first, quads(chest));
        BakedEntities.onStitched(new TextureStitchEvent.Post(blocks));
        assertNotSame(first, quads(chest));
        List<BakedQuad> second = quads(chest);
        BakedEntities.onModelBake(new ModelBakeEvent(null, null, null));
        assertNotSame(second, quads(chest));
    }

    @Test
    void theModelsAnswerLikeBlockModels() {
        BakedEntityModel model = (BakedEntityModel) BakedEntities.modelFor(Blocks.CHEST.getDefaultState());
        // Every quad is unassigned, so asking for a side or without a state gets nothing
        assertTrue(model.getQuads(Blocks.CHEST.getDefaultState(), EnumFacing.UP, 0L).isEmpty());
        assertTrue(model.getQuads(null, null, 0L).isEmpty());
        // A state from another block has no variant here
        assertTrue(model.getQuads(Blocks.STONE.getDefaultState(), null, 0L).isEmpty());
        assertTrue(model.isAmbientOcclusion());
        assertFalse(model.isGui3d());
        assertFalse(model.isBuiltInRenderer());
        assertNotNull(model.getParticleTexture());
        assertSame(ItemOverrideList.NONE, model.getOverrides());
        for (IBlockState state : List.of(Blocks.ENDER_CHEST.getDefaultState(), Blocks.STANDING_SIGN.getDefaultState(),
                Blocks.BED.getDefaultState(), Blocks.PURPLE_SHULKER_BOX.getDefaultState())) {
            BakedEntityModel other = (BakedEntityModel) BakedEntities.modelFor(state);
            assertNotNull(other.getParticleTexture());
            assertTrue(other.getQuads(Blocks.STONE.getDefaultState(), null, 0L).isEmpty());
        }
    }

    private static IBlockAccess world(Map<BlockPos, IBlockState> states, Map<BlockPos, TileEntity> tiles) {
        IBlockAccess access = mock(IBlockAccess.class);
        when(access.getBlockState(any())).thenAnswer(invocation -> states.getOrDefault(invocation.<BlockPos>getArgument(0), Blocks.AIR.getDefaultState()));
        when(access.getTileEntity(any())).thenAnswer(invocation -> tiles.get(invocation.<BlockPos>getArgument(0)));
        return access;
    }

    private static TileEntity animating(boolean settled) {
        TileEntity tile = Mc.mock(TileEntity.class, AnimatedBlockEntity.class);
        when(((AnimatedBlockEntity) tile).impetus$isSettled()).thenReturn(settled);
        return tile;
    }

    @Test
    void chestsPairWithTheirNeighboursAndHideWhileTheLidMoves() {
        BlockPos pos = new BlockPos(10, 64, 10);
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            IBlockState chest = Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing);
            // Without a world it is a single chest
            BakedEntityContext.clear();
            assertFalse(quads(chest).isEmpty());
            // Chests only pair side by side, so the partner is to the left or right of the facing
            for (BlockPos partner : List.of(pos.offset(facing.rotateY()), pos.offset(facing.rotateYCCW()))) {
                Map<BlockPos, IBlockState> states = new HashMap<>();
                states.put(partner, chest);
                BakedEntityContext.set(world(states, Map.of()), pos);
                assertFalse(quads(chest).isEmpty(), facing + " " + partner);
                // Either half opening hides the pair until both are shut
                BakedEntityContext.set(world(states, Map.of(partner, animating(false))), pos);
                assertTrue(quads(chest).isEmpty());
            }
        }
        BakedEntityContext.set(world(Map.of(), Map.of(pos, animating(false))), pos);
        assertTrue(quads(Blocks.CHEST.getDefaultState()).isEmpty());
        BakedEntityContext.set(world(Map.of(), Map.of(pos, animating(true))), pos);
        assertFalse(quads(Blocks.TRAPPED_CHEST.getDefaultState()).isEmpty());
        BakedEntityContext.clear();
        assertNull(BakedEntityContext.get());
    }

    @Test
    void theOtherBlockEntitiesBakeEveryOrientation() {
        BlockPos pos = new BlockPos(0, 64, 0);
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            assertFalse(quads(Blocks.ENDER_CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, facing)).isEmpty());
            assertFalse(quads(Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, facing)).isEmpty());
            for (BlockBed.EnumPartType part : BlockBed.EnumPartType.values()) {
                assertFalse(quads(Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, facing).withProperty(BlockBed.PART, part)).isEmpty());
            }
        }
        for (int rotation = 0; rotation < 16; rotation++) {
            assertFalse(quads(Blocks.STANDING_SIGN.getDefaultState().withProperty(BlockStandingSign.ROTATION, rotation)).isEmpty());
        }
        for (EnumFacing facing : EnumFacing.values()) {
            assertFalse(quads(Blocks.RED_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, facing)).isEmpty());
        }
        assertEquals(EnumDyeColor.RED, ShulkerBoxBakedModel.colorOf((BlockShulkerBox) Blocks.RED_SHULKER_BOX));

        // A bed takes its colour from its block entity
        TileEntityBed bed = new TileEntityBed();
        bed.setColor(EnumDyeColor.BLUE);
        BakedEntityContext.set(world(Map.of(), Map.of(pos, bed)), pos);
        assertFalse(quads(Blocks.BED.getDefaultState()).isEmpty());
        BakedEntityContext.set(world(Map.of(), Map.of(pos, mock(TileEntity.class))), pos);
        assertFalse(quads(Blocks.BED.getDefaultState()).isEmpty());
        // A moving lid hides the ender chest and the shulker box too
        BakedEntityContext.set(world(Map.of(), Map.of(pos, animating(false))), pos);
        assertTrue(quads(Blocks.ENDER_CHEST.getDefaultState()).isEmpty());
        assertTrue(quads(Blocks.RED_SHULKER_BOX.getDefaultState()).isEmpty());
        BakedEntityContext.set(world(Map.of(), Map.of()), pos);
        assertFalse(quads(Blocks.ENDER_CHEST.getDefaultState()).isEmpty());
        assertFalse(quads(Blocks.RED_SHULKER_BOX.getDefaultState()).isEmpty());
    }

    @Test
    void theBakerFollowsTheModelTreeAndClipsAtAPlane() {
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("x");
        ModelBase base = new ModelBase() { };
        ModelRenderer root = new ModelRenderer(base, 0, 0);
        root.addBox(0, 0, 0, 16, 16, 16);
        root.rotateAngleX = 0.1F;
        root.rotateAngleY = 0.2F;
        root.rotateAngleZ = 0.3F;
        ModelRenderer child = new ModelRenderer(base, 0, 0);
        child.addBox(0, 0, 0, 4, 4, 4);
        root.addChild(child);
        ModelRenderer hidden = new ModelRenderer(base, 0, 0);
        hidden.addBox(0, 0, 0, 4, 4, 4);
        hidden.isHidden = true;
        ModelRenderer empty = new ModelRenderer(base, 0, 0);
        empty.cubeList = null;
        empty.childModels = null;

        EntityModelBaker baker = new EntityModelBaker(sprite);
        baker.bake(root, new Matrix4f());
        baker.bake(hidden, new Matrix4f());
        baker.bake(empty, new Matrix4f());
        assertEquals(12, baker.quads().size());

        // A box of one unit spanning the plane at a half is cut to the half on the kept side, shifted back
        EntityModelBaker cube = new EntityModelBaker(sprite);
        ModelRenderer unit = new ModelRenderer(base, 0, 0);
        unit.addBox(0, 0, 0, 16, 16, 16);
        cube.bake(unit, new Matrix4f());
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            List<BakedQuad> below = EntityModelBaker.clip(cube.quads(), axis, 0.5F, false, 0.0F);
            List<BakedQuad> above = EntityModelBaker.clip(cube.quads(), axis, 0.5F, true, 0.5F);
            // The face on the far side of the plane drops out of each half
            assertEquals(5, below.size(), axis.name());
            assertEquals(5, above.size(), axis.name());
        }
        // A quad that does not have four corners is skipped
        EntityModelBaker odd = new EntityModelBaker(sprite);
        ModelRenderer triangle = new ModelRenderer(base, 0, 0);
        triangle.cubeList.add(new net.minecraft.client.model.ModelBox(triangle, 0, 0, 0, 0, 0, 1, 1, 1, 0));
        TexturedQuad[] faces = ((ModelBoxAccess) triangle.cubeList.get(0)).impetus$getQuads();
        faces[0] = new TexturedQuad(new PositionTextureVertex[] {faces[0].vertexPositions[0], faces[0].vertexPositions[1], faces[0].vertexPositions[2]});
        odd.bake(triangle, new Matrix4f());
        assertEquals(5, odd.quads().size());
    }

    private static ModelRenderer box(ModelBase model, float x, float y, float z, int dx, int dy, int dz) {
        ModelRenderer part = new ModelRenderer(model, 0, 0);
        part.addBox(x, y, z, dx, dy, dz);
        return part;
    }

    // x, y, z, then atlas u, v of each corner
    private static float[][] corners(BakedQuad quad) {
        int[] data = quad.getVertexData();
        float[][] corners = new float[4][];
        for (int i = 0; i < 4; i++) {
            int offset = i * 7;
            corners[i] = new float[] {Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2]),
                    Float.intBitsToFloat(data[offset + 4]), Float.intBitsToFloat(data[offset + 5])};
        }
        return corners;
    }

    // Lowest and highest value one corner component takes
    private static float[] range(BakedQuad quad, int component) {
        float[] range = {Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] corner : corners(quad)) {
            range[0] = Math.min(range[0], corner[component]);
            range[1] = Math.max(range[1], corner[component]);
        }
        return range;
    }

    // Area two quads share across the axes other than the normal one
    private static float shared(BakedQuad first, BakedQuad second, int normal) {
        float area = 1.0F;
        for (int axis = 0; axis < 3; axis++) {
            if (axis != normal) {
                float[] a = range(first, axis);
                float[] b = range(second, axis);
                area *= Math.max(0.0F, Math.min(a[1], b[1]) - Math.max(a[0], b[0]));
            }
        }
        return area;
    }

    // Whether two quads are the same rectangle corner for corner, which is what makes them rasterize to the same depths and texels
    private static boolean sameRectangle(BakedQuad first, BakedQuad second) {
        float[][] a = corners(first);
        float[][] b = corners(second);
        for (int i = 0; i < 4; i++) {
            for (int axis = 0; axis < 3; axis++) {
                if (Float.floatToRawIntBits(a[i][axis]) != Float.floatToRawIntBits(b[i][axis])) {
                    return false;
                }
            }
        }
        return true;
    }

    // Two faces turned the same way on one plane either share no area or are the same rectangle; anything in between z-fights in the mesh
    private static void assertFacesOnOnePlaneMissOrMatch(List<BakedQuad> quads, String variant) {
        assertFalse(quads.isEmpty(), variant);
        for (int i = 0; i < quads.size(); i++) {
            for (int j = i + 1; j < quads.size(); j++) {
                BakedQuad first = quads.get(i);
                BakedQuad second = quads.get(j);
                int normal = first.getFace().getAxis().ordinal();
                float[] firstPlane = range(first, normal);
                float[] secondPlane = range(second, normal);
                if (first.getFace() == second.getFace() && firstPlane[1] - firstPlane[0] < 1.0E-4F && secondPlane[1] - secondPlane[0] < 1.0E-4F
                        && Math.abs(firstPlane[0] - secondPlane[0]) < 1.0E-4F && shared(first, second, normal) > 1.0E-6F) {
                    assertTrue(sameRectangle(first, second), variant + ": two " + first.getFace() + " faces overlap on one plane without matching");
                }
            }
        }
    }

    // The faces turned one way whose sheet rows run exactly from one row to another
    private static List<BakedQuad> rows(List<BakedQuad> quads, EnumFacing face, int from, int to) {
        return quads.stream().filter(quad -> quad.getFace() == face && Math.abs(range(quad, 4)[0] - from / 64.0F) < 1.0E-5F
                && Math.abs(range(quad, 4)[1] - to / 64.0F) < 1.0E-5F).toList();
    }

    @Test
    void facesOnOnePlaneBakeIntoMatchingCellsAndBothStay() {
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("x");
        ModelBase model = new ModelBase() { };
        // A small box sunk into the north face of a big one: the big face becomes a three by three grid whose middle cell is the small face's own rectangle, drawn before it
        EntityModelBaker framed = new EntityModelBaker(sprite);
        framed.bake(box(model, 0, 0, 0, 16, 16, 16), new Matrix4f());
        framed.bake(box(model, 4, 4, 0, 8, 8, 4), new Matrix4f());
        List<BakedQuad> quads = framed.quads();
        assertSame(quads, framed.quads());
        assertEquals(20, quads.size());
        assertFacesOnOnePlaneMissOrMatch(quads, "framed");
        List<BakedQuad> north = quads.stream().filter(quad -> quad.getFace() == EnumFacing.NORTH && range(quad, 2)[1] < 1.0E-6F).toList();
        assertEquals(10, north.size());
        float area = 0.0F;
        for (BakedQuad cell : north.subList(0, 9)) {
            area += shared(cell, cell, 2);
        }
        assertEquals(1.0F, area, 1.0E-5F);
        BakedQuad middle = north.subList(0, 9).stream().filter(quad -> range(quad, 0)[0] == 0.25F && range(quad, 1)[0] == 0.25F).findFirst().orElseThrow();
        assertTrue(sameRectangle(middle, north.get(9)));
        // The cells keep the big face's own texture: the middle left one shows the first quarter of the face's u span (16 to 32 of 64) and the middle half of its v span
        BakedQuad left = north.subList(0, 9).stream().filter(quad -> range(quad, 0)[1] == 0.25F && range(quad, 1)[0] == 0.25F).findFirst().orElseThrow();
        assertArrayEquals(new float[] {0.25F, 0.3125F}, range(left, 3), 1.0E-5F);
        assertArrayEquals(new float[] {0.625F, 0.875F}, range(left, 4), 1.0E-5F);
        // Another bake starts over
        framed.bake(box(model, 0, 0, 0, 1, 1, 1), new Matrix4f());
        assertNotSame(quads, framed.quads());
        // A part drawn twice keeps both copies, each face once per copy and the same rectangle, the later copy after
        EntityModelBaker twice = new EntityModelBaker(sprite);
        twice.bake(box(model, 0, 0, 0, 16, 16, 16), new Matrix4f());
        twice.bake(box(model, 0, 0, 0, 16, 16, 16), new Matrix4f());
        assertEquals(12, twice.quads().size());
        for (int i = 0; i < 12; i += 2) {
            assertTrue(sameRectangle(twice.quads().get(i), twice.quads().get(i + 1)));
        }
        // Boxes meeting face to face push the two touching faces apart along their own normals, so neither shows through the other
        EntityModelBaker touching = new EntityModelBaker(sprite);
        touching.bake(box(model, 0, 0, 0, 16, 16, 16), new Matrix4f());
        touching.bake(box(model, 16, 0, 0, 16, 16, 16), new Matrix4f());
        assertEquals(12, touching.quads().size());
        float half = 1.0F / 1024.0F;
        assertTrue(touching.quads().stream().anyMatch(quad -> quad.getFace() == EnumFacing.EAST && range(quad, 0)[0] == 1.0F + half));
        assertTrue(touching.quads().stream().anyMatch(quad -> quad.getFace() == EnumFacing.WEST && range(quad, 0)[0] == 1.0F - half));
        // Boxes apart are left exactly as baked
        EntityModelBaker apart = new EntityModelBaker(sprite);
        apart.bake(box(model, 0, 0, 0, 4, 4, 4), new Matrix4f());
        apart.bake(box(model, 8, 0, 0, 4, 4, 4), new Matrix4f());
        assertEquals(12, apart.quads().size());
        assertTrue(apart.quads().stream().noneMatch(quad -> range(quad, 0)[0] == 0.5F + half));
    }

    @Test
    void aShutShulkerBoxBakesTheRowsLidAndBaseShareAsOneQuadInEach() {
        List<BakedQuad> box = quads(Blocks.WHITE_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, EnumFacing.UP));
        // The four flat faces whole and each of the eight sides as two slices
        assertEquals(20, box.size());
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            // Every row of the sheet stays: the base's teeth (rows 44 to 48) over its lower four, the lid's notched rows 24 to 28 under its upper eight
            List<BakedQuad> baseLower = rows(box, side, 48, 52);
            List<BakedQuad> baseBand = rows(box, side, 44, 48);
            List<BakedQuad> lidBand = rows(box, side, 24, 28);
            List<BakedQuad> lidUpper = rows(box, side, 16, 24);
            assertEquals(1, baseLower.size(), side.getName());
            assertEquals(1, baseBand.size(), side.getName());
            assertEquals(1, lidBand.size(), side.getName());
            assertEquals(1, lidUpper.size(), side.getName());
            assertArrayEquals(new float[] {0.0F, 0.25F}, range(baseLower.get(0), 1), 1.0E-5F, side.getName());
            assertArrayEquals(new float[] {0.25F, 0.5F}, range(baseBand.get(0), 1), 1.0E-5F, side.getName());
            assertArrayEquals(new float[] {0.5F, 1.0F}, range(lidUpper.get(0), 1), 1.0E-5F, side.getName());
            // The shared rows are one rectangle in both on the same texel columns, so the lid's see-through notch lands exactly on the base's tooth; the renderer's order, base then lid, is kept
            assertTrue(sameRectangle(baseBand.get(0), lidBand.get(0)), side.getName());
            for (int corner = 0; corner < 4; corner++) {
                assertEquals(corners(baseBand.get(0))[corner][3], corners(lidBand.get(0))[corner][3], side.getName());
            }
            assertTrue(box.indexOf(baseBand.get(0)) < box.indexOf(lidBand.get(0)), side.getName());
        }
        // Every facing fills its block exactly, so stacked and neighbouring boxes meet with no slit between them
        for (EnumFacing facing : EnumFacing.values()) {
            List<BakedQuad> turned = quads(Blocks.WHITE_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, facing));
            for (int axis = 0; axis < 3; axis++) {
                float low = Float.MAX_VALUE;
                float high = -Float.MAX_VALUE;
                for (BakedQuad quad : turned) {
                    low = Math.min(low, range(quad, axis)[0]);
                    high = Math.max(high, range(quad, axis)[1]);
                }
                assertArrayEquals(new float[] {0.0F, 1.0F}, new float[] {low, high}, 1.0E-5F, facing + " axis " + axis);
            }
        }
    }

    @Test
    void aShutChestBakesTheRowLidAndBaseShareAsOneQuadInEach() {
        BakedEntityContext.clear();
        List<BakedQuad> chest = quads(Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, EnumFacing.SOUTH));
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            // The lid's bottom row (18 to 19 of the sheet) and the base's top row (33 to 34) become one rectangle, the base drawn after as the renderer does
            List<BakedQuad> lidBand = rows(chest, side, 18, 19);
            List<BakedQuad> baseBand = rows(chest, side, 33, 34);
            assertEquals(1, lidBand.size(), side.getName());
            assertEquals(1, baseBand.size(), side.getName());
            assertTrue(sameRectangle(lidBand.get(0), baseBand.get(0)), side.getName());
            assertTrue(chest.indexOf(lidBand.get(0)) < chest.indexOf(baseBand.get(0)), side.getName());
            // The rest of each side keeps its rows
            assertEquals(1, rows(chest, side, 14, 18).size(), side.getName());
            assertEquals(1, rows(chest, side, 34, 43).size(), side.getName());
        }
    }

    @Test
    void bakedFacesOnOnePlaneEitherMissEachOtherOrMatchExactly() {
        BlockPos pos = new BlockPos(10, 64, 10);
        for (EnumFacing facing : EnumFacing.values()) {
            assertFacesOnOnePlaneMissOrMatch(quads(Blocks.WHITE_SHULKER_BOX.getDefaultState().withProperty(BlockShulkerBox.FACING, facing)), "shulker box " + facing);
        }
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            IBlockState chest = Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing);
            BakedEntityContext.clear();
            assertFacesOnOnePlaneMissOrMatch(quads(chest), "chest " + facing);
            for (BlockPos partner : List.of(pos.offset(facing.rotateY()), pos.offset(facing.rotateYCCW()))) {
                BakedEntityContext.set(world(Map.of(partner, chest), Map.of()), pos);
                assertFacesOnOnePlaneMissOrMatch(quads(chest), "double chest " + facing + " " + partner);
            }
            BakedEntityContext.clear();
            // The ender chest's lid and base sides each split at the row they share (eight more than its eighteen faces); the wall sign board and the bed's piece and legs share no plane
            List<BakedQuad> enderChest = quads(Blocks.ENDER_CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, facing));
            assertFacesOnOnePlaneMissOrMatch(enderChest, "ender chest " + facing);
            assertEquals(26, enderChest.size());
            List<BakedQuad> wallSign = quads(Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, facing));
            assertFacesOnOnePlaneMissOrMatch(wallSign, "wall sign " + facing);
            assertEquals(6, wallSign.size());
            for (BlockBed.EnumPartType part : BlockBed.EnumPartType.values()) {
                List<BakedQuad> bed = quads(Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, facing).withProperty(BlockBed.PART, part));
                assertFacesOnOnePlaneMissOrMatch(bed, "bed " + facing + " " + part);
                assertEquals(18, bed.size());
            }
        }
        for (int rotation = 0; rotation < 16; rotation++) {
            List<BakedQuad> sign = quads(Blocks.STANDING_SIGN.getDefaultState().withProperty(BlockStandingSign.ROTATION, rotation));
            assertFacesOnOnePlaneMissOrMatch(sign, "standing sign " + rotation);
            assertEquals(12, sign.size());
        }
    }

    @Test
    void anEntitySheetLoadsItselfWhateverItsShape() throws Exception {
        EntitySheetSprite sprite = new EntitySheetSprite(new ResourceLocation("minecraft:entity/sign"), 4);
        assertTrue(sprite.hasCustomLoader(null, null));
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        IResourceManager manager = mock(IResourceManager.class);
        IResource resource = mock(IResource.class);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(png.toByteArray()));
        ResourceLocation file = new ResourceLocation("minecraft:textures/entity/sign.png");
        when(manager.getResource(file)).thenReturn(resource);
        // Loaded here, then stitched like any sprite
        assertFalse(sprite.load(manager, file, location -> null));
        assertEquals(64, sprite.getIconWidth());
        assertEquals(32, sprite.getIconHeight());
        assertEquals(5, sprite.getFrameTextureData(0).length);
        // A sheet the pack lacks is left on the missing sprite
        when(manager.getResource(file)).thenThrow(new FileNotFoundException("gone"));
        assertTrue(sprite.load(manager, file, location -> null));
    }

    @Test
    void aLidIsTrackedThroughAGracePeriodAfterSettling() {
        int state = LidTracking.INITIAL;
        assertFalse(LidTracking.needsRenderer(state, true));
        int opening = LidTracking.tick(state, false);
        assertTrue(LidTracking.restChanged(state, opening));
        assertTrue(LidTracking.needsRenderer(opening, false));
        int shut = LidTracking.tick(opening, true);
        assertTrue(LidTracking.restChanged(opening, shut));
        // Still drawn by the renderer for a few ticks while the mesh catches up
        assertTrue(LidTracking.needsRenderer(shut, true));
        for (int i = 0; i < 4; i++) {
            shut = LidTracking.tick(shut, true);
        }
        assertFalse(LidTracking.needsRenderer(shut, true));
        assertFalse(LidTracking.restChanged(shut, LidTracking.tick(shut, true)));
        assertNotNull(Mixins.construct(LidTracking.class));
    }
}
