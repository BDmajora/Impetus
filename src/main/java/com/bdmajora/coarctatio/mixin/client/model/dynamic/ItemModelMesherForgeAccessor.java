package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.registries.IRegistryDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// Every registered item model location, the prebake's work list. remap = false, Forge class
@Mixin(value = ItemModelMesherForge.class, remap = false)
public interface ItemModelMesherForgeAccessor {
    @Accessor("locations")
    Map<IRegistryDelegate<Item>, Int2ObjectMap<ModelResourceLocation>> coarctatio$locations();
}
