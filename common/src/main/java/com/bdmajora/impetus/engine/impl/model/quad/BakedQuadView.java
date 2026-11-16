package com.bdmajora.impetus.engine.impl.model.quad;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Quad view with extra data of a baked quad.
public interface BakedQuadView extends ModelQuadView {
    // Returns the face of the normal.
    ModelQuadFacing getNormalFace();

    // Returns whether the quad is shaded.
    boolean hasShade();

    // Adds flags to the quad.
    void addFlags(int flags);

    // Returns the number of vertices.
    int getVerticesCount();

    // Returns the transparency of the sprite or null.
    @Nullable SpriteTransparencyLevel getTransparencyLevel();

    // Casts an object to a baked quad view.
    static BakedQuadView of(Object o) {
        return (BakedQuadView)o;
    }

    // Casts a list to a list of baked quad views.
    @SuppressWarnings("unchecked")
    static <T> List<? extends BakedQuadView> ofList(List<T> quads) {
        return (List<? extends BakedQuadView>)(List<?>)quads;
    }
}
