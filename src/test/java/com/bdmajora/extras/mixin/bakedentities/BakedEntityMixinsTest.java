package com.bdmajora.extras.mixin.bakedentities;

import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.bakedentities.AnimatedBlockEntity;
import com.bdmajora.extras.client.bakedentities.BakedEntities;
import com.bdmajora.extras.client.bakedentities.BakedEntityContext;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BakedEntityMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void switchOff() {
        switches(false);
        BakedEntityContext.clear();
    }

    private static void switches(boolean on) {
        ExtrasConfig.BakedEntitySettings settings = new ExtrasConfig.BakedEntitySettings();
        settings.enabled = on;
        BakedEntities.apply(settings);
    }

    private static CallbackInfoReturnable<EnumBlockRenderType> renderType(Object mixin, String handler, IBlockState state) {
        CallbackInfoReturnable<EnumBlockRenderType> cir = Mixins.cir();
        Mixins.call(mixin, handler, state, cir);
        return cir;
    }

    @Test
    void bakedBlocksRenderAsModels() {
        List<Object[]> cases = List.of(
                new Object[] {Mixins.instance(BlockChestRenderTypeMixin.class), "impetus", Blocks.CHEST},
                new Object[] {Mixins.instance(BlockEnderChestRenderTypeMixin.class), "impetus", Blocks.ENDER_CHEST},
                new Object[] {Mixins.instance(BlockBedRenderTypeMixin.class), "impetus", Blocks.BED},
                new Object[] {Mixins.instance(BlockShulkerBoxRenderTypeMixin.class), "impetus", Blocks.PURPLE_SHULKER_BOX},
                new Object[] {Mixins.instance(BlockContainerRenderTypeMixin.class), "impetus$bakedRenderType", Blocks.STANDING_SIGN});
        for (Object[] c : cases) {
            IBlockState state = ((net.minecraft.block.Block) c[2]).getDefaultState();
            assertFalse(renderType(c[0], (String) c[1], state).isCancelled());
        }
        switches(true);
        for (Object[] c : cases) {
            IBlockState state = ((net.minecraft.block.Block) c[2]).getDefaultState();
            assertEquals(EnumBlockRenderType.MODEL, renderType(c[0], (String) c[1], state).getReturnValue());
        }

        // The block model lookup answers ours first
        BlockModelShapesMixin shapes = Mixins.instance(BlockModelShapesMixin.class);
        CallbackInfoReturnable<IBakedModel> ours = Mixins.cir();
        Mixins.call(shapes, "impetus$bakedEntityModel", Blocks.CHEST.getDefaultState(), ours);
        assertNotNull(ours.getReturnValue());
        CallbackInfoReturnable<IBakedModel> vanilla = Mixins.cir();
        Mixins.call(shapes, "impetus$bakedEntityModel", Blocks.STONE.getDefaultState(), vanilla);
        assertFalse(vanilla.isCancelled());
    }

    @Test
    void theMesherPinsTheBlockItIsRendering() {
        BlockRendererDispatcherContextMixin dispatcher = Mixins.instance(BlockRendererDispatcherContextMixin.class);
        IBlockAccess access = mock(IBlockAccess.class);
        BlockPos pos = new BlockPos(1, 2, 3);
        Mixins.call(dispatcher, "impetus$pinContext", null, pos, access, null, Mixins.cir());
        assertSame(access, BakedEntityContext.get().access);
        assertEquals(pos, BakedEntityContext.get().pos);
        Mixins.call(dispatcher, "impetus$unpinContext", null, pos, access, null, Mixins.cir());
        assertNull(BakedEntityContext.get());
        Mixins.call(dispatcher, "impetus$pinDamageContext", null, pos, null, access, Mixins.ci());
        assertNotNull(BakedEntityContext.get());
        Mixins.call(dispatcher, "impetus$unpinDamageContext", null, pos, null, access, Mixins.ci());
        assertNull(BakedEntityContext.get());
    }

    private static World clientWorld() {
        World world = mock(World.class);
        Mixins.set(world, "isRemote", true);
        return world;
    }

    @Test
    void aChestMarksItsWholePairWhenItsLidLeavesOrReturnsToRest() {
        TileEntityChestAnimationMixin chest = Mixins.instance(TileEntityChestAnimationMixin.class);
        TileEntityChest self = (TileEntityChest) (Object) chest;
        Mixins.set(chest, "pos", new BlockPos(10, 64, 10));
        Mixins.set(chest, "impetus$lid", 1);
        assertTrue(chest.impetus$isSettled());
        assertFalse(chest.impetus$needsRenderer());

        // Without a world, or on a server, nothing is tracked
        Mixins.call(chest, "impetus$trackLid", Mixins.ci());
        Mixins.set(chest, "world", mock(World.class));
        Mixins.call(chest, "impetus$trackLid", Mixins.ci());

        World world = clientWorld();
        Mixins.set(chest, "world", world);
        TileEntityChest east = new TileEntityChest();
        east.setPos(new BlockPos(11, 64, 10));
        self.adjacentChestXPos = east;
        self.lidAngle = 0.5F;
        assertFalse(chest.impetus$isSettled());
        Mixins.call(chest, "impetus$trackLid", Mixins.ci());
        verify(world).markBlockRangeForRenderUpdate(10, 64, 10, 11, 64, 10);
        assertTrue(chest.impetus$needsRenderer());
        // No change of rest, no rebuild
        Mixins.call(chest, "impetus$trackLid", Mixins.ci());
        verify(world).markBlockRangeForRenderUpdate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void theEnderChestAndShulkerBoxTrackTheirLidsToo() {
        TileEntityEnderChestAnimationMixin ender = Mixins.instance(TileEntityEnderChestAnimationMixin.class);
        Mixins.set(ender, "pos", BlockPos.ORIGIN);
        Mixins.set(ender, "impetus$lid", 1);
        assertTrue(ender.impetus$isSettled());
        Mixins.call(ender, "impetus$trackLid", Mixins.ci());
        World world = clientWorld();
        Mixins.set(ender, "world", world);
        ((TileEntityEnderChest) (Object) ender).prevLidAngle = 0.2F;
        Mixins.call(ender, "impetus$trackLid", Mixins.ci());
        verify(world).markBlockRangeForRenderUpdate(BlockPos.ORIGIN, BlockPos.ORIGIN);
        assertTrue(ender.impetus$needsRenderer());
        Mixins.call(ender, "impetus$trackLid", Mixins.ci());

        TileEntityShulkerBoxAnimationMixin box = Mixins.instance(TileEntityShulkerBoxAnimationMixin.class);
        Mixins.set(box, "pos", BlockPos.ORIGIN);
        Mixins.set(box, "impetus$lid", 1);
        assertTrue(box.impetus$isSettled());
        assertFalse(box.impetus$needsRenderer());
        Mixins.call(box, "impetus$trackLid", Mixins.ci());
        World boxWorld = clientWorld();
        Mixins.set(box, "world", boxWorld);
        Mixins.set(box, "animationStatus", TileEntityShulkerBox.AnimationStatus.OPENING);
        Mixins.call(box, "impetus$trackLid", Mixins.ci());
        verify(boxWorld).markBlockRangeForRenderUpdate(BlockPos.ORIGIN, BlockPos.ORIGIN);
        Mixins.call(box, "impetus$trackLid", Mixins.ci());
    }

    @Test
    void renderersStepAsideForBlockEntitiesTheMeshDraws() {
        switches(true);
        World world = clientWorld();

        // A chest at rest is drawn by the mesh; one animating, or next to one, still goes through the renderer
        TileEntityChestRendererMixin chests = Mixins.instance(TileEntityChestRendererMixin.class);
        TileEntityChestAnimationMixin chest = Mixins.instance(TileEntityChestAnimationMixin.class);
        Mixins.set(chest, "impetus$lid", 1);
        TileEntityChest te = (TileEntityChest) (Object) chest;
        assertFalse(skip(chests, "impetus$skipStaticChest", te, -1).isCancelled());
        te.setWorld(world);
        assertTrue(skip(chests, "impetus$skipStaticChest", te, -1).isCancelled());
        assertFalse(skip(chests, "impetus$skipStaticChest", te, 3).isCancelled());
        TileEntityChestAnimationMixin opening = Mixins.instance(TileEntityChestAnimationMixin.class);
        Mixins.set(opening, "impetus$lid", 1);
        ((TileEntityChest) (Object) opening).lidAngle = 0.4F;
        te.adjacentChestZNeg = (TileEntityChest) (Object) opening;
        assertFalse(skip(chests, "impetus$skipStaticChest", te, -1).isCancelled());

        TileEntityEnderChestRendererMixin enders = Mixins.instance(TileEntityEnderChestRendererMixin.class);
        TileEntityEnderChestAnimationMixin ender = Mixins.instance(TileEntityEnderChestAnimationMixin.class);
        Mixins.set(ender, "impetus$lid", 1);
        TileEntityEnderChest enderTe = (TileEntityEnderChest) (Object) ender;
        assertFalse(skip(enders, "impetus$skipStaticChest", enderTe, -1).isCancelled());
        enderTe.setWorld(world);
        assertTrue(skip(enders, "impetus$skipStaticChest", enderTe, -1).isCancelled());
        enderTe.lidAngle = 0.3F;
        assertFalse(skip(enders, "impetus$skipStaticChest", enderTe, -1).isCancelled());

        TileEntityShulkerBoxRendererMixin boxes = Mixins.instance(TileEntityShulkerBoxRendererMixin.class);
        TileEntityShulkerBoxAnimationMixin box = Mixins.instance(TileEntityShulkerBoxAnimationMixin.class);
        Mixins.set(box, "impetus$lid", 1);
        TileEntityShulkerBox boxTe = (TileEntityShulkerBox) (Object) box;
        assertFalse(skip(boxes, "impetus$skipStaticBox", boxTe, -1).isCancelled());
        boxTe.setWorld(world);
        assertTrue(skip(boxes, "impetus$skipStaticBox", boxTe, -1).isCancelled());
        Mixins.set(box, "animationStatus", TileEntityShulkerBox.AnimationStatus.OPENING);
        assertFalse(skip(boxes, "impetus$skipStaticBox", boxTe, -1).isCancelled());

        TileEntityBedRendererMixin beds = Mixins.instance(TileEntityBedRendererMixin.class);
        TileEntityBed bed = new TileEntityBed();
        assertFalse(skip(beds, "impetus$skipStaticBed", null, -1).isCancelled());
        assertFalse(skip(beds, "impetus$skipStaticBed", bed, -1).isCancelled());
        bed.setWorld(world);
        assertTrue(skip(beds, "impetus$skipStaticBed", bed, -1).isCancelled());
        assertFalse(skip(beds, "impetus$skipStaticBed", bed, 2).isCancelled());

        // A sign's board is baked, its text still drawn; breaking it draws the board again for the crack overlay
        TileEntitySignRendererMixin signs = Mixins.instance(TileEntitySignRendererMixin.class);
        Mc.Recorded<Void> board = Mc.operation();
        Mixins.call(signs, "impetus$skipStaticBoard", mock(ModelSign.class), board, -1);
        assertEquals(0, board.count());
        Mixins.call(signs, "impetus$skipStaticBoard", mock(ModelSign.class), board, 4);
        assertEquals(1, board.count());

        // Switched off, every renderer draws as vanilla
        switches(false);
        assertFalse(skip(chests, "impetus$skipStaticChest", te, -1).isCancelled());
        assertFalse(skip(enders, "impetus$skipStaticChest", enderTe, -1).isCancelled());
        assertFalse(skip(boxes, "impetus$skipStaticBox", boxTe, -1).isCancelled());
        assertFalse(skip(beds, "impetus$skipStaticBed", bed, -1).isCancelled());
        Mixins.call(signs, "impetus$skipStaticBoard", mock(ModelSign.class), board, -1);
        assertEquals(2, board.count());
        verify(world, never()).markBlockRangeForRenderUpdate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    private static CallbackInfo skip(Object renderer, String handler, Object te, int destroyStage) {
        CallbackInfo ci = Mixins.ci();
        Mixins.call(renderer, handler, te, 0.0D, 0.0D, 0.0D, 0.0F, destroyStage, 1.0F, ci);
        return ci;
    }

    @Test
    void theModelBoxAccessorReadsTheBoxsOwnQuads() {
        ModelBoxAccessorMixin box = Mixins.concrete(ModelBoxAccessorMixin.class);
        Mixins.set(box, "quadList", new net.minecraft.client.model.TexturedQuad[6]);
        assertEquals(6, box.impetus$getQuads().length);
    }
}
