package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.program.GlProgram;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.sampler.ShadowSamplerKinds;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ComputePassTest {
    private static ComputePass pass(int[] groups, float[] scale, int[] local, int indirect, long offset) {
        return new ComputePass(mock(GlProgram.class), mock(ProgramUniforms.class), ShadowSamplerKinds.ALL_COMPARE,
                groups, scale, local, indirect, offset);
    }

    @Test
    void anIndirectDispatchReadsItsCountsFromThePackBufferAndUnbindsIt() {
        pass(new int[]{1, 1, 1}, new float[]{1, 1}, new int[]{8, 8, 1}, 12, 64L).dispatch(320, 240);
        InOrder order = Mockito.inOrder(TestGl.gl());
        order.verify(TestGl.gl()).glBindBuffer(0x90EE, 12);
        order.verify(TestGl.gl()).glDispatchComputeIndirect(64L);
        order.verify(TestGl.gl()).glBindBuffer(0x90EE, 0);
        verify(TestGl.gl(), never()).glDispatchCompute(anyInt(), anyInt(), anyInt());
    }

    @Test
    void aScreenRelativeDispatchCoversTheRenderSizeThisFrame() {
        ComputePass pass = pass(new int[]{1, 1, 1}, new float[]{0.5f, 0.25f}, new int[]{16, 8, 1}, -1, 0L);
        pass.dispatch(320, 240);
        verify(TestGl.gl()).glDispatchCompute(10, 8, 1);
        // After a resize the same pass covers the new size
        pass.dispatch(1920, 1080);
        verify(TestGl.gl()).glDispatchCompute(60, 34, 1);
        verify(TestGl.gl(), never()).glDispatchComputeIndirect(anyLong());
    }

    @Test
    void withoutALocalSizeEveryInvocationIsOneGroup() {
        pass(new int[]{1, 1, 1}, new float[]{1.0f, 1.0f}, null, -1, 0L).dispatch(4, 3);
        verify(TestGl.gl()).glDispatchCompute(4, 3, 1);
    }

    @Test
    void aFixedDispatchLaunchesItsDeclaredGroups() {
        pass(new int[]{2, 3, 4}, null, new int[]{8, 8, 1}, -1, 0L).dispatch(320, 240);
        verify(TestGl.gl()).glDispatchCompute(2, 3, 4);
    }

    @Test
    void destroyFreesTheProgram() {
        ComputePass pass = pass(new int[]{1, 1, 1}, null, null, -1, 0L);
        pass.destroy();
        verify(pass.program).destroy();
    }
}
