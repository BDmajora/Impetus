package com.bdmajora.impetus.impl.render.terrain.compile.light;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

import static com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing.*;

// Directional shading of 1.12 faces.
public enum VintageDiffuseProvider implements DiffuseProvider {
    // The one instance.
    INSTANCE;

    // Flag set when directional shading is off.
    private static boolean directionalShadingDisabled() {
        return com.bdmajora.impetus.umbra.material.WorldRenderingSettings.shouldDisableDirectionalShading();
    }

    // Returns the shade of a normal.
    @Override
    public float getDiffuse(float normalX, float normalY, float normalZ, boolean shade) {
        if (!shade || directionalShadingDisabled()) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(normalX, normalY, normalZ);
    }

    // Returns the facing of a quad facing.
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

    // Returns the quad facing of a 1.12 face.
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

    // Returns the quad facing of a 1.12 face or UNASSIGNED for none.
    public static ModelQuadFacing fromEnumFacingOrUnassigned(EnumFacing facing) {
        if (facing == null) {
            return UNASSIGNED;
        }
        return fromEnumFacing(facing);
    }

    // Returns the shade of a face.
    @Override
    public float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        if (!shade || directionalShadingDisabled()) {
            return 1.0f;
        }
        return DIFFUSE_BY_FACING[lightFace.ordinal()];
    }

    // Diffuse by facing.
    private static final float[] DIFFUSE_BY_FACING = buildDiffuseTable();

    // Returns the shade of each face.
    private static float[] buildDiffuseTable() {
        float[] table = new float[ModelQuadFacing.COUNT];
        for (ModelQuadFacing facing : ModelQuadFacing.DIRECTIONS) {
            table[facing.ordinal()] = LightUtil.diffuseLight(toEnumFacing(facing));
        }
        table[UNASSIGNED.ordinal()] = 1.0f;
        return table;
    }
}
