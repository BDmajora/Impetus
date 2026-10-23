package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SkyHorizonTest {
    private PipelineGl gl;
    private BufferBuilder realBuffer;
    private BufferBuilder buffer;
    private final List<double[]> vertices = new ArrayList<>();
    private final Vector3d savedCamera = new Vector3d();

    @BeforeAll
    static void bootstrap() {
        PipelineGl.bootstrap();
    }

    @BeforeEach
    void open() {
        this.gl = new PipelineGl();
        this.realBuffer = Tessellator.getInstance().getBuffer();
        this.buffer = mock(BufferBuilder.class, Mockito.RETURNS_SELF);
        doAnswer(inv -> {
            this.vertices.add(new double[]{inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)});
            return this.buffer;
        }).when(this.buffer).pos(anyDouble(), anyDouble(), anyDouble());
        Mixins.set(Tessellator.getInstance(), "buffer", this.buffer);
        this.savedCamera.set(CapturedRenderingState.INSTANCE.getCameraPosition());
        CapturedRenderingState.INSTANCE.setCameraPosition(0, 70, 0);
    }

    @AfterEach
    void close() {
        Mixins.set(Tessellator.getInstance(), "buffer", this.realBuffer);
        CapturedRenderingState.INSTANCE.setCameraPosition(this.savedCamera.x, this.savedCamera.y, this.savedCamera.z);
        this.gl.close();
    }

    // The ring exactly as OptiFine's drawHorizon (and the code this replaced) emitted it
    private static List<double[]> optiFineRing(float radius, double bottom) {
        double d0 = radius * 0.9238D;
        double d1 = radius * 0.3826D;
        double d2 = -d1;
        double d3 = -d0;
        double top = 16.0D;
        double[][] ring = {
                {d2, bottom, d3}, {d2, top, d3}, {d3, top, d2}, {d3, bottom, d2},
                {d3, bottom, d2}, {d3, top, d2}, {d3, top, d1}, {d3, bottom, d1},
                {d3, bottom, d1}, {d3, top, d1}, {d2, top, d1}, {d2, bottom, d1},
                {d2, bottom, d1}, {d2, top, d1}, {d1, top, d0}, {d1, bottom, d0},
                {d1, bottom, d0}, {d1, top, d0}, {d0, top, d1}, {d0, bottom, d1},
                {d0, bottom, d1}, {d0, top, d1}, {d0, top, d2}, {d0, bottom, d2},
                {d0, bottom, d2}, {d0, top, d2}, {d1, top, d3}, {d1, bottom, d3},
                {d1, bottom, d3}, {d1, top, d3}, {d2, top, d3}, {d2, bottom, d3}};
        return List.of(ring);
    }

    private void assertRing(float radius) {
        List<double[]> expected = optiFineRing(radius, -70.0);
        assertEquals(expected.size(), this.vertices.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), this.vertices.get(i), "vertex " + i);
        }
    }

    @Test
    void theRingMatchesOptiFinesVertexForVertexAtTheRenderDistanceEdge() {
        new SkyHorizon().draw();
        verify(this.buffer).begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        verify(this.buffer, times(32)).endVertex();
        verify(this.buffer).finishDrawing();
        assertRing(8 * 16.0f);
    }

    @Test
    void theRadiusStopsAt256BlocksSoTheRingNeverReachesTheFarPlane() {
        this.gl.settings.renderDistanceChunks = 32;
        new SkyHorizon().draw();
        assertRing(256.0f);
    }

    @Test
    void aFailureMidRingLeavesTheSharedBufferUsableAndTheNextFrameDrawsAgain() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 5) {
                throw new IllegalStateException("buffer full");
            }
            return this.buffer;
        }).when(this.buffer).pos(anyDouble(), anyDouble(), anyDouble());
        SkyHorizon horizon = new SkyHorizon();
        horizon.draw();
        // A half-built ring left the buffer "already building" and vanilla's next begin crashed the client
        verify(this.buffer).finishDrawing();
        horizon.draw();
        verify(this.buffer, times(2)).begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
    }

    @Test
    void aBufferSomeoneElseIsBuildingIsLeftAlone() {
        doAnswer(inv -> {
            throw new IllegalStateException("Already building!");
        }).when(this.buffer).begin(Mockito.anyInt(), Mockito.any());
        new SkyHorizon().draw();
        verify(this.buffer, never()).finishDrawing();
        verify(this.buffer, never()).pos(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void reentryIsIgnoredAndAVirtualMachineErrorIsNotSwallowed() {
        SkyHorizon horizon = new SkyHorizon();
        doAnswer(inv -> {
            horizon.draw();
            return null;
        }).when(this.buffer).begin(Mockito.anyInt(), Mockito.any());
        horizon.draw();
        verify(this.buffer, times(1)).begin(Mockito.anyInt(), Mockito.any());
        doAnswer(inv -> {
            throw new StackOverflowError();
        }).when(this.buffer).begin(Mockito.anyInt(), Mockito.any());
        assertThrows(StackOverflowError.class, horizon::draw);
        // The guard is released even then, so the next frame tries again
        doAnswer(inv -> null).when(this.buffer).begin(Mockito.anyInt(), Mockito.any());
        horizon.draw();
        verify(this.buffer, times(3)).begin(Mockito.anyInt(), Mockito.any());
    }
}
