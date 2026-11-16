package com.bdmajora.fulgor.mixin.world;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.api.LightingEngineProvider;
import com.bdmajora.fulgor.lighting.LightingEngine;
import com.bdmajora.fulgor.lighting.LightingHooks;
import com.bdmajora.fulgor.lighting.WorldChunkSlice;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Chunk.
@Mixin(Chunk.class)
public abstract class ChunkMixin implements ChunkLightingData, LightingEngineProvider {
    // Field fulgor horizontals added to the target.
    @Unique
    private static final EnumFacing[] FULGOR_HORIZONTALS = EnumFacing.Plane.HORIZONTAL.facings();

    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Reference to the field storage arrays of the target.
    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    // Reference to the field height map of the target.
    @Shadow
    @Final
    private int[] heightMap;

    // Reference to the field update skylight columns of the target.
    @Shadow
    @Final
    private boolean[] updateSkylightColumns;

    // Reference to the field x of the target.
    @Shadow
    @Final
    public int x;

    // Reference to the field z of the target.
    @Shadow
    @Final
    public int z;

    // Reference to the field height map minimum of the target.
    @Shadow
    private int heightMapMinimum;

    // Reference to the field dirty of the target.
    @Shadow
    private boolean dirty;

    // Reference to the field is terrain populated of the target.
    @Shadow
    private boolean isTerrainPopulated;

    // Reference to the field is gap lighting updated of the target.
    @Shadow
    private boolean isGapLightingUpdated;

    // Reference to the method can see sky of the target.
    @Shadow
    public abstract boolean canSeeSky(BlockPos pos);

    // Reference to the method get height value of the target.
    @Shadow
    public abstract int getHeightValue(int x, int z);

    // Reference to the method get block light opacity of the target.
    @Shadow
    protected abstract int getBlockLightOpacity(int x, int y, int z);

    // Reference to the method set skylight updated of the target.
    @Shadow
    protected abstract void setSkylightUpdated();

    // Field lighting engine added to the target.
    @Unique
    private LightingEngine fulgor$lightingEngine;

    // Field neighbor light checks added to the target.
    @Unique
    private short[] fulgor$neighborLightChecks;

    // Field light initialized added to the target.
    @Unique
    private boolean fulgor$lightInitialized;

    // Runs before every return of constructor.
    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void fulgor$captureLightingEngine(World world, int x, int z, CallbackInfo ci) {
        this.fulgor$lightingEngine = ((LightingEngineProvider) world).fulgor$getLightingEngine();
    }

    // Runs at the start of getLightSubtracted.
    @Inject(method = "getLightSubtracted", at = @At("HEAD"))
    private void fulgor$flushBeforeLightSubtracted(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        this.fulgor$lightingEngine.processLightUpdates();
    }

