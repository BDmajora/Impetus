package com.bdmajora.extras.client.particle;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.util.EnumParticleTypes;

// Switches that hide particle types.
public final class ParticleFilters {
    // Filter of each particle identifier.
    private static final Filter[] BY_ID = buildTable();

    // Prevents instantiation
    private ParticleFilters() {
    }

    // Returns whether the particle is shown.
    public static boolean isAllowed(int particleId) {
        ExtrasConfig options = Extras.options();

        if (!options.particle.all) {
            return false;
        }

        if (particleId < 0 || particleId >= BY_ID.length) {
            return true;
        }

        Filter filter = BY_ID[particleId];
        return filter == null || filter.isEnabled(options);
    }

    // Returns the filter of each particle identifier.
    private static Filter[] buildTable() {
        int size = 0;
        for (EnumParticleTypes type : EnumParticleTypes.values()) {
            size = Math.max(size, type.getParticleID() + 1);
        }

        Filter[] table = new Filter[size];

        put(table, Filter.EXPLOSION, EnumParticleTypes.EXPLOSION_NORMAL,
                EnumParticleTypes.EXPLOSION_LARGE, EnumParticleTypes.EXPLOSION_HUGE);
        put(table, Filter.SMOKE, EnumParticleTypes.SMOKE_NORMAL, EnumParticleTypes.SMOKE_LARGE);
        put(table, Filter.FLAME, EnumParticleTypes.FLAME);
        put(table, Filter.REDSTONE, EnumParticleTypes.REDSTONE);
        put(table, Filter.PORTAL, EnumParticleTypes.PORTAL);

        put(table, Filter.WATER, EnumParticleTypes.SUSPENDED);
        put(table, Filter.VOID, EnumParticleTypes.SUSPENDED_DEPTH);
        put(table, Filter.POTION, EnumParticleTypes.SPELL, EnumParticleTypes.SPELL_INSTANT,
                EnumParticleTypes.SPELL_MOB, EnumParticleTypes.SPELL_MOB_AMBIENT,
                EnumParticleTypes.SPELL_WITCH);
        put(table, Filter.DRIPPING, EnumParticleTypes.DRIP_WATER, EnumParticleTypes.DRIP_LAVA);
        put(table, Filter.FIREWORK, EnumParticleTypes.FIREWORKS_SPARK);

        return table;
    }

    // Assigns the filter to the particle types.
    private static void put(Filter[] table, Filter filter, EnumParticleTypes... types) {
        for (EnumParticleTypes type : types) {
            table[type.getParticleID()] = filter;
        }
    }

    // Switch that governs a group of particle types.
    private enum Filter {
        // Explosion particles.
        EXPLOSION {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.animation.explosion;
            }
        },
        // Smoke particles.
        SMOKE {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.animation.smoke;
            }
        },
        // Flame particles.
        FLAME {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.animation.flame;
            }
        },
        // Redstone particles.
        REDSTONE {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.animation.redstone;
            }
        },
        // Portal particles.
        PORTAL {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.portalParticles;
            }
        },
        // Underwater particles.
        WATER {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.waterParticles;
            }
        },
        // Void particles.
        VOID {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.voidParticles;
            }
        },
        // Potion particles.
        POTION {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.potionParticles;
            }
        },
        // Dripping particles.
        DRIPPING {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.drippingWaterLava;
            }
        },
        // Firework particles.
        FIREWORK {
            @Override
            boolean isEnabled(ExtrasConfig options) {
                return options.particle.fireworkParticles;
            }
        };

        // Returns whether the group is shown.
        abstract boolean isEnabled(ExtrasConfig options);
    }
}
