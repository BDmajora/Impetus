package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.impl.extensions.VertexFormatExtension;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.util.QuadUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.bdmajora.impetus.impl.render.terrain.compile.light.VintageDiffuseProvider;

// Mixin that changes BakedQuad.
@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin implements BakedQuadView {
    // Reference to the field face of the target.
    @Shadow
    @Final
    protected EnumFacing face;

    // Reference to the field apply diffuse lighting of the target.
    @Shadow
    @Final
    protected boolean applyDiffuseLighting;

    // Reference to the method get vertex data of the target.
    @Shadow
    public abstract int[] getVertexData();

    // Reference to the method get format of the target.
    @Shadow
    public abstract VertexFormat getFormat();

    // Reference to the field sprite of the target.
    @Shadow
    @Final
    protected TextureAtlasSprite sprite;
    // Reference to the field tint index of the target.
    @Shadow
    @Final
    protected int tintIndex;
    // Field flags added to the target.
    @Unique
    private int flags;

    // Field normal added to the target.
    @Unique
    private int normal;

    // Field normal face added to the target.
    @Unique
    private ModelQuadFacing normalFace;

    // Returns the normal face.
    @Override
    public ModelQuadFacing getNormalFace() {
        var face = this.normalFace;
        if (face == null) {
            this.normalFace = face = QuadUtil.findNormalFace(getComputedFaceNormal());
        }
        return face;
    }

    // Returns the flags.
    @Override
    public int getFlags() {
        int f = this.flags;
        if ((f & ModelQuadFlags.IS_POPULATED) == 0) {
            this.flags = f = ModelQuadFlags.getQuadFlags(this, getLightFace(), f);
        }
        return f;
    }

    // Handles add flags.
    @Override
    public void addFlags(int flags) {
        this.flags |= flags;
    }

    // Returns whether shade.
    @Override
    public boolean hasShade() {
        return this.applyDiffuseLighting;
    }

    // Returns the vertices count.
    @Override
    public int getVerticesCount() {
        return this.getVertexData().length / impetus$layout().impetus$intStride();
    }

    // Returns the transparency level.
    @Override
    public @Nullable SpriteTransparencyLevel getTransparencyLevel() {
        return null;
    }

    // Helper method layout added to the target.
    @Unique
    private VertexFormatExtension impetus$layout() {
        return (VertexFormatExtension) this.getFormat();
    }

    // Returns the x of a vertex.
    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride()]);
    }

    // Returns the y of a vertex.
    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride() + 1]);
    }

    // Returns the z of a vertex.
    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride() + 2]);
    }

    // Returns the color of a vertex.
    @Override
    public int getColor(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$colorIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    // Returns the sprite.
    @Override
    public Object impetus$getSprite() {
        return this.sprite;
    }

    // Returns the u of a vertex.
    @Override
    public float getTexU(int idx) {
        var layout = impetus$layout();
        return Float.intBitsToFloat(this.getVertexData()[idx * layout.impetus$intStride() + layout.impetus$uvIndex()]);
    }

    // Returns the v of a vertex.
    @Override
    public float getTexV(int idx) {
        var layout = impetus$layout();
        return Float.intBitsToFloat(this.getVertexData()[idx * layout.impetus$intStride() + layout.impetus$uvIndex() + 1]);
    }

    // Returns the packed light of a vertex.
    @Override
    public int getLight(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$lightIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    // Returns the packed normal of a vertex.
    @Override
    public int getForgeNormal(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$normalIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    // Returns the light face.
    @Override
    public ModelQuadFacing getLightFace() {
        var face = this.face;
        return face == null ? ModelQuadFacing.POS_Y : VintageDiffuseProvider.fromEnumFacing(face);
    }

    // Returns the computed face normal.
    @Override
    public int getComputedFaceNormal() {
        int n = this.normal;
        if (n == 0) {
            this.normal = n = QuadUtil.calculateNormal(this);
        }
        return n;
    }

    // Returns the color index.
    @Override
    public int getColorIndex() {
        return this.tintIndex;
    }
}
