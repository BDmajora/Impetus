package team.chisel.ctm.client.util;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;

// Test stand-in for CTM's metadata handler.
public class TextureMetadataHandler {
    public static final TextureMetadataHandler INSTANCE = new TextureMetadataHandler();

    // Model wrap returns or null to fail.
    public static IBakedModel wrapped;

    private final Object2BooleanMap<ResourceLocation> wrappedModels = new Object2BooleanOpenHashMap<>();

    private IBakedModel wrap(IModel model, IBakedModel object) {
        if (wrapped == null) {
            throw new IllegalStateException("nothing to wrap " + object + " with");
        }
        return wrapped;
    }
}