    // Runs before every return of onLoad.
    @Inject(method = "onLoad", at = @At("RETURN"))
    private void fulgor$replayBoundaryChecks(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.world, (Chunk) (Object) this);
    }

    // Replaces the call of Chunk.generateSkylightMap in setLightFor.
    @Redirect(
            method = "setLightFor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;generateSkylightMap()V"),
            expect = 0)
    private void fulgor$seedNewSectionOnly(Chunk chunk, EnumSkyBlock lightType, BlockPos pos, int value) {
        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, this.storageArrays[pos.getY() >> 4]);
    }

    // Replaces the whole method relightBlock.
    @Overwrite
    private void relightBlock(int x, int y, int z) {
        int oldHeight = this.heightMap[z << 4 | x] & 255;
        int newHeight = Math.max(y, oldHeight);

        while (newHeight > 0 && this.getBlockLightOpacity(x, newHeight - 1, z) == 0) {
            newHeight--;
        }

        if (newHeight == oldHeight) {
            return;
        }

        this.heightMap[z << 4 | x] = newHeight;

        if (this.world.provider.hasSkyLight()) {
            LightingHooks.relightSkylightColumn(this.world, (Chunk) (Object) this, x, z, oldHeight, newHeight);
        }

        if (newHeight < this.heightMapMinimum) {
            this.heightMapMinimum = newHeight;
        }
    }

    // Replaces the whole method getLightFor.
    @Overwrite
    public int getLightFor(EnumSkyBlock lightType, BlockPos pos) {
        this.fulgor$lightingEngine.processLightUpdatesForType(lightType);

        return this.fulgor$getCachedLightFor(lightType, pos);
    }

    // Replaces the whole method checkLight.
    @Overwrite
    public void checkLight() {
        this.isTerrainPopulated = true;

        LightingHooks.checkChunkLighting(this.world, (Chunk) (Object) this);
    }

    // Replaces the whole method recheckGaps.
    @Overwrite
    private void recheckGaps(boolean onlyOne) {
        this.world.profiler.startSection("recheckGaps");

        try {
            if (!this.world.isAreaLoaded(new BlockPos((this.x << 4) + 8, 0, (this.z << 4) + 8), 16)) {
                return;
            }

            WorldChunkSlice slice = new WorldChunkSlice(this.world.getChunkProvider(), this.x, this.z);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (this.fulgor$recheckGapsForColumn(slice, x, z) && onlyOne) {
                        return;
                    }
                }
            }

            this.isGapLightingUpdated = false;
        } finally {
            this.world.profiler.endSection();
        }
    }

    // Helper method recheck gaps for column added to the target.
    @Unique
    private boolean fulgor$recheckGapsForColumn(WorldChunkSlice slice, int x, int z) {
        int index = x + z * 16;

        if (!this.updateSkylightColumns[index]) {
            return false;
        }

        this.updateSkylightColumns[index] = false;

        int worldX = (this.x << 4) + x;
        int worldZ = (this.z << 4) + z;

        int height = this.getHeightValue(x, z);
        int lowestNeighborHeight = this.fulgor$lowestNeighborHeight(slice, worldX, worldZ);

        this.fulgor$checkSkylightNeighborHeight(slice, worldX, worldZ, lowestNeighborHeight);

        for (EnumFacing facing : FULGOR_HORIZONTALS) {
            this.fulgor$checkSkylightNeighborHeight(slice,
                    worldX + facing.getXOffset(), worldZ + facing.getZOffset(), height);
        }

        return true;
    }

    // Helper method lowest neighbor height added to the target.
    @Unique
    private int fulgor$lowestNeighborHeight(WorldChunkSlice slice, int x, int z) {
        int lowest = Integer.MAX_VALUE;

        for (EnumFacing facing : FULGOR_HORIZONTALS) {
            Chunk chunk = slice.getChunkFromWorldCoords(x + facing.getXOffset(), z + facing.getZOffset());

            if (chunk != null) {
                lowest = Math.min(lowest, chunk.getLowestHeight());
            }
        }

        return lowest;
    }

    // Helper method check skylight neighbor height added to the target.
    @Unique
    private void fulgor$checkSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int maxValue) {
        Chunk chunk = slice.getChunkFromWorldCoords(x, z);

        if (chunk == null) {
            return;
        }

        int height = chunk.getHeightValue(x & 15, z & 15);

        if (height > maxValue) {
            this.fulgor$updateSkylightNeighborHeight(slice, x, z, maxValue, height + 1);
        } else if (height < maxValue) {
            this.fulgor$updateSkylightNeighborHeight(slice, x, z, height, maxValue + 1);
        }
    }

    // Helper method update skylight neighbor height added to the target.
    @Unique
    private void fulgor$updateSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int startY, int endY) {
        if (endY <= startY || !slice.isLoaded(x, z, 16)) {
            return;
        }

        for (int y = startY; y < endY; y++) {
            this.world.checkLightFor(EnumSkyBlock.SKY, new BlockPos(x, y, z));
        }

        this.dirty = true;
    }

    // Returns the neighbor light checks.
    @Override
    public short[] fulgor$getNeighborLightChecks() {
        return this.fulgor$neighborLightChecks;
    }

    // Stores the neighbor light checks.
    @Override
    public void fulgor$setNeighborLightChecks(short[] data) {
        this.fulgor$neighborLightChecks = data;
    }

    // Initializes the neighbor light checks.
    @Override
    public void fulgor$initNeighborLightChecks() {
        if (this.fulgor$neighborLightChecks == null) {
            this.fulgor$neighborLightChecks = new short[Fulgor.BOUNDARY_FLAG_COUNT];
        }
    }

    // Returns whether light initialized.
    @Override
    public boolean fulgor$isLightInitialized() {
        return this.fulgor$lightInitialized;
    }

    // Stores the light initialized.
    @Override
    public void fulgor$setLightInitialized(boolean lightInitialized) {
        this.fulgor$lightInitialized = lightInitialized;
    }

    // Stores the skylight updated.
    @Override
    public void fulgor$setSkylightUpdated() {
        this.setSkylightUpdated();
    }

    // Returns the cached light for.
    @Override
    public int fulgor$getCachedLightFor(EnumSkyBlock lightType, BlockPos pos) {
        int x = pos.getX() & 15;
        int y = pos.getY();
        int z = pos.getZ() & 15;

        if (y < 0 || y > 255) {
            return y > 255 ? lightType.defaultLightValue : 0;
        }

        ExtendedBlockStorage section = this.storageArrays[y >> 4];

        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return this.canSeeSky(pos) ? lightType.defaultLightValue : 0;
        }

        if (lightType == EnumSkyBlock.SKY) {
            return this.world.provider.hasSkyLight() ? section.getSkyLight(x, y & 15, z) : 0;
        }

        return lightType == EnumSkyBlock.BLOCK ? section.getBlockLight(x, y & 15, z) : lightType.defaultLightValue;
    }

    // Returns the lighting engine.
    @Override
    public LightingEngine fulgor$getLightingEngine() {
        return this.fulgor$lightingEngine;
    }
}
