package com.bdmajora.equilibrium.mixin.world.explosions.block_raycast;

import com.bdmajora.equilibrium.common.world.ChunkSectionCursor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Mixin that changes Explosion.
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Reference to the field exploder of the target.
    @Shadow
    @Final
    private Entity exploder;

    // Reference to the field size of the target.
    @Shadow
    @Final
    private float size;

    // Reference to the field x of the target.
    @Shadow
    @Final
    private double x;

    // Reference to the field y of the target.
    @Shadow
    @Final
    private double y;

    // Reference to the field z of the target.
    @Shadow
    @Final
    private double z;

    // Reference to the field affected block positions of the target.
    @Shadow
    @Final
    private List<BlockPos> affectedBlockPositions;

    @Shadow
    @Final
    private Map<EntityPlayer, Vec3d> playerKnockbackMap;

    // Replaces the whole method doExplosionA.
    @Overwrite
    public void doExplosionA() {
        Set<BlockPos> affected = new HashSet<>();

        ChunkSectionCursor cursor = new ChunkSectionCursor(this.world, true);

        for (int rayX = 0; rayX < 16; ++rayX) {
            for (int rayY = 0; rayY < 16; ++rayY) {
                for (int rayZ = 0; rayZ < 16; ++rayZ) {
                    if (rayX != 0 && rayX != 15 && rayY != 0 && rayY != 15 && rayZ != 0 && rayZ != 15) {
                        continue;
                    }

                    double dirX = rayX / 15.0F * 2.0F - 1.0F;
                    double dirY = rayY / 15.0F * 2.0F - 1.0F;
                    double dirZ = rayZ / 15.0F * 2.0F - 1.0F;

                    double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                    dirX /= length;
                    dirY /= length;
                    dirZ /= length;

                    float energy = this.size * (0.7F + this.world.rand.nextFloat() * 0.6F);

                    double stepX = this.x;
                    double stepY = this.y;
                    double stepZ = this.z;

                    int lastX = Integer.MIN_VALUE;
                    int lastY = Integer.MIN_VALUE;
                    int lastZ = Integer.MIN_VALUE;

                    for (; energy > 0.0F; energy -= 0.22500001F) {
                        int blockX = MathHelper.floor(stepX);
                        int blockY = MathHelper.floor(stepY);
                        int blockZ = MathHelper.floor(stepZ);

                        if (blockX != lastX || blockY != lastY || blockZ != lastZ) {
                            lastX = blockX;
                            lastY = blockY;
                            lastZ = blockZ;

                            IBlockState state = cursor.getBlockState(blockX, blockY, blockZ);

                            BlockPos pos = null;

                            if (state.getMaterial() != Material.AIR) {
                                pos = new BlockPos(blockX, blockY, blockZ);

                                float resistance = this.exploder != null
                                        ? this.exploder.getExplosionResistance((Explosion) (Object) this, this.world, pos, state)
                                        : state.getBlock().getExplosionResistance(this.world, pos, null, (Explosion) (Object) this);

                                energy -= (resistance + 0.3F) * 0.3F;
                            }

                            if (energy > 0.0F) {
                                if (pos == null) {
                                    pos = new BlockPos(blockX, blockY, blockZ);
                                }

                                if (this.exploder == null
                                        || this.exploder.canExplosionDestroyBlock((Explosion) (Object) this, this.world, pos, state, energy)) {
                                    affected.add(pos);
                                }
                            }
                        }

                        stepX += dirX * 0.30000001192092896D;
                        stepY += dirY * 0.30000001192092896D;
                        stepZ += dirZ * 0.30000001192092896D;
                    }
                }
            }
        }

        this.affectedBlockPositions.addAll(affected);

        float radius = this.size * 2.0F;

        int minX = MathHelper.floor(this.x - radius - 1.0D);
        int maxX = MathHelper.floor(this.x + radius + 1.0D);
        int minY = MathHelper.floor(this.y - radius - 1.0D);
        int maxY = MathHelper.floor(this.y + radius + 1.0D);
        int minZ = MathHelper.floor(this.z - radius - 1.0D);
        int maxZ = MathHelper.floor(this.z + radius + 1.0D);

        List<Entity> entities = this.world.getEntitiesWithinAABBExcludingEntity(this.exploder,
                new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));

        net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.world, (Explosion) (Object) this, entities, radius);

        Vec3d center = new Vec3d(this.x, this.y, this.z);

        for (int i = 0; i < entities.size(); ++i) {
            Entity entity = entities.get(i);

            if (entity.isImmuneToExplosions()) {
                continue;
            }

            double normalisedDistance = entity.getDistance(this.x, this.y, this.z) / radius;

            if (normalisedDistance > 1.0D) {
                continue;
            }

            double deltaX = entity.posX - this.x;
            double deltaY = entity.posY + entity.getEyeHeight() - this.y;
            double deltaZ = entity.posZ - this.z;

            double distance = MathHelper.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            if (distance == 0.0D) {
                continue;
            }

            deltaX /= distance;
            deltaY /= distance;
            deltaZ /= distance;

            double exposure = this.world.getBlockDensity(center, entity.getEntityBoundingBox());
            double impact = (1.0D - normalisedDistance) * exposure;

            entity.attackEntityFrom(DamageSource.causeExplosionDamage((Explosion) (Object) this),
                    (float) ((int) ((impact * impact + impact) / 2.0D * 7.0D * radius + 1.0D)));

            double knockback = impact;

            if (entity instanceof EntityLivingBase) {
                knockback = EnchantmentProtection.getBlastDamageReduction((EntityLivingBase) entity, impact);
            }

            entity.motionX += deltaX * knockback;
            entity.motionY += deltaY * knockback;
            entity.motionZ += deltaZ * knockback;

            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;

                if (!player.isSpectator() && (!player.isCreative() || !player.capabilities.isFlying)) {
                    this.playerKnockbackMap.put(player, new Vec3d(deltaX * impact, deltaY * impact, deltaZ * impact));
                }
            }
        }
    }
}
