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

// Replaces RenderGlobal's HashSet<BlockPos> light-update set (MC-80966: vanilla only drains it from updateClouds when the chunk builder is idle, which under load never happens) with a DeduplicatedLongQueue drained unconditionally from the client tick; see MinecraftMixin for ordering
@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin implements LightUpdateProcessor {
    // One tick's worth of changed positions, always deduplicating (unlike the engine's queues) since one light change routinely notifies the same position from several sides
    @Unique
    private final DeduplicatedLongQueue fulgor$lightUpdates =
            new DeduplicatedLongQueue(new DeduplicatedLongQueue.Pool(), 1024, true);

    @Shadow
    protected abstract void markBlocksForUpdate(int minX, int minY, int minZ,
                                                int maxX, int maxY, int maxZ, boolean updateImmediately);

    // Overwrites vanilla's notifyLightSet to collect into a long queue instead of a set of boxed positions
    @Overwrite
    public void notifyLightSet(BlockPos pos) {
        this.fulgor$lightUpdates.enqueue(pos.toLong());
    }

    // Disables vanilla's updateClouds drain by claiming its light-update set is always empty (ordinal 0; the second isEmpty() is the pending-rebuild check and is left alone)
    @Redirect(method = "updateClouds",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 0, remap = false))
    private boolean fulgor$disableVanillaDrain(Set<?> lightUpdates) {
        return true;
    }

    // Drains the queue into chunk rebuilds; dedup reset happens after the drain (not before, as in the engine) since nothing is enqueued mid-drain and resetting early would drop a position that changed on two consecutive ticks
    @Override
    public void fulgor$processLightUpdates() {
        while (!this.fulgor$lightUpdates.isEmpty()) {
            BlockPos pos = BlockPos.fromLong(this.fulgor$lightUpdates.dequeue());

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            // A one-block margin matching vanilla: a light change at a section edge alters the neighbouring section's face AO too
            this.markBlocksForUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
        }

        this.fulgor$lightUpdates.resetDeduplication();
    }
}
