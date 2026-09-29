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
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
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
        assertSame(ItemCameraTransforms.DEFAULT, model.getItemCameraTransforms());
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
