package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.fml.common.eventhandler.Event;

// Event posted when a dynamic model finishes baking.
public class DynamicModelBakeEvent extends Event {
    // Location of the model.
    public final ResourceLocation location;
    // Model before baking.
    public final IModel unbakedModel;
    // Baked model that listeners may replace.
    public IBakedModel bakedModel;

    // Creates an empty event.
    public DynamicModelBakeEvent() {
        this(null, null, null);
    }

    // Creates an event for a model.
    public DynamicModelBakeEvent(ResourceLocation location, IModel unbakedModel, IBakedModel bakedModel) {
        this.location = location;
        this.unbakedModel = unbakedModel;
        this.bakedModel = bakedModel;
    }
}
