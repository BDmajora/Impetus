package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.lighting.LightingHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkSkylightMixin {
    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Changes a local variable in setBlockState.
    @ModifyVariable(method = "setBlockState", at = @At(value = "STORE", ordinal = 1), index = 12)
    private ExtendedBlockStorage fulgor$seedNewSection(ExtendedBlockStorage section) {
        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, section);

        return section;
    }

    // Changes a local variable in setBlockState.
    @ModifyVariable(method = "setBlockState", at = @At(value = "STORE", ordinal = 1), index = 13)
    private boolean fulgor$suppressSkylightMapRegeneration(boolean generateSkylightMap) {
        return false;
    }

    // Replaces the call of Chunk.propagateSkylightOcclusion in setBlockState.
    @Redirect(
            method = "setBlockState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;propagateSkylightOcclusion(II)V"))
    private void fulgor$skipSkylightOcclusion(Chunk chunk, int x, int z) {
    }

    // Replaces the call of Chunk.getLightFor in setBlockState.
    @Redirect(
            method = "setBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;getLightFor(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)I"))
    private int fulgor$skipLightQuery(Chunk chunk, EnumSkyBlock lightType, BlockPos pos) {
        return 0;
    }
}
