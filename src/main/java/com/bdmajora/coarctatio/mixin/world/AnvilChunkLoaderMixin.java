package com.bdmajora.coarctatio.mixin.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes AnvilChunkLoader.
@Mixin(AnvilChunkLoader.class)
public abstract class AnvilChunkLoaderMixin {
    // Field coarctatio retained tags added to the target.
    @Unique
    private static final String[] COARCTATIO_RETAINED_TAGS = {"Entities", "TileEntities", "TileTicks"};

    // Runs before every return of checkedReadChunkFromNBT__Async.
    @Inject(method = "checkedReadChunkFromNBT__Async", at = @At("RETURN"), remap = false)
    private void coarctatio$stripPendingChunkNbt(World world, int x, int z, NBTTagCompound compound,
                                                CallbackInfoReturnable<Object[]> cir) {
        Object[] result = cir.getReturnValue();

        if (result == null || result.length < 2 || !(result[1] instanceof NBTTagCompound)) {
            return;
        }

        NBTTagCompound original = (NBTTagCompound) result[1];

        if (!original.hasKey("Level", 10)) {
            return;
        }

        NBTTagCompound level = original.getCompoundTag("Level");
        NBTTagCompound strippedLevel = new NBTTagCompound();

        for (String tag : COARCTATIO_RETAINED_TAGS) {
            if (level.hasKey(tag, 9)) {
                strippedLevel.setTag(tag, level.getTag(tag));
            }
        }

        NBTTagCompound stripped = new NBTTagCompound();
        stripped.setTag("Level", strippedLevel);

        result[1] = stripped;
    }
}
