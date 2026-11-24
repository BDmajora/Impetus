package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.impl.compat.thaumicaugmentation.ThaumicAugmentationCompat;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

// Where Impetus' replacement block entity pass hands Thaumic Augmentation its end of renderEntities hook.
class RenderGlobalTileEntityPassTest {
    private final List<String> events = new ArrayList<>();
    private RenderGlobalMixin global;
    private TileEntityRendererDispatcher dispatcher;
    private TileEntityRendererDispatcher previousDispatcher;
    private Set<TileEntity> globalTiles;
    private ICamera camera;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void setUp() {
        Minecraft client = Mc.client();
        EntityRenderer entityRenderer = mock(EntityRenderer.class);
        doAnswer(inv -> events.add("lightmapOff")).when(entityRenderer).disableLightmap();
        Mixins.set(client, "entityRenderer", entityRenderer);
        Mixins.set(client, "profiler", mock(Profiler.class));

        ImpetusWorldRenderer renderer = mock(ImpetusWorldRenderer.class);
        doAnswer(inv -> {
            events.add("sections");
            return 0;
        }).when(renderer).renderBlockEntities(any());

        global = Mixins.instance(RenderGlobalMixin.class);
        Mixins.set(global, "renderer", renderer);
        Mixins.set(global, "mc", client);
        Mixins.set(global, "damagedBlocks", new HashMap<>());
        globalTiles = new HashSet<>();
        Mixins.set(global, "setTileEntities", globalTiles);

        previousDispatcher = TileEntityRendererDispatcher.instance;
        dispatcher = mock(TileEntityRendererDispatcher.class);
        doAnswer(inv -> events.add("drawBatch" + inv.getArgument(0))).when(dispatcher).drawBatch(anyInt());
        Statics.set(TileEntityRendererDispatcher.class, "instance", dispatcher);
        camera = mock(ICamera.class);
        when(camera.isBoundingBoxInFrustum(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        Statics.set(TileEntityRendererDispatcher.class, "instance", previousDispatcher);
    }

    private CallbackInfo pass(int pass) {
        CallbackInfo ci = Mixins.ci();
        global.impetus$renderTileEntities(mock(Entity.class), camera, 0.5F, ci, pass);
        return ci;
    }

    private MockedStatic<ThaumicAugmentationCompat> recordTa() {
        MockedStatic<ThaumicAugmentationCompat> ta = mockStatic(ThaumicAugmentationCompat.class);
        ta.when(() -> ThaumicAugmentationCompat.onRenderEntities(anyInt())).thenAnswer(inv -> {
            events.add("ta" + inv.getArgument(0));
            return null;
        });
        return ta;
    }

    @Test
    void taRunsAfterEveryBlockEntityAndBeforeTheLightmapGoesOff() {
        TileEntity tile = mock(TileEntity.class);
        when(tile.shouldRenderInPass(0)).thenReturn(true);
        when(tile.getRenderBoundingBox()).thenReturn(new AxisAlignedBB(0, 0, 0, 1, 1, 1));
        globalTiles.add(tile);
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            CallbackInfo ci = pass(0);
            assertEquals(List.of("sections", "drawBatch0", "ta0", "lightmapOff"), events);
            assertTrue(ci.isCancelled());
            ta.verify(() -> ThaumicAugmentationCompat.onRenderEntities(0), times(1));
        }
        verify(dispatcher).render(tile, 0.5F, -1);
    }

    @Test
    void taStillDrainsWithNoGlobalBlockEntities() {
        // TA's own TESRs are section block entities, so the drain must not hinge on the global set
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            pass(0);
            assertEquals(List.of("sections", "ta0", "lightmapOff"), events);
        }
        verify(dispatcher, never()).drawBatch(anyInt());
    }

