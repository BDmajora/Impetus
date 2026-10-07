package com.bdmajora.impetus.mixin.core.terrain;

import com.bdmajora.impetus.impl.render.terrain.compile.ChunkBufferBuilder;
import com.bdmajora.impetus.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Hooks only the model call inside renderBlock, so mods that replace the dispatcher or inject into renderBlock (ArchitectureCraft, Snow! Real Magic) still run under the fast block renderer
@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    // Optional: a coremod that rewrites renderBlock leaves the meshing loop on vanilla's model renderer
    @WrapOperation(method = "renderBlock", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BlockModelRenderer;renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;Z)Z"))
    private boolean impetus$useFastBlockRenderer(BlockModelRenderer renderer, IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, Operation<Boolean> original) {
        if (ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER && buffer instanceof ChunkBufferBuilder chunkBuffer && world instanceof ImpetusBlockAccess blockAccess) {
            return chunkBuffer.getContext().getBlockRenderer().renderModel(model, state, pos, blockAccess, chunkBuffer.getLayer());
        }
        return original.call(renderer, world, model, state, pos, buffer, checkSides);
    }
}
