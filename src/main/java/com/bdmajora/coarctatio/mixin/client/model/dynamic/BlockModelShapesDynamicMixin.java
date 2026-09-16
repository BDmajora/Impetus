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

// Vanilla fills a state-to-model table with every model at reload; here a state remembers its model once first drawn, and the table field becomes a view over the registry for mods that reach into it
@Mixin(BlockModelShapes.class)
public abstract class BlockModelShapesDynamicMixin implements LocationAwareBlockModelShapes {
    @Shadow
    @Final
    private ModelManager modelManager;

    @Shadow
    @Final
    private BlockStateMapper blockStateMapper;

    @Shadow
    @Final
    @Mutable
    private Map<IBlockState, IBakedModel> bakedModelStore;

    private StateModelLocations coarctatio$locations;

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Clear the per-state caches instead of baking every state's model
     */
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
        // A mod calling this before any dynamic reload has nothing to view yet
        if (DynamicModels.baked() != null) {
            this.bakedModelStore = DynamicModels.baked().stateStore();
        }
    }

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Answer from the state's own cache, baking through the model manager on a miss
     */
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

    // Mods pass null states here; vanilla's table lookup answered the missing model for them
    private IBakedModel coarctatio$lookup(IBlockState state) {
        if (state == null) {
            return this.modelManager.getMissingModel();
        }
        IBakedModel model = this.modelManager.getModel(this.coarctatio$locationForState(state));
        return model != null ? model : this.modelManager.getMissingModel();
    }

    @Override
    public ModelResourceLocation coarctatio$locationForState(IBlockState state) {
        if (this.coarctatio$locations == null) {
            this.coarctatio$locations = new StateModelLocations(this.blockStateMapper);
        }
        return this.coarctatio$locations.locationFor(state);
    }
}
