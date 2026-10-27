package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.render.terrain.SimpleWorldRenderer;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.pipeline.shadow.GlCache;
import com.bdmajora.impetus.umbra.pipeline.shadow.GlCalls;
import com.bdmajora.impetus.umbra.pipeline.shadow.ShadowColorSettings;
import com.bdmajora.impetus.umbra.shaderpack.ConstDirectives;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the shadow renderer facade from construction through render to teardown
class UmbraShadowRendererTest {
    // Vertex stage of every test program
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }";
    // Two flags that are both off
    private static final boolean[] OFF = {false, false};
    // Identity matrix in column order
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    // Distant Horizons calls made by the stand in handles in order
    private static final List<String> DH_CALLS = new ArrayList<>();

    // GL model of the test
    private PipelineGl gl;
    // World renderer whose searches and draws are recorded
    private ImpetusWorldRenderer worldRenderer;
    // Pass events in order
    private final List<String> events = new ArrayList<>();
    // Texture names handed out by glGenTextures in order
    private final List<Integer> generated = new ArrayList<>();
    // Model view of the active render info before the test
    private final float[] savedModelView = new float[16];
    // Distant Horizons statics before the test in field order
    private final Object[] savedDh = new Object[6];
    // Names of the Distant Horizons statics the test swaps
    private static final String[] DH_FIELDS = {"dhPresent", "isRenderingEnabled", "renderShadowSolid",
            "renderDeferredLods", "getNearPlane", "getFarPlane"};

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Records an opaque Distant Horizons draw
    static void recordSolid() {
        DH_CALLS.add("dh solid");
    }

    // Records a translucent Distant Horizons draw
    static void recordDeferred() {
        DH_CALLS.add("dh deferred");
    }

    // Builds a world with a view entity and a recording world renderer
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        GlCache.reset();
        DH_CALLS.clear();
        for (int i = 0; i < DH_FIELDS.length; i++) {
            this.savedDh[i] = Statics.get(DhCompat.class, DH_FIELDS[i]);
        }
        LWJGLService service = TestGl.gl();
        AtomicInteger next = new AtomicInteger(5000);
        doAnswer(inv -> {
            int name = next.incrementAndGet();
            this.generated.add(name);
            return name;
        }).when(service).glGenTextures();
        doAnswer(inv -> this.events.add("copy")).when(service)
                .glCopyTexSubImage2D(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        when(service.glGetBoolean(GL11.GL_CULL_FACE)).thenReturn(true);
        when(service.glGetInteger(GL11.GL_DEPTH_FUNC)).thenReturn(GL11.GL_LESS);

        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "loadedEntityList", new ArrayList<Entity>());
        Mixins.set(world, "loadedTileEntityList", new ArrayList<TileEntity>());
        this.gl.client.world = world;
        when(this.gl.client.getRenderViewEntity()).thenReturn(mock(EntityPlayerSP.class));
        when(this.gl.client.getRenderManager()).thenReturn(mock(RenderManager.class));
        this.worldRenderer = mock(ImpetusWorldRenderer.class);
        RenderGlobal renderGlobal = Mc.mock(RenderGlobal.class, SimpleWorldRenderer.Provider.class);
        when(((SimpleWorldRenderer.Provider<?>) renderGlobal).impetus$getWorldRenderer()).thenAnswer(inv -> this.worldRenderer);
        this.gl.client.renderGlobal = renderGlobal;
        doAnswer(inv -> {
            this.events.add("setup " + UmbraShadowRenderer.isShadowPass() + " " + deviceActive());
            return null;
        }).when(this.worldRenderer).setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
        doAnswer(inv -> this.events.add(inv.<BlockRenderLayer>getArgument(0).name() + " " + deviceActive())).when(this.worldRenderer)
                .drawChunkLayer(any(BlockRenderLayer.class), anyDouble(), anyDouble(), anyDouble());

        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        modelView.get(0, this.savedModelView);
        modelView.put(0, IDENTITY);
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.setGbufferProjection(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
        state.setCameraPosition(8.5, 70.0, -8.5);
        state.setFogColor(0.25f, 0.5f, 0.75f);
    }

    // Restores every static the test changed
    @AfterEach
    void close() {
        for (int i = 0; i < DH_FIELDS.length; i++) {
            Statics.set(DhCompat.class, DH_FIELDS[i], this.savedDh[i]);
        }
        Statics.<FloatBuffer>get(ActiveRenderInfo.class, "MODELVIEW").put(0, this.savedModelView);
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        state.setGbufferProjection(new Matrix4f());
        state.setGbufferModelView(new Matrix4f());
        state.setShadowModelView(new Matrix4f());
        state.setShadowProjection(new Matrix4f());
        state.setCameraPosition(0, 0, 0);
        state.setFogColor(0, 0, 0);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        GlCache.reset();
        this.gl.close();
    }

    // Returns whether the engine render device is inside managed code
    private static boolean deviceActive() {
        return Mixins.<Boolean>get(RenderDevice.INSTANCE, "isActive");
    }

    // Returns a shadow program source with the given fragment stage
    private static ProgramSource source(String fragment) {
        return new ProgramSource("shadow", VERTEX, null, null, null, fragment, null);
    }

    // Returns content settings from shaders.properties lines
    private static ShadowContentSettings content(String... lines) {
        return ShadowContentSettings.from(ShaderProperties.parse(String.join("\n", lines)));
    }

    // Returns a renderer over two colour buffers with the given source and content and restorer
    private UmbraShadowRenderer renderer(ProgramSource shadow, ShadowContentSettings content, Runnable restorer) {
        return new UmbraShadowRenderer(256, 64.0f, -100.05f, 156.0f, 2.0f, null, 0.0f, shadow, null, null,
                Map.of(), Map.of(), OFF, OFF, OFF, false, restorer, content, 0.0f, 64.0f, false,
                ShadowColorSettings.parse(new ConstDirectives(""), ShadowColorSettings.OPTIFINE_BUFFERS), new BitSet());
    }

    // Returns a renderer with default content that records each restore
    private UmbraShadowRenderer renderer() {
        return renderer(source("/* DRAWBUFFERS:01 */\nvoid main() {}"), ShadowContentSettings.defaults(),
                () -> this.events.add("restore"));
    }

    // Checks the targets and draw mask and the state before the first pass
    @Test
    void constructionBuildsTheTargetsAndTheDrawMask() {
        UmbraShadowRenderer renderer = renderer();
        assertEquals(256, renderer.getResolution());
        assertEquals(2, renderer.getColorBufferCount());
        assertNotEquals(0, renderer.getColorTextureId(0));
        assertNotEquals(0, renderer.getColorTextureId(1));
        assertEquals(0, renderer.getColorTextureId(2));
        assertEquals(0, renderer.getColorTextureId(-1));
        assertEquals(GL11.GL_RGBA8, renderer.getColorInternalFormat(1));
        assertEquals(0, renderer.getColorInternalFormat(2));
        assertEquals(0, renderer.getColorInternalFormat(-1));
        assertNotEquals(renderer.getDepthTextureId(), renderer.getDepthTextureNoTranslucentsId());
        assertTrue(renderer.getFramebuffer().hasDepthAttachment());
        assertArrayEquals(new int[]{0, 1}, Mixins.<int[]>get(renderer, "drawBuffers"));
        assertNull(renderer.getCullingFrustum());
        assertEquals(new Matrix4f(), renderer.getShadowModelView());
        assertEquals(new Matrix4f(), renderer.getShadowProjection());
        assertFalse(UmbraShadowRenderer.isShadowPass());
        renderer.destroy();
    }

    // Checks that a mask naming a missing buffer and a missing fragment stage both fall back to buffer 0
    @Test
    void missingBuffersAndAMissingFragmentFallBackToBufferZero() {
        UmbraShadowRenderer missing = renderer(source("/* DRAWBUFFERS:5 */\nvoid main() {}"), ShadowContentSettings.defaults(), () -> { });
        assertArrayEquals(new int[]{0}, Mixins.<int[]>get(missing, "drawBuffers"));
        missing.destroy();
        UmbraShadowRenderer noFragment = renderer(source(null), ShadowContentSettings.defaults(), () -> { });
        assertArrayEquals(new int[]{0}, Mixins.<int[]>get(noFragment, "drawBuffers"));
        noFragment.destroy();
    }

    // Checks that invalid arguments fail before any texture is generated
    @Test
    void invalidArgumentsFailBeforeAnyGlWork() {
        ShadowColorSettings colors = ShadowColorSettings.parse(new ConstDirectives(""), 2);
        ProgramSource shadow = source("void main() {}");
        ShadowContentSettings content = ShadowContentSettings.defaults();
        Runnable restorer = () -> { };
        assertThrows(NullPointerException.class, () -> new UmbraShadowRenderer(256, 64, -1, 1, 2, null, 0, null, null, null,
                Map.of(), Map.of(), OFF, OFF, OFF, false, restorer, content, 0, 64, false, colors, new BitSet()));
        assertThrows(NullPointerException.class, () -> new UmbraShadowRenderer(256, 64, -1, 1, 2, null, 0, shadow, null, null,
                null, Map.of(), OFF, OFF, OFF, false, restorer, content, 0, 64, false, colors, new BitSet()));
        assertThrows(NullPointerException.class, () -> new UmbraShadowRenderer(256, 64, -1, 1, 2, null, 0, shadow, null, null,
                Map.of(), Map.of(), OFF, OFF, OFF, false, null, content, 0, 64, false, colors, new BitSet()));
        assertThrows(NullPointerException.class, () -> new UmbraShadowRenderer(256, 64, -1, 1, 2, null, 0, shadow, null, null,
                Map.of(), Map.of(), OFF, OFF, OFF, false, restorer, null, 0, 64, false, colors, new BitSet()));
        for (int resolution : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new UmbraShadowRenderer(resolution, 64, -1, 1, 2, null, 0, shadow,
                    null, null, Map.of(), Map.of(), OFF, OFF, OFF, false, restorer, content, 0, 64, false, colors, new BitSet()));
        }
        assertThrows(IllegalArgumentException.class, () -> new UmbraShadowRenderer(256, 64, -1, 1, 2, null, 0, shadow, null, null,
                Map.of(), Map.of(), new boolean[1], OFF, OFF, false, restorer, content, 0, 64, false, colors, new BitSet()));
        assertTrue(this.generated.isEmpty());
    }

    // Checks that an error while compiling the caster programs deletes every target already made
    @Test
    void anErrorWhileCompilingReleasesTheTargets() {
        doThrow(new AssertionError("driver")).when(TestGl.gl()).glCreateShader(anyInt());
        doThrow(new AssertionError("driver")).when(TestGl.gl()).glCreateProgram();
        assertThrows(AssertionError.class, this::renderer);
        assertEquals(4, this.generated.size());
        assertEquals(this.generated, GlCalls.deletedTextures().stream().sorted().toList());
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
    }

    // Checks the stage order and the scope and flag and state of a full pass
    @Test
    void aPassRunsEveryStageInOrderAndRestoresTheState() {
        UmbraShadowRenderer renderer = renderer();
        this.events.clear();
        GlCache.seed(false, true, false, false, GL11.GL_ALWAYS, 0.5, 0, 0, 0, 0);
        renderer.render();
        assertEquals(List.of("setup true true", "SOLID true", "CUTOUT_MIPPED true", "CUTOUT true",
                "restore", "restore", "restore", "restore", "copy", "restore", "TRANSLUCENT true", "restore"), this.events);
        verify(this.worldRenderer).setupShadowTerrain(any(), any(), any(), eq(1), eq(false));
        assertFalse(UmbraShadowRenderer.isShadowPass());
        assertFalse(deviceActive());
        assertTrue(GlCache.cull());
        assertEquals(GL11.GL_LESS, GlCache.depthFunc());
        assertTrue(GlCache.depthMask());
        assertFalse(GlCache.blend());
        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 0.0f}, GlCache.clearColor());
        assertEquals(renderer.getShadowModelView(), CapturedRenderingState.INSTANCE.getShadowModelView());
        assertEquals(renderer.getShadowProjection(), CapturedRenderingState.INSTANCE.getShadowProjection());
        assertNotEquals(new Matrix4f(), renderer.getShadowProjection());
        assertNotNull(renderer.getCullingFrustum());
        verify(this.gl.client.getTextureManager(), Mockito.times(2)).bindTexture(any());
        renderer.render();
        verify(this.worldRenderer).setupShadowTerrain(any(), any(), any(), eq(2), eq(false));
        renderer.destroy();
    }

    // Checks that the terrain switch skips every layer and the translucent switch skips only the translucent layer
    @Test
    void theTerrainSwitchesSkipTheirLayers() {
        UmbraShadowRenderer noTerrain = renderer(source("void main() {}"), content("shadowTerrain = false"), () -> { });
        noTerrain.render();
        verify(this.worldRenderer).setupShadowTerrain(any(), any(), any(), eq(1), eq(false));
        verify(this.worldRenderer, never()).drawChunkLayer(any(), anyDouble(), anyDouble(), anyDouble());
        noTerrain.destroy();
        UmbraShadowRenderer noTranslucent = renderer(source("void main() {}"), content("shadowTranslucent = false"), () -> { });
        noTranslucent.render();
        verify(this.worldRenderer).drawChunkLayer(eq(BlockRenderLayer.SOLID), anyDouble(), anyDouble(), anyDouble());
        verify(this.worldRenderer, never()).drawChunkLayer(eq(BlockRenderLayer.TRANSLUCENT), anyDouble(), anyDouble(), anyDouble());
        noTranslucent.destroy();
    }

    // Checks that a pass without a world renderer touches nothing
    @Test
    void withoutAWorldRendererThePassDoesNothing() {
        UmbraShadowRenderer renderer = renderer();
        this.worldRenderer = null;
        this.events.clear();
        Mockito.clearInvocations(TestGl.gl());
        renderer.render();
        assertTrue(this.events.isEmpty());
        verify(TestGl.gl(), never()).glClear(anyInt());
        assertFalse(UmbraShadowRenderer.isShadowPass());
        assertEquals(new Matrix4f(), renderer.getShadowModelView());
        renderer.destroy();
    }

    // Checks that three failed passes disable shadows with one log line and the state comes back after each
    @Test
    void threeFailuresDisableShadowsAndEachPassRestoresTheState() {
        Logger original = Statics.get(UmbraShadowRenderer.class, "LOGGER");
        Logger logger = mock(Logger.class);
        Statics.set(UmbraShadowRenderer.class, "LOGGER", logger);
        try {
            UmbraShadowRenderer renderer = renderer();
            doAnswer(inv -> {
                throw new IllegalStateException("no render lists yet");
            }).when(this.worldRenderer).setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
            for (int pass = 1; pass <= UmbraShadowRenderer.MAX_FAILURES; pass++) {
                GlCache.seed(false, false, false, false, GL11.GL_ALWAYS, 1.0, 0, 0, 0, 0);
                renderer.render();
                assertFalse(UmbraShadowRenderer.isShadowPass());
                assertFalse(deviceActive());
                assertTrue(GlCache.cull());
                assertEquals(GL11.GL_LESS, GlCache.depthFunc());
                verify(logger, Mockito.times(pass == UmbraShadowRenderer.MAX_FAILURES ? 1 : 0))
                        .error(anyString(), any(Throwable.class));
            }
            renderer.render();
            verify(this.worldRenderer, Mockito.times(UmbraShadowRenderer.MAX_FAILURES))
                    .setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
            renderer.destroy();
        } finally {
            Statics.set(UmbraShadowRenderer.class, "LOGGER", original);
        }
    }

    // Checks that a virtual machine error propagates without counting as a failure and still resets the pass
    @Test
    void aVirtualMachineErrorPropagatesAndResetsThePass() {
        UmbraShadowRenderer renderer = renderer();
        doAnswer(inv -> {
            throw new StackOverflowError("deep");
        }).when(this.worldRenderer).setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
        assertThrows(StackOverflowError.class, renderer::render);
        assertFalse(UmbraShadowRenderer.isShadowPass());
        assertFalse(deviceActive());
        assertTrue(GlCache.cull());
        assertEquals(0, Mixins.<Integer>get(renderer, "failureCount"));
        renderer.destroy();
    }

    // Checks that the frame clear colour comes back even when the clear throws
    @Test
    void theClearColourComesBackWhenTheClearThrows() {
        UmbraShadowRenderer renderer = renderer();
        doThrow(new IllegalStateException("clear")).when(TestGl.gl()).glClear(GL11.GL_DEPTH_BUFFER_BIT);
        renderer.render();
        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 0.0f}, GlCache.clearColor());
        assertEquals(1, Mixins.<Integer>get(renderer, "failureCount"));
        verify(this.worldRenderer, never()).setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
        renderer.destroy();
    }

    // Checks that destroy frees everything once and texture lookups and passes stop afterwards
    @Test
    void destroyFreesEverythingOnceAndStopsThePass() {
        UmbraShadowRenderer renderer = renderer();
        Mockito.clearInvocations(TestGl.gl());
        renderer.destroy();
        int deleted = GlCalls.deletedTextures().size();
        assertEquals(4, deleted);
        verify(TestGl.gl()).glDeleteFramebuffers(anyInt());
        verify(TestGl.gl()).glDeleteProgram(anyInt());
        renderer.destroy();
        assertEquals(deleted, GlCalls.deletedTextures().size());
        assertThrows(IllegalStateException.class, renderer::getDepthTextureId);
        assertThrows(IllegalStateException.class, renderer::getDepthTextureNoTranslucentsId);
        assertThrows(IllegalStateException.class, () -> renderer.getColorTextureId(0));
        assertEquals(GL11.GL_RGBA8, renderer.getColorInternalFormat(0));
        assertEquals(256, renderer.getResolution());
        assertEquals(2, renderer.getColorBufferCount());
        renderer.render();
        verify(this.worldRenderer, never()).setupShadowTerrain(any(), any(), any(), anyInt(), anyBoolean());
    }

    // Checks that Distant Horizons terrain draws after the opaque and translucent layers when the pack enables it
    @Test
    void distantHorizonsTerrainDrawsAfterEachTerrainStage() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Statics.set(DhCompat.class, "dhPresent", true);
        Statics.set(DhCompat.class, "isRenderingEnabled", MethodHandles.constant(boolean.class, true));
        Statics.set(DhCompat.class, "getNearPlane", MethodHandles.constant(float.class, 0.1f));
        Statics.set(DhCompat.class, "getFarPlane", MethodHandles.constant(float.class, 4096.0f));
        Statics.set(DhCompat.class, "renderShadowSolid",
                lookup.findStatic(UmbraShadowRendererTest.class, "recordSolid", MethodType.methodType(void.class)));
        Statics.set(DhCompat.class, "renderDeferredLods",
                lookup.findStatic(UmbraShadowRendererTest.class, "recordDeferred", MethodType.methodType(void.class)));
        UmbraRenderingPipeline pipeline = mock(UmbraRenderingPipeline.class);
        DhCompat compat = mock(DhCompat.class);
        when(compat.shouldRenderShadows()).thenReturn(true);
        when(pipeline.getDhCompat()).thenReturn(compat);
        Mixins.set(Umbra.class, "renderingPipeline", pipeline);
        UmbraShadowRenderer renderer = renderer();
        doAnswer(inv -> DH_CALLS.add(inv.<BlockRenderLayer>getArgument(0).name())).when(this.worldRenderer)
                .drawChunkLayer(any(BlockRenderLayer.class), anyDouble(), anyDouble(), anyDouble());
        renderer.render();
        assertEquals(List.of("SOLID", "CUTOUT_MIPPED", "CUTOUT", "dh solid", "TRANSLUCENT", "dh deferred"), DH_CALLS);
        renderer.destroy();
    }
}
