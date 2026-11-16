package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;

import java.util.Objects;

// Pools for item camera transforms.
public final class TransformCaches {
    // Equality rule that compares every display slot.
    private static final Hash.Strategy<ItemCameraTransforms> TRANSFORMS_STRATEGY =
            new Hash.Strategy<ItemCameraTransforms>() {
                // Returns a hash over every display slot.
                @Override
                public int hashCode(ItemCameraTransforms transforms) {
                    if (transforms == null) {
                        return 0;
                    }

                    int hash = vectorHash(transforms.thirdperson_left);
                    hash = 31 * hash + vectorHash(transforms.thirdperson_right);
                    hash = 31 * hash + vectorHash(transforms.firstperson_left);
                    hash = 31 * hash + vectorHash(transforms.firstperson_right);
                    hash = 31 * hash + vectorHash(transforms.head);
                    hash = 31 * hash + vectorHash(transforms.gui);
                    hash = 31 * hash + vectorHash(transforms.ground);
                    return 31 * hash + vectorHash(transforms.fixed);
                }

                // Returns whether every display slot is equal.
                @Override
                public boolean equals(ItemCameraTransforms a, ItemCameraTransforms b) {
                    if (a == b) {
                        return true;
                    }
                    if (a == null || b == null) {
                        return false;
                    }

                    return Objects.equals(a.thirdperson_left, b.thirdperson_left)
                            && Objects.equals(a.thirdperson_right, b.thirdperson_right)
                            && Objects.equals(a.firstperson_left, b.firstperson_left)
                            && Objects.equals(a.firstperson_right, b.firstperson_right)
                            && Objects.equals(a.head, b.head)
                            && Objects.equals(a.gui, b.gui)
                            && Objects.equals(a.ground, b.ground)
                            && Objects.equals(a.fixed, b.fixed);
                }
            };

    // Pool of camera transforms.
    public static final DeduplicationCache<ItemCameraTransforms> TRANSFORMS =
            new DeduplicationCache<>("Camera transforms", CoarctatioConfig.get().poolSizeLimit, TRANSFORMS_STRATEGY);

    // Prevents instantiation
    private TransformCaches() {
    }

    // Returns the hash of a transform or zero when null.
    @SuppressWarnings("deprecation")
    private static int vectorHash(ItemTransformVec3f vector) {
        return vector == null ? 0 : vector.hashCode();
    }

    // Returns the shared empty list for a list without overrides.
    public static ItemOverrideList deduplicate(ItemOverrideList overrides) {
        if (overrides == null) {
            return null;
        }

        return overrides.getOverrides().isEmpty() ? ItemOverrideList.NONE : overrides;
    }

    // Opens the pool.
    public static void open() {
        TRANSFORMS.open();
    }

    // Closes the pool.
    public static void close() {
        TRANSFORMS.close();
    }
}
