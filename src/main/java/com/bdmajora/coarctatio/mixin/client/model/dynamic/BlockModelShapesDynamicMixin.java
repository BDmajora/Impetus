package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DynamicModels;
import com.bdmajora.coarctatio.client.model.dynamic.LocationAwareBlockModelShapes;
import com.bdmajora.coarctatio.client.model.dynamic.ModelHoldingState;
import com.bdmajora.coarctatio.client.model.dynamic.StateModelLocations;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.BlockStateMapper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

// Mixin that changes BlockModelShapes.
@Mixin(BlockModelShapes.class)
public abstract class BlockModelShapesDynamicMixin implements LocationAwareBlockModelShapes {
    // Reference to the field model manager of the target.
    @Shadow
    @Final
    private ModelManager modelManager;

    // Reference to the field block state mapper of the target.
    @Shadow
    @Final
    private BlockStateMapper blockStateMapper;

    @Shadow
    @Final
    @Mutable
    private Map<IBlockState, IBakedModel> bakedModelStore;

    // Model locations of every state.
    private StateModelLocations coarctatio$locations;

    // Replaces the whole method reloadModels.
    @Overwrite
    public void reloadModels() {
        if (this.coarctatio$locations == null) {
            this.coarctatio$locations = new StateModelLocations(this.blockStateMapper);
        }
        for (Block block : ForgeRegistries.BLOCKS) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                if (state instanceof ModelHoldingState) {
                    ((ModelHoldingState) state).coarctatio$cacheModel(null);
                }
            }
        }
        if (DynamicModels.baked() != null) {
            this.bakedModelStore = DynamicModels.baked().stateStore();
        }
    }

    // Replaces the whole method getModelForState.
    @Overwrite
    public IBakedModel getModelForState(IBlockState state) {
        if (!(state instanceof ModelHoldingState)) {
            return this.coarctatio$lookup(state);
        }
        ModelHoldingState holder = (ModelHoldingState) state;
        IBakedModel model = holder.coarctatio$cachedModel();
        if (model == null) {
            model = this.coarctatio$lookup(state);
            holder.coarctatio$cacheModel(model);
        }
        return model;
    }

    // Returns the baked model of a state and bakes it on demand.
    private IBakedModel coarctatio$lookup(IBlockState state) {
        if (state == null) {
            return this.modelManager.getMissingModel();
        }
        IBakedModel model = this.modelManager.getModel(this.coarctatio$locationForState(state));
        return model != null ? model : this.modelManager.getMissingModel();
    }

    // Returns the model location of a state through the block's state mapper.
    @Override
    public ModelResourceLocation coarctatio$locationForState(IBlockState state) {
        if (this.coarctatio$locations == null) {
            this.coarctatio$locations = new StateModelLocations(this.blockStateMapper);
        }
        return this.coarctatio$locations.locationFor(state);
    }
}
