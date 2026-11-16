package com.bdmajora.extras.client.itemfaces;

import com.bdmajora.extras.Extras;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Caches the resolved quads of item models.
public final class ItemFaceCache {
    // Name prefix of the vanilla model wrapper.
    private static final String VANILLA_WRAPPER = "net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$";
    // Whether each model class may be resolved.
    private static final ClassValue<Boolean> FIXED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type == SimpleBakedModel.class
                    || PerspectiveMapWrapper.class.isAssignableFrom(type) && type.getName().startsWith(VANILLA_WRAPPER);
        }
    };
    // Marker for a model that needs no change.
    private static final List<List<BakedQuad>> UNCHANGED = List.of();
    // Resolved quads of each model.
    private static final Map<IBakedModel, List<List<BakedQuad>>> RESOLVED = new WeakHashMap<>();
    // Model of the last lookup.
    private static IBakedModel lastModel;
    // Resolved quads of the last model.
    private static List<List<BakedQuad>> lastLists = UNCHANGED;

    // Prevents instantiation
    private ItemFaceCache() {
    }

    // Returns the resolved quads of the model or its own quads.
    public static List<BakedQuad> quads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        if (state == null && rand == 0L && Extras.options().render.itemModelFlickerFix && FIXED.get(model.getClass())) {
            List<List<BakedQuad>> lists = resolved(model);
            if (lists != UNCHANGED) {
                return lists.get(side == null ? EnumFacing.VALUES.length : side.getIndex());
            }
        }
        return model.getQuads(state, side, rand);
    }

    // Returns the resolved quads of the model.
    private static List<List<BakedQuad>> resolved(IBakedModel model) {
        if (model != lastModel) {
            List<List<BakedQuad>> lists = RESOLVED.get(model);
            if (lists == null) {
                List<List<BakedQuad>> source = new ArrayList<>(CoplanarFaces.LISTS);
                for (EnumFacing side : EnumFacing.VALUES) {
                    source.add(model.getQuads(null, side, 0L));
                }
                source.add(model.getQuads(null, null, 0L));
                lists = CoplanarFaces.resolve(source);
                if (lists == null) {
                    lists = UNCHANGED;
                }
                RESOLVED.put(model, lists);
            }
            lastModel = model;
            lastLists = lists;
        }
        return lastLists;
    }

    // Forgets every resolved model.
    static void clear() {
        RESOLVED.clear();
        lastModel = null;
        lastLists = UNCHANGED;
    }
}
