package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;

import java.util.Objects;

// Bake-scoped pools for camera transforms and the override list, which every baked model carries and almost never varies; FoamFix's dedup done at construction instead of a reflective post-bake walk
public final class TransformCaches {
    // ItemCameraTransforms defines no equals, so the strategy compares its eight vectors in the same order for hash and equality
    private static final Hash.Strategy<ItemCameraTransforms> TRANSFORMS_STRATEGY =
            new Hash.Strategy<ItemCameraTransforms>() {
                // Structural hash over all eight transforms, so equal-by-value instances collide
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

                // Structural equality; vanilla's class does not define equals
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

    public static final DeduplicationCache<ItemCameraTransforms> TRANSFORMS =
            new DeduplicationCache<>("Camera transforms", CoarctatioConfig.get().poolSizeLimit, TRANSFORMS_STRATEGY);

    private TransformCaches() {
    }

    // ItemTransformVec3f does define hashCode, so this only has to add null tolerance. Forge deprecates the type for TRSRTransformation, but vanilla's ItemCameraTransforms is built from it
    @SuppressWarnings("deprecation")
    private static int vectorHash(ItemTransformVec3f vector) {
        return vector == null ? 0 : vector.hashCode();
    }

    // Collapses an empty override list onto ItemOverrideList.NONE; JSON models with no `overrides` still get a fresh empty instance, and non-empty lists are genuinely per-model
    public static ItemOverrideList deduplicate(ItemOverrideList overrides) {
        if (overrides == null) {
            return null;
        }

        return overrides.getOverrides().isEmpty() ? ItemOverrideList.NONE : overrides;
    }

    // Arms the pools for a bake
    public static void open() {
        TRANSFORMS.open();
    }

    // Drops the pool indices after a bake; the shared objects live on in the models
    public static void close() {
        TRANSFORMS.close();
    }
}
