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

// Item models with their coplanar faces resolved, cached per model on the render thread; only models whose quads are fixed for a null state are touched, and weak keys let a resource reload's old models go
public final class ItemFaceCache {
    // Forge's wrapper around a baked JSON model (every resource pack item model); for a null state it answers its SimpleBakedModel's quads
    private static final String VANILLA_WRAPPER = "net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$";
    // Whether a model class only ever answers the same quads for a null state; anything else (dynamic mod models, Forge's generated sprites) is drawn as it comes
    private static final ClassValue<Boolean> FIXED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type == SimpleBakedModel.class
                    || PerspectiveMapWrapper.class.isAssignableFrom(type) && type.getName().startsWith(VANILLA_WRAPPER);
        }
    };
    // Stands for a model with nothing to resolve
    private static final List<List<BakedQuad>> UNCHANGED = List.of();
    private static final Map<IBakedModel, List<List<BakedQuad>>> RESOLVED = new WeakHashMap<>();
    // renderModel asks one model for all seven lists in a row, so only the first ask looks it up
    private static IBakedModel lastModel;
    private static List<List<BakedQuad>> lastLists = UNCHANGED;

    private ItemFaceCache() {
    }

    // The quads renderModel draws for one side: the resolved list while the fix is on and the model is fixed, else the model's own
    public static List<BakedQuad> quads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        if (state == null && rand == 0L && Extras.options().render.itemModelFlickerFix && FIXED.get(model.getClass())) {
            List<List<BakedQuad>> lists = resolved(model);
            if (lists != UNCHANGED) {
                return lists.get(side == null ? EnumFacing.VALUES.length : side.getIndex());
            }
        }
        return model.getQuads(state, side, rand);
    }

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

    // Forgets every resolved model
    static void clear() {
        RESOLVED.clear();
        lastModel = null;
        lastLists = UNCHANGED;
    }
}
