package com.bdmajora.impetus.engine.impl.model.quad.properties;

import lombok.Getter;
import com.bdmajora.impetus.engine.api.util.NormI8;

import java.util.Arrays;

// Directions a model quad can face.
public enum ModelQuadFacing {
    POS_X(1, 0, 0, Axis.X),
    POS_Y(0, 1, 0, Axis.Y),
    POS_Z(0, 0, 1, Axis.Z),
    NEG_X(-1, 0, 0, Axis.X),
    NEG_Y(0, -1, 0, Axis.Y),
    NEG_Z(0, 0, -1, Axis.Z),
    UNASSIGNED(0, 0, 0, null);

    // Every facing.
    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();
    // Every facing except the unassigned one.
    public static final ModelQuadFacing[] DIRECTIONS = Arrays.stream(VALUES).filter(facing -> facing != UNASSIGNED).toArray(ModelQuadFacing[]::new);
    // Every axis.
    public static final Axis[] AXES = ModelQuadFacing.Axis.values();

    // Number of facings.
    public static final int COUNT = VALUES.length;

    // Bit mask without a facing.
    public static final int NONE = 0;
    // Bit mask of every facing.
    public static final int ALL = (1 << COUNT) - 1;

    @Getter
    // Normal packed into an int.
    private final int packedNormal;

    @Getter
    // Step of the normal along each axis.
    private final int stepX, stepY, stepZ;

    @Getter
    // Axis of the facing or null.
    private final Axis axis;

    // Creates a facing from its normal and axis.
    ModelQuadFacing(int stepX, int stepY, int stepZ, Axis axis) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.axis = axis;
        this.packedNormal = NormI8.pack(stepX, stepY, stepZ);
    }

    // Returns the opposite facing and the unassigned facing for itself.
    public ModelQuadFacing getOpposite() {
        return switch (this) {
            case POS_Y -> NEG_Y;
            case NEG_Y -> POS_Y;
            case POS_X -> NEG_X;
            case NEG_X -> POS_X;
            case POS_Z -> NEG_Z;
            case NEG_Z -> POS_Z;
            default -> UNASSIGNED;
        };
    }

    // Returns whether the facing is a real direction.
    public boolean isDirection() {
        return this != UNASSIGNED;
    }

    // Axes of the facings.
    public enum Axis {
        X,
        Y,
        Z;

        // Returns the positive or negative facing of the axis.
        public ModelQuadFacing getFacing(boolean positive) {
            return switch (this) {
                case X -> positive ? POS_X : NEG_X;
                case Y -> positive ? POS_Y : NEG_Y;
                case Z -> positive ? POS_Z : NEG_Z;
            };
        }
    }
}
