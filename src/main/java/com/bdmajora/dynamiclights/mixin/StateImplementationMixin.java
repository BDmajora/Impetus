package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import com.bdmajora.impetus.impl.util.EmptyBlockAccess;
import com.bdmajora.impetus.impl.world.WorldSlice;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes StateImplementation.
@SideOnly(Side.CLIENT)
@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class StateImplementationMixin {
    // Runs before every return of getPackedLightmapCoords and can cancel it.
    @Inject(method = "getPackedLightmapCoords", at = @At("RETURN"), cancellable = true)
    private void impetus$addDynamicLight(IBlockAccess source, BlockPos pos,
                                         CallbackInfoReturnable<Integer> cir) {
        if (!DynamicLights.options().mode.isEnabled()) {
            return;
        }

        // Nothing lit skips the state queries below, which run for every block a mesh reads
        if (source == EmptyBlockAccess.INSTANCE || DynamicLights.engine().getLightSourcesCount() == 0) {
            return;
        }
        // Meshes leave moving lights to the terrain shaders: a chunk slice, or any thread but the client's, such as LittleTiles' render thread baking against the live world
        if (TerrainDynamicLights.isShaderApplied()
                && (source instanceof WorldSlice || !Minecraft.getMinecraft().isCallingFromMinecraftThread())) {
            return;
        }

        IBlockState state = (IBlockState) (Object) this;
        if (state.isOpaqueCube() && state.getLightValue(source, pos) == 0) {
            return;
        }

        cir.setReturnValue(DynamicLights.engine().getLightmapWithDynamicLight(pos, cir.getReturnValueI()));
    }
}
