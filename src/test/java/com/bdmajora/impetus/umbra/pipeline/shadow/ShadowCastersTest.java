package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.pipeline.rendering.PipelineGl;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.TestGl;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

// Covers the entity and block entity shadow draws
class ShadowCastersTest {
    // Shadow distance in blocks that gives a cull range of 48
    private static final float DISTANCE = 32.0f;
    // Partial tick passed to every draw
    private static final float PARTIAL = 0.5f;

    // GL model of the test
    private PipelineGl gl;
    // World whose lists the casters walk
    private WorldClient world;
    // Entity renderer whose draws are recorded
    private RenderManager renderManager;
    // Block entity dispatcher whose draws are recorded
    private TileEntityRendererDispatcher dispatcher;
    // Dispatcher installed before the test
    private TileEntityRendererDispatcher originalDispatcher;
    // Player that is also the view entity
    private EntityPlayerSP player;
    // Programs whose binds are recorded
    private ShadowCasterPrograms programs;
    // Matrix and program and draw events in order
    private final List<String> events = new ArrayList<>();
    // Camera position of every pass
    private final Vector3d camera = new Vector3d(100.0, 64.0, 100.0);
    // Projection whose first and depth and translation entries read 2 and 4 and 0
    private final Matrix4f projection = new Matrix4f().scaling(2, 3, 4);
    // Model view whose first and depth and translation entries read 1 and 1 and 1
    private final Matrix4f modelView = new Matrix4f().translation(1, 2, 3);

