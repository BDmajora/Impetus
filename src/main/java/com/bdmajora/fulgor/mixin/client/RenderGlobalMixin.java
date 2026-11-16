package com.bdmajora.fulgor.mixin.client;

import com.bdmajora.fulgor.api.LightUpdateProcessor;
import com.bdmajora.fulgor.collections.DeduplicatedLongQueue;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

// Mixin that changes RenderGlobal.
@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin implements LightUpdateProcessor {
    // Field light updates added to the target.
    @Unique
    private final DeduplicatedLongQueue fulgor$lightUpdates =
            new DeduplicatedLongQueue(new DeduplicatedLongQueue.Pool(), 1024, true);

    // Reference to the method mark blocks for update of the target.
    @Shadow
    protected abstract void markBlocksForUpdate(int minX, int minY, int minZ,
                                                // Highest corner of the box and the immediate flag.
                                                int maxX, int maxY, int maxZ, boolean updateImmediately);

    // Replaces the whole method notifyLightSet.
    @Overwrite
    public void notifyLightSet(BlockPos pos) {
        this.fulgor$lightUpdates.enqueue(pos.toLong());
    }

    // Replaces the call of Set.isEmpty in updateClouds.
    @Redirect(method = "updateClouds",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 0, remap = false))
    private boolean fulgor$disableVanillaDrain(Set<?> lightUpdates) {
        return true;
    }

    // Handles process light updates.
    @Override
    public void fulgor$processLightUpdates() {
        while (!this.fulgor$lightUpdates.isEmpty()) {
            BlockPos pos = BlockPos.fromLong(this.fulgor$lightUpdates.dequeue());

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            this.markBlocksForUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
        }

        this.fulgor$lightUpdates.resetDeduplication();
    }
}
