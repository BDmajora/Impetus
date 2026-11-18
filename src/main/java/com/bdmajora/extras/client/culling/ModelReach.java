package com.bdmajora.extras.client.culling;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;
import java.util.List;

// Measures how far the item models an entity wears or holds reach out from its head.
public final class ModelReach {
    // Reach around the head pivot of a plain helmet or skull.
    static final float WORN_RADIUS = 0.75F;
    // Distance from the head pivot to where a held item is drawn.
    static final float HAND_OFFSET = 1.05F;
    // Offset of a head item from the head pivot.
    static final float HEAD_OFFSET = 0.25F;
    // Scale a head item is drawn at.
    static final float HEAD_SCALE = 0.625F;
    // Reach used when a model cannot be measured.
    static final float FALLBACK_RADIUS = 3.0F;
    // Largest number of measured models kept before the cache starts over.
    static final int MAX_MODELS = 8192;
    // Index of the head transform in a measurement.
    private static final int HEAD = 0;
    // Index of the right hand transform in a measurement.
    private static final int RIGHT_HAND = 1;
    // Index of the left hand transform in a measurement.
    private static final int LEFT_HAND = 2;

    // Measurements of each model by identity.
    private static final Reference2ObjectOpenHashMap<IBakedModel, float[]> MEASURED = new Reference2ObjectOpenHashMap<>();
    // Missing model of the loaded model set used to notice a resource reload.
    private static Object modelSet;
    // Number of model sets seen so far.
    private static int generation;

    // Prevents instantiation
    private ModelReach() {
    }

    // Drops every measurement when the models were reloaded.
    public static void beginTick(Minecraft client) {
        RenderItem items = client.getRenderItem();
        Object current = items == null ? null : items.getItemModelMesher().getModelManager().getMissingModel();
        if (current != modelSet) {
            modelSet = current;
            generation++;
            MEASURED.clear();
        }
    }

    // Returns the height of the head pivot above the feet.
    public static double pivotHeight(EntityLivingBase entity) {
        if (entity instanceof EntityArmorStand) {
            return entity.isChild() ? 0.75D : 1.5D;
        }
        return entity.height * 0.85D;
    }

