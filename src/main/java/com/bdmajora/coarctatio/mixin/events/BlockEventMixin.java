package com.bdmajora.coarctatio.mixin.events;

import com.bdmajora.coarctatio.events.RecyclableEvent;
import com.google.common.base.Preconditions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// The block fields live on the base class, so the refresh and the recycled phase do too. remap = false, Forge class
@Mixin(value = BlockEvent.class, remap = false)
public abstract class BlockEventMixin extends Event implements RecyclableEvent {
    @Shadow
    @Final
    @Mutable
    private World world;

    @Shadow
    @Final
    @Mutable
    private BlockPos pos;

    @Shadow
    @Final
    @Mutable
    private IBlockState state;

    @Unique
    private EventPriority coarctatio$phase;

    @Nullable
    @Override
    public EventPriority getPhase() {
        return this.coarctatio$phase;
    }

    @Override
    public void setPhase(@Nonnull EventPriority value) {
        Preconditions.checkNotNull(value, "setPhase argument must not be null");
        int prev = this.coarctatio$phase == null ? -1 : this.coarctatio$phase.ordinal();
        Preconditions.checkArgument(prev < value.ordinal(), "Attempted to set event phase to %s when already %s", value, this.coarctatio$phase);
        this.coarctatio$phase = value;
    }

    @Override
    public void coarctatio$resetEventState() {
        this.coarctatio$phase = null;
        if (isCancelable()) {
            setCanceled(false);
        }
        if (hasResult()) {
            setResult(Result.DEFAULT);
        }
    }

    @Override
    public void coarctatio$refreshBlock(World world, BlockPos pos, IBlockState state) {
        this.world = world;
        this.pos = pos;
        this.state = state;
    }
}