    @Test
    void everyPassIsForwardedOncePerFrame() {
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            pass(0);
            pass(1);
            pass(0);
            assertEquals(List.of("ta0", "ta1", "ta0"), events.stream().filter(e -> e.startsWith("ta")).toList());
        }
    }

    @Test
    void aGlobalBlockEntityOutOfPassOrViewStillLetsTaRun() {
        TileEntity hidden = mock(TileEntity.class);
        when(hidden.shouldRenderInPass(anyInt())).thenReturn(false);
        globalTiles.add(hidden);
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            pass(1);
            assertEquals(List.of("sections", "drawBatch1", "ta1", "lightmapOff"), events);
        }
        verify(dispatcher, never()).render(any(TileEntity.class), anyFloat(), anyInt());
    }

    @Test
    void withoutTaThePassIsUnchanged() {
        // Real compat class, no TA on the class path: nothing recorded between the batch and the lightmap
        pass(0);
        assertEquals(List.of("sections", "lightmapOff"), events);
    }

    // Stands in for a TA hook that throws on every call.
    public static void failingHook(int pass) {
        throw new IllegalStateException("ta " + pass);
    }

    @Test
    void aFailingTaHookStillLetsThePassFinish() throws Exception {
        java.lang.invoke.MethodHandle previous = Statics.get(ThaumicAugmentationCompat.class, "hook");
        Statics.set(ThaumicAugmentationCompat.class, "hook", java.lang.invoke.MethodHandles.lookup().findStatic(
                RenderGlobalTileEntityPassTest.class, "failingHook", java.lang.invoke.MethodType.methodType(void.class, int.class)));
        try {
            CallbackInfo ci = assertDoesNotThrow(() -> pass(0));
            // The lightmap is still switched off and the vanilla tail still skipped, so GL state is what vanilla leaves
            assertEquals(List.of("sections", "lightmapOff"), events);
            assertTrue(ci.isCancelled());
            // The next frame does not try TA again
            events.clear();
            pass(0);
            assertEquals(List.of("sections", "lightmapOff"), events);
            assertNull(Statics.get(ThaumicAugmentationCompat.class, "hook"));
        } finally {
            Statics.set(ThaumicAugmentationCompat.class, "hook", previous);
        }
    }

    @Test
    void aGlobalBlockEntityOutsideTheViewIsSkippedButTaRuns() {
        TileEntity behind = mock(TileEntity.class);
        when(behind.shouldRenderInPass(0)).thenReturn(true);
        AxisAlignedBB box = new AxisAlignedBB(5, 5, 5, 6, 6, 6);
        when(behind.getRenderBoundingBox()).thenReturn(box);
        when(camera.isBoundingBoxInFrustum(box)).thenReturn(false);
        globalTiles.add(behind);
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            pass(0);
            assertEquals(List.of("sections", "drawBatch0", "ta0", "lightmapOff"), events);
        }
        verify(dispatcher, never()).render(any(TileEntity.class), anyFloat(), anyInt());
    }

    @Test
    void oddPassNumbersAreForwardedUntouched() {
        // Forge only uses 0 and 1, but a mod driving extra passes must see its own number, not a clamp
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            pass(-1);
            pass(2);
            pass(Integer.MAX_VALUE);
            assertEquals(List.of("ta-1", "ta2", "ta" + Integer.MAX_VALUE),
                    events.stream().filter(e -> e.startsWith("ta")).toList());
        }
    }

    @Test
    void theSectionPassFailingSkipsTaAndPropagates() {
        // A crash in Impetus' own section pass must surface as itself, not be followed by TA drawing on broken state
        ImpetusWorldRenderer renderer = Mixins.get(global, "renderer");
        doThrow(new IllegalStateException("sections")).when(renderer).renderBlockEntities(any());
        try (MockedStatic<ThaumicAugmentationCompat> ta = recordTa()) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> pass(0));
            assertEquals("sections", thrown.getMessage());
            ta.verify(() -> ThaumicAugmentationCompat.onRenderEntities(anyInt()), never());
        }
    }
}