    // Returns how far the equipment models reach from the head pivot or zero when nothing is worn or held.
    public static float radius(EntityLivingBase entity, ModelReachHolder holder) {
        ItemStack head = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        ItemStack mainHand = entity.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
        ItemStack offHand = entity.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);
        ModelReachState state = holder.impetus$modelReach();
        if (state == null) {
            if (head.isEmpty() && mainHand.isEmpty() && offHand.isEmpty()) {
                return 0.0F;
            }
            state = new ModelReachState();
            holder.impetus$setModelReach(state);
        }
        boolean stale = state.generation != generation;
        if (stale || head != state.head || head.getItemDamage() != state.headDamage) {
            state.head = head;
            state.headDamage = head.getItemDamage();
            state.headRadius = headRadius(entity, head);
        }
        if (stale || mainHand != state.mainHand || mainHand.getItemDamage() != state.mainHandDamage
                || offHand != state.offHand || offHand.getItemDamage() != state.offHandDamage) {
            state.mainHand = mainHand;
            state.mainHandDamage = mainHand.getItemDamage();
            state.offHand = offHand;
            state.offHandDamage = offHand.getItemDamage();
            state.handRadius = Math.max(handRadius(entity, mainHand), handRadius(entity, offHand));
        }
        state.generation = generation;
        boolean child = entity.isChild();
        return Math.max(state.headRadius * (child ? 0.7F : 1.0F), state.handRadius * (child ? 0.5F : 1.0F));
    }

    // Returns the reach of a head item around the head pivot.
    static float headRadius(EntityLivingBase entity, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        if (stack.getItem() == Items.SKULL || stack.getItem() instanceof ItemArmor armor && armor.armorType == EntityEquipmentSlot.HEAD) {
            return WORN_RADIUS;
        }
        return HEAD_OFFSET + HEAD_SCALE * measure(entity, stack)[HEAD];
    }

    // Returns the reach of a held item around the head pivot.
    static float handRadius(EntityLivingBase entity, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        float[] reach = measure(entity, stack);
        return HAND_OFFSET + Math.max(reach[RIGHT_HAND], reach[LEFT_HAND]);
    }

    // Returns the measurements of the model the stack is drawn with.
    private static float[] measure(EntityLivingBase entity, ItemStack stack) {
        IBakedModel model;
        try {
            model = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(stack, entity.world, entity);
        } catch (RuntimeException e) {
            return new float[] {FALLBACK_RADIUS, FALLBACK_RADIUS, FALLBACK_RADIUS};
        }
        float[] known = MEASURED.get(model);
        if (known == null) {
            known = new float[] {
                    extent(model, ItemCameraTransforms.TransformType.HEAD),
                    extent(model, ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND),
                    extent(model, ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND)};
            if (MEASURED.size() >= MAX_MODELS) {
                MEASURED.clear();
            }
            MEASURED.put(model, known);
        }
        return known;
    }

    // Returns the farthest distance of the transformed model from its centre.
    static float extent(IBakedModel model, ItemCameraTransforms.TransformType type) {
        try {
            Pair<? extends IBakedModel, Matrix4f> perspective = model.handlePerspective(type);
            IBakedModel shown = perspective == null || perspective.getLeft() == null ? model : perspective.getLeft();
            Matrix4f matrix = perspective == null ? null : perspective.getRight();
            double farthest = 0.0D;
            if (shown.isBuiltInRenderer()) {
                for (int corner = 0; corner < 8; corner++) {
                    farthest = Math.max(farthest, distance(matrix, corner & 1, corner >> 1 & 1, corner >> 2 & 1));
                }
                return (float) farthest;
            }
            farthest = Math.max(farthest, quadsExtent(shown.getQuads(null, null, 0L), matrix));
            for (EnumFacing side : EnumFacing.VALUES) {
                farthest = Math.max(farthest, quadsExtent(shown.getQuads(null, side, 0L), matrix));
            }
            return (float) farthest;
        } catch (RuntimeException e) {
            return FALLBACK_RADIUS;
        }
    }

    // Returns the farthest transformed vertex of the quads.
    private static double quadsExtent(List<BakedQuad> quads, Matrix4f matrix) {
        double farthest = 0.0D;
        for (int i = 0, n = quads.size(); i < n; i++) {
            BakedQuad quad = quads.get(i);
            VertexFormat format = quad.getFormat();
            int offset = positionOffset(format);
            int[] data = quad.getVertexData();
            int stride = format.getIntegerSize();
            if (offset < 0 || stride <= 0) {
                return FALLBACK_RADIUS;
            }
            for (int base = offset; base + 2 < data.length; base += stride) {
                farthest = Math.max(farthest, distance(matrix, Float.intBitsToFloat(data[base]),
                        Float.intBitsToFloat(data[base + 1]), Float.intBitsToFloat(data[base + 2])));
            }
        }
        return farthest;
    }

    // Returns the int offset of the float position in a vertex or minus one when there is none.
    static int positionOffset(VertexFormat format) {
        for (int i = 0, n = format.getElementCount(); i < n; i++) {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION) {
                return element.getType() == VertexFormatElement.EnumType.FLOAT ? format.getOffset(i) / 4 : -1;
            }
        }
        return -1;
    }

    // Returns the distance of a model point from the model centre after the transform.
    static double distance(Matrix4f matrix, double x, double y, double z) {
        double px = x - 0.5D;
        double py = y - 0.5D;
        double pz = z - 0.5D;
        if (matrix == null) {
            return Math.sqrt(px * px + py * py + pz * pz);
        }
        double tx = matrix.m00 * px + matrix.m01 * py + matrix.m02 * pz + matrix.m03;
        double ty = matrix.m10 * px + matrix.m11 * py + matrix.m12 * pz + matrix.m13;
        double tz = matrix.m20 * px + matrix.m21 * py + matrix.m22 * pz + matrix.m23;
        return Math.sqrt(tx * tx + ty * ty + tz * tz);
    }
}
