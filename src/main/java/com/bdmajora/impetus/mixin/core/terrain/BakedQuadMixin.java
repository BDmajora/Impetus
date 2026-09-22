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

// Implements BakedQuadView directly on BakedQuad so the chunk renderer can read vertex data without extra allocations
@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin implements BakedQuadView {
    @Shadow
    @Final
    protected EnumFacing face;

    @Shadow
    @Final
    protected boolean applyDiffuseLighting;

    @Shadow
    public abstract int[] getVertexData();

    @Shadow
    public abstract VertexFormat getFormat();

    @Shadow
    @Final
    protected TextureAtlasSprite sprite;
    @Shadow
    @Final
    protected int tintIndex;
    @Unique
    private int flags;

    @Unique
    private int normal;

    @Unique
    private ModelQuadFacing normalFace;

    // Lazily derived from the computed normal and cached; most quads are only ever asked once
    @Override
    public ModelQuadFacing getNormalFace() {
        var face = this.normalFace;
        if (face == null) {
            this.normalFace = face = QuadUtil.findNormalFace(getComputedFaceNormal());
        }
        return face;
    }

    // Lazily populated on first read, marked by IS_POPULATED so the work is done once
    @Override
    public int getFlags() {
        int f = this.flags;
        if ((f & ModelQuadFlags.IS_POPULATED) == 0) {
            this.flags = f = ModelQuadFlags.getQuadFlags(this, getLightFace(), f);
        }
        return f;
    }

    // ORs in flags set by the model builder, e.g. IS_VANILLA_SHADED
    @Override
    public void addFlags(int flags) {
        this.flags |= flags;
    }

    // Vanilla's diffuse lighting flag, exposed under Sodium's name
    @Override
    public boolean hasShade() {
        return this.applyDiffuseLighting;
    }

    // Derived from the raw array length and the format stride, so it is right for any vertex format
    @Override
    public int getVerticesCount() {
        return this.getVertexData().length / impetus$layout().impetus$intStride();
    }

    @Override
    public @Nullable SpriteTransparencyLevel getTransparencyLevel() {
        return null;
    }

    // The format's cached int layout, through the getter since subclasses may override it; the layout is re-derived if the format itself is mutated
    @Unique
    private VertexFormatExtension impetus$layout() {
        return (VertexFormatExtension) this.getFormat();
    }

    // Position is always the first element of a vertex
    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride()]);
    }

    // Position is always the first element of a vertex
    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride() + 1]);
    }

    // Position is always the first element of a vertex
    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.getVertexData()[idx * impetus$layout().impetus$intStride() + 2]);
    }

    // Reads the colour element if the format has one, else zero
    @Override
    public int getColor(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$colorIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    @Override
    public Object impetus$getSprite() {
        return this.sprite;
    }

    // First UV set; the block atlas coordinates
    @Override
    public float getTexU(int idx) {
        var layout = impetus$layout();
        return Float.intBitsToFloat(this.getVertexData()[idx * layout.impetus$intStride() + layout.impetus$uvIndex()]);
    }

    // First UV set; the block atlas coordinates
    @Override
    public float getTexV(int idx) {
        var layout = impetus$layout();
        return Float.intBitsToFloat(this.getVertexData()[idx * layout.impetus$intStride() + layout.impetus$uvIndex() + 1]);
    }

    // Second UV set holds packed lightmap coordinates when present, else zero
    @Override
    public int getLight(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$lightIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    // Forge's packed per-vertex normal if the format carries one, else zero
    @Override
    public int getForgeNormal(int idx) {
        var layout = impetus$layout();
        int index = layout.impetus$normalIndex();
        return index >= 0 ? this.getVertexData()[idx * layout.impetus$intStride() + index] : 0;
    }

    // Mods sometimes leave the face null; treated as up so the quad still shades
    @Override
    public ModelQuadFacing getLightFace() {
        // Handle mods not supplying a light face
        var face = this.face;
        return face == null ? ModelQuadFacing.POS_Y : VintageDiffuseProvider.fromEnumFacing(face);
    }

    // Lazily computed from the vertex positions and cached; zero doubles as the unset marker
    @Override
    public int getComputedFaceNormal() {
        int n = this.normal;
        if (n == 0) {
            this.normal = n = QuadUtil.calculateNormal(this);
        }
        return n;
    }

    // Vanilla's tint index, exposed under Sodium's name
    @Override
    public int getColorIndex() {
        return this.tintIndex;
    }
}
