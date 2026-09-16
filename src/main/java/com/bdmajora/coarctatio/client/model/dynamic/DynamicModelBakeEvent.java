package com.bdmajora.coarctatio.client.model.dynamic;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.fml.common.eventhandler.Event;

// Posted on MinecraftForge.EVENT_BUS each time a model is baked on demand, the dynamic-loading stand-in for walking ModelBakeEvent's registry; a listener may replace bakedModel, and a null unbakedModel means the load failed and bakedModel is the missing model
public class DynamicModelBakeEvent extends Event {
    public final ResourceLocation location;
    public final IModel unbakedModel;
    public IBakedModel bakedModel;

    // FML's bus instantiates every event type once through its no-arg constructor when a listener registers
    public DynamicModelBakeEvent() {
        this(null, null, null);
    }

    public DynamicModelBakeEvent(ResourceLocation location, IModel unbakedModel, IBakedModel bakedModel) {
        this.location = location;
        this.unbakedModel = unbakedModel;
        this.bakedModel = bakedModel;
    }
}
