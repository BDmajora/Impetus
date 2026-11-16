package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.ModelLookupCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.registries.IRegistryDelegate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

// Mixin that changes ItemModelMesherForge.
@Mixin(ItemModelMesherForge.class)
public abstract class ItemModelMesherForgeDynamicMixin extends ItemModelMesher {
    @Shadow(remap = false)
    @Final
    Map<IRegistryDelegate<Item>, Int2ObjectMap<ModelResourceLocation>> locations;

    // Item models baked on demand.
    private final ModelLookupCache<ModelResourceLocation> coarctatio$models = new ModelLookupCache<>(location -> this.getModelManager().getModel(location), true);

    // Creates an item model mesher forge dynamic mixin from its model manager.
    protected ItemModelMesherForgeDynamicMixin(ModelManager modelManager) {
        super(modelManager);
    }

    // Replaces the whole method getItemModel.
    @Overwrite
    @Override
    protected IBakedModel getItemModel(Item item, int meta) {
        Int2ObjectMap<ModelResourceLocation> byMeta = this.locations.get(item.delegate);
        if (byMeta == null) {
            return null;
        }
        ModelResourceLocation location = byMeta.get(meta);
        return location != null ? this.coarctatio$models.get(location) : null;
    }

    // Replaces the whole method register.
    @Overwrite
    @Override
    public void register(Item item, int meta, ModelResourceLocation location) {
        IRegistryDelegate<Item> key = item.delegate;
        Int2ObjectMap<ModelResourceLocation> byMeta = this.locations.get(key);
        if (byMeta == null) {
            byMeta = new Int2ObjectOpenHashMap<>();
            this.locations.put(key, byMeta);
        }
        byMeta.put(meta, location);
    }

    // Replaces the whole method rebuildCache.
    @Overwrite
    @Override
    public void rebuildCache() {
        this.coarctatio$models.clear();
    }
}
