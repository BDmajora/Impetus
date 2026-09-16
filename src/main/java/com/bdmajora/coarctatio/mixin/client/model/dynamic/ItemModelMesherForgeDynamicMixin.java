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

// The mesher keeps locations only; models come from the registry through an identity-keyed cache, since each item's location object is unique; the overwritten methods are vanilla overrides and remap, the field is Forge's and does not
@Mixin(ItemModelMesherForge.class)
public abstract class ItemModelMesherForgeDynamicMixin extends ItemModelMesher {
    @Shadow(remap = false)
    @Final
    Map<IRegistryDelegate<Item>, Int2ObjectMap<ModelResourceLocation>> locations;

    private final ModelLookupCache<ModelResourceLocation> coarctatio$models = new ModelLookupCache<>(location -> this.getModelManager().getModel(location), true);

    protected ItemModelMesherForgeDynamicMixin(ModelManager modelManager) {
        super(modelManager);
    }

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Resolve the location through the dynamic registry instead of a table filled at registration
     */
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

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Registration must not fetch the model, which would bake it
     */
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

    /**
     * @author embeddedt, bdmajora
     * @reason Rebuilding the table would bake every item model
     */
    @Overwrite
    @Override
    public void rebuildCache() {
        this.coarctatio$models.clear();
    }
}
