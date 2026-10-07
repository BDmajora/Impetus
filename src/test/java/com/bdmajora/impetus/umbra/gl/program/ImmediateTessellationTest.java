package com.bdmajora.impetus.umbra.gl.program;

import com.bdmajora.impetus.mixin.core.shader.ModelRendererTessellationMixin;
import com.bdmajora.impetus.mixin.core.shader.TessellationDrawMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImmediateTessellationTest {
    private static final int PATCHES = 0xE;

    @AfterEach
    void forget() {
        ImmediateTessellation.reset();
    }

    // Binds a tessellated program as GL itself reports it
    private static void bindTessellated(int handle) {
        ImmediateTessellation.programBound(handle, true);
        Mockito.when(TestGl.gl().glGetInteger(ImmediateTessellation.CURRENT_PROGRAM)).thenReturn(handle);
    }

    @Test
    void onlyATessellatedProgramTurnsImmediateDrawsIntoPatches() {
        assertFalse(ImmediateTessellation.isActive());
        assertFalse(ImmediateTessellation.drawArrays(GL11.GL_QUADS, 0, 8));

        bindTessellated(5);
        Mockito.verify(TestGl.gl()).glPatchParameteri(0x8E72, 3);
        assertTrue(ImmediateTessellation.isActive());
        // Lines and empty draws are left to the caller
        assertFalse(ImmediateTessellation.drawArrays(GL11.GL_LINES, 0, 2));
        assertFalse(ImmediateTessellation.drawArrays(GL11.GL_QUADS, 0, 0));

        assertTrue(ImmediateTessellation.drawArrays(GL11.GL_TRIANGLES, 3, 6));
        Mockito.verify(TestGl.gl()).glDrawArrays(PATCHES, 3, 6);

        // Quads go through the shared index buffer, offset by the first vertex
        assertTrue(ImmediateTessellation.drawArrays(GL11.GL_QUADS, 4, 8));
        Mockito.verify(TestGl.gl()).glDrawElementsBaseVertex(PATCHES, 12, GL11.GL_UNSIGNED_INT, 0L, 4);
        assertTrue(ImmediateTessellation.drawArrays(GL11.GL_QUADS, 0, 4));
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glBufferData(Mockito.eq(GL15.GL_ELEMENT_ARRAY_BUFFER), Mockito.any(ByteBuffer.class), Mockito.eq(GL15.GL_STATIC_DRAW));
        // A batch past the buffer grows it to the next power of two
        assertTrue(ImmediateTessellation.drawArrays(GL11.GL_QUADS, 0, 4 * 3000));
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glBufferData(Mockito.eq(GL15.GL_ELEMENT_ARRAY_BUFFER), Mockito.any(ByteBuffer.class), Mockito.eq(GL15.GL_STATIC_DRAW));
        assertEquals(4096, (int) Statics.<Integer>get(ImmediateTessellation.class, "quadCapacity"));
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glGenBuffers();

        // Something else bound behind Umbra's back is not tessellated
        Mockito.when(TestGl.gl().glGetInteger(ImmediateTessellation.CURRENT_PROGRAM)).thenReturn(9);
        assertFalse(ImmediateTessellation.isActive());
        ImmediateTessellation.programBound(0, false);
        assertFalse(ImmediateTessellation.isActive());

        ImmediateTessellation.reset();
        ImmediateTessellation.reset();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteBuffers(Mockito.anyInt());
    }

    @Test
    void modelPartsDrawTheirBoxesWhileTessellated() {
        ModelRenderer model = new ModelRenderer(new ModelBase() {});
        ModelBox box = Mockito.mock(ModelBox.class);
        model.cubeList.add(box);

        try (MockedStatic<GlStateManager> gl = Mockito.mockStatic(GlStateManager.class)) {
            ImmediateTessellation.callModelList(model, 9, 0.0625F);
            gl.verify(() -> GlStateManager.callList(9));
            Mockito.verify(box, Mockito.never()).render(Mockito.any(), Mockito.anyFloat());

            bindTessellated(5);
            ImmediateTessellation.callModelList(model, 9, 0.0625F);
            Mockito.verify(box).render(Mockito.any(BufferBuilder.class), Mockito.eq(0.0625F));
            gl.verify(() -> GlStateManager.callList(9), Mockito.times(1));
        }
    }

    @Test
    void theMixinsHandDrawsToTheConversion() {
        Mixins.instance(TessellationDrawMixin.class);
        CallbackInfo untouched = Mixins.ci();
        Mixins.call(TessellationDrawMixin.class, "impetus$drawAsPatches", GL11.GL_TRIANGLES, 0, 3, untouched);
        assertFalse(untouched.isCancelled());
        bindTessellated(5);
        CallbackInfo patched = Mixins.ci();
        Mixins.call(TessellationDrawMixin.class, "impetus$drawAsPatches", GL11.GL_TRIANGLES, 0, 3, patched);
        assertTrue(patched.isCancelled());

        ModelRendererTessellationMixin model = Mixins.instance(ModelRendererTessellationMixin.class);
        ModelBox box = Mockito.mock(ModelBox.class);
        Mixins.set(model, "cubeList", new ArrayList<>(List.of(box)));
        Mc.Recorded<Void> original = Mc.operation();
        Mixins.call(model, "impetus$tessellatedList", 9, original, 0.5F);
        Mockito.verify(box).render(Mockito.any(BufferBuilder.class), Mockito.eq(0.5F));
        assertEquals(0, original.count());
        ImmediateTessellation.programBound(0, false);
        Mixins.call(model, "impetus$tessellatedList", 9, original, 0.5F);
        assertEquals(1, original.count());
    }
}
