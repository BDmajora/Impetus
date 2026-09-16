package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.DefinitionLoader;
import com.bdmajora.coarctatio.client.model.dynamic.ModelLocations;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.VariantList;
import net.minecraft.client.renderer.block.model.multipart.Multipart;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

// Forge's variant loader looks multipart definitions up in the loader's maps, which loadBlock filled; here the definition itself says whether the variant is one of the block's, and the model classes are reached by handle since both are private. remap = false, Forge class
@Mixin(targets = "net/minecraftforge/client/model/ModelLoader$VariantLoader", remap = false)
public abstract class VariantLoaderDynamicMixin {
    @Shadow
    private ModelLoader loader;

    private static final MethodHandle WEIGHTED_MODEL = constructor("net.minecraftforge.client.model.ModelLoader$WeightedRandomModel", ResourceLocation.class, VariantList.class);
    private static final MethodHandle MULTIPART_MODEL = constructor("net.minecraftforge.client.model.ModelLoader$MultipartModel", ResourceLocation.class, Multipart.class);

    /**
     * @author embeddedt, Runemoro, bdmajora
     * @reason Resolve variants against the dynamic definition cache
     */
    @Overwrite
    public IModel loadModel(ResourceLocation modelLocation) throws Exception {
        ModelResourceLocation variant = (ModelResourceLocation) modelLocation;
        ModelBlockDefinition definition = ((DefinitionLoader) (Object) this.loader).coarctatio$definition(variant);
        VariantList variants = coarctatio$variant(definition, variant.getVariant());
        if (variants != null) {
            return coarctatio$construct(WEIGHTED_MODEL, variant, variants);
        }
        if (!definition.hasMultipartData()) {
            throw definition.new MissingVariantException();
        }
        ResourceLocation blockstate = new ResourceLocation(variant.getNamespace(), variant.getPath());
        if (!ModelLocations.isKnownVariant(blockstate, variant)) {
            throw new Exception("Not a valid multipart variant for " + definition.getMultipartData().getStateContainer() + ": " + variant);
        }
        return coarctatio$construct(MULTIPART_MODEL, blockstate, definition.getMultipartData());
    }

    // Forge's blockstate format writes the default variant as "" where vanilla's uses "normal"
    private static VariantList coarctatio$variant(ModelBlockDefinition definition, String name) {
        if (definition.hasVariant(name)) {
            return definition.getVariant(name);
        }
        if (name.equals("normal") && definition.hasVariant("")) {
            return definition.getVariant("");
        }
        return null;
    }

    private static IModel coarctatio$construct(MethodHandle constructor, Object first, Object second) throws Exception {
        try {
            return (IModel) constructor.invoke(first, second);
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle constructor(String className, Class<?>... parameters) {
        try {
            return MethodHandles.lookup().unreflectConstructor(Class.forName(className).getDeclaredConstructor(parameters));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
