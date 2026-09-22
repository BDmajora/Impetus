package com.bdmajora.impetus.impl.render.terrain.compile.light;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

import static com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing.*;

public enum VintageDiffuseProvider implements DiffuseProvider {
    INSTANCE;

    // True when the pack set oldLighting=false, so vanilla's per-face 0.5/0.6/0.8 shading must NOT be baked into the vertex colour on top of the pack's own normal-derived lighting; Iris forces the shade lookup to UP, OptiFine sets its shade constants to 1.0
    private static boolean directionalShadingDisabled() {
        return com.bdmajora.impetus.umbra.material.WorldRenderingSettings.shouldDisableDirectionalShading();
    }

    // Vanilla's per-normal diffuse, or full brightness when shading is off
    @Override
    public float getDiffuse(float normalX, float normalY, float normalZ, boolean shade) {
        if (!shade || directionalShadingDisabled()) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(normalX, normalY, normalZ);
    }

    // Sodium facing to vanilla; UNASSIGNED has no equivalent and yields null
    public static EnumFacing toEnumFacing(ModelQuadFacing facing) {
        return switch (facing) {
            case NEG_Y -> EnumFacing.DOWN;
            case POS_Y -> EnumFacing.UP;
            case NEG_Z -> EnumFacing.NORTH;
            case POS_Z -> EnumFacing.SOUTH;
            case NEG_X -> EnumFacing.WEST;
            case POS_X -> EnumFacing.EAST;
            case UNASSIGNED -> throw new IllegalArgumentException();
        };
    }

    // Vanilla facing to Sodium
    public static ModelQuadFacing fromEnumFacing(EnumFacing facing) {
        return switch (facing) {
            case DOWN  -> NEG_Y;
            case UP    -> POS_Y;
            case NORTH -> NEG_Z;
            case SOUTH -> POS_Z;
            case WEST  -> NEG_X;
            case EAST  -> POS_X;
        };
    }

    // Null-tolerant variant for quads with no light face
    public static ModelQuadFacing fromEnumFacingOrUnassigned(EnumFacing facing) {
        if (facing == null) {
            return UNASSIGNED;
        }
        return fromEnumFacing(facing);
    }

    // Per-face diffuse from the table; this runs per quad and LightUtil recomputes the formula on every call
    @Override
    public float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        if (!shade || directionalShadingDisabled()) {
            return 1.0f;
        }
        return DIFFUSE_BY_FACING[lightFace.ordinal()];
    }

    // Vanilla's six per-face constants (0.5 down, 1.0 up, 0.8 N/S, 0.6 E/W) indexed by ModelQuadFacing ordinal; UNASSIGNED gets full brightness
    private static final float[] DIFFUSE_BY_FACING = buildDiffuseTable();

    private static float[] buildDiffuseTable() {
        float[] table = new float[ModelQuadFacing.COUNT];
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            table[facing.ordinal()] = LightUtil.diffuseLight(toEnumFacing(facing));
        }
        table[UNASSIGNED.ordinal()] = 1.0f;
        return table;
    }
}