    // Registers the vanilla content once
    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    // Builds a world with a player and records every matrix and draw call
    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        GlCache.reset();
        this.world = mock(WorldClient.class);
        Mixins.set(this.world, "loadedEntityList", new ArrayList<Entity>());
        Mixins.set(this.world, "loadedTileEntityList", new ArrayList<TileEntity>());
        this.gl.client.world = this.world;
        this.player = mock(EntityPlayerSP.class, withSettings().name("player"));
        this.player.posX = 100.0;
        this.player.posZ = 100.0;
        when(this.gl.client.getRenderViewEntity()).thenReturn(this.player);
        this.renderManager = mock(RenderManager.class);
        when(this.gl.client.getRenderManager()).thenReturn(this.renderManager);
        doAnswer(inv -> this.events.add("entity " + inv.getArgument(0) + " " + inv.getArgument(1) + " " + inv.getArgument(2)))
                .when(this.renderManager).renderEntityStatic(any(Entity.class), anyFloat(), anyBoolean());
        this.originalDispatcher = TileEntityRendererDispatcher.instance;
        this.dispatcher = mock(TileEntityRendererDispatcher.class);
        TileEntityRendererDispatcher.instance = this.dispatcher;
        doAnswer(inv -> this.events.add("block " + inv.getArgument(0) + " " + inv.getArgument(1) + " " + inv.getArgument(2)))
                .when(this.dispatcher).render(any(TileEntity.class), anyFloat(), anyInt());
        this.programs = mock(ShadowCasterPrograms.class);
        when(this.programs.hasEntityProgram()).thenReturn(true);
        when(this.programs.hasBlockEntityProgram()).thenReturn(true);
        doAnswer(inv -> this.events.add("bind entity")).when(this.programs).bindEntity();
        doAnswer(inv -> this.events.add("bind block")).when(this.programs).bindBlockEntity();
        this.gl.gl11.when(() -> GL11.glMatrixMode(anyInt())).thenAnswer(inv -> this.events.add("mode " + inv.getArgument(0)));
        this.gl.gl11.when(GL11::glPushMatrix).thenAnswer(inv -> this.events.add("push"));
        this.gl.gl11.when(GL11::glPopMatrix).thenAnswer(inv -> this.events.add("pop"));
        this.gl.gl11.when(GL11::glLoadIdentity).thenAnswer(inv -> this.events.add("identity"));
        this.gl.gl11.when(() -> GL11.glMultMatrix(any(FloatBuffer.class))).thenAnswer(inv -> {
            FloatBuffer matrix = inv.getArgument(0);
            this.events.add("mult " + matrix.get(0) + " " + matrix.get(10) + " " + matrix.get(12));
            return null;
        });
    }

    // Restores the dispatcher and the caches
    @AfterEach
    void close() {
        TileEntityRendererDispatcher.instance = this.originalDispatcher;
        GlCache.reset();
        this.gl.close();
    }

    // Returns content settings from shaders.properties lines
    private static ShadowContentSettings content(String... lines) {
        return ShadowContentSettings.from(ShaderProperties.parse(String.join("\n", lines)));
    }

    // Adds a pig at a horizontal position
    private EntityPig pig(String name, double x, double z) {
        EntityPig pig = mock(EntityPig.class, withSettings().name(name));
        pig.posX = x;
        pig.posZ = z;
        this.world.loadedEntityList.add(pig);
        return pig;
    }

    // Adds a block entity at a position with a light value and an optional renderer
    private TileEntity blockEntity(String name, int x, int z, int light, boolean renderer) {
        TileEntity tileEntity = mock(TileEntity.class, withSettings().name(name));
        BlockPos pos = new BlockPos(x, 64, z);
        when(tileEntity.getPos()).thenReturn(pos);
        Block block = mock(Block.class);
        when(block.getLightValue(any(), any(), any())).thenReturn(light);
        when(tileEntity.getBlockType()).thenReturn(block);
        if (renderer) {
            doReturn(mock(TileEntitySpecialRenderer.class)).when(this.dispatcher).getRenderer(tileEntity);
        }
        this.world.loadedTileEntityList.add(tileEntity);
        return tileEntity;
    }

    // Draws once with the given content and the default distance
    private void render(ShadowContentSettings content) {
        new ShadowCasters(this.programs, content, DISTANCE).render(this.gl.client, this.camera, this.projection,
                this.modelView, PARTIAL);
    }

    // Returns the event list of a pass whose draws are the given lines
    private static List<String> pass(String... draws) {
        List<String> expected = new ArrayList<>(List.of("mode " + GL11.GL_PROJECTION, "push", "identity", "mult 2.0 4.0 0.0",
                "mode " + GL11.GL_MODELVIEW, "push", "identity", "mult 1.0 1.0 1.0"));
        expected.addAll(List.of(draws));
        expected.addAll(List.of("mode " + GL11.GL_PROJECTION, "pop", "mode " + GL11.GL_MODELVIEW, "pop"));
        return expected;
    }

    // Checks that every enabled caster in range draws inside balanced matrix stacks and the depth state is restored
    @Test
    void everyEnabledCasterInRangeDrawsInsideBalancedStacks() {
        this.world.loadedEntityList.add(this.player);
        pig("pig", 110.0, 90.0);
        pig("dead", 100.0, 100.0).isDead = true;
        pig("far", 148.001, 100.0);
        pig("edge", 148.0, 52.0);
        pig("nan", Double.NaN, 100.0);
        blockEntity("lamp", 120, 80, 15, true);
        blockEntity("furnace", 100, 100, 0, false);
        blockEntity("chest", 100, 200, 0, true);
        GlCache.seed(false, true, false, false, GL11.GL_LEQUAL, 1.0, 0, 0, 0, 0);
        render(ShadowContentSettings.defaults());
        assertEquals(pass("bind entity", "entity player 0.5 false", "entity pig 0.5 false", "entity edge 0.5 false",
                "entity nan 0.5 false", "bind block", "block lamp 0.5 -1"), this.events);
        TextureManager textures = this.gl.client.getTextureManager();
        verify(this.renderManager).cacheActiveRenderInfo(eq(this.world), isNull(), eq(this.player), isNull(),
                eq(this.gl.settings), eq(PARTIAL));
        verify(this.renderManager).setRenderPosition(100.0, 64.0, 100.0);
        verify(this.dispatcher).prepare(eq(this.world), eq(textures), isNull(), eq(this.player), isNull(), eq(PARTIAL));
        verify(TestGl.gl()).glBindVertexArray(0);
        verify(TestGl.gl()).glUseProgram(0);
        assertFalse(GlCache.blend());
        assertTrue(GlCache.depthTest());
        assertTrue(GlCache.depthMask());
    }

    // Checks that only light emitting block entities draw when only emitters are enabled
    @Test
    void lightOnlyBlockEntitiesSkipDarkOnes() {
        blockEntity("lit", 100, 100, 15, true);
        blockEntity("dark", 100, 100, 0, true);
        blockEntity("negative", 100, 100, -1, true);
        render(content("shadowBlockEntities = false", "shadowLightBlockEntities = true", "shadowEntities = false",
                "shadowPlayer = false"));
        assertEquals(pass("bind block", "block lit 0.5 -1"), this.events);
        this.events.clear();
        render(ShadowContentSettings.defaults());
        assertEquals(pass("bind entity", "bind block", "block lit 0.5 -1", "block dark 0.5 -1", "block negative 0.5 -1"),
                this.events);
    }

    // Checks that the player and entity switches are independent
    @Test
    void thePlayerAndEntitySwitchesAreIndependent() {
        this.world.loadedEntityList.add(this.player);
        pig("pig", 100.0, 100.0);
        render(content("shadowEntities = false", "shadowBlockEntities = false"));
        assertEquals(pass("bind entity", "entity player 0.5 false"), this.events);
        this.events.clear();
        render(content("shadowPlayer = false", "shadowBlockEntities = false"));
        assertEquals(pass("bind entity", "entity pig 0.5 false"), this.events);
    }

    // Checks that a missing entity program still lets block entities draw
    @Test
    void aMissingEntityProgramStillDrawsBlockEntities() {
        when(this.programs.hasEntityProgram()).thenReturn(false);
        pig("pig", 100.0, 100.0);
        blockEntity("lamp", 100, 100, 15, true);
        render(ShadowContentSettings.defaults());
        assertEquals(pass("bind block", "block lamp 0.5 -1"), this.events);
        verify(this.programs, never()).bindEntity();
    }

    // Checks that a missing block entity program still lets entities draw
    @Test
    void aMissingBlockProgramStillDrawsEntities() {
        when(this.programs.hasBlockEntityProgram()).thenReturn(false);
        pig("pig", 100.0, 100.0);
        blockEntity("lamp", 100, 100, 15, true);
        render(ShadowContentSettings.defaults());
        assertEquals(pass("bind entity", "entity pig 0.5 false"), this.events);
        verify(this.programs, never()).bindBlockEntity();
    }

    // Checks that nothing enabled or no programs touch no state at all
    @Test
    void nothingToDrawTouchesNoState() {
        pig("pig", 100.0, 100.0);
        render(content("shadowEntities = false", "shadowPlayer = false", "shadowBlockEntities = false"));
        when(this.programs.hasEntityProgram()).thenReturn(false);
        when(this.programs.hasBlockEntityProgram()).thenReturn(false);
        render(ShadowContentSettings.defaults());
        assertTrue(this.events.isEmpty());
        verify(this.renderManager, never()).setRenderPosition(anyDouble(), anyDouble(), anyDouble());
    }

    // Checks that a missing world or view entity draws nothing
    @Test
    void noWorldOrViewEntityDrawsNothing() {
        pig("pig", 100.0, 100.0);
        this.gl.client.world = null;
        render(ShadowContentSettings.defaults());
        this.gl.client.world = this.world;
        when(this.gl.client.getRenderViewEntity()).thenReturn(null);
        render(ShadowContentSettings.defaults());
        assertTrue(this.events.isEmpty());
    }

    // Checks that a throwing caster still unwinds both stacks and restores the depth state
    @Test
    void aThrowingCasterStillUnwindsTheStacks() {
        pig("pig", 100.0, 100.0);
        doAnswer(inv -> {
            throw new IllegalStateException("model");
        }).when(this.renderManager).renderEntityStatic(any(Entity.class), anyFloat(), anyBoolean());
        GlCache.seed(true, true, false, false, GL11.GL_LEQUAL, 1.0, 0, 0, 0, 0);
        assertThrows(IllegalStateException.class, () -> render(ShadowContentSettings.defaults()));
        assertEquals(pass("bind entity"), this.events);
        verify(TestGl.gl()).glUseProgram(0);
        assertFalse(GlCache.blend());
        assertTrue(GlCache.depthTest());
        assertTrue(GlCache.depthMask());
    }

    // Checks the range test at the boundary and for non finite values
    @Test
    void theRangeTestKeepsTheBoundaryAndNaN() {
        Vector3d at = new Vector3d(100.0, 0.0, 100.0);
        assertFalse(ShadowCasters.outsideRange(148.0, 100.0, at, 48.0));
        assertFalse(ShadowCasters.outsideRange(52.0, 52.0, at, 48.0));
        assertTrue(ShadowCasters.outsideRange(148.001, 100.0, at, 48.0));
        assertTrue(ShadowCasters.outsideRange(100.0, 51.999, at, 48.0));
        assertFalse(ShadowCasters.outsideRange(Double.NaN, Double.NaN, at, 48.0));
        assertFalse(ShadowCasters.outsideRange(1.0e300, 100.0, at, Double.NaN));
        assertTrue(ShadowCasters.outsideRange(100.0, 100.0, at, -1.0));
        assertFalse(ShadowCasters.outsideRange(Double.MAX_VALUE, -Double.MAX_VALUE, at, Double.POSITIVE_INFINITY));
        assertFalse(ShadowCasters.outsideRange(Double.POSITIVE_INFINITY, 100.0, at, Double.POSITIVE_INFINITY));
        assertTrue(ShadowCasters.outsideRange(Double.POSITIVE_INFINITY, 100.0, at, Double.MAX_VALUE));
    }

    // Checks that a negative shadow distance culls every caster while the stacks stay balanced
    @Test
    void aNegativeDistanceCullsEveryCaster() {
        pig("pig", 100.0, 100.0);
        blockEntity("lamp", 100, 100, 15, true);
        new ShadowCasters(this.programs, ShadowContentSettings.defaults(), -100.0f)
                .render(this.gl.client, this.camera, this.projection, this.modelView, PARTIAL);
        assertEquals(pass("bind entity", "bind block"), this.events);
    }

    // Checks that the largest finite distance keeps a caster far away
    @Test
    void theLargestDistanceKeepsFarCasters() {
        pig("pig", 1.0e30, -1.0e30);
        new ShadowCasters(this.programs, content("shadowBlockEntities = false"), Float.MAX_VALUE)
                .render(this.gl.client, this.camera, this.projection, this.modelView, PARTIAL);
        assertEquals(pass("bind entity", "entity pig 0.5 false"), this.events);
    }

    // Checks that empty lists still bind and balance the stacks and that repeated passes repeat the same calls
    @Test
    void emptyListsAndRepeatedPassesStayBalanced() {
        ShadowCasters casters = new ShadowCasters(this.programs, ShadowContentSettings.defaults(), DISTANCE);
        casters.render(this.gl.client, this.camera, this.projection, this.modelView, PARTIAL);
        List<String> first = new ArrayList<>(this.events);
        assertEquals(pass("bind entity", "bind block"), first);
        this.events.clear();
        casters.render(this.gl.client, this.camera, this.projection, this.modelView, PARTIAL);
        assertEquals(first, this.events);
    }

    // Checks that null programs or content are rejected
    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ShadowCasters(null, ShadowContentSettings.defaults(), 1.0f));
        assertThrows(NullPointerException.class, () -> new ShadowCasters(this.programs, null, 1.0f));
    }
}
